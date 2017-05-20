/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.core.test.standalone.mgmt;

import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.test.integration.management.util.CustomCLIExecutor.MANAGEMENT_HTTP_PORT;
import static org.jboss.as.test.integration.management.util.CustomCLIExecutor.MANAGEMENT_NATIVE_PORT;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.inject.Inject;
import org.hamcrest.CoreMatchers;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.categories.CommonCriteria;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Testing the removal of management interfaces.
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
@Category(CommonCriteria.class)
public class RemoveManagementInterfaceTestCase {
    public static Logger LOGGER = Logger.getLogger(RemoveManagementInterfaceTestCase.class);

    @Inject
    protected static ServerController controller;

    private static String securityRealm = "ManagementRealm";
    private static String saslAuthFactory = null;

    @BeforeClass
    public static void startAndSetupContainer() throws Exception {
        controller.startInAdminMode();
        ManagementClient managementClient = controller.getClient();
        serverSetup(managementClient.getControllerClient());
        // To have the native management interface ok, we need a reload of the server
        controller.reload();
    }

    @Test
    public void testRemoveManagementInterface() throws Exception {
        ModelControllerClient client = getHttpModelControllerClient();
        ModelNode operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-http", ModelDescriptionConstants.READ_RESOURCE_OPERATION);
        ModelNode response = client.execute(operation);
        assertThat(response.hasDefined(OUTCOME), is(true));
        assertThat(response.get(OUTCOME).asString(), is(SUCCESS));
        operation = createOpNode("core-service=management/management-interface=http-interface", ModelDescriptionConstants.REMOVE);
        CoreUtils.applyUpdate(operation, client);
        client.close();
        ServerReload.executeReloadAndWaitForCompletion(getNativeModelControllerClient(), TimeoutUtil.adjust(30000), false, "remote", TestSuiteEnvironment.getServerAddress(), MANAGEMENT_NATIVE_PORT);
        client = getNativeModelControllerClient();
        operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-http", ModelDescriptionConstants.READ_RESOURCE_OPERATION);
        response = client.execute(operation);
        assertThat(response.hasDefined(OUTCOME), is(true));
        assertThat(response.get(OUTCOME).asString(), is(SUCCESS));
        operation = createOpNode("core-service=management/management-interface=native-interface", ModelDescriptionConstants.REMOVE);
        response = client.execute(operation);
        assertThat(DomainTestSupport.validateFailedResponse(response).asString(), CoreMatchers.containsString("WFLYRMT0025"));
        client.close();
        client = getHttpModelControllerClient();
        operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-http", ModelDescriptionConstants.READ_RESOURCE_OPERATION);
        try {
            client.execute(operation);
            Assert.fail("Shouldn't be able to connect to http management");
        } catch (IOException ioex) {
            assertThat(ioex.getMessage(), CoreMatchers.containsString("WFLYPRT0053"));
        } finally {
            client.close();
        }
    }

    @AfterClass
    public static void stopContainer() throws Exception {
        serverTearDown();
        controller.stop();
    }

    private static void serverSetup(ModelControllerClient client) throws Exception {
        // add native socket binding
        ModelNode operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-native", ModelDescriptionConstants.ADD);
        operation.get("port").set(MANAGEMENT_NATIVE_PORT);
        operation.get("interface").set("management");
        CoreUtils.applyUpdate(operation, client);

        // Determine the we should be using a security-realm or SASL
        ModelNode op = Operations.createReadResourceOperation(Operations.createAddress("core-service", "management", "management-interface", "http-interface"));
        ModelNode result = ManagementOperations.executeOperation(client, op);
        if (result.hasDefined("security-realm")) {
            securityRealm = result.get("security-realm").asString();
        } else if (result.hasDefined("http-upgrade")) {
            final ModelNode httpUpgrade = result.get("http-upgrade");
            if (httpUpgrade.hasDefined("sasl-authentication-factory")) {
                saslAuthFactory = httpUpgrade.get("sasl-authentication-factory").asString();
            }
        }

        // create native interface to control server while http interface will be removed
        operation = createOpNode("core-service=management/management-interface=native-interface", ModelDescriptionConstants.ADD);

        if (saslAuthFactory != null) {
            operation.get("sasl-authentication-factory").set(saslAuthFactory);
        } else {
            operation.get("security-realm").set(securityRealm);
        }
        operation.get("socket-binding").set("management-native");
        CoreUtils.applyUpdate(operation, client);
    }

    private static void serverTearDown() throws Exception {
        ModelControllerClient client = getNativeModelControllerClient();
        ModelNode operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-http", ModelDescriptionConstants.READ_RESOURCE_OPERATION);
        ModelNode response = client.execute(operation);
        if (response.hasDefined(OUTCOME) && FAILED.equals(response.get(OUTCOME).asString())) {
            // add http-management socket binding
            operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-http", ModelDescriptionConstants.ADD);
            operation.get("port").set(MANAGEMENT_HTTP_PORT);
            operation.get("interface").set("management");
            CoreUtils.applyUpdate(operation, client);
        }
        operation = createOpNode("core-service=management/management-interface=http-interface", ModelDescriptionConstants.READ_RESOURCE_OPERATION);
        response = client.execute(operation);
        if (response.hasDefined(OUTCOME) && FAILED.equals(response.get(OUTCOME).asString())) {
            // create http interface to control server
            operation = createOpNode("core-service=management/management-interface=http-interface", ModelDescriptionConstants.ADD);
            final ModelNode httpUpgrade = new ModelNode().setEmptyObject();
            httpUpgrade.get("enabled").set(true);
            if (saslAuthFactory != null) {
                httpUpgrade.get("sasl-authentication-factory").set(saslAuthFactory);
            } else {
                operation.get("security-realm").set(securityRealm);
            }
            operation.get("socket-binding").set("management-http");
            operation.get("http-upgrade").set(httpUpgrade);
            CoreUtils.applyUpdate(operation, client);
        }
        // To recreate http interface, a reload of server is required
        ServerReload.executeReloadAndWaitForCompletion(getNativeModelControllerClient(), TimeoutUtil.adjust(30000), false, "remote", TestSuiteEnvironment.getServerAddress(), MANAGEMENT_NATIVE_PORT);
        client = getHttpModelControllerClient();
        //Remove native interface
        try {
            operation = createOpNode("core-service=management/management-interface=native-interface", ModelDescriptionConstants.REMOVE);
            CoreUtils.applyUpdate(operation, client);
            operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-native", ModelDescriptionConstants.REMOVE);
            CoreUtils.applyUpdate(operation, client);
        } finally {
            safeCloseClient(client);
        }
    }

    static ModelControllerClient getNativeModelControllerClient() {
        ModelControllerClient client = null;
        try {
            client = ModelControllerClient.Factory.create("remote", InetAddress.getByName(TestSuiteEnvironment.getServerAddress()),
                    MANAGEMENT_NATIVE_PORT, new org.wildfly.core.testrunner.Authentication.CallbackHandler());
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        return client;
    }

    static ModelControllerClient getHttpModelControllerClient() {
        ModelControllerClient client = null;
        try {
            client = ModelControllerClient.Factory.create("remote+http", InetAddress.getByName(TestSuiteEnvironment.getServerAddress()),
                    MANAGEMENT_HTTP_PORT, new org.wildfly.core.testrunner.Authentication.CallbackHandler());
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        return client;
    }

    private static void safeCloseClient(ModelControllerClient client) {
        try {
            if (client != null) {
                client.close();
            }
        } catch (final Exception e) {
            LOGGER.warnf(e, "Caught exception closing ModelControllerClient");
        }
    }
}
