/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.jar.runtime;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

import org.wildfly.core.jar.runtime._private.BootableJarLogger;

/**
 * Allows for cleanup of a bootable JAR installation. The {@link #run()} method blocks until the
 * {@linkplain BootableEnvironment#getPidFile() PID file} is deleted or a
 * {@linkplain BootableEnvironment#getTimeout() timeout} is reached. Then there is an attempt to delete the install
 * directory.
 * <p>
 * If the {@code org.wildfly.core.jar.cleanup.newProcess} system property is set to {@code true}, the default for Windows,
 * a new process will be launched to delete the install directory.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class InstallationCleaner implements Runnable {
    private final BootableEnvironment environment;
    private final Path cleanupMarker;
    private final BootableJarLogger logger;
    private final boolean newProcess;
    private final int retries;
    private final boolean alreadyRunning;

    InstallationCleaner(final BootableEnvironment environment, final BootableJarLogger logger) throws IOException {
        this.environment = environment;
        cleanupMarker = environment.getJBossHome().resolve("wildfly-cleanup-marker");
        alreadyRunning = Files.exists(cleanupMarker);
        if (!alreadyRunning) {
            Files.createFile(cleanupMarker);
        }
        this.logger = logger;
        newProcess = getProperty("org.wildfly.core.jar.cleanup.newProcess", environment.isWindows());
        retries = getProperty("org.wildfly.core.jar.cleanup.retries", 3);
    }

    boolean isAlreadyRunning() {
        return alreadyRunning;
    }

    @Override
    public void run() {
        if (!alreadyRunning) {
            try {
                long timeout = environment.getTimeout() * 1000;
                final Path pidFile = environment.getPidFile();
                final long wait = 500L;
                while (Files.exists(pidFile)) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(wait);
                    } catch (InterruptedException ignore) {
                        break;
                    }
                    timeout -= wait;
                    if (timeout <= 0) {
                        logger.cleanupTimeout(environment.getTimeout(), pidFile);
                        break;
                    }
                }
                cleanup();
            } catch (IOException e) {
                logger.failedToStartCleanupProcess(e, environment.getJBossHome());
            }
        }
    }

    /**
     * Either starts a new process to delete the install directory or deletes the install directory in the current
     * process.
     * <p>
     * By default Windows will launch a new cleanup process. This can be controlled by setting the
     * {@code org.wildfly.core.jar.cleanup.newProcess} to {@code true} to launch or a new process or {@code false} to
     * delete the install directory in the current process.
     * </p>
     *
     * @throws IOException if an error occurs deleting the directory
     */
    void cleanup() throws IOException {
        if (newProcess) {
            try {
                newProcess();
            } catch (IOException e) {
                IOException suppressed = null;
                try {
                    deleteDirectory();
                } catch (IOException ex) {
                    suppressed = ex;
                }
                if (suppressed != null) {
                    e.addSuppressed(suppressed);
                }
                throw e;
            }
        } else {
            deleteDirectory();
        }
    }

    private void deleteDirectory() throws IOException {
        final Path installDir = environment.getJBossHome();
        Files.walkFileTree(installDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                try {
                    // Don't delete the cleanup marker until we're ready to delete the directory
                    if (!file.equals(cleanupMarker)) {
                        Files.delete(file);
                    }
                } catch (IOException e) {
                    logger.cantDelete(file.toString(), e);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
                try {
                    if (dir.equals(installDir)) {
                        // We have to delete the marker before we can delete the directory
                        Files.deleteIfExists(cleanupMarker);
                    }
                    Files.delete(dir);
                } catch (IOException e) {
                    logger.cantDelete(dir.toString(), e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void newProcess() throws IOException {
        // Start a new process which will clean up the install directory. This is done in a new process in cases where
        // this process may hold locks on to resources that need to be cleaned up.
        final String[] cmd = {
                getJavaCommand(),
                "-cp",
                // Use the current class path as it should just have the bootable JAR on it and this is where the
                // CleanupProcess is located.
                System.getProperty("java.class.path"),
                "org.wildfly.core.jar.boot.CleanupProcessor",
                environment.getJBossHome().toString(),
                Integer.toString(retries)
        };
        final ProcessBuilder builder = new ProcessBuilder(cmd)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .directory(new File(System.getProperty("user.dir")));
        builder.start();
    }

    private String getJavaCommand() {
        final Path javaHome = Paths.get(System.getProperty("java.home"));
        final Path java;
        if (environment.isWindows()) {
            java = javaHome.resolve("bin").resolve("java.exe");
        } else {
            java = javaHome.resolve("bin").resolve("java");
        }
        if (Files.exists(java)) {
            return java.toString();
        }
        return "java";
    }

    @SuppressWarnings("SameParameterValue")
    private static boolean getProperty(final String key, final boolean dft) {
        final String value = System.getProperty(key);
        if (value == null) {
            return dft;
        }
        return value.isEmpty() || Boolean.parseBoolean(value);
    }

    @SuppressWarnings("SameParameterValue")
    private static int getProperty(final String key, final int dft) {
        final String value = System.getProperty(key);
        if (value == null) {
            return dft;
        }
        return Integer.parseInt(value);
    }
}
