/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.integration.management.cli;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.global.MapOperations;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * CLI should show the real value of expression properties in addition to the expression
 *
 * https://issues.jboss.org/browse/WFCORE-729
 *
 * @author Marek Kopecky <mkopecky@redhat.com>
 */
@RunWith(WildflyTestRunner.class)
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

        // get java.version system property on server
        PathAddress address = PathAddress.pathAddress(
                PathElement.pathElement(ModelDescriptionConstants.CORE_SERVICE, ModelDescriptionConstants.PLATFORM_MBEAN),
                PathElement.pathElement(ModelDescriptionConstants.TYPE, "runtime")
        );
        String result = cliRequest(String.format("%s:%s(name=%s, key=%s", address.toCLIStyleString(), MapOperations.MAP_GET_DEFINITION.getName(), ModelDescriptionConstants.SYSTEM_PROPERTIES, oldSystemProperty));
        String testJavaVersion = ModelNode.fromString(result).get(ModelDescriptionConstants.RESULT).asString();
        // cli
        try {
            cliRequest(prepareCliAddSystemPropertyCommand(oldSystemProperty));

            // read-resource test
            String command = "/system-property=" + newSystemPropertyName + ":read-resource(resolve-expressions=true)";
            String output = cliRequest(command);
            assertTrue(output, output.contains(testJavaVersion));
            assertTrue(output, output.contains("value"));
            assertFalse(output, output.contains(oldSystemProperty));
            assertFalse(output, output.contains(defaultValue));

            // read-attribute test
            command = "/system-property=" + newSystemPropertyName + ":read-attribute(name=value, resolve-expressions=true)";
            output = cliRequest(command);
            assertTrue(output, output.contains(testJavaVersion));
            assertFalse(output, output.contains(oldSystemProperty));
            assertFalse(output, output.contains(defaultValue));

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
            assertTrue(output, output.contains(defaultValue));
            assertTrue(output, output.contains("value"));
            assertFalse(output, output.contains(oldSystemProperty));

            // read-attribute test
            command = "/system-property=" + newSystemPropertyName + ":read-attribute(name=value, resolve-expressions=true)";
            output = cliRequest(command);
            assertTrue(output, output.contains(defaultValue));
            assertFalse(output, output.contains(oldSystemProperty));

        } finally {
            cliRequest("/system-property=" + newSystemPropertyName + ":remove");
        }
    }

}
