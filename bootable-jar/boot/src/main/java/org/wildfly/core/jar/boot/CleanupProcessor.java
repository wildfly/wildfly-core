/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.jar.boot;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

/**
 * An entry point that expects a first parameter of an install directory, a second parameter of the number of retries
 * used to delete the install directory and finally the pid of the process that started it.
 * <p>
 * The retries are used for cases where some un-expected errors occurs during deletion.
 * A 0.5 second sleep will happen between each retry.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("MagicNumber")
public class CleanupProcessor {

    public static void main(final String[] args) throws Exception {
        if (args == null || args.length != 3) {
            throw new IllegalArgumentException("The path to the install directory, number of retires and pid are required.");
        }
        final Path installDir = Paths.get(args[0]);
        final int retries = Integer.parseInt(args[1]);
        final long pid = Long.parseLong(args[2]);
        final Path cleanupMarker = installDir.resolve("wildfly-cleanup-marker");
        // An invalid case, do not attempt to delete an installation that has no marker.
        if (Files.notExists(cleanupMarker)) {
            return;
        }
        // Wait until the process that started this process is terminated.
        // Starting the deletion of the installation during the other process shutdown
        // has no guarantee that the installation will be fully cleaned up.
        while (ProcessHandle.of(pid).isPresent()) {
            TimeUnit.MILLISECONDS.sleep(500L);
        }
        int attempts = 1;
        while (attempts <= retries) {
            final boolean lastAttempt = attempts == retries;
            try {
                cleanup(installDir, cleanupMarker, lastAttempt);
                break;
            } catch (IOException e) {
                if (lastAttempt) {
                    throw e;
                }
                TimeUnit.MILLISECONDS.sleep(500L);
                attempts++;
            }
        }
    }

    private static void cleanup(final Path installDir, final Path cleanupMarker, final boolean log) throws IOException {
        Files.walkFileTree(installDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                try {
                    // Don't delete the cleanup marker until we're ready to delete the directory
                    if (!file.equals(cleanupMarker)) {
                        Files.delete(file);
                    }
                } catch (IOException e) {
                    if (log) {
                        logError("Failed to delete file %s%n\t%s%n", file, e.getLocalizedMessage());
                    } else {
                        throw e;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                try {
                    if (dir.equals(installDir)) {
                        // We have to delete the marker before we can delete the directory
                        Files.deleteIfExists(cleanupMarker);
                    }
                    Files.delete(dir);
                } catch (IOException e) {
                    if (log) {
                        logError("Failed to delete directory %s%n\t%s%n", dir, e.getLocalizedMessage());
                    } else {
                        throw e;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @SuppressWarnings("UseOfSystemOutOrSystemErr")
            private void logError(final String format, final Object... args) {
                System.err.printf(format, args);
            }
        });
    }
}
