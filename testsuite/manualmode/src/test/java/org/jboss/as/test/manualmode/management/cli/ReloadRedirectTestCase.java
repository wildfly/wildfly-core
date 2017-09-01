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
import static org.junit.Assert.assertTrue;

import java.net.UnknownHostException;

import javax.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.cli.CliProcessWrapper;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class ReloadRedirectTestCase {

    public static final int MANAGEMENT_NATIVE_PORT = 9999;

    @Inject
    private static ServerController container;

    private static boolean elytron;
    @BeforeClass
    public static void initServer() throws Exception {
        container.start();

        ModelControllerClient client = container.getClient().getControllerClient();

        // Set up native management so we can use it to do cleanup without dealing with https
        // add native socket binding
        ModelNode operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-native", ModelDescriptionConstants.ADD);
        operation.get("port").set(MANAGEMENT_NATIVE_PORT);
        operation.get("interface").set("management");
        CoreUtils.applyUpdate(operation, client);

        // add a temp realm socket binding
        operation = Util.createEmptyOperation("composite", PathAddress.EMPTY_ADDRESS);
        operation.get("steps").add(createOpNode("core-service=management/security-realm=native-realm", ModelDescriptionConstants.ADD));
        ModelNode localAuth = createOpNode("core-service=management/security-realm=native-realm/authentication=local", ModelDescriptionConstants.ADD);
        localAuth.get("default-user").set("$local");
        operation.get("steps").add(localAuth);
        CoreUtils.applyUpdate(operation, client);

        // create native interface
        operation = createOpNode("core-service=management/management-interface=native-interface", ModelDescriptionConstants.ADD);
        operation.get("security-realm").set("native-realm");
        operation.get("socket-binding").set("management-native");
        CoreUtils.applyUpdate(operation, client);

        // elytron or legacy
        try {
            container.getClient().executeForResult(createOpNode("core-service=management/"
                    + "security-realm=ManagementRealm/", "read-resource"));
            elytron = false;
        } catch (UnsuccessfulOperationException ignored) {
            elytron = true;
        }
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
                try {
                    removeNativeMgmt(client);
                } catch (Exception ex) {
                    if (e == null) {
                        e = ex;
                    }
                } finally {
                    try {
                        removeNativeRealm(client);
                    } catch (Exception ex) {
                        if (e == null) {
                            e = ex;
                        }
                    } finally {
                        try {
                            remoteNativeMgmtPort(client);
                        } catch (Exception ex) {
                            if (e == null) {
                                e = ex;
                            }
                        }
                    }
                }
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
        if (elytron) {
            ModelNode undefine = createOpNode("core-service=management/management-interface=http-interface",
                    "undefine-attribute");
            undefine.get("name").set("ssl-context");
            client.executeForResult(undefine);
            remove(client, "subsystem=elytron/server-ssl-context=elytronHttpsSSC");
            remove(client, "subsystem=elytron/key-manager=elytronHttpsKM");
            remove(client, "subsystem=elytron/key-store=elytronHttpsKS");
        } else {
            remove(client, "core-service=management/security-realm=ManagementRealm/server-identity=ssl");
        }
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

    private static void removeNativeRealm(ManagementClient client) throws UnsuccessfulOperationException {
        ModelNode remove = createOpNode("core-service=management/security-realm=native-realm",
                "remove");
        client.executeForResult(remove);
    }

    private static void remoteNativeMgmtPort(ManagementClient client) throws UnsuccessfulOperationException {
        ModelNode remove = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-native",
                "remove");
        client.executeForResult(remove);
    }

    private static ManagementClient getCleanupClient() throws UnknownHostException {
        // Use a client that connects to 9999
        ModelControllerClient mcc = ModelControllerClient.Factory.create("remote", TestSuiteEnvironment.getServerAddress(), MANAGEMENT_NATIVE_PORT);
        return new ManagementClient(mcc, TestSuiteEnvironment.getServerAddress(), MANAGEMENT_NATIVE_PORT, "remote");
    }

    /**
     * We should have the same test with "shutdown --restart" but testing
     * framework doesn't allow to restart the server (not launched from server
     * script file). "shutdown --restart" must be tested manually.
     *
     * @throws Exception
     */
    @Test
    public void testReloadwithRedirect() throws Exception {
        CliProcessWrapper cliProc = new CliProcessWrapper()
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
            boolean closed = cliProc.ctrlDAndWaitForClose();
            assertTrue("Process did not terminate correctly. Output: '" + cliProc.getOutput() + "'", closed);
        }
    }

    private void setupSSL(CliProcessWrapper cliProc) throws Exception {
        if (elytron) {
            setupElytronSSL(cliProc);
        } else {
            setupLegacySSL(cliProc);
        }
        boolean promptFound = cliProc.pushLineAndWaitForResults("/core-service=management/"
                + "management-interface=http-interface:"
                + "write-attribute(name=secure-socket-binding,value=management-https)", null);
        assertTrue("Invalid prompt" + cliProc.getOutput(), promptFound);
        cliProc.clearOutput();
    }

    private void setupElytronSSL(CliProcessWrapper cliProc) throws Exception {
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
    }

    private void setupLegacySSL(CliProcessWrapper cliProc) throws Exception {
        boolean promptFound = cliProc.
                pushLineAndWaitForResults("/core-service=management/"
                        + "security-realm=ManagementRealm/"
                        + "server-identity=ssl:add(keystore-path=management.keystore,"
                        + "keystore-relative-to=jboss.server.config.dir,"
                        + "keystore-password=password,alias=server,key-password=password,"
                        + "generate-self-signed-certificate-host=localhost)", null);
        assertTrue("Invalid prompt" + cliProc.getOutput(), promptFound);
        cliProc.clearOutput();
    }
}
