package io.kairo.skill;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkillSearchIndexTest {

    private SkillSearchIndex index;

    @BeforeEach
    void setUp() {
        index = new SkillSearchIndex();
        index.buildIndex(
                List.of(
                        skill(
                                "code-review",
                                "Review code changes by severity",
                                List.of("review code", "code review", "/review", "代码审查")),
                        skill(
                                "test-writer",
                                "Write unit tests for code",
                                List.of("write tests", "add tests", "/test", "写测试", "写单测")),
                        skill(
                                "debug",
                                "Systematic debugging workflow",
                                List.of("debug this", "debug", "/debug")),
                        skill(
                                "security-review",
                                "OWASP security audit",
                                List.of("security review", "security audit", "/security")),
                        skill(
                                "docker",
                                "Multi-stage Docker builds",
                                List.of("dockerfile", "docker", "/docker"))));
    }

    @Test
    void searchByEnglishKeyword() {
        var results = index.search("review code", 5);
        assertFalse(results.isEmpty());
        assertEquals("code-review", results.get(0).name());
        assertTrue(results.get(0).score() >= SkillSearchIndex.AUTO_LOAD_THRESHOLD);
    }

    @Test
    void searchByChinese() {
        var results = index.search("代码审查", 5);
        assertFalse(results.isEmpty());
        assertEquals("code-review", results.get(0).name());
    }

    @Test
    void searchChineseTest() {
        var results = index.search("写测试", 5);
        assertFalse(results.isEmpty());
        assertEquals("test-writer", results.get(0).name());
    }

    @Test
    void searchBySlashCommand() {
        var results = index.search("/debug", 5);
        assertFalse(results.isEmpty());
        assertEquals("debug", results.get(0).name());
    }

    @Test
    void searchNoMatch() {
        var results = index.search("kubernetes deployment yaml", 5);
        assertTrue(
                results.isEmpty() || results.get(0).score() < SkillSearchIndex.AUTO_LOAD_THRESHOLD);
    }

    @Test
    void emptyQueryReturnsEmpty() {
        assertTrue(index.search("", 5).isEmpty());
        assertTrue(index.search(null, 5).isEmpty());
    }

    @Test
    void nameSubstringBoost() {
        var results = index.search("docker", 5);
        assertFalse(results.isEmpty());
        assertEquals("docker", results.get(0).name());
        assertTrue(results.get(0).score() >= 0.75);
    }

    @Test
    void indexSize() {
        assertEquals(5, index.size());
    }

    private static SkillDefinition skill(String name, String desc, List<String> triggers) {
        return new SkillDefinition(
                name,
                "1.0.0",
                desc,
                "instructions here",
                triggers,
                SkillCategory.CODE,
                null,
                null,
                null,
                0,
                null);
    }
}
