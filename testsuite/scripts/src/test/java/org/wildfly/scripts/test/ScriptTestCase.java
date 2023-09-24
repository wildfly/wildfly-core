/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.scripts.test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.common.test.ServerConfigurator;
import org.wildfly.common.test.ServerHelper;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class ScriptTestCase {

    static final Map<String, String> MAVEN_JAVA_OPTS = new LinkedHashMap<>();

    private final String scriptBaseName;
    private ExecutorService service;


    ScriptTestCase(final String scriptBaseName) {
        this.scriptBaseName = scriptBaseName;
    }

    @BeforeClass
    public static void configureEnvironment() throws Exception {
        final String localRepo = System.getProperty("maven.repo.local");
        if (localRepo != null) {
            MAVEN_JAVA_OPTS.put("JAVA_OPTS", "-Dmaven.repo.local=" + localRepo);
        }
        ServerConfigurator.configure();
    }

    @Before
    public void setup() {
        service = Executors.newCachedThreadPool();
    }

    @After
    public void cleanup() {
        service.shutdownNow();
    }

    @Test
    public void testBatchScript() throws Exception {
        Assume.assumeTrue(Shell.BATCH.isSupported());
        executeTests(Shell.BATCH);
    }

    @Test
    public void testPowerShellScript() throws Exception {
        Assume.assumeTrue(TestSuiteEnvironment.isWindows() && Shell.POWERSHELL.isSupported());
        executeTests(Shell.POWERSHELL);
    }

    @Test
    public void testBashScript() throws Exception {
        Assume.assumeTrue(!TestSuiteEnvironment.isWindows() && Shell.BASH.isSupported());
        executeTests(Shell.BASH);
    }

    @Test
    public void testDashScript() throws Exception {
        Assume.assumeTrue(!TestSuiteEnvironment.isWindows() && Shell.DASH.isSupported());
        executeTests(Shell.DASH);
    }

    @Test
    public void testKshScript() throws Exception {
        Assume.assumeTrue(!TestSuiteEnvironment.isWindows() && Shell.KSH.isSupported());
        executeTests(Shell.KSH);
    }

    abstract void testScript(ScriptProcess script) throws InterruptedException, TimeoutException, IOException;

    @SuppressWarnings("UnusedReturnValue")
    <T> Future<T> execute(final Callable<T> callable) {
        return service.submit(callable);
    }

    void validateProcess(final ScriptProcess script) throws InterruptedException {
        if (script.waitFor(ServerHelper.TIMEOUT, TimeUnit.SECONDS)) {
            // The script has exited, validate the exit code is valid
            final int exitValue = script.exitValue();
            if (exitValue != 0) {
                Assert.fail(script.getErrorMessage(String.format("Expected an exit value 0f 0 got %d", exitValue)));
            }
        } else {
            Assert.fail(script.getErrorMessage("The script process did not exit within " + ServerHelper.TIMEOUT + " seconds."));
        }
    }

    static ModelNode executeOperation(final ModelControllerClient client, final ModelNode op) throws IOException {
        return executeOperation(client, OperationBuilder.create(op).build());
    }

    private void executeTests(final Shell shell) throws InterruptedException, IOException, TimeoutException {
        for (Path path : ServerConfigurator.PATHS) {
            try (ScriptProcess script = new ScriptProcess(path, scriptBaseName, shell, ServerHelper.TIMEOUT)) {
                testScript(script);
                script.close();
                testCommonConf(script, shell);
            }
        }
    }

    private void testCommonConf(final ScriptProcess script, final Shell shell) throws InterruptedException, IOException, TimeoutException {
        testCommonConf(script, true, shell);
        testCommonConf(script, false, shell);
    }

    private void testCommonConf(final ScriptProcess script, final boolean useEnvVar, final Shell shell) throws InterruptedException, IOException, TimeoutException {
        final Map<String, String> env = new HashMap<>();
        final Path confFile;
        if (useEnvVar) {
            confFile = Paths.get(TestSuiteEnvironment.getTmpDir(), "test-common" + shell.getConfExtension());
            env.put("COMMON_CONF", confFile.toString());
        } else {
            confFile = script.getContainerHome().resolve("bin").resolve("common" + shell.getConfExtension());
        }
        // Create the common conf file which will simply echo some text and then exit the script
        final String text = "Test from common configuration to " + confFile.getFileName().toString();
        try (BufferedWriter writer = Files.newBufferedWriter(confFile, StandardCharsets.UTF_8)) {
            if (shell == Shell.POWERSHELL) {
                writer.write("Write-Output \"");
                writer.write(text);
                writer.write('"');
                writer.newLine();
                writer.write("break");
            } else {
                writer.write("echo \"");
                writer.write(text);
                writer.write('"');
                writer.newLine();
                writer.write("exit");
            }
            writer.newLine();
        }
        try {
            script.start(env);
            if (!script.waitFor(ServerHelper.TIMEOUT, TimeUnit.SECONDS)) {
                Assert.fail(script.getErrorMessage("Failed to exit script from " + confFile));
            }
            // Batch scripts print the quotes around the text
            final String expectedText = (shell == Shell.BATCH ? "\"" + text + "\"" : text);
            final List<String> lines = script.getStdout();
            Assert.assertEquals(script.getErrorMessage("There should only be one line logged before the script exited"),
                    1, lines.size());
            Assert.assertEquals(expectedText, lines.get(0));
        } finally {
            script.close();
            Files.delete(confFile);
        }
    }

    private static ModelNode executeOperation(final ModelControllerClient client, final Operation op) throws IOException {
        final ModelNode result = client.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(String.format("Failed to execute op: %s%nFailure Description: %s", op, Operations.getFailureDescription(result)));
        }
        return Operations.readResult(result);
    }
}
