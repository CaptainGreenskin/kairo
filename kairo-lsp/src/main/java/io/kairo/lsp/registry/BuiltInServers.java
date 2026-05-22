/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp.registry;

import io.kairo.api.lsp.ServerDef;
import java.util.List;
import java.util.Set;

/**
 * Built-in {@link ServerDef}s for five common languages. Each entry uses the binary name that the
 * language's official tooling installs to PATH; install / shimming is the user's job (we do not
 * auto-install in v1.3).
 *
 * <p>Add new languages by adding a constant here and referencing it from {@link #all()}.
 */
public final class BuiltInServers {

    public static final ServerDef PYRIGHT =
            new ServerDef(
                    "pyright",
                    "Pyright",
                    Set.of("py", "pyi"),
                    Set.of(
                            "pyproject.toml",
                            "setup.py",
                            "setup.cfg",
                            "Pipfile",
                            "requirements.txt"),
                    List.of("pyright-langserver", "--stdio"),
                    "python");

    public static final ServerDef TYPESCRIPT =
            new ServerDef(
                    "typescript-language-server",
                    "TypeScript Language Server",
                    Set.of("ts", "tsx", "js", "jsx", "mjs", "cjs"),
                    Set.of("tsconfig.json", "jsconfig.json", "package.json"),
                    List.of("typescript-language-server", "--stdio"),
                    "typescript");

    public static final ServerDef GOPLS =
            new ServerDef(
                    "gopls",
                    "gopls",
                    Set.of("go"),
                    Set.of("go.mod", "go.work"),
                    List.of("gopls", "serve"),
                    "go");

    public static final ServerDef RUST_ANALYZER =
            new ServerDef(
                    "rust-analyzer",
                    "rust-analyzer",
                    Set.of("rs"),
                    Set.of("Cargo.toml", "Cargo.lock"),
                    List.of("rust-analyzer"),
                    "rust");

    public static final ServerDef CLANGD =
            new ServerDef(
                    "clangd",
                    "clangd",
                    Set.of("c", "cc", "cpp", "cxx", "h", "hh", "hpp", "hxx"),
                    Set.of("compile_commands.json", "CMakeLists.txt", ".clangd"),
                    List.of("clangd"),
                    "cpp");

    private BuiltInServers() {}

    public static List<ServerDef> all() {
        return List.of(PYRIGHT, TYPESCRIPT, GOPLS, RUST_ANALYZER, CLANGD);
    }
}
