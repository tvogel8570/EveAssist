package com.eveassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every text or search box in a template says what the browser may do with earlier entries. Without an
 * {@code autocomplete} attribute the browser opens a dropdown of earlier entries over the page (it hid the text under the
 * new-plan box). See documentation/architecture/ui-conventions.md.
 */
class TemplateInputsTest {

    private static final Pattern INPUT = Pattern.compile("<input\\b[^>]*>", Pattern.DOTALL);
    private static final Pattern TYPE = Pattern.compile("\\btype\\s*=\\s*\"([^\"]*)\"");

    @Test
    void everyTextOrSearchBoxSaysWhatTheBrowserMayDoWithEarlierEntries() throws IOException {
        var missing = new ArrayList<String>();
        try (var files = Files.walk(Path.of("src/main/resources/templates"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".html")).toList()) {
                var html = Files.readString(file);
                var matcher = INPUT.matcher(html);
                while (matcher.find()) {
                    var tag = matcher.group();
                    // a box made by a fragment gets its attributes from there
                    if (tag.contains("th:replace") || tag.contains("th:insert")) {
                        continue;
                    }
                    var type = TYPE.matcher(tag);
                    var kind = type.find() ? type.group(1) : "text";
                    if (kind.equals("text") || kind.equals("search")) {
                        if (!tag.contains("autocomplete") && !tag.contains("th:fragment")) {
                            missing.add(file.getFileName() + ": " + tag.replaceAll("\\s+", " "));
                        }
                    }
                }
            }
        }
        assertThat(missing).as("text or search boxes without an autocomplete attribute").isEmpty();
    }

    @Test
    void theSharedSearchBoxFragmentSaysAutocompleteOff() throws IOException {
        var fragment = Files.readString(Path.of("src/main/resources/templates/fragments/search-input.html"));

        assertThat(fragment).contains("autocomplete=\"off\"");
    }
}
