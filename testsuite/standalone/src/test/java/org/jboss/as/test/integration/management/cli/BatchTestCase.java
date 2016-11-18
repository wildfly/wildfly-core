/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;


/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
@RunWith(WildflyTestRunner.class)
public class BatchTestCase extends AbstractCliTestBase {

    @BeforeClass
    public static void before() throws Exception {
        AbstractCliTestBase.initCLI();
    }

    @AfterClass
    public static void after() throws Exception {
        AbstractCliTestBase.closeCLI();
    }

    @Test
    public void testRunBatch() throws Exception {

        addProperty("prop1", "prop1_a");
        addProperty("prop2", "prop2_a");

        cli.sendLine("batch");
        writeProperty("prop1", "prop1_b");
        writeProperty("prop2", "prop2_b");

        assertEquals("prop1_a", readProperty("prop1"));
        assertEquals("prop2_a", readProperty("prop2"));

        cli.sendLine("run-batch");

        String line = cli.readOutput();
        assertTrue(line.contains("The batch executed successfully"));

        assertEquals("prop1_b", readProperty("prop1"));
        assertEquals("prop2_b", readProperty("prop2"));

        cli.sendLine("batch");
        removeProperty("prop1");
        removeProperty("prop2");
        cli.sendLine("holdback-batch dbatch");
        cli.sendLine("batch dbatch");

        assertTrue(cli.isValidPath("system-property", "prop1"));
        assertTrue(cli.isValidPath("system-property", "prop2"));

        cli.sendLine("run-batch");
        assertFalse(cli.isValidPath("system-property", "prop1"));
        assertFalse(cli.isValidPath("system-property", "prop2"));
    }

    @Test
    public void testRollbackBatch() throws Exception {

        addProperty("prop1", "prop1_a");

        cli.sendLine("batch");
        addProperty("prop2", "prop2_a");
        addProperty("prop1", "prop1_b");

        assertEquals("prop1_a", readProperty("prop1"));
        assertFalse(cli.isValidPath("system-property", "prop2"));

        // this should fail
        cli.sendLine("run-batch", true);

        String line = cli.readOutput();
        String expectedErrorCode = ControllerLogger.ROOT_LOGGER.compositeOperationFailed();
        expectedErrorCode = expectedErrorCode.substring(0, expectedErrorCode.indexOf(':'));
        assertTrue("Batch did not fail.", line.contains(expectedErrorCode));
        assertTrue("Operation is not contained in error message.",
                line.contains("/system-property=prop1:add(value=prop1_b)"));
        assertEquals("prop1_a", readProperty("prop1"));
        assertFalse(cli.isValidPath("system-property", "prop2"));

        cli.sendLine("discard-batch");

        removeProperty("prop1");
        assertFalse(cli.isValidPath("system-property", "prop1"));
    }

    protected void addProperty(String name, String value) {
        cli.sendLine("/system-property=" + name + ":add(value=" + value + ")");
    }

    protected void writeProperty(String name, String value) {
        cli.sendLine("/system-property=" + name + ":write-attribute(name=value,value=" + value + ")");
    }

    protected String readProperty(String name) {
        cli.sendLine("read-attribute --node=/system-property=" + name + " value");
        return cli.readOutput();
    }

    protected void removeProperty(String name) {
        cli.sendLine("/system-property=" + name + ":remove");
    }
}
