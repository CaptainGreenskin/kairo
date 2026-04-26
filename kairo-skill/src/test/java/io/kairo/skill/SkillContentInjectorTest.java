package io.kairo.skill;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.SkillTrustLevel;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class SkillContentInjectorTest {

    private InMemoryEvolvedSkillStore store;
    private SkillContentInjector injector;

    @BeforeEach
    void setUp() {
        store = new InMemoryEvolvedSkillStore();
        injector = new SkillContentInjector(store);
    }

    private EvolvedSkill skill(String name, SkillTrustLevel trust) {
        return new EvolvedSkill(
                name,
                "1.0.0",
                "desc",
                "instructions for " + name,
                "general",
                Set.of(),
                trust,
                null,
                Instant.now(),
                Instant.now(),
                0);
    }

    @Test
    void sectionNameIsEvolvedSkills() {
        assertThat(injector.sectionName()).isEqualTo("evolved-skills");
    }

    @Test
    void injectsValidatedAndTrustedSkills() {
        store.save(skill("draft-skill", SkillTrustLevel.DRAFT)).block();
        store.save(skill("validated-skill", SkillTrustLevel.VALIDATED)).block();
        store.save(skill("trusted-skill", SkillTrustLevel.TRUSTED)).block();

        StepVerifier.create(injector.content())
                .assertNext(
                        content -> {
                            assertThat(content).contains("validated-skill");
                            assertThat(content).contains("trusted-skill");
                            assertThat(content).doesNotContain("draft-skill");
                        })
                .verifyComplete();
    }

    @Test
    void skipsDraftSkills() {
        store.save(skill("only-draft", SkillTrustLevel.DRAFT)).block();

        StepVerifier.create(injector.content())
                .assertNext(content -> assertThat(content).isEmpty())
                .verifyComplete();
    }

    @Test
    void emptyStoreReturnsEmptyContent() {
        StepVerifier.create(injector.content())
                .assertNext(content -> assertThat(content).isEmpty())
                .verifyComplete();
    }
}
