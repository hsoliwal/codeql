// SPDX-License-Identifier: MIT
package com.synexia.m3.codeql;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class CodeQlPlan {
    record Step(String name, List<String> arguments, List<Path> outputs) {
        Step { arguments = List.copyOf(arguments); outputs = List.copyOf(outputs); }
    }

    record Input(String role, Path source, String path, long bytes, String sha256) {}
    private final CliOptions options;
    private final List<Input> inputs;
    private final List<Step> steps;
    private final List<String> providers;

    private CodeQlPlan(CliOptions options, List<Input> inputs, List<Step> steps,
                       List<String> providers) {
        this.options = options;
        this.inputs = List.copyOf(inputs);
        this.steps = List.copyOf(steps);
        this.providers = List.copyOf(providers);
    }

    List<Step> steps() { return steps; }

    static CodeQlPlan create(CliOptions o) throws IOException {
        Path source = o.root().resolve("m3/ql/src");
        Path tests = o.root().resolve("m3/ql/test");
        for (String component : List.of("m3", "m3/ql", "m3/ql/src", "m3/ql/test")) {
            Path path = o.root().resolve(component);
            if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Required real M3 directory is missing or symbolic: " + path);
            }
        }
        List<Input> inputs = new ArrayList<>();
        for (Path directory : List.of(source, tests)) {
            if (!Files.isRegularFile(directory.resolve("qlpack.yml"), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Required qlpack.yml missing: " + directory);
            }
            addInputs(o, inputs, directory, "M3_INPUT");
        }
        inputs.sort(Comparator.comparing(Input::path));
        if (inputs.stream().noneMatch(i -> i.path().startsWith("m3/ql/src/") && i.path().endsWith(".ql"))) {
            throw new IOException("M3 source pack contains no .ql queries.");
        }
        if (inputs.stream().noneMatch(i -> i.path().startsWith("m3/ql/test/") &&
                (i.path().endsWith(".ql") || i.path().endsWith(".qlref")))) {
            throw new IOException("M3 test pack contains no .ql or .qlref tests.");
        }
        if (o.command() == CliOptions.Command.ANALYZE) {
            for (String required : List.of("m3-java.qls", "inventory/CallableInventory.ql", "inventory/CallDependencies.ql",
                    "inventory/NativeBoundaryInventory.ql")) {
                if (!Files.isRegularFile(source.resolve(required), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Required analysis input is missing: " + source.resolve(required));
                }
            }
            if (!Files.isRegularFile(o.database().resolve("codeql-database.yml"), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Database lacks codeql-database.yml: " + o.database());
            }
        }
        Path work = o.output().resolve("work");
        List<String> providers = new ArrayList<>(List.of("m3/ql/src/qlpack.yml", "m3/ql/test/qlpack.yml"));
        if (Files.isRegularFile(o.root().resolve("java/ql/lib/qlpack.yml"))) {
            providers.add("java/ql/lib/qlpack.yml");
            addInputs(o, inputs, o.root().resolve("java/ql/lib"), "DEPENDENCY_INPUT");
        }
        Path shared = o.root().resolve("shared");
        if (Files.isDirectory(shared)) {
            try (var directories = Files.list(shared)) {
                for (Path directory : directories.sorted().toList()) {
                    if (Files.isRegularFile(directory.resolve("qlpack.yml"))) {
                        providers.add(PathSafety.relative(o.root(), directory.resolve("qlpack.yml")));
                        addInputs(o, inputs, directory, "DEPENDENCY_INPUT");
                    }
                }
            }
        }
        inputs.sort(Comparator.comparing(Input::path));
        List<Step> steps = new ArrayList<>();
        steps.add(step(o, "version", List.of("version", "--format=json"), List.of()));
        List<String> format = new ArrayList<>(List.of("query", "format", "--check-only", "--"));
        for (Input input : inputs) {
            if (input.role().equals("M3_INPUT") && (input.path().endsWith(".ql") || input.path().endsWith(".qll"))) {
                format.add(work.resolve(input.path()).toString());
            }
        }
        steps.add(step(o, "format", format, List.of()));
        List<String> compile = new ArrayList<>(List.of("query", "compile"));
        compile.addAll(resources(o));
        compile.addAll(List.of("--", work.resolve("m3/ql/src").toString()));
        steps.add(step(o, "compile", compile, List.of()));
        List<String> test = new ArrayList<>(List.of("test", "run", "--no-learn", "--no-reuse-dataset", "--keep-databases", "--strict-test-discovery"));
        test.addAll(resources(o));
        test.addAll(List.of("--", work.resolve("m3/ql/test").toString()));
        steps.add(step(o, "test", test, List.of()));
        if (o.command() == CliOptions.Command.ANALYZE) addAnalysis(o, steps);
        return new CodeQlPlan(o, inputs, steps, providers);
    }

    private static void addInputs(CliOptions o, List<Input> inputs, Path directory, String role) throws IOException {
        if (!directory.toRealPath().equals(directory)) throw new IOException("Symbolic input directory: " + directory);
        for (Path file : PathSafety.files(directory)) {
            inputs.add(new Input(role, file, PathSafety.relative(o.root(), file), Files.size(file), PathSafety.sha256(file)));
        }
    }

    private static List<String> resources(CliOptions o) {
        return List.of("--threads=" + o.threads(), "--ram=" + o.ram(),
                "--no-default-compilation-cache", "--compilation-cache=" + o.output().resolve("cache"),
                "--common-caches=" + o.output().resolve("common-cache"), "--search-path=" + o.output().resolve("work"),
                "--additional-packs=" + o.output().resolve("work/m3/ql"));
    }

    private static void addAnalysis(CliOptions o, List<Step> steps) {
        Path src = o.output().resolve("work/m3/ql/src");
        Path sarif = o.output().resolve("m3-java.sarif");
        List<String> analysis = new ArrayList<>(List.of("database", "analyze", "--format=sarifv2.1.0", "--output=" + sarif));
        analysis.addAll(resources(o));
        analysis.addAll(List.of("--", o.database().toString(), "path:" + src.resolve("m3-java.qls")));
        steps.add(step(o, "analyze", analysis, List.of(sarif)));
        for (String name : List.of("CallableInventory", "CallDependencies", "NativeBoundaryInventory")) {
            Path bqrs = o.output().resolve(name + ".bqrs");
            Path csv = o.output().resolve(name + ".csv");
            List<String> query = new ArrayList<>(List.of("query", "run", "--database=" + o.database(), "--output=" + bqrs));
            query.addAll(resources(o));
            query.addAll(List.of("--", src.resolve("inventory/" + name + ".ql").toString()));
            steps.add(step(o, "run-" + name, query, List.of(bqrs)));
            steps.add(step(o, "decode-" + name, List.of("bqrs", "decode", "--format=csv", "--result-set=#select",
                    "--output=" + csv, "--", bqrs.toString()), List.of(csv)));
        }
    }

    private static Step step(CliOptions o, String name, List<String> arguments, List<Path> outputs) {
        List<String> full = new ArrayList<>(List.of(o.codeql()));
        full.addAll(arguments);
        return new Step(name, full, outputs);
    }

    void writeProvenance() throws IOException {
        List<List<?>> rows = new ArrayList<>();
        for (Input input : inputs) rows.add(List.of(input.role(), input.path(), input.bytes(), input.sha256()));
        Tsv.write(options.output().resolve("PROVENANCE.tsv"), List.of("role", "path", "bytes", "sha256"), rows);
    }

    void snapshot() throws IOException {
        Path work = options.output().resolve("work");
        Files.createDirectory(work);
        for (Input input : inputs) {
            Path destination = work.resolve(input.path());
            Files.createDirectories(destination.getParent());
            Files.copy(input.source(), destination, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
            if (!PathSafety.sha256(destination).equals(input.sha256())) {
                throw new IOException("Input changed while creating snapshot: " + input.path());
            }
        }
        StringBuilder yaml = new StringBuilder("# Generated execution workspace; original packs are unchanged.\nprovide:\n");
        for (String provider : providers) yaml.append("  - ").append(yamlString(provider)).append('\n');
        Files.writeString(work.resolve("codeql-workspace.yml"), yaml);
    }

    private static String yamlString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                .replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
}
