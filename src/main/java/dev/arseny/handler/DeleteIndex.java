package dev.arseny.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import dev.arseny.RequestUtils;
import dev.arseny.model.DeleteIndexRequest;
import dev.arseny.service.IndexWriterService;
import org.apache.lucene.index.IndexWriter;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;

@Named("deleteIndex")
public class DeleteIndex implements RequestHandler<Void, Integer> {
    private static final Logger LOG = Logger.getLogger(DeleteIndex.class);

    @Inject
    protected IndexWriterService indexWriterService;

    @Override
    public Integer handleRequest(Void event, Context context) {
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest();

        IndexWriter writer = indexWriterService.getIndexWriter(deleteIndexRequest.getIndexName());

        try {
            writer.deleteAll();
            writer.commit();
            writer.close();
        } catch (IOException e) {
            LOG.error(e);
            return 500;
        }

        return 200;

    }
}
