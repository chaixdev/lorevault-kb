package com.lorevault.catalog;

import java.util.List;

@FunctionalInterface
public interface EmbeddingFunction {
    float[] embed(String text);

    default List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
