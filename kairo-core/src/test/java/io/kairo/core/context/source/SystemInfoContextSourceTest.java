package io.kairo.core.context.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SystemInfoContextSourceTest {

    private SystemInfoContextSource source;

    @BeforeEach
    void setUp() {
        source = new SystemInfoContextSource();
    }

    @Test
    void getName() {
        assertThat(source.getName()).isEqualTo("system-info");
    }

    @Test
    void getPriority() {
        assertThat(source.priority()).isEqualTo(10);
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
    void collectContainsSystemInfo() {
        String result = source.collect();
        assertThat(result).contains("System:");
    }

    @Test
    void collectContainsJavaInfo() {
        String result = source.collect();
        assertThat(result).contains("Java:");
    }

    @Test
    void collectContainsWorkingDirectory() {
        String result = source.collect();
        assertThat(result).contains("Working Directory:");
    }

    @Test
    void collectIsCached() {
        String first = source.collect();
        String second = source.collect();
        assertThat(first).isSameAs(second);
    }
}
