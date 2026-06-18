/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.jar.runtime;

import java.io.FileWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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

    private static final String FILTER_PROP = "jdk.serialFilter";

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

        {
            Path script = Files.createTempFile(null, ".cli");
            try {
                String[] args = {"--cli-script=" + script };
                Arguments arguments = Arguments.parseArguments(Arrays.asList(args), createEnvironment());
                assertEquals(arguments.getCLIScript(), script);
                assertEquals(0, arguments.getServerArguments().size());
            } finally {
                Files.delete(script);
            }
        }

        {
            boolean error = false;
            try {
                String[] args = {"--cli-script=foo.cli"};
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
            String[] args = {};
            final TestPropertyUpdater propertyUpdater = new TestPropertyUpdater();
            Arguments arguments = Arguments.parseArguments(Arrays.asList(args), createEnvironment(propertyUpdater));
            if (System.getenv("DISABLE_JDK_SERIAL_FILTER") == null) {
                Assert.assertNotNull("Expected filter to exist.", arguments.getRequiredSerialFilter());
                String val = System.getenv("JDK_SERIAL_FILTER");
                if (val != null) {
                    Assert.assertEquals("Expected the value " + val + " for the serial filter", val, arguments.getRequiredSerialFilter());
                }
            } else {
                Assert.assertNull("No filter expected ", arguments.getRequiredSerialFilter());
            }
        }

        {
            Properties p = new Properties();
            p.setProperty(FILTER_PROP, "foo");
            Path propsFile = Files.createTempFile(null, ".properties");
            propsFile.toFile().deleteOnExit();
            try (FileWriter w = new FileWriter(propsFile.toFile())) {
                p.store(w, "");
            }
            String[] args = {"--properties", propsFile.toString()};
            final TestPropertyUpdater propertyUpdater = new TestPropertyUpdater();
            Arguments arguments = Arguments.parseArguments(Arrays.asList(args), createEnvironment(propertyUpdater));
            // The property is not set, it has been replaced by an explicit call.
            Assert.assertFalse("Property " + FILTER_PROP + " should not be set.", propertyUpdater.properties.containsKey(FILTER_PROP));
            Assert.assertEquals("Expected the value foo for the serial filter", "foo", arguments.getRequiredSerialFilter());
        }
    }

    @Test
    public void testSerialFilterProperty() throws Exception {
        final List<String> args = Collections.singletonList("-D"+FILTER_PROP+"=foo2");
        final TestPropertyUpdater propertyUpdater = new TestPropertyUpdater();
        Arguments arguments = Arguments.parseArguments(args, createEnvironment(propertyUpdater));
        Assert.assertFalse("Property " + FILTER_PROP + " should not be set.", propertyUpdater.properties.containsKey(FILTER_PROP));
        Assert.assertEquals("Expected the value foo2 for the serial filter", "foo2", arguments.getRequiredSerialFilter());
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
