package dev.arseny.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import dev.arseny.RequestUtils;
import dev.arseny.model.IndexRequest;
import dev.arseny.service.IndexWriterService;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

            final List<Pair<Term, Document>> documents = new ArrayList<>();

            for (Map<String, Object> requestDocument : request.getDocuments()) {
                Document document = new Document();
                for (Map.Entry<String, Object> entry : requestDocument.entrySet()) {
                    if (entry.getKey() == "id")
                        document.add(new StringField(entry.getKey(), entry.getValue().toString(), Field.Store.YES));
                    else
                        document.add(new TextField(entry.getKey(), entry.getValue().toString(),
                                entry.getKey().startsWith("text") ? Field.Store.NO : Field.Store.YES));
                }

                final Term query = new Term("id", requestDocument.get("id").toString());

                documents.add(new Pair<Term, Document>(query, document));
            }

            documents.stream().forEach(v -> {
                try {
                    if(requestDocument.entrySet().size()==1)
                        writer.deleteDocuments(v.getLeft());
                    else
                        writer.updateDocument(v.getLeft(), v.getRight());
                } catch (IOException e) {
                    LOG.error(e);
                }
            });
        }

        for (IndexWriter writer : writerMap.values()) {
            try {
                writer.forceMergeDeletes(true);
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
