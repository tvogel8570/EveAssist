package com.eveassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A button that removes something is drawn by {@code fragments/confirm.html}, which puts the Cancel beside it. One written by hand would be
 * a confirmation with no way to say no.
 */
class ConfirmationTemplatesTest {

    private static final Pattern DANGEROUS_SUBMIT = Pattern.compile("<button(?=[^>]*type=\"submit\")(?=[^>]*\\bbtn-error\\b)[^>]*>");

    @Test
    void noTemplateWritesARemovingButtonOfItsOwn() throws IOException {
        var templates = Path.of("src/main/resources/templates");
        try (var files = Files.walk(templates)) {
            var written = files.filter(file -> file.toString().endsWith(".html"))
                    .filter(file -> DANGEROUS_SUBMIT.matcher(read(file)).find())
                    // the account's deletion asks on a page of its own, with its own Cancel and a box to tick
                    .map(file -> templates.relativize(file).toString()).filter(name -> !name.equals("account-delete.html")).toList();

            assertThat(written).isEmpty();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
