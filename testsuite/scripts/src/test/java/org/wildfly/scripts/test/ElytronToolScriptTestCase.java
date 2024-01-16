/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.scripts.test;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ElytronToolScriptTestCase extends ScriptTestCase {

    public ElytronToolScriptTestCase() {
        super("elytron-tool");
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        try {
            // Read an attribute
            script.start(MAVEN_JAVA_OPTS, "mask", "--salt", "12345678", "--iteration", "123", "--secret", "supersecretstorepassword");
            Assert.assertNotNull("The process is null and may have failed to start.", script);
            Assert.assertTrue("The process is not running and should be", script.isAlive());

            validateProcess(script);

            // Get the output and test the masked password
            for (String line : script.getStdout()) {
                if (line.contains("Picked up JDK_JAVA_OPTIONS:") || line.contains("Picked up JAVA_TOOL_OPTIONS:")) {
                    continue;
                }
                Assert.assertEquals("MASK-8VzWsSNwBaR676g8ujiIDdFKwSjOBHCHgnKf17nun3v;12345678;123", line);
            }
        } finally {
            script.close();
        }
    }
}
