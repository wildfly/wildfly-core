/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.jboss.as.test.integration.logging.perdeploy;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.AssumeTestGroupUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * @author Petr Křemenský <pkremens@redhat.com>
 */
@RunWith(WildflyTestRunner.class)
public class JBossLog4jXmlTestCase extends DeploymentBaseTestCase {

    private static final Path logFile = getAbsoluteLogFilePath("jboss-log4j-xml-test.log");

    @BeforeClass
    public static void deploy() throws Exception {
        AssumeTestGroupUtil.assumeSecurityManagerDisabled();
        deploy(createDeployment("jboss-log4j.xml"), DEPLOYMENT_NAME);
    }

    @AfterClass
    public static void undeploy() throws Exception {
        undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void logsTest() throws IOException {
        final String msg = "logTest: jboss-log4j message";
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
        final String name = resultAddress.getLast().getValue().asString();
        Assert.assertTrue("The configuration path did not include jboss-log4j.xml: " + name, name.endsWith("jboss-log4j.xml"));
        Assert.assertTrue(loggingConfiguration.has("handler"));
        // A log4j configuration cannot be defined in the model
        Assert.assertFalse("No handlers should be defined", loggingConfiguration.get("handler").isDefined());
    }
}
