/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.core.testrunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a JVM description.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"SameParameterValue", "MethodOnlyUsedFromInnerClass", "unused"})
public class TestJvm {
    private static final String JAVA_EXE;
    private static final Path JAVA_HOME;

    static {
        final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String exe = "java";
        if (os.contains("win")) {
            exe = "java.exe";
        }
        JAVA_EXE = exe;
        String javaHome = System.getProperty("test.java.home", System.getenv("JAVA_HOME"));
        if (javaHome == null) {
            javaHome = System.getProperty("java.home");
        }
        JAVA_HOME = Paths.get(javaHome).toAbsolutePath();
    }

    private static class Holder {
        static final TestJvm INSTANCE = new TestJvm(JAVA_HOME, isModularJavaHome(JAVA_HOME));
    }

    private final Path path;
    private final boolean isModular;
    private final boolean isIbm;
    private final boolean isJ9;

    private TestJvm(final Path path, final boolean isModular) {
        this.path = path;
        this.isModular = isModular;
        final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        isIbm = System.getProperty("java.vendor").startsWith("IBM");
        isJ9 = System.getProperty("java.vendor").contains("OpenJ9") || isIbm;
    }

    /**
     * Returns the JVM instance a forked process should run on.
     *
     * @return the JVM instance
     */
    public static TestJvm getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * The the command which can launch this JVM.
     *
     * @return the command
     */
    public String getCommand() {
        return resolveJavaCommand(path);
    }

    /**
     * The path to this JVM.
     *
     * @return the path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Indicates whether or not this is a modular JVM.
     *
     * @return {@code true} if this is a modular JVM, otherwise {@code false}
     */
    public boolean isModular() {
        return isModular;
    }

    /**
     * Indicates whether or not this is a IBM JVM.
     *
     * @return {@code true} if this is a IBM JVM, otherwise {@code false}
     *
     * @see #isJ9Jvm()
     */
    public boolean isIbmJvm() {
        return isIbm;
    }

    /**
     * Indicates whether or not this is an Eclipse OpenJ9 or IBM J9 JVM.
     *
     * @return {@code true} if this is an Eclipse OpenJ9 or IBM J9 JVM, otherwise {@code false}
     */
    public boolean isJ9Jvm() {
        return isJ9;
    }


    private static boolean isModularJavaHome(final Path javaHome) {
        final Path currentJavaHome = Paths.get(System.getProperty("java.home"));
        if (javaHome.equals(currentJavaHome)) {
            final String javaSpecVersion = System.getProperty("java.specification.version");
            if (javaSpecVersion != null) {
                final Matcher matcher = Pattern.compile("^(?:1\\.)?(\\d+)$").matcher(javaSpecVersion);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1)) >= 9;
                }
            }
        }
        final Path jmodsDir = javaHome.resolve("jmods");
        // If the jmods directory exists we can safely assume this is a modular JDK, note even in a modular JDK this
        // may not exist.
        if (Files.isDirectory(jmodsDir)) {
            return true;
        }
        // Next check for a $JAVA_HOME/release file, for a JRE this will not exist
        final Path releaseFile = javaHome.resolve("release");
        if (Files.isReadable(releaseFile) && Files.isRegularFile(releaseFile)) {
            // Read the file and look for a JAVA_VERSION property
            try (final BufferedReader reader = Files.newBufferedReader(releaseFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("JAVA_VERSION=")) {
                        // Get the version value
                        final int index = line.indexOf('=');
                        return isModularJavaVersion(line.substring(index + 1).replace("\"", ""));
                    }
                }
            } catch (IOException ignore) {
            }
        }
        // Final check is to launch a new process with some modular JVM arguments and check the exit code
        return isModular(javaHome);
    }

    private static boolean isModularJavaVersion(final String version) {
        if (version != null) {
            try {
                final String[] versionParts = version.split("\\.");
                if (versionParts.length == 1) {
                    return Integer.parseInt(versionParts[0]) >= 9;
                } else if (versionParts.length > 1) {
                    // Check the first part and if one, use the second part
                    if ("1".equals(versionParts[0])) {
                        return Integer.parseInt(versionParts[2]) >= 9;
                    }
                    return Integer.parseInt(versionParts[0]) >= 9;
                }
            } catch (Exception ignore) {
            }
        }
        return false;
    }


    /**
     * Checks to see if the {@code javaHome} is a modular JVM.
     *
     * @param javaHome the Java Home if {@code null} an attempt to discover the Java Home will be done
     *
     * @return {@code true} if this is a modular environment
     */
    private static boolean isModular(final Path javaHome) {
        boolean result;
        final List<String> cmd = new ArrayList<>();
        cmd.add(resolveJavaCommand(javaHome));
        cmd.add("--add-modules=java.se");
        cmd.add("-version");
        final ProcessBuilder builder = new ProcessBuilder(cmd);
        Process process = null;
        Path stdout = null;
        try {
            // Create a temporary file for stdout
            stdout = Files.createTempFile("stdout", ".txt");
            process = builder.redirectErrorStream(true)
                    .redirectOutput(stdout.toFile()).start();

            if (process.waitFor(1, TimeUnit.SECONDS)) {
                result = process.exitValue() == 0;
            } else {
                result = false;
            }
        } catch (IOException | InterruptedException e) {
            result = false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (stdout != null) {
                try {
                    Files.deleteIfExists(stdout);
                } catch (IOException ignore) {
                }
            }
        }
        return result;
    }


    /**
     * Returns the Java executable command.
     *
     * @param javaHome the java home directory or {@code null} to use the default
     *
     * @return the java command to use
     */
    private static String resolveJavaCommand(final Path javaHome) {
        final String exe;
        if (javaHome == null) {
            exe = JAVA_EXE;
        } else {
            exe = javaHome.resolve("bin").resolve(JAVA_EXE).toString();
        }
        if (exe.contains(" ")) {
            return "\"" + exe + "\"";
        }
        return exe;
    }
}
