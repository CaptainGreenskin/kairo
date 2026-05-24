package io.kairo.evolution.curator;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.SkillTrustLevel;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillQualityScorerTest {

    private final SkillQualityScorer scorer = new SkillQualityScorer();

    private EvolvedSkill skill(long usageCount, Instant updatedAt) {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        return new EvolvedSkill(
                "test-skill",
                "1.0",
                "Test",
                "instructions",
                "general",
                Set.of(),
                SkillTrustLevel.VALIDATED,
                null,
                created,
                updatedAt,
                usageCount);
    }

    @Test
    void highUsageRecentlyUpdatedScoresHigh() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        EvolvedSkill s = skill(80, now.minus(Duration.ofDays(1)));
        double score = scorer.score(s, now);
        assertThat(score).isGreaterThan(0.7);
    }

    @Test
    void noUsageLongAgoScoresLow() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        EvolvedSkill s = skill(0, now.minus(Duration.ofDays(60)));
        double score = scorer.score(s, now);
        assertThat(score).isLessThan(0.1);
    }

    @Test
    void moderateUsageRecentScoresMedium() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        EvolvedSkill s = skill(30, now.minus(Duration.ofDays(5)));
        double score = scorer.score(s, now);
        assertThat(score).isBetween(0.3, 0.7);
    }

    @Test
    void usageCappedAt100() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        EvolvedSkill s100 = skill(100, now);
        EvolvedSkill s200 = skill(200, now);
        assertThat(scorer.score(s100, now)).isEqualTo(scorer.score(s200, now));
    }

    @Test
    void scoreNeverExceedsOne() {
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        EvolvedSkill s = skill(500, now);
        assertThat(scorer.score(s, now)).isLessThanOrEqualTo(1.0);
    }

    @Test
    void customWeights() {
        SkillQualityScorer usageOnly = new SkillQualityScorer(1.0, 0.0, 100, Duration.ofDays(15));
        Instant now = Instant.parse("2026-05-16T00:00:00Z");
        EvolvedSkill s = skill(50, now.minus(Duration.ofDays(100)));
        double score = usageOnly.score(s, now);
        assertThat(score).isEqualTo(0.5, org.assertj.core.data.Offset.offset(0.01));
    }
}
