package io.kairo.evolution.curator;

import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.EvolvedSkillStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background daemon that periodically audits evolved skills and auto-deprecates low-quality ones.
 *
 * <p>Deprecation criteria: skill unused for {@code maxIdleDays} AND quality score below {@code
 * deprecationThreshold}.
 */
public final class CuratorDaemon {

    private static final Logger log = LoggerFactory.getLogger(CuratorDaemon.class);

    private final EvolvedSkillStore store;
    private final SkillQualityScorer scorer;
    private final Duration auditInterval;
    private final double deprecationThreshold;
    private final Duration maxIdleDuration;
    private volatile ScheduledExecutorService scheduler;
    private volatile Instant lastAuditTime;

    public CuratorDaemon(EvolvedSkillStore store) {
        this(store, new SkillQualityScorer(), Duration.ofHours(24), 0.3, Duration.ofDays(30));
    }

    public CuratorDaemon(
            EvolvedSkillStore store,
            SkillQualityScorer scorer,
            Duration auditInterval,
            double deprecationThreshold,
            Duration maxIdleDuration) {
        this.store = store;
        this.scorer = scorer;
        this.auditInterval = auditInterval;
        this.deprecationThreshold = deprecationThreshold;
        this.maxIdleDuration = maxIdleDuration;
    }

    public void start() {
        if (scheduler != null) {
            return;
        }
        scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "kairo-curator");
                            t.setDaemon(true);
                            return t;
                        });
        scheduler.scheduleAtFixedRate(
                this::runAudit,
                auditInterval.toMinutes(),
                auditInterval.toMinutes(),
                TimeUnit.MINUTES);
        log.info("CuratorDaemon started, audit interval: {}", auditInterval);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
            log.info("CuratorDaemon stopped");
        }
    }

    public boolean isRunning() {
        return scheduler != null && !scheduler.isShutdown();
    }

    public Instant lastAuditTime() {
        return lastAuditTime;
    }

    /** Run one audit cycle synchronously. Returns the names of deprecated skills. */
    public List<String> runAudit() {
        Instant now = Instant.now();
        List<String> deprecated = new ArrayList<>();
        try {
            List<EvolvedSkill> skills = store.list().collectList().block();
            if (skills == null || skills.isEmpty()) {
                lastAuditTime = now;
                return deprecated;
            }

            for (EvolvedSkill skill : skills) {
                double quality = scorer.score(skill, now);
                Instant lastActive =
                        skill.updatedAt() != null ? skill.updatedAt() : skill.createdAt();
                boolean idle =
                        lastActive != null
                                && Duration.between(lastActive, now).compareTo(maxIdleDuration) > 0;

                if (idle && quality < deprecationThreshold) {
                    store.delete(skill.name()).block();
                    deprecated.add(skill.name());
                    log.info(
                            "Deprecated skill '{}' (score={}, idle=true)",
                            skill.name(),
                            String.format("%.2f", quality));
                }
            }

            lastAuditTime = now;
            log.debug(
                    "Curator audit complete: {} skills audited, {} deprecated",
                    skills.size(),
                    deprecated.size());
        } catch (Exception e) {
            log.warn("Curator audit failed: {}", e.getMessage());
        }
        return deprecated;
    }
}
