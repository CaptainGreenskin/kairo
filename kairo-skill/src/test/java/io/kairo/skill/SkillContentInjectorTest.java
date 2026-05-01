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

    @Test
    void singleValidatedSkill_formattedWithHeaderAndInstructions() {
        store.save(skill("my-skill", SkillTrustLevel.VALIDATED)).block();

        StepVerifier.create(injector.content())
                .assertNext(
                        content -> {
                            assertThat(content).startsWith("### my-skill");
                            assertThat(content).contains("instructions for my-skill");
                        })
                .verifyComplete();
    }

    @Test
    void trustedSkill_alsoIncluded() {
        store.save(skill("trusted-only", SkillTrustLevel.TRUSTED)).block();

        StepVerifier.create(injector.content())
                .assertNext(content -> assertThat(content).contains("trusted-only"))
                .verifyComplete();
    }

    @Test
    void multipleValidatedSkills_joinedByDoubleNewline() {
        store.save(skill("alpha", SkillTrustLevel.VALIDATED)).block();
        store.save(skill("beta", SkillTrustLevel.VALIDATED)).block();

        StepVerifier.create(injector.content())
                .assertNext(
                        content -> {
                            assertThat(content).contains("### alpha");
                            assertThat(content).contains("### beta");
                            assertThat(content).contains("\n\n");
                        })
                .verifyComplete();
    }

    @Test
    void formatSkill_includesNameAsMarkdownHeader() {
        store.save(skill("header-test", SkillTrustLevel.VALIDATED)).block();

        StepVerifier.create(injector.content())
                .assertNext(
                        content -> {
                            assertThat(content).contains("### header-test\n");
                        })
                .verifyComplete();
    }

    @Test
    void allDraftSkills_returnsEmpty() {
        store.save(skill("draft-a", SkillTrustLevel.DRAFT)).block();
        store.save(skill("draft-b", SkillTrustLevel.DRAFT)).block();
        store.save(skill("draft-c", SkillTrustLevel.DRAFT)).block();

        StepVerifier.create(injector.content())
                .assertNext(content -> assertThat(content).isEmpty())
                .verifyComplete();
    }

    @Test
    void mixOfAllTrustLevels_onlyValidatedAndAboveIncluded() {
        store.save(skill("draft-1", SkillTrustLevel.DRAFT)).block();
        store.save(skill("validated-1", SkillTrustLevel.VALIDATED)).block();
        store.save(skill("trusted-1", SkillTrustLevel.TRUSTED)).block();

        StepVerifier.create(injector.content())
                .assertNext(
                        content -> {
                            assertThat(content).contains("validated-1");
                            assertThat(content).contains("trusted-1");
                            assertThat(content).doesNotContain("draft-1");
                            // Exactly 2 skills means exactly one separator
                            long headerCount =
                                    content.lines().filter(l -> l.startsWith("### ")).count();
                            assertThat(headerCount).isEqualTo(2);
                        })
                .verifyComplete();
    }
}
