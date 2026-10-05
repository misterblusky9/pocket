package com.misterblusky9.pym;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class PocketIndependenceTest {
    private static final Path SOURCES = Path.of("src", "main");
    private static final Pattern POCKET = Pattern.compile("pocket", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_CONSTANT = Pattern.compile("LEGACY_[A-Z_]+\\s*=\\s*\"[^\"]*\"");

    public static void main(final String[] args) {
        final List<String> failures = new ArrayList<>();
        final int[] scanned = {0};
        try (Stream<Path> files = Files.walk(SOURCES)) {
            files.filter(path -> {
                final String name = path.toString();
                return name.endsWith(".java") || name.endsWith(".json") || name.endsWith(".toml");
            }).forEach(path -> {
                scanned[0]++;
                final String relative = SOURCES.relativize(path).toString().replace('\\', '/');
                final String[] lines = read(path).split("\\R", -1);
                for (int i = 0; i < lines.length; i++) {
                    final String line = LEGACY_CONSTANT.matcher(lines[i]).replaceAll("");
                    final Matcher pocket = POCKET.matcher(line);
                    if (pocket.find()) {
                        failures.add(relative + ":" + (i + 1) + ": mentions Pocket outside a LEGACY_ constant: "
                                + lines[i].strip());
                    }
                }
            });
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }

        if (scanned[0] < 50) throw new AssertionError("suspiciously few sources scanned: " + scanned[0]);
        if (!failures.isEmpty()) throw new AssertionError("Pym depends on Pocket:\n  " + String.join("\n  ", failures));
        System.out.println("PocketIndependenceTest: PASS (" + scanned[0] + " files)");
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private PocketIndependenceTest() {}
}
