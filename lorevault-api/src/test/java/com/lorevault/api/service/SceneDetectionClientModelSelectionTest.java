// filepath: /home/chaitanya/projects/lorevault/lorevault-api/src/test/java/com/lorevault/api/service/SceneDetectionClientModelSelectionTest.java
package com.lorevault.api.service;

import com.lorevault.api.configuration.properties.LoreVaultPromptProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/*
Common reasons these tests fail:
- Property keys don't match the structure of LoreVaultPromptProperties (wrong prefix/segment names).
- LoreVaultPromptProperties isn't registered via @ConfigurationPropertiesScan or @EnableConfigurationProperties.
- Using different key styles than your property names without relaxed binding at each segment.

The tests below include both hyphen-case and camelCase keys, and verify override/independence.
*/

// Existing test: verifies direct binding via @SpringBootTest + @TestPropertySource.
@SpringBootTest
@TestPropertySource(properties = {
    "lorevault.ai.prompts.scene-detection-pass1.model=nlp-big",
    "lorevault.ai.prompts.scene-detection-pass2.model=nlp-small"
})
class SceneDetectionClientModelSelectionTest {

    @Autowired
    private LoreVaultPromptProperties promptProperties;

    @Test
    void verifyConfigurationIsCorrect() {
        assertThat(promptProperties.sceneDetectionPass1().model())
            .as("pass1 model should come from TestPropertySource")
            .isEqualTo("nlp-big");
        assertThat(promptProperties.sceneDetectionPass2().model())
            .as("pass2 model should come from TestPropertySource")
            .isEqualTo("nlp-small");
    }
}

// Additional tests: fast, focused property-binding checks using ApplicationContextRunner.
class SceneDetectionClientPropertyBindingTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Configuration
    @EnableConfigurationProperties(LoreVaultPromptProperties.class)
    static class TestConfig { }

    @Test
    void bindsWithHyphenCaseKeys() {
        contextRunner
            .withPropertyValues(
                "lorevault.ai.prompts.scene-detection-pass1.model=alpha-hyphen",
                "lorevault.ai.prompts.scene-detection-pass2.model=beta-hyphen"
            )
            .run(ctx -> {
                LoreVaultPromptProperties props = ctx.getBean(LoreVaultPromptProperties.class);
                assertThat(props.sceneDetectionPass1().model()).isEqualTo("alpha-hyphen");
                assertThat(props.sceneDetectionPass2().model()).isEqualTo("beta-hyphen");
            });
    }

    @Test
    void bindsWithCamelCaseKeys() {
        contextRunner
            .withPropertyValues(
                "lorevault.ai.prompts.sceneDetectionPass1.model=alphaCamel",
                "lorevault.ai.prompts.sceneDetectionPass2.model=betaCamel"
            )
            .run(ctx -> {
                LoreVaultPromptProperties props = ctx.getBean(LoreVaultPromptProperties.class);
                assertThat(props.sceneDetectionPass1().model()).isEqualTo("alphaCamel");
                assertThat(props.sceneDetectionPass2().model()).isEqualTo("betaCamel");
            });
    }

    @Test
    void lastPropertyWinsWhenOverridingSameKey() {
        contextRunner
            .withPropertyValues(
                "lorevault.ai.prompts.scene-detection-pass1.model=first",
                "lorevault.ai.prompts.scene-detection-pass1.model=second" // overrides previous
            )
            .run(ctx -> {
                LoreVaultPromptProperties props = ctx.getBean(LoreVaultPromptProperties.class);
                assertThat(props.sceneDetectionPass1().model()).isEqualTo("second");
            });
    }
}

// Ensures the two passes are independently configurable under a full Spring context.
@SpringBootTest
@TestPropertySource(properties = {
    "lorevault.ai.prompts.scene-detection-pass1.model=alpha",
    "lorevault.ai.prompts.scene-detection-pass2.model=beta"
})
class SceneDetectionClientModelIndependenceTest {

    @Autowired
    private LoreVaultPromptProperties promptProperties;

    @Test
    void differentPassesDontBleed() {
        assertThat(promptProperties.sceneDetectionPass1().model()).isEqualTo("alpha");
        assertThat(promptProperties.sceneDetectionPass2().model()).isEqualTo("beta");
    }
}
