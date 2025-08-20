package com.lorevault.api.infrastructure.ai;

import com.lorevault.api.application.port.EmbeddingPort;
import com.lorevault.api.tck.ai.EmbeddingPortTCK;
import org.junit.jupiter.api.Disabled;

/**
 * TCK for EmbeddingModelAdapter.
 * Disabled by default since it would perform real HTTP calls; enable when a
 * local mock server is wired.
 */
@Disabled("Requires HTTP endpoint; provide a mock RestTemplate and enable in IT profile")
public class EmbeddingModelAdapterTckTest extends EmbeddingPortTCK {
    @Override
    protected EmbeddingPort createPort() {
        // Intentionally left empty; see note above.
        return new EmbeddingModelAdapter(null, null, null);
    }
}
