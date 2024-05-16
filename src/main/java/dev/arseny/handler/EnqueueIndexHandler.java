package dev.arseny.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import dev.arseny.RequestUtils;
import dev.arseny.model.IndexRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.inject.Inject;
import javax.inject.Named;

@Named("enqueue-index")
public class EnqueueIndexHandler implements RequestHandler<IndexRequest, Integer> {
    protected String queueName = System.getenv("QUEUE_URL");

    @Inject
    protected SqsClient sqsClient;

    @Override
    public Integer handleRequest(IndexRequest event, Context context) {
        event.setIndexName(System.getenv("index"));

        this.sqsClient.sendMessage(SendMessageRequest.builder()
                .messageBody(RequestUtils.writeIndexRequest(event))
                .queueUrl(queueName).build());
        return 200;
    }
}
