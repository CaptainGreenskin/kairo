package io.kairo.evolution.curator;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.api.evolution.SkillTrustLevel;
import io.kairo.evolution.FileEvolvedSkillStore;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CuratorDaemonTest {

    @TempDir Path tempDir;

    private EvolvedSkill skill(String name, long usageCount, Instant updatedAt) {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        return new EvolvedSkill(
                name,
                "1.0",
                "Test skill " + name,
                "instructions",
                "general",
                Set.of(),
                SkillTrustLevel.VALIDATED,
                null,
                created,
                updatedAt,
                usageCount);
    }

    private EvolvedSkillStore storeWith(EvolvedSkill... skills) {
        FileEvolvedSkillStore store = new FileEvolvedSkillStore(tempDir);
        for (EvolvedSkill s : skills) {
            store.save(s).block();
        }
        return store;
    }

    @Test
    void deprecatesIdleLowQualitySkills() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        EvolvedSkill idle = skill("idle-skill", 0, now.minus(Duration.ofDays(45)));
        EvolvedSkill active = skill("active-skill", 80, now.minus(Duration.ofDays(1)));
        EvolvedSkillStore store = storeWith(idle, active);

        CuratorDaemon daemon =
                new CuratorDaemon(
                        store,
                        new SkillQualityScorer(),
                        Duration.ofHours(24),
                        0.3,
                        Duration.ofDays(30));

        List<String> deprecated = daemon.runAudit();

        assertThat(deprecated).containsExactly("idle-skill");
        assertThat(store.get("idle-skill").block()).isEmpty();
        assertThat(store.get("active-skill").block()).isPresent();
    }

    @Test
    void keepsRecentlyUsedSkills() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        EvolvedSkill recent = skill("recent", 5, now.minus(Duration.ofDays(2)));
        EvolvedSkillStore store = storeWith(recent);

        CuratorDaemon daemon =
                new CuratorDaemon(
                        store,
                        new SkillQualityScorer(),
                        Duration.ofHours(24),
                        0.3,
                        Duration.ofDays(30));

        List<String> deprecated = daemon.runAudit();

        assertThat(deprecated).isEmpty();
        assertThat(store.get("recent").block()).isPresent();
    }

    @Test
    void emptyStoreNoOps() {
        EvolvedSkillStore store = storeWith();
        CuratorDaemon daemon = new CuratorDaemon(store);

        List<String> deprecated = daemon.runAudit();

        assertThat(deprecated).isEmpty();
        assertThat(daemon.lastAuditTime()).isNotNull();
    }

    @Test
    void startAndStop() {
        EvolvedSkillStore store = storeWith();
        CuratorDaemon daemon = new CuratorDaemon(store);

        assertThat(daemon.isRunning()).isFalse();
        daemon.start();
        assertThat(daemon.isRunning()).isTrue();
        daemon.stop();
        assertThat(daemon.isRunning()).isFalse();
    }

    @Test
    void doubleStartIsIdempotent() {
        EvolvedSkillStore store = storeWith();
        CuratorDaemon daemon = new CuratorDaemon(store);

        daemon.start();
        daemon.start();
        assertThat(daemon.isRunning()).isTrue();
        daemon.stop();
    }

    @Test
    void highUsageIdleSkillKept() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        EvolvedSkill highUsage = skill("popular", 90, now.minus(Duration.ofDays(45)));
        EvolvedSkillStore store = storeWith(highUsage);

        CuratorDaemon daemon =
                new CuratorDaemon(
                        store,
                        new SkillQualityScorer(),
                        Duration.ofHours(24),
                        0.3,
                        Duration.ofDays(30));

        List<String> deprecated = daemon.runAudit();

        assertThat(deprecated).isEmpty();
    }
}
