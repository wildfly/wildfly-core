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

import javax.inject.Inject;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.CommandContextImpl;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
@RunWith(WildflyTestRunner.class)
public class BasicOpsTestCase {
    @Inject
    protected ManagementClient managementClient;

    @Test
    public void testConnect() throws Exception {
        CLIWrapper cli = new CLIWrapper(false);

        assertFalse(cli.isConnected());
        cli.sendLine("connect " + TestSuiteEnvironment.getServerAddress() + ":" + TestSuiteEnvironment.getServerPort());
        assertTrue(cli.isConnected());

        cli.quit();
    }

    @Test
    public void testConnectBind() throws Exception {
        CLIWrapper cli = new CLIWrapper(false);

        assertFalse(cli.isConnected());
        cli.sendLine("connect " + TestSuiteEnvironment.getServerAddress() + ":"
                + TestSuiteEnvironment.getServerPort() + " --bind=" + TestSuiteEnvironment.getServerAddress());
        assertTrue(cli.isConnected());

        cli.quit();
    }

    @Test
    public void testLs() throws Exception {
        CLIWrapper cli = new CLIWrapper(true);
        cli.sendLine("ls");
        String ls = cli.readOutput();

        assertTrue(ls.contains("subsystem"));
        assertTrue(ls.contains("interface"));
        assertTrue(ls.contains("extension"));
        assertTrue(ls.contains("subsystem"));
        assertTrue(ls.contains("core-service"));
        assertTrue(ls.contains("system-property"));
        assertTrue(ls.contains("socket-binding-group"));
        assertTrue(ls.contains("deployment"));
        assertTrue(ls.contains("path"));

        cli.quit();
    }

    @Test
    public void testReadAttribute() throws Exception {
        CLIWrapper cli = new CLIWrapper(true);
        {
            assertTrue("Failed to read attribute: " + cli.readOutput(),
                    cli.sendLine("read-attribute namespaces", true));
        }
        {
            assertFalse("Expected command to fail on invalid node",
                    cli.sendLine("read-attribute --node=subsystem=request-controller namespaces", true));
            String output = cli.readOutput();
            assertTrue(output.matches("WFLYCTL0201.*namespaces.*"));
        }

        {
            assertTrue("Failed to read attribute: " + cli.readOutput(),
                    cli.sendLine("read-attribute active-requests --node=subsystem=request-controller", true));
        }

        {
            assertTrue("Failed to read attribute: " + cli.readOutput(),
                    cli.sendLine("read-attribute --node=subsystem=request-controller active-requests", true));
        }
        cli.quit();
    }

    @Test
    public void testImplicitValuesBoot() throws Exception {
        CommandContext ctx = new CommandContextImpl(null);
        ctx.bindClient(managementClient.getControllerClient());
        doTestImplicitValues(ctx);
    }

    @Test
    public void testImplicitValues() throws Exception {
        try ( CLIWrapper cli = new CLIWrapper(true)) {
            doTestImplicitValues(cli.getCommandContext());
        }
    }

    void doTestImplicitValues(CommandContext ctx) throws Exception {
        ctx.handle(":read-resource(recursive)");

        {
            ModelNode mn
                    = ctx.
                            buildRequest(":read-resource(recursive)");
            assertTrue(mn.get("recursive").asBoolean());
        }

        {
            ModelNode mn
                    = ctx.
                            buildRequest(":reload(admin-only, server-config=toto, use-current-server-config)");
            assertTrue(mn.get("admin-only").asBoolean());
            assertTrue(mn.get("use-current-server-config").asBoolean());
            assertTrue(mn.get("server-config").asString().equals("toto"));
        }

        {
            ModelNode mn
                    = ctx.
                            buildRequest(":reload(admin-only=false, server-config=toto, use-current-server-config=false)");
            assertTrue(!mn.get("admin-only").asBoolean());
            assertTrue(!mn.get("use-current-server-config").asBoolean());
            assertTrue(mn.get("server-config").asString().equals("toto"));
        }
    }

    @Test
    public void testWithVariables() throws Exception {
        try (CLIWrapper cli = new CLIWrapper(true)) {
            cli.sendLine("set var1=core-service");
            cli.sendLine("ls /$var1=management");
            cli.sendLine("read-operation --node=/$var1=management whoami");
            cli.sendLine("read-attribute --node=/$var1=capability-registry capabilities");
            cli.sendLine("cd /$var1=management");
        }
    }
}
