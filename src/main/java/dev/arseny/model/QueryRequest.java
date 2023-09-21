package dev.arseny.model;

public class QueryRequest {
    private String query;

    public QueryRequest() {
    }

    public String getIndexName() {
        return System.getenv("index");
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
