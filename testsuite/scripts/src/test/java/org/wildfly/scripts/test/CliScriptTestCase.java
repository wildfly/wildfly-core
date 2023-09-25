/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.scripts.test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class CliScriptTestCase extends ScriptTestCase {

    public CliScriptTestCase() {
        super("jboss-cli");
    }

    @Override
    void testScript(final ScriptProcess script) throws InterruptedException, TimeoutException, IOException {
        Map<String, String> env = new LinkedHashMap<>(MAVEN_JAVA_OPTS);
        if (!TestSuiteEnvironment.isWindows()) {
            // WFCORE-5216
            env.put("JBOSS_MODULEPATH", "$JBOSS_HOME/modules:$HOME");
        }
        // Read an attribute
        script.start(env, "--commands=embed-server,:read-attribute(name=server-state),exit");
        Assert.assertNotNull("The process is null and may have failed to start.", script);
        Assert.assertTrue("The process is not running and should be", script.isAlive());

        validateProcess(script);

        StringBuilder builder = new StringBuilder();
        // Read the output lines which should be valid DMR
        for (String line : script.getStdout()) {
            // Skip lines like: "Picked up _JAVA_OPTIONS: ..."
            if (line.startsWith("Picked up _JAVA_") || line.startsWith("WARNING")) {
                continue;
            }
            builder.append(line);
        }
        final ModelNode result = ModelNode.fromString(builder.toString());
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(result.asString());
        }
        Assert.assertEquals(ClientConstants.CONTROLLER_PROCESS_STATE_RUNNING, Operations.readResult(result).asString());
    }
}
