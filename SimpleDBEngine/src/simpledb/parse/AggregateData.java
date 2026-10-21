package simpledb.parse;

public class AggregateData {
    private String function;
    private String field;

    public AggregateData(String function, String field) {
        this.function = function;
        this.field = field;
    }

    public String function() {
        return function;
    }

    public String field() {
        return field;
    }
}
