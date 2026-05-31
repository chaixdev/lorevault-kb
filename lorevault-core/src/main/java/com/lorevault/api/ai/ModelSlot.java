package com.lorevault.api.ai;

/**
 * Type-safe LLM model slot identifiers.
 *
 * <p>Replaces raw {@code "nlp-small"} / {@code "nlp-big"} string literals
 * used in {@code LlmClient} switch statements and health checks.
 * Each slot maps to a configured provider, model, and generation parameters.
 */
public enum ModelSlot {

    NLP_SMALL("nlp-small"),
    NLP_BIG("nlp-big");

    private final String slotName;

    ModelSlot(String slotName) {
        this.slotName = slotName;
    }

    /** The slot name as used in configuration properties and switch dispatching. */
    public String slotName() {
        return slotName;
    }
}
