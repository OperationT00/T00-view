package com.example.pikpak.web;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;

class BoundedBodySubscriberTest {
    static class Subscription implements Flow.Subscription {
        boolean cancelled;
        public void request(long n) {}
        public void cancel() { cancelled=true; }
    }
    @Test void accumulatedChunksCannotExceedLimit() {
        var subscriber=new BoundedBodySubscriber(3);
        var subscription=new Subscription();subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2})));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{3,4})));
        assertTrue(subscription.cancelled);
        assertThrows(CompletionException.class,()->subscriber.getBody().toCompletableFuture().join());
    }
    @Test void acceptsExactLimitAndPreservesBytes() {
        var subscriber=new BoundedBodySubscriber(3);subscriber.onSubscribe(new Subscription());
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1}),ByteBuffer.wrap(new byte[]{2,3})));
        subscriber.onComplete();
        assertArrayEquals(new byte[]{1,2,3},subscriber.getBody().toCompletableFuture().join());
    }
}
