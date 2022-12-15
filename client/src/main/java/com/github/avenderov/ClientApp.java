package com.github.avenderov;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.bootstrap.AsyncRequesterBootstrap;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Copied from <a href="https://github.com/apache/httpcomponents-core/blob/master/httpcore5/src/test/java/org/apache/hc/core5/http/examples/AsyncPipelinedRequestExecutionExample.java">AsyncPipelinedRequestExecutionExample.java</a>
 */
public class ClientApp {

    public static void main(String[] args) throws Exception {

        var ioReactorConfig = IOReactorConfig.custom()
            .setSoTimeout(5, TimeUnit.SECONDS)
            .build();

        // Create and start requester
        var requester = AsyncRequesterBootstrap.bootstrap()
            .setIOReactorConfig(ioReactorConfig)
            .setStreamListener(new Http1StreamListener() {

                @Override
                public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
                    System.out.println(connection.getRemoteAddress() + " " + new RequestLine(request));

                }

                @Override
                public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
                    System.out.println(connection.getRemoteAddress() + " " + new StatusLine(response));
                }

                @Override
                public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                    if (keepAlive) {
                        System.out.println(connection.getRemoteAddress() + " exchange completed (connection kept alive)");
                    } else {
                        System.out.println(connection.getRemoteAddress() + " exchange completed (connection closed)");
                    }
                }

            })
            .create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("HTTP requester shutting down");
            requester.close(CloseMode.GRACEFUL);
        }));
        requester.start();

        var target = new HttpHost("localhost", 5050);
        var requestUris =
            IntStream.range(0, 5)
                .boxed()
                .sorted(Collections.reverseOrder())
                .map(i -> "/files/" + i)
                .collect(Collectors.toList());

        var future = requester.connect(target, Timeout.ofSeconds(5));
        var clientEndpoint = future.get();

        var results = new ConcurrentLinkedQueue<String>();

        var latch = new CountDownLatch(requestUris.size());
        for (var requestUri : requestUris) {

            var requestProducer =
                AsyncRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath(requestUri)
                    .build();


            var responseConsumer = new BasicResponseConsumer<>(new StringAsyncEntityConsumer());

            clientEndpoint.execute(
                requestProducer,
                responseConsumer,
                new FutureCallback<>() {

                    @Override
                    public void completed(final Message<HttpResponse, String> message) {
                        latch.countDown();
                        final HttpResponse response = message.getHead();
                        final String body = message.getBody();
                        System.out.println(requestUri + "->" + response.getCode());

                        results.add(body);
                    }

                    @Override
                    public void failed(final Exception ex) {
                        latch.countDown();
                        System.out.println(requestUri + "->" + ex);
                    }

                    @Override
                    public void cancelled() {
                        latch.countDown();
                        System.out.println(requestUri + " cancelled");
                    }

                });
        }

        latch.await();

        // Manually release client endpoint when done !!!
        clientEndpoint.releaseAndDiscard();

        System.out.println("Shutting down I/O reactor");
        requester.initiateShutdown();

        System.out.println("Received files:");
        results.forEach(System.out::println);
    }
}
