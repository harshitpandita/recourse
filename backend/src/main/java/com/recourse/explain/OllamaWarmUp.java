package com.recourse.explain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Once the app has started, loads the language model in the background so the first
 * explanation a student asks for isn't slowed down by model loading. It runs on its own
 * daemon thread, so it never delays startup or shutdown, and a failure only means the
 * first explanation is slower (or falls back to the template, as usual).
 */
@Component
public class OllamaWarmUp {

    private static final Logger log = LoggerFactory.getLogger(OllamaWarmUp.class);

    private final OllamaClient llm;

    public OllamaWarmUp(OllamaClient llm) {
        this.llm = llm;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        start();
    }

    /** Starts the warm-up and returns at once. */
    Thread start() {
        if (!llm.enabled()) return null;
        return Thread.ofPlatform().daemon().name("ollama-warm-up").start(() -> {
            long began = System.currentTimeMillis();
            if (llm.warmUp()) {
                log.info("Language model {} loaded in {} ms", llm.model(), System.currentTimeMillis() - began);
            } else {
                log.info("Language model {} not loaded; explanations will use the template until Ollama is available",
                        llm.model());
            }
        });
    }
}
