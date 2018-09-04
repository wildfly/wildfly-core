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
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ElytronToolScriptTestCase extends ScriptTestCase {

    @Test
    public void testBatchScript() throws Exception {
        Assume.assumeTrue(Environment.isWindows());
        testStart(new ScriptProcess(getExecutable("elytron-tool.bat"), WIN_CMD_PREFIX));
    }

    @Test
    public void testPowerShellScript() throws Exception {
        Assume.assumeTrue(Environment.isWindows() && isShellSupported("powershell", "-Help"));
        testStart(new ScriptProcess(getExecutable("elytron-tool.ps1"), POWER_SHELL_PREFIX));
    }

    @Test
    public void testBashScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("bash", "-c", "echo", "test"));
        testStart(new ScriptProcess(getExecutable("elytron-tool.sh")));
    }

    @Test
    @Ignore("Current tests do not work with csh")
    public void testCshScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("csh", "-c", "echo", "test"));
        testStart(new ScriptProcess(getExecutable("elytron-tool.sh"), "csh"));
    }


    @Test
    public void testDashScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("dash", "-c", "echo", "test"));
        testStart(new ScriptProcess(getExecutable("elytron-tool.sh"), "dash"));
    }

    @Test
    @Ignore("Current tests do not work with fish")
    public void testFishScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("fish", "-c", "echo", "test"));
        testStart(new ScriptProcess(getExecutable("elytron-tool.sh"), "fish"));
    }

    @Test
    public void testKshScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("ksh", "-c", "echo", "test"));
        testStart(new ScriptProcess(getExecutable("elytron-tool.sh"), "ksh"));
    }

    @Test
    @Ignore("Current tests do not work with tcsh")
    public void testTcshScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("tcsh", "-c", "echo", "test"));
        testStart(new ScriptProcess(getExecutable("elytron-tool.sh"), "tcsh"));
    }

    @Test
    @Ignore("Current tests do not work with zsh")
    public void testZshScript() throws Exception {
        Assume.assumeTrue(!Environment.isWindows() && isShellSupported("zsh", "-c", "echo", "test"));
        testStart(new ScriptProcess(getExecutable("elytron-tool.sh"), "zsh"));
    }

    // EAP_HOME/bin/elytron-tool.sh mask --salt 12345678 --iteration 123 --secret supersecretstorepassword
    //
    //MASK-8VzWsSNwBaR676g8ujiIDdFKwSjOBHCHgnKf17nun3v;12345678;123

    private void testStart(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        try {
            // Read an attribute
            script.start("mask", "--salt", "12345678", "--iteration", "123", "--secret", "supersecretstorepassword");
            Assert.assertNotNull("The process is null and may have failed to start.", script);
            Assert.assertTrue("The process is not running and should be", script.isAlive());

            validateProcess(script);

            // Get the output and test the masked password
            final List<String> allLines = Files.readAllLines(script.getStdout());
            // We should only have one line
            Assert.assertEquals("Expected only one entry: " + allLines.toString(), 1, allLines.size());
            Assert.assertEquals("MASK-8VzWsSNwBaR676g8ujiIDdFKwSjOBHCHgnKf17nun3v;12345678;123", allLines.get(0));
        } finally {
            script.close();
        }
    }
}
