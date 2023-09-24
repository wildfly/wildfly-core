/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.logging.perdeploy;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author Petr Křemenský <pkremens@redhat.com>
 */
@RunWith(WildFlyRunner.class)
public class LoggingPropertiesTestCase extends DeploymentBaseTestCase {

    private static final Path logFile = getAbsoluteLogFilePath("logging-properties-test.log");

    @BeforeClass
    public static void deploy() throws Exception {
        deploy(createDeployment("logging.properties"), DEPLOYMENT_NAME);
    }

    @AfterClass
    public static void undeploy() throws Exception {
        undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void logsTest() throws IOException {
        final String msg = "logTest: logging.properties message";
        final int statusCode = getResponse(msg, Collections.singletonMap("includeLevel", "true"));
        assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);
        boolean trace = false;
        boolean fatal = false;
        String traceLine = msg + " - trace";
        String fatalLine = msg + " - fatal";
        try (final BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(traceLine)) {
                    trace = true;
                }
                if (line.contains(fatalLine)) {
                    fatal = true;
                }
            }
        }
        Assert.assertTrue("Log file should contain line: " + traceLine, trace);
        Assert.assertTrue("Log file should contain line: " + fatalLine, fatal);
    }

    @Test
    public void testDeploymentConfigurationResource() throws Exception {
        final ModelNode loggingConfiguration = readDeploymentResource(DEPLOYMENT_NAME);
        // The address should have jboss-log4j.xml
        final LinkedList<Property> resultAddress = new LinkedList<>(Operations.getOperationAddress(loggingConfiguration).asPropertyList());
        Assert.assertTrue("The configuration path did not include logging.properties", resultAddress.getLast().getValue().asString().contains("logging.properties"));

        final ModelNode handler = loggingConfiguration.get("handler", "FILE");
        Assert.assertTrue("The FILE handler was not found effective configuration", handler.isDefined());
        Assert.assertTrue(handler.hasDefined("properties"));
        String fileName = null;
        // Find the fileName property
        for (Property property : handler.get("properties").asPropertyList()) {
            if ("fileName".equals(property.getName())) {
                fileName = property.getValue().asString();
                break;
            }
        }
        Assert.assertNotNull("fileName property not found", fileName);
        Assert.assertTrue(fileName.endsWith("logging-properties-test.log"));
    }

}
