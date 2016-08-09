/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildflyTestRunner.class)
public class LsTestCase extends AbstractCliTestBase {

    private CommandContext ctx;

    @Before
    public void before() throws Exception {
        ctx = CLITestUtil.getCommandContext();
        ctx.connectController();
        AbstractCliTestBase.initCLI();
    }

    @After
    public void after() throws Exception {
        ctx.terminateSession();
    }

    @Test
    public void testBasicPathArgumentRecognitionAndParsing() throws Exception {
        ModelNode request = ctx.buildRequest("ls / -l --resolve-expressions");
        ModelNode address = request.get("address");
        assertEquals(ModelType.LIST, address.getType());
        assertTrue(address.asList().isEmpty());
        assertTrue(request.hasDefined("steps"));
        final List<ModelNode> steps = request.get("steps").asList();
        assertEquals(3, steps.size());
        for(ModelNode step : steps) {
            address = step.get("address");
            assertTrue(address.asList().isEmpty());
        }

        request = ctx.buildRequest("ls /system-property");
        address = request.get("address");
        assertEquals(ModelType.LIST, address.getType());
        assertTrue(address.asList().isEmpty());
        assertFalse(request.hasDefined("steps"));
    }

    @Test
    public void testSimple() throws Exception {
        cli.sendLine("ls");
        String out = cli.readOutput();
        assertFalse(out.contains("Unrecognized arguments:"));
        cli.sendLine("ls --headers={allow-resource-service-restart=true;}");
        out = cli.readOutput();
        assertFalse(out.contains("Unrecognized arguments:"));
        cli.sendLine("ls -l");
        out = cli.readOutput();
        assertFalse(out.contains("Unrecognized arguments:"));
        cli.sendLine("ls --resolve-expressions");
        out = cli.readOutput();
        assertFalse(out.contains("Unrecognized arguments:"));
    }

    @Test
    public void testDescriptionAttributes() throws Exception {
        {
            cli.sendLine("ls -l --storage --nillable");
            String out = cli.readOutput();
            assertTrue(out.contains("STORAGE"));
            assertFalse(out.contains("Unrecognized arguments:"));
            assertTrue(out.contains("NILLABLE"));
        }

        {
            cli.sendLine("ls --storage");
            String out = cli.readOutput();
            assertFalse(out.contains("Unrecognized arguments:"));
            assertFalse(out.contains("STORAGE"));
        }
    }
}
