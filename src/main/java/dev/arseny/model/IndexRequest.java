package dev.arseny.model;

import java.util.List;
import java.util.Map;

public class IndexRequest {
    private List<Map<String, Object>> documents;

    public IndexRequest() {
    }

    public String getIndexName() {
        return System.getenv("index");
    }

    public List<Map<String, Object>> getDocuments() {
        return documents;
    }

    public void setDocuments(List<Map<String, Object>> documents) {
        this.documents = documents;
    }
}
