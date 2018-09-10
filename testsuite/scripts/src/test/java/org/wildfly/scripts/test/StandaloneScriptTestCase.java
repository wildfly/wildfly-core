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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class StandaloneScriptTestCase extends ScriptTestCase {

    @BeforeClass
    public static void updateConf() throws Exception {
        appendConf("standalone", "JAVA_OPTS");
    }

    @Test
    public void testBatchScript() throws Exception {
        Assume.assumeTrue(Environment.isWindows());
        testStart(new ScriptProcess(getExecutable("standalone.bat"), STANDALONE_CHECK, WIN_CMD_PREFIX));
    }

    @Test
    public void testPowerShellScript() throws Exception {
        Assume.assumeTrue(Environment.isWindows() && isShellSupported("powershell", "-Help"));
        testStart(new ScriptProcess(getExecutable("standalone.ps1"), STANDALONE_CHECK, POWER_SHELL_PREFIX), "&&", "exit");
    }

    @Test
    public void testBashScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("bash", "-c", "echo", "test"));
        testStart(new ScriptProcess(getExecutable("standalone.sh"), STANDALONE_CHECK));
    }

    @Test
    @Ignore("Current tests do not work with csh")
    public void testCshScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("csh", "-c", "echo", "test"));
        testStart(new ScriptProcess(getExecutable("standalone.sh"), STANDALONE_CHECK, "csh"));
    }


    @Test
    public void testDashScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("dash", "-c", "echo", "test"));
        testStart(new ScriptProcess(getExecutable("standalone.sh"), STANDALONE_CHECK, "dash"));
    }

    @Test
    @Ignore("Current tests do not work with fish")
    public void testFishScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("fish", "-c", "echo", "test"));
        testStart(new ScriptProcess(getExecutable("standalone.sh"), STANDALONE_CHECK, "fish"));
    }

    @Test
    public void testKshScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("ksh", "-c", "echo", "test"));
        testStart(new ScriptProcess(getExecutable("standalone.sh"), STANDALONE_CHECK, "ksh"));
    }

    @Test
    @Ignore("Current tests do not work with tcsh")
    public void testTcshScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("tcsh", "-c", "echo", "test"));
        testStart(new ScriptProcess(getExecutable("standalone.sh"), STANDALONE_CHECK, "tcsh"));
    }

    @Test
    @Ignore("Current tests do not work with zsh")
    public void testZshScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("zsh", "-c", "echo", "test"));
        testStart(new ScriptProcess(getExecutable("standalone.sh"), STANDALONE_CHECK, "zsh"));
    }

    private void testStart(final ScriptProcess script, final String... arguments) throws InterruptedException, TimeoutException, IOException {
        try {
            final Collection<String> args = new ArrayList<>();
            args.addAll(Arrays.asList(DEFAULT_SERVER_JAVA_OPTS));
            args.addAll(Arrays.asList(arguments));
            script.start(args);
            Assert.assertNotNull("The process is null and may have failed to start.", script);
            Assert.assertTrue("The process is not running and should be", script.isAlive());

            // Shutdown the server
            final Callable<ModelNode> callable = new Callable<ModelNode>() {
                @Override
                public ModelNode call() throws Exception {
                    try (ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
                        return executeOperation(client, Operations.createOperation("shutdown"));
                    }
                }
            };
            execute(callable);
            validateProcess(script);
        } finally {
            script.close();
        }
    }
}
