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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeoutException;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
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
        // Read an attribute
        script.start(MAVEN_JAVA_OPTS, "--commands=embed-server,:read-attribute(name=server-state),exit");
        Assert.assertNotNull("The process is null and may have failed to start.", script);
        Assert.assertTrue("The process is not running and should be", script.isAlive());

        validateProcess(script);

        StringBuilder builder = new StringBuilder();
        // Read the output lines which should be valid DMR
        try (BufferedReader reader = Files.newBufferedReader(script.getStdout(), StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            // Skip lines like: "Picked up _JAVA_OPTIONS: ..."
            while (line.startsWith("Picked up _JAVA_")) {
                line = reader.readLine();
            }
            while (line.startsWith("WARNING")) {
                line = reader.readLine();
            }
            while (line != null) {
                builder.append(line);
                line = reader.readLine();
            }
        }
        final ModelNode result = ModelNode.fromString(builder.toString());
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(result.asString());
        }
        Assert.assertEquals(ClientConstants.CONTROLLER_PROCESS_STATE_RUNNING, Operations.readResult(result).asString());
    }
}
