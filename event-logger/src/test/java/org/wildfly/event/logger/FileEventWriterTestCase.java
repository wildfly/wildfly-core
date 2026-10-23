/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.event.logger;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonObject;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link FileEventWriter}.
 */
public class FileEventWriterTestCase {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void writesJsonRecordToFile() throws Exception {
        final Path file = tmp.newFile("access.log").toPath();
        final EventFormatter formatter = JsonEventFormatter.builder().build();

        try (FileEventWriter writer = FileEventWriter.open(file, formatter, "")) {
            final Map<String, Object> data = new LinkedHashMap<>();
            data.put("bean", "OrderBean");
            data.put("outcome", "success");
            writer.write(makeEvent(data));
        }

        final List<String> lines = Files.readAllLines(file);
        Assert.assertEquals("expected one line", 1, lines.size());
        final JsonObject obj = Json.createReader(new StringReader(lines.get(0))).readObject();
        Assert.assertEquals("OrderBean", obj.getString("bean"));
        Assert.assertEquals("success", obj.getString("outcome"));
    }

    @Test
    public void rotatesOnDayChange() throws Exception {
        final Path file = tmp.newFile("access.log").toPath();
        final String suffix = ".yyyy-MM-dd";
        final EventFormatter formatter = JsonEventFormatter.builder().build();

        try (FileEventWriter writer = FileEventWriter.open(file, formatter, suffix)) {
            final Map<String, Object> data1 = new LinkedHashMap<>();
            data1.put("n", 1);
            writer.write(makeEvent(data1));
            Assert.assertTrue(Files.exists(file));

            // Force currentDate to yesterday via reflection
            final java.lang.reflect.Field dateField =
                    FileEventWriter.class.getDeclaredField("currentDate");
            dateField.setAccessible(true);
            final LocalDate yesterday = LocalDate.now().minusDays(1);
            dateField.set(writer, yesterday);

            final Map<String, Object> data2 = new LinkedHashMap<>();
            data2.put("n", 2);
            writer.write(makeEvent(data2));

            final Path rotated = file.resolveSibling(
                    file.getFileName() + DateTimeFormatter.ofPattern(suffix).format(yesterday));
            Assert.assertTrue("rotated file must exist", Files.exists(rotated));
            Assert.assertEquals("rotated file has first record",
                    1, Files.readAllLines(rotated).size());
            Assert.assertEquals("base file has second record",
                    1, Files.readAllLines(file).size());
        }
    }

    @Test
    public void noRotationWhenSuffixEmpty() throws Exception {
        final Path file = tmp.newFile("norate.log").toPath();

        try (FileEventWriter writer =
                FileEventWriter.open(file, JsonEventFormatter.builder().build(), "")) {
            final Map<String, Object> data1 = new LinkedHashMap<>();
            data1.put("n", 1);
            writer.write(makeEvent(data1));

            final java.lang.reflect.Field dateField =
                    FileEventWriter.class.getDeclaredField("currentDate");
            dateField.setAccessible(true);
            dateField.set(writer, LocalDate.now().minusDays(1));

            final Map<String, Object> data2 = new LinkedHashMap<>();
            data2.put("n", 2);
            writer.write(makeEvent(data2));
        }

        Assert.assertEquals("both records in single file", 2, Files.readAllLines(file).size());
        final long siblings = Files.list(tmp.getRoot().toPath())
                .filter(p -> !p.equals(file))
                .count();
        Assert.assertEquals("no rotation file", 0, siblings);
    }

    // -------------------------------------------------------------------------

    private static Event makeEvent(final Map<String, Object> data) {
        return new Event() {
            @Override
            public String getSource() {
                return "test";
            }

            @Override
            public Instant getInstant() {
                return Instant.EPOCH;
            }

            @Override
            public Map<String, Object> getData() {
                return data;
            }
        };
    }
}
