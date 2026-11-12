package com.eveassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The migration naming rules from documentation/architecture/data.md, checked on the files themselves. */
class MigrationNamingTest {

    private static final Path PRODUCTION = Path.of("src/main/resources/db/migration");
    private static final Path TEST_ONLY = Path.of("src/test/resources/db/test");

    private static final String DESCRIPTION = "[a-z0-9]+(-[a-z0-9]+)*";
    private static final Pattern PRODUCTION_NAME = Pattern.compile("V(\\d{8}_\\d{4})__" + DESCRIPTION + "\\.sql");
    private static final Pattern TEST_NAME = Pattern.compile(
            "(?:V(\\d{8}_\\d{4})__T_|afterMigrate__T_|R__T_)" + DESCRIPTION + "\\.sql");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    @Test
    void productionMigrationsAreNamedWithAUtcTimestampAndDashes() throws IOException {
        for (var name : names(PRODUCTION)) {
            var matcher = PRODUCTION_NAME.matcher(name);
            assertThat(matcher.matches())
                    .as("%s should be VYYYYMMDD_HHMM__descriptive-name-with-dashes.sql", name).isTrue();
            assertRealStampNotInTheFuture(name, matcher.group(1));
        }
    }

    @Test
    void testOnlyFilesCarryTheMarkerAndProductionFilesNeverDo() throws IOException {
        for (var name : names(TEST_ONLY)) {
            var matcher = TEST_NAME.matcher(name);
            assertThat(matcher.matches())
                    .as("%s should be VYYYYMMDD_HHMM__T_name.sql, afterMigrate__T_name.sql or R__T_name.sql", name)
                    .isTrue();
            if (matcher.group(1) != null) {
                assertRealStampNotInTheFuture(name, matcher.group(1));
            }
        }
        assertThat(names(PRODUCTION)).noneMatch(name -> name.contains("__T_"));
    }

    private static void assertRealStampNotInTheFuture(String name, String stamp) {
        LocalDateTime time = LocalDateTime.parse(stamp, STAMP);
        assertThat(time).as("%s has a stamp in the future, so later migrations would be blocked", name)
                .isBeforeOrEqualTo(LocalDateTime.now(ZoneOffset.UTC));
    }

    private static List<String> names(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString()).filter(name -> !name.equals(".gitkeep")).sorted().toList();
        }
    }
}
