package com.lorevault.api.infrastructure.search;

import com.lorevault.api.application.port.SemanticSearchPort;
import com.lorevault.api.testutil.fakes.FakeContentPersistencePort;
import com.lorevault.api.tck.search.SemanticSearchPortTCK;

/**
 * Concrete TCK suite for InMemorySemanticSearchAdapter.
 */
public class InMemorySemanticSearchAdapterTckTest extends SemanticSearchPortTCK {
    @Override
    protected Fixture createFixture() {
        FakeContentPersistencePort fake = new FakeContentPersistencePort();
        SemanticSearchPort port = new InMemorySemanticSearchAdapter(fake);
        return new Fixture(port, fake);
        }
}
