package com.lorevault.api;

import com.lorevault.api.test.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
class LoreVaultApiApplicationTests extends IntegrationTestBase {

    @Test
    void contextLoads() {
        // This test ensures that the Spring context loads successfully
    }
}
