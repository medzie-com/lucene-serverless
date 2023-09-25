package dev.arseny.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import dev.arseny.RequestUtils;
import dev.arseny.model.IndexRequest;
import dev.arseny.service.IndexWriterService;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.TermQuery;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.management.Query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Pair<T, U> {
    private T left;
    private U right;

    public Pair(T left, U right) {
        this.left = left;
        this.right = right;
    }

    public T getLeft() {
        return this.left;
    }

    public U getRight() {
        return this.right;
    }
}

@Named("index")
public class IndexHandler implements RequestHandler<SQSEvent, Integer> {
    private static final Logger LOG = Logger.getLogger(IndexHandler.class);

    @Inject
    protected IndexWriterService indexWriterService;

    @Override
    public Integer handleRequest(SQSEvent event, Context context) {
        List<SQSEvent.SQSMessage> records = event.getRecords();

        List<IndexRequest> requests = new ArrayList<>();

        for (SQSEvent.SQSMessage record : records) {
            requests.add(RequestUtils.parseIndexRequest(record.getBody()));
        }

        Map<String, IndexWriter> writerMap = new HashMap<>();

        for (IndexRequest request : requests) {
            IndexWriter writer;
            if (writerMap.containsKey(request.getIndexName())) {
                writer = writerMap.get(request.getIndexName());
            } else {
                writer = indexWriterService.getIndexWriter(request.getIndexName());
                writerMap.put(request.getIndexName(), writer);
            }

            final List<Pair<org.apache.lucene.search.Query, Document>> documents = new ArrayList<>();

            for (Map<String, Object> requestDocument : request.getDocuments()) {
                Document document = new Document();
                for (Map.Entry<String, Object> entry : requestDocument.entrySet()) {
                    document.add(new TextField(entry.getKey(), entry.getValue().toString(), Field.Store.YES));
                }

                final org.apache.lucene.search.Query query = new TermQuery(
                        new Term("id", requestDocument.get("id").toString()));

                documents.add(new Pair<org.apache.lucene.search.Query, Document>(query, document));
            }

            try {
                documents.stream().map(v -> v.getLeft()).forEach(t -> {
                    try {
                        writer.deleteDocuments(t);
                    } catch (IOException e) {
                        LOG.error(e);
                    }
                });
                writer.addDocuments(documents.stream().map(v -> v.getRight()).collect(Collectors.toList()));
            } catch (IOException e) {
                LOG.error(e);
            }
        }

        for (IndexWriter writer : writerMap.values()) {
            try {
                writer.commit();
            } catch (IOException e) {
                LOG.error(e);
                return 500;
            } finally {
                try {
                    writer.close();
                } catch (IOException e) {
                    LOG.error(e);
                    return 500;
                }
            }
        }

        return 200;
    }
}
