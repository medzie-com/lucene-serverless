package dev.arseny.service;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class SqsClientProducer {
    protected SqsClient client = SqsClient.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build();

    @Produces
    public SqsClient sqsClient() {
        return client;
    }
}
