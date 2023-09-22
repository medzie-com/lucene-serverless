package dev.arseny.model;

import java.util.List;
import java.util.Map;

public class IndexRequest {
    private String indexName;
    private List<Map<String, Object>> documents;

    public IndexRequest() {
    }

    public String getIndexName() {
        return this.indexName;
    }

    public void setIndexName(String value) {
        this.indexName = value;
    }

    public List<Map<String, Object>> getDocuments() {
        return documents;
    }

    public void setDocuments(List<Map<String, Object>> documents) {
        this.documents = documents;
    }
}
