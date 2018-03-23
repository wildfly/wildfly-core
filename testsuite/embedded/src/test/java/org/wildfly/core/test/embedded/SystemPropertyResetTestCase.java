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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.core.embedded.EmbeddedProcessFactory;
import org.wildfly.core.embedded.HostController;
import org.wildfly.core.embedded.StandaloneServer;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SystemPropertyResetTestCase extends AbstractTestCase {

    @Test
    public void testStandaloneSystemProperties() throws Exception {
        final Map<String, String> setProperties = Collections.singletonMap("jboss.server.log.dir", Environment.LOG_DIR.toString());
        setSystemProperties(setProperties);

        final Path baseDir = Environment.JBOSS_HOME.resolve("standalone");

        final Map<String, String> expectedProperties = new HashMap<>();
        expectedProperties.put("jboss.server.base.dir", resolvePath(baseDir));
        expectedProperties.put("jboss.server.config.dir", resolvePath(baseDir, "configuration"));
        expectedProperties.put("jboss.server.content.dir", resolvePath(baseDir, "data", "content"));
        expectedProperties.put("jboss.server.data.dir", resolvePath(baseDir, "data"));
        expectedProperties.put("jboss.server.temp.dir", resolvePath(baseDir, "tmp"));

        final StandaloneServer server = EmbeddedProcessFactory.createStandaloneServer(Environment.createConfigBuilder().build());

        validateNullProperties(expectedProperties.keySet());
        validateProperties(setProperties);

        startAndWaitFor(server, STANDALONE_CHECK);
        // Ensure the server has started
        try (ModelControllerClient client = server.getModelControllerClient()) {
            final ModelNode op = Operations.createReadAttributeOperation(AbstractTestCase.EMPTY_ADDRESS, "launch-type");
            final ModelNode result = executeOperation(client, op);
            Assert.assertEquals("EMBEDDED", result.asString());
        }
        // The properties should now be set
        validateProperties(expectedProperties);
        validateProperties(setProperties);

        // Stop the server and validate the properties have been reset
        server.stop();
        validateNullProperties(expectedProperties.keySet());
        validateProperties(setProperties);
    }

    @Test
    public void testHostControllerSystemProperties() throws Exception {

        final Map<String, String> setProperties = Collections.singletonMap("jboss.domain.log.dir", Environment.LOG_DIR.toString());
        setSystemProperties(setProperties);

        final Path baseDir = Environment.JBOSS_HOME.resolve("domain");

        final Map<String, String> expectedProperties = new HashMap<>();
        expectedProperties.put("jboss.domain.base.dir", resolvePath(baseDir));
        expectedProperties.put("jboss.domain.config.dir", resolvePath(baseDir, "configuration"));
        expectedProperties.put("jboss.domain.content.dir", resolvePath(baseDir, "data", "content"));
        expectedProperties.put("jboss.domain.data.dir", resolvePath(baseDir, "data"));
        expectedProperties.put("jboss.domain.temp.dir", resolvePath(baseDir, "tmp"));

        final HostController server = EmbeddedProcessFactory.createHostController(Environment.createConfigBuilder().build());

        validateNullProperties(expectedProperties.keySet());
        validateProperties(setProperties);

        startAndWaitFor(server, HOST_CONTROLLER_CHECK);
        // Ensure the server has started
        try (ModelControllerClient client = server.getModelControllerClient()) {
            final ModelNode op = Operations.createReadAttributeOperation(AbstractTestCase.EMPTY_ADDRESS, "launch-type");
            final ModelNode result = executeOperation(client, op);
            Assert.assertEquals("EMBEDDED", result.asString());
        }
        // The properties should now be set
        validateProperties(expectedProperties);
        validateProperties(setProperties);

        // Stop the server and validate the properties have been reset
        server.stop();
        validateNullProperties(expectedProperties.keySet());
        validateProperties(setProperties);
    }

    private static void validateNullProperties(final Set<String> expected) {
        for (String name : expected) {
            Assert.assertNull(String.format("Expected property %s to be null.", name), System.getProperty(name));
        }
    }

    private static void validateProperties(final Map<String, String> expected) {
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            final String key = entry.getKey();
            final String expectedValue = entry.getValue();
            final String foundValue = System.getProperty(key);
            Assert.assertEquals(String.format("Expected value %s for key %s, but found %s", expectedValue, key, foundValue), expectedValue, foundValue);
        }
    }

    private static void setSystemProperties(final Map<String, String> props) {
        for (Map.Entry<String, String> entry : props.entrySet()) {
            Assert.assertNull(System.setProperty(entry.getKey(), entry.getValue()));
        }
    }

    private static String resolvePath(final Path baseDir, final String... paths) {
        return Paths.get(baseDir.toString(), paths).toAbsolutePath().toString();
    }
}
