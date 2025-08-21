package com.lorevault.api.tck.ai;

import com.lorevault.api.application.port.EmbeddingPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Minimal TCK for EmbeddingPort to validate basic contract invariants.
 */
public abstract class EmbeddingPortTCK {

    protected abstract EmbeddingPort createPort();

    @Test
    void embedBatch_returns_one_vector_per_input_and_never_null() {
        EmbeddingPort port = createPort();
        List<double[]> out = port.embedBatch(List.of("a", "b", "c"));
        assertThat(out).hasSize(3);
        assertThat(out).allSatisfy(vec -> assertThat(vec).isNotNull());
    }

    @Test
    void embed_single_consistent_with_batch_first_element() {
        EmbeddingPort port = createPort();
        double[] one = port.embed("hello");
        double[] first = port.embedBatch(List.of("hello", "world")).get(0);
        assertThat(one.length).isEqualTo(first.length);
    }
}
