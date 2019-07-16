/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

package org.wildfly.core.test.embedded;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.wildfly.core.embedded.Configuration;
import org.wildfly.core.embedded.Configuration.LoggerHint;
import org.wildfly.core.embedded.EmbeddedManagedProcess;
import org.wildfly.core.embedded.EmbeddedProcessFactory;
import org.wildfly.core.embedded.EmbeddedProcessStartException;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public abstract class LoggingTestCase extends AbstractTestCase {
    private static final PrintStream DFT_STDOUT = System.out;
    private static final PrintStream DFT_STDERR = System.err;
    private static final ByteArrayOutputStream STDOUT = new ByteArrayOutputStream();
    private static final ByteArrayOutputStream STDERR = new ByteArrayOutputStream();

    @BeforeClass
    public static void replaceStreams() {
        System.setOut(new PrintStream(STDOUT));
        System.setErr(new PrintStream(STDERR));
    }

    @AfterClass
    public static void restoreStreams() {
        System.setOut(DFT_STDOUT);
        System.setErr(DFT_STDERR);
    }

    @After
    public void clearStreams() throws IOException {
        if (STDOUT.size() > 0) {
            DFT_STDOUT.write(STDOUT.toByteArray());
            DFT_STDOUT.flush();
            STDOUT.reset();
        }
        if (STDERR.size() > 0) {
            DFT_STDERR.write(STDERR.toByteArray());
            DFT_STDERR.flush();
            STDERR.reset();
        }
    }

    protected void testStandalone(final LoggerHint loggerHint, final String filename, final String expectedPrefix) throws Exception {
        testStandalone(loggerHint, filename, expectedPrefix, true);
    }

    protected void testStandalone(final LoggerHint loggerHint, final String filename, final String expectedPrefix,
                                  final boolean validateConsoleOutput) throws Exception {
        // We need to explicitly override the JBoss Logging provider property as it's set in surefire
        if (loggerHint != null) {
            System.setProperty("org.jboss.logging.provider", loggerHint.getProviderCode());
        }
        final Configuration configuration = Environment.createConfigBuilder()
                .setLoggerHint(loggerHint)
                .build();
        test(EmbeddedProcessFactory.createStandaloneServer(configuration), filename, expectedPrefix, STANDALONE_CHECK, validateConsoleOutput);
    }

    protected void testHostController(final LoggerHint loggerHint, final String filename, final String expectedPrefix) throws Exception {
        testHostController(loggerHint, filename, expectedPrefix, true);
    }

    protected void testHostController(final LoggerHint loggerHint, final String filename, final String expectedPrefix,
                                      final boolean validateConsoleOutput) throws Exception {
        // We need to explicitly override the JBoss Logging provider property as it's set in surefire
        if (loggerHint != null) {
            System.setProperty("org.jboss.logging.provider", loggerHint.getProviderCode());
        }
        final Configuration configuration = Environment.createConfigBuilder()
                .setLoggerHint(loggerHint)
                .build();
        test(EmbeddedProcessFactory.createHostController(configuration), filename, expectedPrefix, HOST_CONTROLLER_CHECK, validateConsoleOutput);
    }

    private void test(final EmbeddedManagedProcess server, final String filename, final String expectedPrefix,
                      final Function<EmbeddedManagedProcess, Boolean> check, final boolean validateConsoleOutput) throws EmbeddedProcessStartException,
            IOException, TimeoutException, InterruptedException {
        final Path logFile = Environment.LOG_DIR.resolve(filename);
        try {
            startAndWaitFor(server, check);
            // Check for existence of the file and just ensure it's not empty
            Assert.assertTrue(String.format("Expected file \"%s\" to exist", logFile), Files.exists(logFile));
            final List<String> invalidLines = new ArrayList<>();
            if (expectedPrefix == null) {
                // Since no prefix is expected just check if the file is empty
                try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
                    Assert.assertNotNull("Log file should have at least one line: " + logFile, reader.readLine());
                }
            } else {
                final List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
                Assert.assertFalse("No lines found in file " + logFile, lines.isEmpty());
                for (String line : lines) {
                    if (!line.startsWith(expectedPrefix)) {
                        invalidLines.add(line);
                    }
                }
                if (!invalidLines.isEmpty()) {
                    final StringBuilder msg = new StringBuilder(64);
                    msg.append("The following lines did not contain the prefix \"")
                            .append(expectedPrefix)
                            .append('"')
                            .append(System.lineSeparator());
                    for (String line : invalidLines) {
                        msg.append('\t').append(line).append(System.lineSeparator());
                    }
                    Assert.fail(msg.toString());
                }
            }

            // Check that stdout and stderr are empty
            if (validateConsoleOutput) {
                System.out.flush();
                System.err.flush();
                Assert.assertEquals(String.format("The following messages were found on the console: %n%s", STDOUT.toString()), 0, STDOUT.size());
                // LOGMGR-213 introduced a java.lang.System.LoggerFinder which will activate and attempt to set the
                // java.util.logging.manager when a System.Logger is accessed. In most cases this is not an issue and
                // the embedded server does not require org.jboss.logmanager.LogManager. However since some tests here
                // use it, it's on the class path so an error message may be printed if it's not set as the log manager.
                // We need to ignore stderr output if that's the case. This will only be activated on Java 9 or higher.
                if (!supportsSystemLogger()) {
                    Assert.assertEquals(String.format("The following messages were found on the error console: %n%s", STDERR.toString()), 0, STDERR.size());
                }
            }
        } finally {
            server.stop();
            // We want to delete the log file in case another test checks for the same file. However on Windows the
            // log manager would still have the file open and the delete will fail. For Windows we need to ignore the
            // deleting the file to avoid the file lock failure.
            if (!TestSuiteEnvironment.isWindows()) {
                Files.deleteIfExists(logFile);
            }
        }
    }

    protected static boolean isIbmJdk() {
        return System.getProperty("java.vendor").startsWith("IBM");
    }

    private static boolean supportsSystemLogger() {
        try {
            Class.forName("java.lang.System$Logger", false, LoggingTestCase.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignore) {}
        return false;
    }
}
