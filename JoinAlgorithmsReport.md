# SimpleDB: Supporting Multiple Join Algorithms

This report describes the changes made to SimpleDB so that the query planner
can execute joins with three different algorithms, sort-merge join, index-based
join and nested-loops join, and choose between them. All work was done in the
`SimpleDBEngine` project; the planner pair in use is
`(HeuristicQueryPlanner, IndexUpdatePlanner)`.

## 1. Summary of changes

| File | Change |
|---|---|
| `src/simpledb/server/SimpleDB.java` | Switched the planner pair to `HeuristicQueryPlanner` + `IndexUpdatePlanner`. |
| `src/simpledb/opt/TablePlanner.java` | `makeJoinPlan` now builds an index-join, a sort-merge-join and a nested-loops-join candidate and returns the one with the lowest estimated block accesses. A system property `simpledb.join` (`index`, `merge`, `nested`, default `auto`) can force an algorithm. Each planner records a one-line description of the plan it produced. |
| `src/simpledb/opt/HeuristicQueryPlanner.java` | Prints the access path chosen for every table (`[plan] ...`) so the join algorithm is visible; adds the `order by` `SortPlan` on top of the projection (this planner previously ignored the `order by` clause added in the last assignment); the table-planner list is now local to `createPlan` so the planner is safe for concurrent queries. |
| `src/simpledb/plan/NestedLoopJoinPlan.java` (new) | Plan for the nested-loops join. Adapted from `ProductPlan` but carries the join predicate. |
| `src/simpledb/query/NestedLoopJoinScan.java` (new) | Scan for the nested-loops join. Adapted from `ProductScan`; the join predicate is evaluated inside `next()`, so no `SelectScan` is placed above it. |
| `src/simpledb/materialize/MergeJoinPlan.java` | `blocksAccessed()` now includes the one-time cost of sorting both inputs, so the merge join can be compared fairly with the other algorithms. |
| `src/simpledb/materialize/SortPlan.java` | New `preprocessingCost()` estimate used by `MergeJoinPlan`. `open()` no longer fails when the input is empty (it used to call `runs.get(0)` on an empty list). |
| `src/simpledb/materialize/SortScan.java` | `savePosition()`/`restorePosition()` now also save and restore `hasmore1`, `hasmore2` and `currentscan`. Restoring only the RIDs left the scan reading from the wrong run when the sorted input consisted of two runs, which gave wrong merge-join results. |
| `src/simpledb/query/Constant.java` | `equals()` is null-safe. `MergeJoinScan.next()` compares the first RHS value with a `null` join value and previously threw a `NullPointerException`. |
| `src/simpledb/query/Predicate.java` | New `withoutEquality(f1, f2)` returns the residual predicate after removing the equality term that an index join or merge join enforces itself. |
| `src/simpledb/metadata/IndexInfo.java` | The estimated number of index blocks is rounded up and never below 1. With tiny tables it was 0, and `BTreeIndex.searchCost` took `log(0)`, which overflowed to a large negative cost that made the index join look free. |
| `src/simpledb/opt/JoinTest.java` (new) | Test program: runs ten join queries under every join mode and checks that all modes return the same rows. |
| `bin/...` | Rebuilt from `src`. The committed class files had drifted from the source (for example `IndexMgr.class` and `MetadataMgr.class` called constructor and method signatures that no longer exist), so the whole tree was recompiled with `javac --release 17`, matching the bytecode version already in `bin`. |

## 2. The three join algorithms

`TablePlanner.makeJoinPlan(current)` is called by the heuristic planner for every
table not yet in the join order. It first extracts the join sub-predicate, the
terms that mention both the current plan and this table. If there is none the
method returns `null` and the planner falls back to a product. Otherwise it
builds up to three candidate plans.

### 2.1 Index join (part b)

`makeIndexJoin` looks for an index on a field of this table that the join
predicate equates (`=`) with a field of the current plan. Only equality terms
are considered, which is all a hash index can support. The plan is

```
Select[residual join terms]( Select[this table's own terms]( IndexJoin(current, table, index) ) )
```

The two selects are only added when there is something left to check. Because
the index join already enforces the equality term, that term is removed from the
residual predicate with `Predicate.withoutEquality`. In the original code the
whole join predicate was re-applied by a select, which double-counted the
reduction factor and made the estimated output of an index join artificially
small.

Note that `IndexJoinPlan` needs the indexed table to be the right-hand (inner)
side, so an index is only usable when the indexed table is joined *into* the
current plan. Since the heuristic planner starts with the smallest table, a
two-table query whose indexed table happens to be the smaller one cannot use the
index (query 2 in the results below).

### 2.2 Sort-merge join (part a)

`makeMergeJoin` looks for any equality term between a field of this table and a
field of the current plan and then builds

```
Select[residual join terms]( MergeJoin(current, Select[table's own terms](table), outerfield, innerfield) )
```

`MergeJoinPlan` wraps both inputs in a `SortPlan` on their join field and opens a
`MergeJoinScan`. Getting the provided merge-join code to actually work required
the three fixes listed above in `Constant`, `SortScan` and `SortPlan`.

### 2.3 Nested-loops join (part c)

`makeNestedLoopJoin` always applies and produces

```
NestedLoopJoin(current, Select[table's own terms](table), joinpred)
```

`NestedLoopJoinScan` iterates like `ProductScan` (the current plan is the outer
loop, the table the inner loop) but only returns pairs for which
`pred.isSatisfied(this)` holds. The whole join predicate, including
non-equality terms such as `gradyear > yearoffered`, is evaluated inside the
scan, so there is no separate selection operator. This is also the only one of
the three algorithms that can evaluate a non-equijoin.

## 3. Choosing the algorithm

The candidate with the lowest `blocksAccessed()` wins; ties go to the index
join, then the merge join. The estimates are:

| Algorithm | Estimated block accesses |
|---|---|
| Nested-loops join | `B(outer) + R(outer) * B(inner)` |
| Index join | `B(outer) + R(outer) * B(index) + R(join)` |
| Sort-merge join | `sort(outer) + sort(inner) + B(sorted outer) + B(sorted inner)`, where `sort(p) = B(p) + M + 2*M*passes`, `M` is the size of the materialised table and `passes` is the number of two-way merge passes needed to reduce `R/2` initial runs to two |

The sort cost is the addition that the original `MergeJoinPlan` left out (it
counted only the final merge pass). Without it a merge join always looked
cheaper than a nested-loops join, even for one-block tables.

For experiments, the choice can be forced on the command line, for example
`java -Dsimpledb.join=merge simpledb.test.SimpleIJ`, or from a program with
`System.setProperty("simpledb.join", "index")`. If the forced algorithm is not
applicable (no index, or no equality term), the cost-based choice is used and
the printed plan says so.

Every table's access path is printed when the plan is built, for example:

```
[plan] dept: tablescan
[plan] student: nestedloopjoin[majorid=did] {indexjoin=B10 mergejoin=B8 nestedloopjoin=B4}
[plan] enroll: indexjoin (forced)[sid=studentid] {indexjoin=B22 mergejoin=B19 nestedloopjoin=B16}
```

## 4. Running the tests

Compile the engine, then run from the `SimpleDBEngine` directory (which contains
`studentdb`). The planner rewrites the log and creates `temp*.tbl` files, so run
against a copy of the database, or delete/rename the old one, if you want to
keep the original untouched.

```
cd SimpleDBEngine
javac -d bin $(find src -name "*.java")
java -cp bin simpledb.opt.JoinTest              # all 10 queries, all 4 join modes
java -cp bin simpledb.test.SimpleIJ             # interactive, cost-based choice
java -Dsimpledb.join=merge -cp bin simpledb.test.SimpleIJ   # force sort-merge join
```

`JoinTest` takes an optional database name as its first argument.

## 5. Results on the student database

`JoinTest` was run on a copy of the committed `studentdb` (student 9 rows, dept
3, course 6, section 5, enroll 6; indexes: btree on `student.majorid`, hash on
`enroll.studentid`, btree on `enroll.sectionid`). The table shows the join
order and algorithm chosen in `auto` mode, the estimated block accesses of the
three candidates at each join step (`index / merge / nested`, `-` = not
applicable), and whether the four modes returned the same rows.

| # | Query (`where` clause) | Join order and algorithm chosen (auto) | Candidate costs | Rows | All modes agree |
|---|---|---|---|---|---|
| 1 | student, dept: `majorid = did` | dept, then student: nested-loops | 10 / 8 / 4 | 9 | yes |
| 2 | student, enroll: `sid = studentid` | enroll, then student: nested-loops | - / 10 / 7 | 4 | yes |
| 3 | student, enroll: `sid = studentid and grade = 'A'` | enroll (select), then student: nested-loops | - / 8 / 3 | 2 | yes |
| 4 | student, dept: `majorid = did and gradyear = 2020` | student (select), then dept: nested-loops | - / 6 / 3 | 3 | yes |
| 5 | student, dept, enroll: `majorid = did and sid = studentid` | dept; student: nested-loops; enroll: nested-loops | 10 / 8 / 4, then 16 / 13 / 10 | 4 | yes |
| 6 | student, enroll, section, course: `sid = studentid and sectionid = sectid and courseid = cid` | section; enroll: nested-loops; course: nested-loops; student: nested-loops | 16 / 8 / 6, then - / 23 / 16, then - / 53 / 36 | 4 | yes |
| 7 | student, section: `gradyear < yearoffered` | section, then student: nested-loops (only applicable algorithm) | - / - / 6 | 0 | yes |
| 8 | student, enroll, section: `sid = studentid and sectionid = sectid and gradyear > yearoffered` | section; enroll: nested-loops; student: nested-loops on `sid=studentid and gradyear>yearoffered` | 16 / 8 / 6, then - / 23 / 16 | 4 | yes |
| 9 | student, dept: `majorid = did and gradyear = 1999` | student (select, empty), then dept: nested-loops | - / 6 / 3 | 0 | yes |
| 10 | student, dept: `majorid = did order by dname, sname desc` | dept, then student: nested-loops, then sort | 10 / 8 / 4 | 9 | yes |

Forcing `index` used the index join in queries 1, 5, 6, 8 and 10 (the btree on
`student.majorid` and the two indexes on `enroll`); forcing `merge` used the
sort-merge join in every query with an equality term (all but 7). In every case
the rows were identical to the `auto` and `nested` runs (only the output order
differed, e.g. the merge join returns rows in join-key order). Query 10 shows
the `order by` clause working on top of a join plan.

Sample output for query 1:

```
QUERY: select sname, dname from student, dept where majorid = did
--- join mode: auto
[plan] dept: tablescan
[plan] student: nestedloopjoin[majorid=did] {indexjoin=B10 mergejoin=B8 nestedloopjoin=B4}
    sname=lee, dname=compsci
    sname=max, dname=compsci
    sname=pat, dname=math
    sname=kim, dname=math
    sname=sue, dname=math
    sname=amy, dname=math
    sname=sam, dname=drama
    sname=art, dname=drama
    sname=bob, dname=drama
    (9 rows)
--- join mode: index
[plan] student: indexjoin (forced)[majorid=did] {...}      (same 9 rows)
--- join mode: merge
[plan] student: mergejoin (forced)[majorid=did] {...}       (same 9 rows, sorted by majorid)
RESULT: 9 rows; all join modes agree: true
```

### Index maintenance through the IndexUpdatePlanner

A second run started from an empty directory: `simpledb.test.CreateStudentDB`
created the tables, then the following statements were executed through
`SimpleIJ`:

```
create index studentidx on enroll(studentid) using hash
create index majoridx on student(majorid) using btree
create index sectionidx on enroll(sectionid) using btree
insert into student(sid, sname, majorid, gradyear) values (10, 'zed', 30, 2023)
insert into enroll(eid, studentid, sectionid, grade) values (74, 10, 13, 'B')
delete from enroll where eid = 24
update student set majorid = 20 where sid = 5
```

Afterwards `select sname, dname, grade from student, dept, enroll where majorid
= did and sid = studentid` returned the same six rows in `auto`, forced `index`
and forced `merge` mode (including the new `zed / drama / B` row and without
joe's deleted `C` enrolment), and `select sname, grade from student, enroll where
sid = studentid and majorid = 30` used the btree index select on `majorid`
followed by an index join on `studentid` and returned only `zed / B`, showing
that the hash and btree indexes were maintained by the inserts, delete and
update.

## 6. Why the planner picks nested loops here, and when it would not

Every table in the student database fits in one block, so the nested-loops
estimate `B(outer) + R(outer) * 1` is only a handful of block accesses and
nothing can beat it. The other algorithms pay off on larger tables. With the
same formulas, joining T1 (1,000 records, 100 blocks) to T2 (10,000 records,
1,000 blocks, btree index on the join field) gives roughly:

| Algorithm | Estimated block accesses |
|---|---|
| Nested-loops join | 100 + 1,000 * 1,000 = 1,000,100 |
| Sort-merge join | sort(T1) 1,800 + sort(T2) 26,000 + merge 1,100 = 28,900 |
| Index join | 100 + 1,000 * 2 + 2,000 = 4,100 |

so the planner would choose the index join, and the sort-merge join if the
index did not exist.
