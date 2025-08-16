package com.lorevault.api.application.port;

import java.util.List;

/** Primary port for generating embeddings from text inputs. */
public interface EmbeddingPort {
    /** Generate embedding vector for single text. */
    double[] embed(String text);
    /** Generate embeddings for batch of texts. */
    List<double[]> embedBatch(List<String> texts);
    /** Model identifier used (for hash construction). */
    String getModelId();
    /** Expected vector dimension. */
    int getDimension();
}
