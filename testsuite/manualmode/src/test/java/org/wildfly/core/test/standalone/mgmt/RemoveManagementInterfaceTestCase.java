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

import org.jboss.as.cli.CommandContext;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.categories.CommonCriteria;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
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
 * Testing https connection to HTTP Management interface with configured two-way SSL. HTTP client has set client
 * keystore with valid/invalid certificate, which is used for authentication to management interface. Result of
 * authentication depends on whether client certificate is accepted in server truststore. HTTP client uses client
 * truststore with accepted server certificate to authenticate server identity.
 * <p/>
 * Keystores and truststores have valid certificates until 25 Octover 2033.
 *
 * @author Filip Bogyai
 * @author Josef Cacek
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
@Category(CommonCriteria.class)
public class RemoveManagementInterfaceTestCase {

    public static Logger LOGGER = Logger.getLogger(RemoveManagementInterfaceTestCase.class);
    @Inject
    protected static ServerController controller;

    @BeforeClass
    public static void startAndSetupContainer() throws Exception {
        controller.start();
        ManagementClient managementClient = controller.getClient();
        serverSetup(managementClient.getControllerClient());
        // To have the native management interface ok, we need a reload of the server
        reloadServer();
    }

    public static void reloadServer() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext("remoting", TestSuiteEnvironment.getServerAddress(), MANAGEMENT_NATIVE_PORT);
        try {
            ctx.connectController();
            ctx.handle("reload");
        } finally {
            ctx.terminateSession();
        }
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
        reloadServer();
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
        ModelControllerClient client = getNativeModelControllerClient();
        serverTearDown(client);
        controller.stop();
    }

    private static void serverSetup(ModelControllerClient client) throws Exception {
        // add native socket binding
        ModelNode operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-native", ModelDescriptionConstants.ADD);
        operation.get("port").set(MANAGEMENT_NATIVE_PORT);
        operation.get("interface").set("management");
        CoreUtils.applyUpdate(operation, client);

        // create native interface to control server while http interface will be removed
        operation = createOpNode("core-service=management/management-interface=native-interface", ModelDescriptionConstants.ADD);
        operation.get("security-realm").set("ManagementRealm");
        operation.get("socket-binding").set("management-native");
        CoreUtils.applyUpdate(operation, client);
    }

    private static void serverTearDown(final ModelControllerClient client) throws Exception {
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
            operation.get("security-realm").set("ManagementRealm");
            operation.get("socket-binding").set("management-http");
            operation.get("http-upgrade-enabled").set(true);
            CoreUtils.applyUpdate(operation, client);
        }
        // To recreate http interface, a reload of server is required
        reloadServer();
        //Remove native interface
        operation = createOpNode("core-service=management/management-interface=native-interface", ModelDescriptionConstants.REMOVE);
        CoreUtils.applyUpdate(operation, client);
        operation = createOpNode("socket-binding-group=standard-sockets/socket-binding=management-native", ModelDescriptionConstants.REMOVE);
        CoreUtils.applyUpdate(operation, client);
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
            client = ModelControllerClient.Factory.create("http-remoting", InetAddress.getByName(TestSuiteEnvironment.getServerAddress()),
                    MANAGEMENT_HTTP_PORT, new org.wildfly.core.testrunner.Authentication.CallbackHandler());
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        return client;
    }
}
