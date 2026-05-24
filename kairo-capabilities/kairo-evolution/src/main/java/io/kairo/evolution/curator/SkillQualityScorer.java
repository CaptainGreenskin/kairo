package io.kairo.evolution.curator;

import io.kairo.api.evolution.EvolvedSkill;
import java.time.Duration;
import java.time.Instant;

/**
 * Scores evolved skills by usage frequency and recency. Higher scores indicate more useful skills.
 *
 * <p>Score = usageWeight * normalizedUsage + recencyWeight * recencyDecay
 *
 * <p>Range: [0.0, 1.0]
 */
public final class SkillQualityScorer {

    private final double usageWeight;
    private final double recencyWeight;
    private final long usageCap;
    private final Duration recencyHalfLife;

    public SkillQualityScorer() {
        this(0.6, 0.4, 100, Duration.ofDays(15));
    }

    public SkillQualityScorer(
            double usageWeight, double recencyWeight, long usageCap, Duration recencyHalfLife) {
        this.usageWeight = usageWeight;
        this.recencyWeight = recencyWeight;
        this.usageCap = usageCap;
        this.recencyHalfLife = recencyHalfLife;
    }

    public double score(EvolvedSkill skill) {
        return score(skill, Instant.now());
    }

    public double score(EvolvedSkill skill, Instant now) {
        double normalizedUsage = Math.min(skill.usageCount(), usageCap) / (double) usageCap;

        Instant lastActive = skill.updatedAt() != null ? skill.updatedAt() : skill.createdAt();
        double daysSinceUpdate =
                lastActive != null ? Duration.between(lastActive, now).toDays() : 365;
        double halfLifeDays = recencyHalfLife.toDays();
        double recencyDecay = Math.exp(-0.693 * daysSinceUpdate / halfLifeDays);

        return Math.min(1.0, usageWeight * normalizedUsage + recencyWeight * recencyDecay);
    }
}
