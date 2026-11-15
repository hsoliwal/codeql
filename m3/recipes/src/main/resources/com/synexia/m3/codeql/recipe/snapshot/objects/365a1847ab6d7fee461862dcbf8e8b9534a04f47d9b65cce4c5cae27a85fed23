package com.synexia.m3.codeql;

/** Deliberately fake executable for process-contract tests; never used as CodeQL validation. */
public final class ProcessFixture {
    private ProcessFixture() {}

    public static void main(String[] arguments) throws InterruptedException {
        switch (arguments[0]) {
            case "success" -> System.out.println("process fixture output");
            case "nonzero" -> System.exit(7);
            case "timeout" -> Thread.sleep(30_000);
            default -> throw new IllegalArgumentException("Unknown fixture mode");
        }
    }
}
