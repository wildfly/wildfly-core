/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LauncherTest {

    private Path stdout;

    @Before
    public void setup() throws IOException {
        stdout = Files.createTempFile("stdout", ".txt");
    }

    @After
    public void deleteStdout() throws IOException {
        if (stdout != null) {
            Files.deleteIfExists(stdout);
        }
    }

    @Test
    public void checkSingleNullEnvironmentVariable() throws Exception {
        final TestCommandBuilder commandBuilder = new TestCommandBuilder();
        checkProcess(Launcher.of(commandBuilder).addEnvironmentVariable("TEST", null));
    }

    @Test
    public void checkNullEnvironmentVariables() throws Exception {
        final TestCommandBuilder commandBuilder = new TestCommandBuilder();
        final Map<String, String> env = new HashMap<>();
        env.put("TEST", null);
        env.put("TEST_2", "test2");
        checkProcess(Launcher.of(commandBuilder).addEnvironmentVariables(env));
    }

    private void checkProcess(final Launcher launcher) throws IOException, InterruptedException {
        Process process = null;
        try {
            process = launcher.setRedirectErrorStream(true).redirectOutput(stdout).launch();
            Assert.assertNotNull("Process should not be null", process);
            Assert.assertTrue("Process should have exited within 5 seconds", process.waitFor(5, TimeUnit.SECONDS));
            Assert.assertEquals(String.format("Process should have exited with an exit code of 0:%n%s", Files.readString(stdout)),
                    0, process.exitValue());
        } finally {
            ProcessHelper.destroyProcess(process);
        }
    }

    /**
     * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
     */
    private static class TestCommandBuilder implements CommandBuilder {
        @Override
        public List<String> buildArguments() {
            return List.of();
        }

        @Override
        public List<String> build() {
            return List.of(Jvm.current().getCommand(), "-version");
        }
    }
}
