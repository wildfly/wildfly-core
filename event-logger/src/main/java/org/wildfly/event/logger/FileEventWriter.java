/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.event.logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * An {@link EventWriter} that writes formatted events to a file, with optional
 * date-suffix-based rotation.
 *
 * <p>When {@code rotateSuffix} is a non-empty {@link DateTimeFormatter}-compatible
 * pattern (e.g. {@code ".yyyy-MM-dd"}), the writer detects a date change on each
 * {@link #write} call.  The active log file is renamed to
 * {@code <basePath><suffix-for-yesterday>} and a fresh file is opened.  An empty
 * or {@code null} {@code rotateSuffix} disables rotation entirely.
 *
 * <p>The resolved {@code basePath} must be an absolute path to the log file.
 * Resolving a {@code relative-to} against a {@code PathManager} is the caller's
 * responsibility — not this class.
 *
 * <p>This class implements self-contained date-suffix rotation using standard JDK NIO APIs
 * rather than depending on {@code org.jboss.logmanager.handlers.PeriodicRotatingFileHandler}.
 * The {@code org.wildfly.event.logger} module carries {@code jakarta.json} and
 * {@code java.logging} and keeping the dependency budget at that level is deliberate.
 *
 * @author Tomasz Adamski
 */
public final class FileEventWriter implements EventWriter {

    private final Path basePath;
    private final EventFormatter formatter;
    private final boolean rotate;
    private final DateTimeFormatter suffixFormatter;

    private volatile BufferedWriter writer;
    private volatile LocalDate currentDate;

    private FileEventWriter(final Path basePath, final EventFormatter formatter, final String rotateSuffix) {
        this.basePath = basePath;
        this.formatter = formatter;
        this.rotate = rotateSuffix != null && !rotateSuffix.isEmpty();
        this.suffixFormatter = this.rotate ? DateTimeFormatter.ofPattern(rotateSuffix) : null;
    }

    /**
     * Creates and opens a new file writer.
     *
     * @param basePath     the absolute path of the log file to write to
     * @param formatter    the formatter used to convert each {@link Event} to a string
     * @param rotateSuffix a {@link DateTimeFormatter} pattern appended to the rotated
     *                     file name (e.g. {@code ".yyyy-MM-dd"}); empty or {@code null}
     *                     disables rotation
     * @return a new, open writer
     * @throws IOException if the file cannot be opened
     */
    public static FileEventWriter open(final Path basePath, final EventFormatter formatter,
            final String rotateSuffix) throws IOException {
        final FileEventWriter w = new FileEventWriter(basePath, formatter, rotateSuffix);
        w.openWriter();
        return w;
    }

    @Override
    public void write(final Event event) {
        rotateIfNeeded();
        final BufferedWriter w = this.writer;
        if (w == null) {
            return;
        }
        try {
            w.write(formatter.format(event));
            w.newLine();
            w.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws Exception {
        final BufferedWriter w = this.writer;
        this.writer = null;
        if (w != null) {
            w.close();
        }
    }

    // -------------------------------------------------------------------------

    private void openWriter() throws IOException {
        this.currentDate = LocalDate.now();
        this.writer = new BufferedWriter(
                new OutputStreamWriter(
                        Files.newOutputStream(basePath,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.APPEND),
                        StandardCharsets.UTF_8));
    }

    private void rotateIfNeeded() {
        if (!rotate) {
            return;
        }
        final LocalDate today = LocalDate.now();
        if (today.equals(currentDate)) {
            return;
        }
        // Day has changed — close the current file, rename it, open a fresh one.
        final String suffix = suffixFormatter.format(currentDate);
        final Path rotatedPath = basePath.resolveSibling(basePath.getFileName() + suffix);
        final BufferedWriter old = this.writer;
        this.writer = null;
        if (old != null) {
            try {
                old.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
        try {
            Files.move(basePath, rotatedPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // best effort — if rename fails we still open a fresh file
        }
        try {
            openWriter();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
