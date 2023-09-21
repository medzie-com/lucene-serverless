package dev.arseny.model;

public class DeleteIndexRequest {

    public DeleteIndexRequest() {
    }

    public String getIndexName() {
        return System.getenv("index");
    }
}
