/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * CLI should show the real value of expression properties in addition to the expression
 *
 * https://issues.jboss.org/browse/WFCORE-729
 *
 * @author Marek Kopecky <mkopecky@redhat.com>
 */
@RunWith(WildFlyRunner.class)
public class RealValueExpressionTestCase extends AbstractCliTestBase {

    private static Logger log = Logger.getLogger(RealValueExpressionTestCase.class);

    private static String newSystemPropertyName = "real.value.expression.test.case";

    private static String defaultValue = "01234default56789";

    @BeforeClass
    public static void before() throws Exception {
        AbstractCliTestBase.initCLI();
    }

    @AfterClass
    public static void after() throws Exception {
        AbstractCliTestBase.closeCLI();
    }

    /**
     * Sends command line to CLI, validate and return output.
     *
     * @param line command line
     * @return CLI output
     */
    private String cliRequest(String line) {
        log.info(line);
        cli.sendLine(line);
        String output = cli.readOutput();
        assertTrue("CLI command \"" + line + " doesn't contain \"success\"", output.contains("success"));
        return output;
    }

    /**
     * Prepare CLI command: /system-property=newSystemPropertyName:add(value=${oldSystemProperty:defaultValue})
     *
     * @param oldSystemProperty old system property
     * @return new CLI command
     */
    private String prepareCliAddSystemPropertyCommand(String oldSystemProperty) {
        StringBuilder cliCommand = new StringBuilder();
        cliCommand.append("/system-property=").append(newSystemPropertyName);
        cliCommand.append(":add(value=\\${").append(oldSystemProperty).append(":").append(defaultValue).append("})");
        return cliCommand.toString();
    }

    /**
     * Read value of "test.bind.address"
     */
    @Test
    public void testRealValue() {
        String oldSystemProperty = "java.version";

        // get test.bind.address system property
        String testBindAddress = System.getProperty(oldSystemProperty);
        testBindAddress = testBindAddress == null ? "" : testBindAddress;
        log.info("testBindAddress = " + testBindAddress);

        // cli
        try {
            cliRequest(prepareCliAddSystemPropertyCommand(oldSystemProperty));

            // read-resource test
            String command = "/system-property=" + newSystemPropertyName + ":read-resource(resolve-expressions=true)";
            String output = cliRequest(command);
            String errorMessage = "CLI command \"" + command + "\" returns unexpected output";
            assertTrue(errorMessage, output.contains(testBindAddress));
            assertTrue(errorMessage, output.contains("value"));
            assertFalse(errorMessage, output.contains(oldSystemProperty));
            assertFalse(errorMessage, output.contains(defaultValue));

            // read-attribute test
            command = "/system-property=" + newSystemPropertyName + ":read-attribute(name=value, resolve-expressions=true)";
            output = cliRequest(command);
            errorMessage = "CLI command \"" + command + "\" returns unexpected output";
            assertTrue(errorMessage, output.contains(testBindAddress));
            assertFalse(errorMessage, output.contains(oldSystemProperty));
            assertFalse(errorMessage, output.contains(defaultValue));

        } finally {
            cliRequest("/system-property=" + newSystemPropertyName + ":remove");
        }
    }

    /**
     * Read default value
     */
    @Test
    public void testDefaultValue() {
        String oldSystemProperty = "nonexistent.attribute";

        // cli
        try {
            cliRequest(prepareCliAddSystemPropertyCommand(oldSystemProperty));

            // read-resource test
            String command = "/system-property=" + newSystemPropertyName + ":read-resource(resolve-expressions=true)";
            String output = cliRequest(command);
            String errorMessage = "CLI command \"" + command + "\" returns unexpected output";
            assertTrue(errorMessage, output.contains(defaultValue));
            assertTrue(errorMessage, output.contains("value"));
            assertFalse(errorMessage, output.contains(oldSystemProperty));

            // read-attribute test
            command = "/system-property=" + newSystemPropertyName + ":read-attribute(name=value, resolve-expressions=true)";
            output = cliRequest(command);
            errorMessage = "CLI command \"" + command + "\" returns unexpected output";
            assertTrue(errorMessage, output.contains(defaultValue));
            assertFalse(errorMessage, output.contains(oldSystemProperty));

        } finally {
            cliRequest("/system-property=" + newSystemPropertyName + ":remove");
        }
    }

}
