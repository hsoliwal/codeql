// SPDX-License-Identifier: MIT
package com.synexia.m3.codeql;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

record CliOptions(Command command, Path root, Path output, String codeql, Path database,
                  int threads, int ram, Duration timeout, int shards, int shard, boolean execute) {
    enum Command { INVENTORY, VERIFY, ANALYZE }

    static final String USAGE = """
            M3 CodeQL tools 0.1.0 (Java 21)
            inventory --root PATH --output FRESH_DIR [--shards N --shard I]
            verify    --root CHECKOUT --output FRESH_DIR [--execute] [CodeQL options]
            analyze   --root CHECKOUT --output FRESH_DIR --database DB [--execute] [CodeQL options]

            CodeQL options: --codeql EXECUTABLE (default: codeql), --threads N (default: 2),
                            --ram MIB (default: 3072), --timeout-seconds N (default: 600).
            Verify and analyze only write a plan unless --execute is present.
            Inventory reads local .ql files and writes evidence immediately; it never runs CodeQL.
            Shards are zero-based sorted-path round-robin assignments; validation covers all shards.
            Output must be a new directory, disjoint from the source root and database.
            RAM is a CodeQL budget hint, not a hard operating-system memory limit.
            --help and --version are available without a command.
            """;

    static CliOptions parse(String[] args) {
        if (args.length == 0) throw new IllegalArgumentException("A command is required.");
        Command command;
        try { command = Command.valueOf(args[0].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown command: " + args[0]); }
        Set<String> allowed = Set.of("--root", "--output", "--codeql", "--database", "--threads", "--ram",
                "--timeout-seconds", "--shards", "--shard", "--execute");
        Map<String, String> values = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (int i = 1; i < args.length; i++) {
            String option = args[i];
            if (!allowed.contains(option)) throw new IllegalArgumentException("Unknown option: " + option);
            if (!seen.add(option)) throw new IllegalArgumentException("Repeated option: " + option);
            if (option.equals("--execute")) { values.put(option, "true"); continue; }
            if (++i == args.length || args[i].startsWith("--") || args[i].isBlank()) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            values.put(option, args[i]);
        }
        Path root = Path.of(required(values, "--root"));
        Path output = Path.of(required(values, "--output"));
        int threads = number(values, "--threads", 2, 1, 256);
        int ram = number(values, "--ram", 3072, 2048, 1_048_576);
        int timeout = number(values, "--timeout-seconds", 600, 1, 86_400);
        int shards = number(values, "--shards", 1, 1, 1_000_000);
        int shard = number(values, "--shard", 0, 0, shards - 1);
        if (command != Command.INVENTORY && (seen.contains("--shards") || seen.contains("--shard"))) {
            throw new IllegalArgumentException("Sharding is supported by inventory only.");
        }
        if (command == Command.INVENTORY && seen.contains("--execute")) {
            throw new IllegalArgumentException("Inventory is local and does not accept --execute.");
        }
        if (command != Command.ANALYZE && seen.contains("--database")) {
            throw new IllegalArgumentException("--database is only valid for analyze.");
        }
        Path database = command == Command.ANALYZE ? Path.of(required(values, "--database")) : null;
        String codeql = values.getOrDefault("--codeql", "codeql");
        if (codeql.contains("/") || codeql.contains("\\")) codeql = Path.of(codeql).toAbsolutePath().normalize().toString();
        return new CliOptions(command, root, output, codeql, database, threads, ram,
                Duration.ofSeconds(timeout), shards, shard, seen.contains("--execute"));
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Required option: " + key);
        return value;
    }

    private static int number(Map<String, String> values, String key, int fallback, int min, int max) {
        try {
            int number = Integer.parseInt(values.getOrDefault(key, Integer.toString(fallback)));
            if (number < min || number > max) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer in [" + min + ", " + max + "].");
        }
    }
}
