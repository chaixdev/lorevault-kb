package com.lorevault.catalog;

import java.util.List;

@FunctionalInterface
public interface EmbeddingFunction {
    float[] embed(String text);

    /**
     * Embed multiple texts in a single call.
     *
     * <p>The default implementation calls {@link #embed(String)} sequentially for each text.
     * Implementations that support batched embedding (e.g., reducing API calls by sending
     * multiple texts in one request) should override this method.</p>
     *
     * @param texts the texts to embed
     * @return a list of embedding vectors, one per input text, in the same order
     */
    default List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
