package com.recourse.explain;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** The startup warm-up must never block startup, and failures must be harmless. */
class OllamaWarmUpTest {

    private static LlmProperties props(boolean enabled, String url) {
        return new LlmProperties(enabled, url, "phi4-mini", 20);
    }

    @Test
    void startReturnsAtOnceEvenIfOllamaIsSlow() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        OllamaClient slow = new OllamaClient(props(true, "http://localhost:11434")) {
            @Override public boolean warmUp() {
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return true;
            }
        };
        long began = System.nanoTime();
        Thread t = new OllamaWarmUp(slow).start();
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began) < 500, "start waited for the warm-up");
        assertTrue(t.isDaemon(), "warm-up thread would hold up shutdown");
        assertTrue(t.isAlive());
        release.countDown();
        t.join(2000);
        assertFalse(t.isAlive());
    }

    @Test
    void unreachableOllamaIsIgnored() throws Exception {
        // Nothing listens on port 9; the call must fail quietly
        OllamaClient down = new OllamaClient(props(true, "http://localhost:9"));
        assertFalse(down.warmUp());
        Thread t = new OllamaWarmUp(down).start();
        t.join(5000);
        assertFalse(t.isAlive());
    }

    @Test
    void nothingHappensWhenTheModelIsSwitchedOff() {
        assertNull(new OllamaWarmUp(new OllamaClient(props(false, "http://localhost:11434"))).start());
    }
}
