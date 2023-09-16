/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package org.wildfly.core.launcher;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.wildfly.core.launcher.logger.LauncherMessages;

/**
 * Represents a JVM description.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class Jvm {
    private static final String JAVA_EXE;
    private static final Path JAVA_HOME;
    private static final boolean MODULAR_JVM;
    private static final boolean ENHANCED_SECURITY_MANAGER;

    static {
        String exe = "java";
        if (Environment.isWindows()) {
            exe = "java.exe";
        }
        JAVA_EXE = exe;
        final String javaHome = System.getProperty("java.home");
        JAVA_HOME = Paths.get(javaHome);

        // Assume we're in a modular environment
        final String javaSpecVersion = System.getProperty("java.specification.version");
        boolean modularJvm = true;
        boolean enhancedSecurityManager = false;
        int jvmVersion = 8;
        if (javaSpecVersion != null) {
            final Matcher matcher = Pattern.compile("^(?:1\\.)?(\\d+)$").matcher(javaSpecVersion);
            if (matcher.find()) {
                jvmVersion = Integer.parseInt(matcher.group(1));
                modularJvm = jvmVersion >= 9;
                enhancedSecurityManager = jvmVersion >= 12;
            }
        }
        MODULAR_JVM = modularJvm;
        ENHANCED_SECURITY_MANAGER = enhancedSecurityManager;
    }

    private static final Jvm DEFAULT = new Jvm(JAVA_HOME, MODULAR_JVM, ENHANCED_SECURITY_MANAGER);

    private final Path path;
    private final boolean isModular;
    private final boolean enhancedSecurityManager;

    private Jvm(final Path path, final boolean isModular, final boolean enhancedSecurityManager) {
        this.path = path;
        this.isModular = isModular;
        this.enhancedSecurityManager = enhancedSecurityManager;
    }

    /**
     * The current JVM.
     *
     * @return the current JVM
     */
    static Jvm current() {
        return DEFAULT;
    }

    /**
     * Creates a new JVM. If the {@code javaHome} is {@code null} the {@linkplain #current() current} JVM is returned.
     *
     * @param javaHome the path to the Java home
     *
     * @return a JVM descriptor based on the Java home path
     */
    static Jvm of(final String javaHome) {
        if (javaHome == null) {
            return DEFAULT;
        }
        return of(Paths.get(javaHome));
    }

    /**
     * Creates a new JVM. If the {@code javaHome} is {@code null} the {@linkplain #current() current} JVM is returned.
     *
     * @param javaHome the path to the Java home
     *
     * @return a JVM descriptor based on the Java home path
     */
    static Jvm of(final Path javaHome) {
        if (javaHome == null || javaHome.equals(JAVA_HOME)) {
            return DEFAULT;
        }
        final Path path = validateJavaHome(javaHome);
        return new Jvm(path, isModularJavaHome(path), hasEnhancedSecurityManager(javaHome));
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
     * Indicates whether or not this is a modular JVM supporting special SecurityManager values like "allow", "disallow" & "default"
     *
     * @return {@code true} if this is a modular JVM with enhanced SecurityManager, otherwise {@code false}
     */
    public boolean enhancedSecurityManagerAvailable() {
        return enhancedSecurityManager;
    }

    private static boolean isModularJavaHome(final Path javaHome) {
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
     * Checks to see if the {@code javaHome} supports special security manager tokens like "allow", "disallow" & "default"
     *
     * @param javaHome the Java Home if {@code null} an attempt to discover the Java Home will be done
     *
     * @return {@code true} if this is a modular environment
     */
    private static boolean hasEnhancedSecurityManager(final Path javaHome) {
        final List<String> cmd = new ArrayList<>();
        cmd.add(resolveJavaCommand(javaHome));
        cmd.add("-Djava.security.manager=allow");
        cmd.add("-version");
        return checkProcessStatus(cmd);
    }

    static boolean isPackageAvailable(final Path javaHome, final String optionalModularArgument) {
        final List<String> cmd = new ArrayList<>();
        cmd.add(resolveJavaCommand(javaHome));
        cmd.add(optionalModularArgument);
        cmd.add("-version");
        return checkProcessStatus(cmd);
    }

    /**
     * Checks to see if the {@code javaHome} is a modular JVM.
     *
     * @param javaHome the Java Home if {@code null} an attempt to discover the Java Home will be done
     *
     * @return {@code true} if this is a modular environment
     */
    private static boolean isModular(final Path javaHome) {
        final List<String> cmd = new ArrayList<>();
        cmd.add(resolveJavaCommand(javaHome));
        cmd.add("--add-modules=java.se");
        cmd.add("-version");
        return checkProcessStatus(cmd);
    }
    /**
     * Checks the process status.
     *
     * @param cmd command to execute
     *
     * @return {@code true} if command was successful, {@code false} if process failed.
     */
    private static boolean checkProcessStatus(final List<String> cmd) {
        boolean result;
        final ProcessBuilder builder = new ProcessBuilder(cmd);
        Process process = null;
        Path stdout = null;
        try {
            // Create a temporary file for stdout
            stdout = Files.createTempFile("stdout", ".txt");
            process = builder.redirectErrorStream(true)
                    .redirectOutput(stdout.toFile()).start();

            if (process.waitFor(30, TimeUnit.SECONDS)) {
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
                    if (containsWarning(stdout)) {
                        result = false;
                    }
                    Files.deleteIfExists(stdout);
                } catch (IOException ignore) {
                }
            }
        }
        return result;
    }

    private static boolean containsWarning(final Path logFile)  throws IOException {
        String line;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(logFile.toFile())))) {
            while ((line = br.readLine()) != null) {
                if (line.startsWith("WARNING:")) {
                    return true;
                }
            }
        }
        return false;
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
            exe = "java";
        } else {
            exe = javaHome.resolve("bin").resolve("java").toString();
        }
        if (exe.contains(" ")) {
            return "\"" + exe + "\"";
        }
        return exe;
    }

    private static Path validateJavaHome(final Path javaHome) {
        if (javaHome == null || Files.notExists(javaHome)) {
            throw LauncherMessages.MESSAGES.pathDoesNotExist(javaHome);
        }
        if (!Files.isDirectory(javaHome)) {
            throw LauncherMessages.MESSAGES.invalidDirectory(javaHome);
        }
        final Path result = javaHome.toAbsolutePath().normalize();
        final Path exe = result.resolve("bin").resolve(JAVA_EXE);
        if (Files.notExists(exe)) {
            final int count = exe.getNameCount();
            throw LauncherMessages.MESSAGES.invalidDirectory(exe.subpath(count - 2, count).toString(), javaHome);
        }
        return result;
    }
}
