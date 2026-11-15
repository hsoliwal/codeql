// SPDX-License-Identifier: MIT
package com.synexia.m3.codeql.recipe;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.FileAttributes;
import org.openrewrite.Parser;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextParser;

/**
 * Installs reviewed new files through the actual OpenRewrite scan/generate lifecycle.
 * Existing paths are never rewritten: exact postimages are fixed points, all other content is a conflict.
 */
public final class M3CodeqlSnapshotRecipe extends ScanningRecipe<M3CodeqlSnapshotRecipe.State> {
    private final Snapshot snapshot;

    /** Used by OpenRewrite's recipe discovery; loads the packaged, digest-checked resource manifest. */
    public M3CodeqlSnapshotRecipe() {
        this(Snapshot.bundled());
    }

    public M3CodeqlSnapshotRecipe(Snapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public static final class State {
        private final Set<String> seen = new HashSet<>();
        private final List<String> conflicts = new ArrayList<>();
    }

    @Override
    public String getDisplayName() {
        return "Install the additive M3 CodeQL evidence integration";
    }

    @Override
    public String getDescription() {
        return "Generates reviewed SHA-256-pinned M3 files, preserves all existing upstream sources, "
                + "and refuses any destination whose existing bytes differ.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("m3", "codeql", "additive", "recipe-first");
    }

    @Override
    public int maxCycles() {
        return 1;
    }

    @Override
    public State getInitialValue(ExecutionContext context) {
        return new State();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(State state) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext context) {
                if (tree instanceof SourceFile file) {
                    stopAfterPreVisit();
                    String path = file.getSourcePath().toString().replace('\\', '/');
                    String normalized = file.getSourcePath().normalize().toString().replace('\\', '/');
                    Snapshot.Entry entry = snapshot.entry(normalized);
                    if (entry != null) {
                        synchronized (state) {
                            if (!path.equals(entry.path()) || file.getSourcePath().isAbsolute()) {
                                state.conflicts.add("Non-canonical destination: " + path);
                            }
                            if (!state.seen.add(entry.path())) {
                                state.conflicts.add("Duplicate destination: " + path);
                            }
                            try {
                                byte[] bytes = Snapshot.encode(file.printAll());
                                if (!Snapshot.hash(bytes).equals(entry.sha256())
                                        || file.isCharsetBomMarked()
                                        || (file.getCharset() != null
                                        && !file.getCharset().equals(StandardCharsets.UTF_8))) {
                                    state.conflicts.add("Existing content differs: " + path);
                                }
                            } catch (IllegalArgumentException malformedText) {
                                state.conflicts.add("Malformed destination text: " + path);
                            }
                            if (file.getFileAttributes() != null
                                    && file.getFileAttributes().isExecutable() != entry.executable()) {
                                state.conflicts.add("Existing executable mode differs: " + path);
                            }
                        }
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(State state, ExecutionContext context) {
        synchronized (state) {
            requireNoConflicts(state);
            List<SourceFile> generated = new ArrayList<>();
            for (Snapshot.Entry entry : snapshot.entries()) {
                if (!state.seen.contains(entry.path())) {
                    List<SourceFile> parsed = PlainTextParser.builder().build().parseInputs(
                            List.of(Parser.Input.fromString(Path.of(entry.path()), entry.text(), StandardCharsets.UTF_8)),
                            null, context).toList();
                    if (parsed.size() != 1 || !(parsed.getFirst() instanceof PlainText)
                            || !parsed.getFirst().printAll().equals(entry.text())) {
                        throw new IllegalStateException("PlainText parse changed the postimage: " + entry.path());
                    }
                    generated.add(parsed.getFirst().withCharset(StandardCharsets.UTF_8)
                            .withFileAttributes(new FileAttributes(null, null, null, true, true,
                                    entry.executable(), entry.bytes())));
                }
            }
            return List.copyOf(generated);
        }
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(State state) {
        requireNoConflicts(state);
        return TreeVisitor.noop();
    }

    private static void requireNoConflicts(State state) {
        if (!state.conflicts.isEmpty()) {
            throw new IllegalStateException(String.join("; ", state.conflicts));
        }
    }
}
