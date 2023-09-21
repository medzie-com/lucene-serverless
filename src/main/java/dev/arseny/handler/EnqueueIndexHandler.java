package dev.arseny.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.inject.Inject;
import javax.inject.Named;

@Named("enqueue-index")
public class EnqueueIndexHandler implements RequestHandler<String, Integer> {
    protected String queueName = System.getenv("QUEUE_URL");

    @Inject
    protected SqsClient sqsClient;

    @Override
    public Integer handleRequest(String event, Context context) {
        this.sqsClient.sendMessage(SendMessageRequest.builder()
                .messageBody(event)
                .queueUrl(queueName).build());
        return 200;
    }
}
