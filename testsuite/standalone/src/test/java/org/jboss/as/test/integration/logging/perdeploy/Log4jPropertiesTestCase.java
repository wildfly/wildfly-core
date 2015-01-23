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

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.apache.http.HttpStatus;
import org.jboss.as.test.integration.logging.Log4jServiceActivator;
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
public class Log4jPropertiesTestCase extends DeploymentBaseTestCase {

    private static final Path logFile = getAbsoluteLogFilePath("log4j-properties-test.log");

    @BeforeClass
    public static void deploy() throws Exception {
        deploy(createDeployment(Log4jServiceActivator.class, "log4j.properties", Log4jServiceActivator.DEPENDENCIES), DEPLOYMENT_NAME);
    }

    @AfterClass
    public static void undeploy() throws Exception {
        undeploy(DEPLOYMENT_NAME);
    }

    @Test
    public void logsTest() throws IOException {
        final String msg = "logTest: log4j.properties message";
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
}
