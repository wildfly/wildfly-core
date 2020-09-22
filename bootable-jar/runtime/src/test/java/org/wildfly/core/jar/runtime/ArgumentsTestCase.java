/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.core.jar.runtime;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author jdenise@redhat.com
 */
public class ArgumentsTestCase {


    @Test
    public void test() throws Exception {
        {
            String[] args = {};
            Arguments arguments = Arguments.parseArguments(Arrays.asList(args), createEnvironment());
            assertNull(arguments.getDeployment());
            assertTrue(arguments.getServerArguments().isEmpty());
            assertFalse(arguments.isHelp());
            assertFalse(arguments.isVersion());
        }

        {
            Path config = Files.createTempFile(null, null);
            Path deployment = Files.createTempFile(null, ".war");
            try {
                String[] args = {"--version", "--help",
                    "--deployment=" + deployment
                };
                Arguments arguments = Arguments.parseArguments(Arrays.asList(args), createEnvironment());
                assertEquals(arguments.getDeployment(), deployment);
                assertEquals(1, arguments.getServerArguments().size());
                assertTrue(arguments.isHelp());
                assertTrue(arguments.isVersion());
            } finally {
                Files.delete(deployment);
                Files.delete(config);
            }
        }

        {
            boolean error = false;
            try {
                String[] args = {"--foo"};
                Arguments arguments = Arguments.parseArguments(Arrays.asList(args), createEnvironment());
                error = true;
            } catch (Exception ex) {
                // OK expected
            }
            if (error) {
                throw new Exception("Should have failed");
            }
        }

        {
            boolean error = false;
            try {
                String[] args = {"--deployment=foo"};
                Arguments arguments = Arguments.parseArguments(Arrays.asList(args), createEnvironment());
                error = true;
            } catch (Exception ex) {
                // OK expected
            }
            if (error) {
                throw new Exception("Should have failed");
            }
        }
    }

    @Test
    public void testSystemProperties() throws Exception {
        final List<String> args = Collections.singletonList("-Dtest.name=value");
        final TestPropertyUpdater propertyUpdater = new TestPropertyUpdater();
        Arguments.parseArguments(args, createEnvironment(propertyUpdater));
        Assert.assertTrue("Expected property test.name to exist: " + propertyUpdater, propertyUpdater.properties.containsKey("test.name"));
        Assert.assertEquals("Expected the value \"value\" for property test.name: " + propertyUpdater,
                "value", propertyUpdater.properties.get("test.name"));
    }

    @Test
    public void testPropertiesWithSpace() throws Exception {
        final URL resource = getClass().getResource("/test-system.properties");
        Assert.assertNotNull("Could not locate test-system.properties", resource);
        final List<String> args = Arrays.asList("--properties", resource.toString());
        final TestPropertyUpdater propertyUpdater = new TestPropertyUpdater();
        Arguments.parseArguments(args, createEnvironment(propertyUpdater));
        Assert.assertTrue("Expected property org.wildfly.core.jar.test to exist: " + propertyUpdater, propertyUpdater.properties.containsKey("org.wildfly.core.jar.test"));
    }

    @Test
    public void testPropertiesWithEquals() throws Exception {
        final URL resource = getClass().getResource("/test-system.properties");
        Assert.assertNotNull("Could not locate test-system.properties", resource);
        final List<String> args = Collections.singletonList("--properties=" + resource.toString());
        final TestPropertyUpdater propertyUpdater = new TestPropertyUpdater();
        Arguments.parseArguments(args, createEnvironment(propertyUpdater));
        Assert.assertTrue("Expected property org.wildfly.core.jar.test to exist: " + propertyUpdater, propertyUpdater.properties.containsKey("org.wildfly.core.jar.test"));
    }

    private static BootableEnvironment createEnvironment() {
        return createEnvironment(new TestPropertyUpdater());
    }

    private static BootableEnvironment createEnvironment(final PropertyUpdater propertyUpdater) {
        final Path fakeHome = Paths.get(System.getProperty("test.jboss.home"));
        return BootableEnvironment.of(fakeHome, propertyUpdater);
    }

    private static class TestPropertyUpdater implements PropertyUpdater {
        final Map<String, String> properties = new HashMap<>();

        @Override
        public String setProperty(final String name, final String value) {
            return properties.put(name, value);
        }

        @Override
        public String toString() {
            return properties.toString();
        }
    }

}
