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

package org.wildfly.scripts.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

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

    private static final String[] POWER_SHELL_PREFIX = {
            "powershell",
            "-ExecutionPolicy",
            "Unrestricted",
            "-NonInteractive",
            "-File"
    };

    private final String scriptBaseName;
    private final Function<ModelControllerClient, Boolean> check;
    private ExecutorService service;


    ScriptTestCase(final String scriptBaseName) {
        this(scriptBaseName, null);
    }

    ScriptTestCase(final String scriptBaseName, final Function<ModelControllerClient, Boolean> check) {
        this.scriptBaseName = scriptBaseName;
        this.check = check;
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
        Assume.assumeTrue(TestSuiteEnvironment.isWindows());
        executeTests(".bat");
    }

    @Test
    public void testPowerShellScript() throws Exception {
        Assume.assumeTrue(TestSuiteEnvironment.isWindows() && isShellSupported("powershell", "-Help"));
        executeTests(".ps1", POWER_SHELL_PREFIX);
    }

    @Test
    public void testBashScript() throws Exception {
        Assume.assumeTrue(!TestSuiteEnvironment.isWindows() && isShellSupported("bash", "-c", "echo", "test"));
        executeTests(".sh");
    }

    @Test
    public void testDashScript() throws Exception {
        Assume.assumeTrue(!TestSuiteEnvironment.isWindows() && isShellSupported("dash", "-c", "echo", "test"));
        executeTests(".sh", "dash");
    }

    @Test
    public void testKshScript() throws Exception {
        Assume.assumeTrue(!TestSuiteEnvironment.isWindows() && isShellSupported("ksh", "-c", "echo", "test"));
        executeTests(".sh", "ksh");
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

    private void executeTests(final String scriptExtension, final String... prefixCmds) throws InterruptedException, IOException, TimeoutException {
        for (Path path : ServerConfigurator.PATHS) {
            try (ScriptProcess script = new ScriptProcess(path, scriptBaseName + scriptExtension, ServerHelper.TIMEOUT, check, prefixCmds)) {
                testScript(script);
            }
        }
    }

    private static ModelNode executeOperation(final ModelControllerClient client, final Operation op) throws IOException {
        final ModelNode result = client.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(String.format("Failed to execute op: %s%nFailure Description: %s", op, Operations.getFailureDescription(result)));
        }
        return Operations.readResult(result);
    }

    private static boolean isShellSupported(final String name, final String... args) throws IOException, InterruptedException {
        final List<String> cmd = new ArrayList<>();
        cmd.add(name);
        if (args != null && args.length > 0) {
            cmd.addAll(Arrays.asList(args));
        }
        final Path stdout = Files.createTempFile(name + "-supported", ".log");
        final ProcessBuilder builder = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(stdout.toFile());
        Process process = null;
        try {
            process = builder.start();
            if (!process.waitFor(ServerHelper.TIMEOUT, TimeUnit.SECONDS)) {
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
            Files.deleteIfExists(stdout);
        }
    }
}
