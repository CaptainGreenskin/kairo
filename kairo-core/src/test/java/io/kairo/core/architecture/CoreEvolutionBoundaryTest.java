package io.kairo.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CoreEvolutionBoundaryTest {

    @Test
    void coreSourceMustNotImportEvolutionImplementationPackage() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            List<Path> offenders =
                    stream.filter(path -> path.toString().endsWith(".java"))
                            .filter(this::containsEvolutionImport)
                            .toList();

            assertThat(offenders)
                    .withFailMessage(
                            "kairo-core must not import io.kairo.evolution implementation classes. Offenders: %s",
                            offenders)
                    .isEmpty();
        }
    }

    private boolean containsEvolutionImport(Path javaFile) {
        try {
            String content = Files.readString(javaFile);
            return content.contains("import io.kairo.evolution.");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read source file " + javaFile, e);
        }
    }
}
