package io.kairo.core.context.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectContextSourceTest {

    private ProjectContextSource source;

    @BeforeEach
    void setUp() {
        source = new ProjectContextSource();
    }

    @Test
    void getName() {
        assertThat(source.getName()).isEqualTo("project-structure");
    }

    @Test
    void getPriority() {
        assertThat(source.priority()).isEqualTo(20);
    }

    @Test
    void isActive() {
        assertThat(source.isActive()).isTrue();
    }

    @Test
    void collectReturnsNonBlank() {
        String result = source.collect();
        assertThat(result).isNotBlank();
    }

    @Test
    void collectContainsProjectStructureHeader() {
        String result = source.collect();
        assertThat(result).contains("Project structure");
    }

    @Test
    void collectIsCached() {
        String first = source.collect();
        String second = source.collect();
        assertThat(first).isSameAs(second);
    }
}
