/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.manualmode.management.cli;

import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.UnknownHostException;

import javax.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.cli.CliProcessWrapper;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class ReloadRedirectTestCase {

    private static final String IBM_OVERRIDE_DEFAULT_TLS = "-Dcom.ibm.jsse2.overrideDefaultTLS=true";
    public static final int MANAGEMENT_NATIVE_PORT = 9999;

    @Inject
    private static ServerController container;

    public static void setupNativeInterface(ServerController controller) throws Exception {
        ModelControllerClient client = controller.getClient().getControllerClient();

        // Set up native management so we can use it to do cleanup without dealing with https

        // Add native socket binding
        ModelNode operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-native", ModelDescriptionConstants.ADD);
        operation.get("port").set(MANAGEMENT_NATIVE_PORT);
        operation.get("interface").set("management");
        CoreUtils.applyUpdate(operation, client);

        // Find the sasl-authentication-factory that's already known to be working so it can be reused
        ModelNode op = createOpNode("core-service=management/"
                + "management-interface=http-interface/", "read-attribute");
        op.get("name").set("http-upgrade");
        ModelNode result = controller.getClient().executeForResult(op);
        String saslFactory = result.get("sasl-authentication-factory").asStringOrNull();
        assertNotNull("Invalid http-upgrade setting: " + result, saslFactory);

        // create native interface
        operation = createOpNode("core-service=management/management-interface=native-interface", ModelDescriptionConstants.ADD);
        operation.get("sasl-authentication-factory").set(saslFactory);
        operation.get("socket-binding").set("management-native");
        CoreUtils.applyUpdate(operation, client);
    }

    public static void removeNativeInterface(ManagementClient client) throws Exception {
        Exception e = null;
        try {
            removeNativeMgmt(client);
        } catch (Exception ex) {
            e = ex;
        } finally {
            try {
                remoteNativeMgmtPort(client);
            } catch (Exception ex) {
                if (e == null) {
                    e = ex;
                }
            }
        }
        if (e != null) {
            throw e;
        }
    }


    @BeforeClass
    public static void initServer() throws Exception {
        container.start();

        setupNativeInterface(container);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        try {
            // Even though we don't reuse this server, the next test uses the config so we
            // need to revert the config changes the test made
            ManagementClient client = getCleanupClient();
            cleanConfig(client);
        } finally {
            container.stop();
        }
    }

    private static void cleanConfig(ManagementClient client) throws Exception {
        Exception e = null;
        try {
            removeHttpsMgmt(client);
        } catch (Exception ex) {
            e = ex;
        } finally {
            try {
                removeSsl(client);
            } catch (Exception ex) {
                if (e == null) {
                    e = ex;
                }
            } finally {
                removeNativeInterface(client);
            }
        }

        if (e != null) {
            throw e;
        }
    }

    private static void removeHttpsMgmt(ManagementClient client) throws UnsuccessfulOperationException {
        ModelNode undefine = createOpNode("core-service=management/management-interface=http-interface",
                "undefine-attribute");
        undefine.get("name").set("secure-socket-binding");
        client.executeForResult(undefine);
    }

    private static void removeSsl(ManagementClient client) throws UnsuccessfulOperationException {
        ModelNode undefine = createOpNode("core-service=management/management-interface=http-interface",
                "undefine-attribute");
        undefine.get("name").set("ssl-context");
        client.executeForResult(undefine);
        remove(client, "subsystem=elytron/server-ssl-context=elytronHttpsSSC");
        remove(client, "subsystem=elytron/key-manager=elytronHttpsKM");
        remove(client, "subsystem=elytron/key-store=elytronHttpsKS");
    }

    private static void remove(ManagementClient client, String addr) {
        try {
            ModelNode remove = createOpNode(addr, "remove");
            client.executeForResult(remove);
        } catch (UnsuccessfulOperationException uoe) {
            // It's ok if the resource doesn't exist due to failure in the test to create it
            try {
                client.executeForResult(createOpNode(addr, "read-resource"));
                // success means it wasn't a missing resource
                throw uoe;
            } catch (UnsuccessfulOperationException ignored) {
                // assume it's due to no such resource
            }
        }
    }

    private static void removeNativeMgmt(ManagementClient client) throws UnsuccessfulOperationException {
        ModelNode remove = createOpNode("core-service=management/management-interface=native-interface",
                "remove");
        client.executeForResult(remove);
    }

    private static void remoteNativeMgmtPort(ManagementClient client) throws UnsuccessfulOperationException {
        ModelNode remove = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-native",
                "remove");
        client.executeForResult(remove);
    }

    public static ManagementClient getCleanupClient() throws UnknownHostException {
        // Use a client that connects to 9999
        ModelControllerClient mcc = ModelControllerClient.Factory.create("remote", TestSuiteEnvironment.getServerAddress(), MANAGEMENT_NATIVE_PORT);
        return new ManagementClient(mcc, TestSuiteEnvironment.getServerAddress(), MANAGEMENT_NATIVE_PORT, "remote");
    }

    /**
     * We should have the same test with "shutdown --restart" but testing
     * framework doesn't allow to restart the server (not launched from server
     * script file). "shutdown --restart" must be tested manually.
     *
     * @throws Exception if one happens
     */
    @Test
    public void testReloadwithRedirect() throws Exception {
        CliProcessWrapper cliProc = new CliProcessWrapper()
                .addJavaOption(IBM_OVERRIDE_DEFAULT_TLS)
                .addCliArgument("--connect")
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort());
        try {
            cliProc.executeInteractive();
            cliProc.clearOutput();
            setupSSL(cliProc);
            boolean promptFound = cliProc.pushLineAndWaitForResults("reload", "Accept certificate");
            assertTrue("No certificate prompt " + cliProc.getOutput(), promptFound);
        } finally {
            cliProc.ctrlCAndWaitForClose();
        }
    }

    @Test
    public void testRedirectWithSecurityCommands() throws Throwable {

        CliProcessWrapper cliProc = new CliProcessWrapper()
                .addJavaOption(IBM_OVERRIDE_DEFAULT_TLS)
                .addCliArgument("--connect")
                .addCliArgument("--no-color-output")
                .addCliArgument("--controller="
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort());
        Throwable exception = null;
        try {
            cliProc.executeInteractive();
            cliProc.clearOutput();
            Assert.assertTrue("No certificate prompt " + cliProc.getOutput(), cliProc.pushLineAndWaitForResults("security enable-ssl-management"
                    + " --key-store-path=target/server.keystore.jks"
                    + " --key-store-password=secret"
                    + " --new-key-store-name=nks"
                    + " --new-key-manager-name=nkm"
                    + " --new-ssl-context-name=nsslctx", "Accept certificate"));
            cliProc.clearOutput();
            Assert.assertTrue(cliProc.getOutput(),
                    cliProc.pushLineAndWaitForResults("T", "[standalone@"));
            cliProc.clearOutput();
            Assert.assertTrue(cliProc.getOutput(), cliProc.pushLineAndWaitForResults("security disable-ssl-management",
                    "[standalone@"));
        } catch (Throwable ex) {
            exception = ex;
        } finally {
            try {
                cliProc.clearOutput();
                Assert.assertTrue(cliProc.getOutput(), cliProc.pushLineAndWaitForResults("/subsystem=elytron/server-ssl-context=nsslctx:remove", null));
                Assert.assertFalse(cliProc.getOutput(), cliProc.getOutput().contains("failed"));
            } catch (Throwable ex) {
                if (exception == null) {
                    exception = ex;
                }
            } finally {
                try {
                    cliProc.clearOutput();
                    Assert.assertTrue(cliProc.getOutput(), cliProc.pushLineAndWaitForResults("/subsystem=elytron/key-manager=nkm:remove", null));
                    Assert.assertFalse(cliProc.getOutput(), cliProc.getOutput().contains("failed"));
                } catch (Throwable ex) {
                    if (exception == null) {
                        exception = ex;
                    }
                } finally {
                    try {
                        cliProc.clearOutput();
                        Assert.assertTrue(cliProc.getOutput(), cliProc.pushLineAndWaitForResults("/subsystem=elytron/key-store=nks:remove", null));
                        Assert.assertFalse(cliProc.getOutput(), cliProc.getOutput().contains("failed"));
                    } catch (Throwable ex) {
                        if (exception == null) {
                            exception = ex;
                        }
                    } finally {
                        try {
                            cliProc.ctrlCAndWaitForClose();
                        } catch (Throwable ex) {
                            if (exception == null) {
                                exception = ex;
                            }
                        }
                    }
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    private void setupSSL(CliProcessWrapper cliProc) throws Exception {

        boolean promptFound = cliProc.
                pushLineAndWaitForResults("/subsystem=elytron/key-store=elytronHttpsKS:"
                        + "add(path=target/server.keystore.jks,"
                        + "credential-reference={clear-text=secret}, "
                        + "type=JKS)", null);
        assertTrue("Invalid prompt" + cliProc.getOutput(), promptFound);
        cliProc.clearOutput();
        promptFound = cliProc.pushLineAndWaitForResults("/subsystem=elytron/"
                + "key-manager=elytronHttpsKM:add(key-store=elytronHttpsKS,"
                + "credential-reference={clear-text=secret})", null);
        assertTrue("Invalid prompt" + cliProc.getOutput(), promptFound);
        cliProc.clearOutput();
        promptFound = cliProc.pushLineAndWaitForResults("/subsystem=elytron/"
                + "server-ssl-context=elytronHttpsSSC:add(key-manager=elytronHttpsKM,"
                + "protocols=[TLSv1.2])", null);
        assertTrue("Invalid prompt" + cliProc.getOutput(), promptFound);
        cliProc.clearOutput();
        promptFound = cliProc.pushLineAndWaitForResults("/core-service=management/"
                + "management-interface=http-interface:write-attribute(name=ssl-context,"
                + "value=elytronHttpsSSC)", null);
        assertTrue("Invalid prompt" + cliProc.getOutput(), promptFound);
        cliProc.clearOutput();

        promptFound = cliProc.pushLineAndWaitForResults("/core-service=management/"
                + "management-interface=http-interface:"
                + "write-attribute(name=secure-socket-binding,value=management-https)", null);
        assertTrue("Invalid prompt" + cliProc.getOutput(), promptFound);
        cliProc.clearOutput();
    }
}
