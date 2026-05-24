package io.kairo.skill;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.SkillTrustLevel;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class InMemoryEvolvedSkillStoreTest {

    private InMemoryEvolvedSkillStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryEvolvedSkillStore();
    }

    private EvolvedSkill skill(String name, String version, SkillTrustLevel trust) {
        return new EvolvedSkill(
                name,
                version,
                "desc-" + name,
                "instructions-" + name,
                "general",
                Set.of(),
                trust,
                null,
                Instant.now(),
                Instant.now(),
                0);
    }

    @Test
    void saveAndGet() {
        EvolvedSkill s = skill("hello", "1.0.0", SkillTrustLevel.DRAFT);

        StepVerifier.create(store.save(s))
                .assertNext(
                        saved -> {
                            assertThat(saved.name()).isEqualTo("hello");
                            assertThat(saved.version()).isEqualTo("1.0.0");
                        })
                .verifyComplete();

        StepVerifier.create(store.get("hello"))
                .assertNext(
                        opt -> {
                            assertThat(opt).isPresent();
                            assertThat(opt.get().name()).isEqualTo("hello");
                            assertThat(opt.get().description()).isEqualTo("desc-hello");
                        })
                .verifyComplete();
    }

    @Test
    void saveOverwritesNewerVersion() {
        EvolvedSkill v1 = skill("x", "1.0.0", SkillTrustLevel.DRAFT);
        EvolvedSkill v2 = skill("x", "2.0.0", SkillTrustLevel.VALIDATED);

        StepVerifier.create(store.save(v1).then(store.save(v2)))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(store.get("x"))
                .assertNext(
                        opt -> {
                            assertThat(opt).isPresent();
                            assertThat(opt.get().version()).isEqualTo("2.0.0");
                        })
                .verifyComplete();
    }

    @Test
    void saveRejectsOlderVersion() {
        EvolvedSkill v2 = skill("x", "2.0.0", SkillTrustLevel.DRAFT);
        EvolvedSkill v1 = skill("x", "1.0.0", SkillTrustLevel.DRAFT);

        StepVerifier.create(store.save(v2).then(store.save(v1)))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(store.get("x"))
                .assertNext(
                        opt -> {
                            assertThat(opt).isPresent();
                            assertThat(opt.get().version()).isEqualTo("2.0.0");
                        })
                .verifyComplete();
    }

    @Test
    void list() {
        StepVerifier.create(
                        store.save(skill("a", "1.0.0", SkillTrustLevel.DRAFT))
                                .then(store.save(skill("b", "1.0.0", SkillTrustLevel.DRAFT)))
                                .then(store.save(skill("c", "1.0.0", SkillTrustLevel.DRAFT)))
                                .thenMany(store.list())
                                .collectList())
                .assertNext(skills -> assertThat(skills).hasSize(3))
                .verifyComplete();
    }

    @Test
    void delete() {
        StepVerifier.create(
                        store.save(skill("del", "1.0.0", SkillTrustLevel.DRAFT))
                                .then(store.delete("del"))
                                .then(store.get("del")))
                .assertNext(opt -> assertThat(opt).isEmpty())
                .verifyComplete();
    }

    @Test
    void listByMinTrust() {
        StepVerifier.create(
                        store.save(skill("draft", "1.0.0", SkillTrustLevel.DRAFT))
                                .then(
                                        store.save(
                                                skill(
                                                        "validated",
                                                        "1.0.0",
                                                        SkillTrustLevel.VALIDATED)))
                                .then(
                                        store.save(
                                                skill("trusted", "1.0.0", SkillTrustLevel.TRUSTED)))
                                .thenMany(store.listByMinTrust(SkillTrustLevel.VALIDATED))
                                .collectList())
                .assertNext(
                        skills -> {
                            assertThat(skills).hasSize(2);
                            assertThat(skills)
                                    .allMatch(
                                            s ->
                                                    s.trustLevel() == SkillTrustLevel.VALIDATED
                                                            || s.trustLevel()
                                                                    == SkillTrustLevel.TRUSTED);
                        })
                .verifyComplete();
    }

    @Test
    void concurrentSave() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            int version = i;
            executor.submit(
                    () -> {
                        try {
                            latch.countDown();
                            latch.await();
                            store.save(skill("concurrent", version + ".0.0", SkillTrustLevel.DRAFT))
                                    .block();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        StepVerifier.create(store.get("concurrent"))
                .assertNext(
                        opt -> {
                            assertThat(opt).isPresent();
                            // The highest version should win
                            assertThat(opt.get().version()).isEqualTo("9.0.0");
                        })
                .verifyComplete();
    }
}
