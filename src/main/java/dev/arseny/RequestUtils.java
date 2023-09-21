package dev.arseny;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import dev.arseny.model.*;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;

public class RequestUtils {
    private static final Logger LOG = Logger.getLogger(RequestUtils.class);

    static ObjectWriter writer = new ObjectMapper().writerFor(ErrorResponse.class);
    static ObjectWriter queryResponseWriter = new ObjectMapper().writerFor(QueryResponse.class);
    static ObjectReader indexRequestReader = new ObjectMapper().readerFor(IndexRequest.class);
    static ObjectWriter indexRequestWriter = new ObjectMapper().writerFor(IndexRequest.class);
    static ObjectReader deleteIndexRequestReader = new ObjectMapper().readerFor(DeleteIndexRequest.class);
    static ObjectReader queryRequestReader = new ObjectMapper().readerFor(QueryRequest.class);

    public static IndexRequest parseIndexRequest(String eventBody) {
        try {
            return indexRequestReader.readValue(eventBody);
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse list of Index Requests in body", e);
        }
    }

    public static String writeIndexRequest(IndexRequest request) {
        try {
            return indexRequestWriter.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to serialize list of Index Requests in body", e);
        }
    }

    public static DeleteIndexRequest parseDeleteIndexRequest(InputStream event) {
        return new DeleteIndexRequest();
    }

    public static QueryRequest parseQueryRequest(String event) {
        QueryRequest request = new QueryRequest();
        request.setQuery(event);
        return request;
    }
}
