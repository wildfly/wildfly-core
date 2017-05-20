/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.security.perimeter;

import java.io.IOException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DisableLocalAuthServerSetupTask implements ServerSetupTask {
    private static final String protocol = "remote";
    private static final String host = TestSuiteEnvironment.getServerAddress();
    private static final int port = 9999;

    private final ModelNode saslFactoryAddress = Operations.createAddress("subsystem", "elytron", "configurable-sasl-server-factory", "configured");
    private final ModelNode managementRealmAddress = Operations.createAddress("core-service", "management", "security-realm", "ManagementRealm", "authentication", "local");
    private final ModelNode nativeSocketBindingAddress = Operations.createAddress("socket-binding-group", "standard-sockets", "socket-binding", "management-native");
    private final ModelNode nativeInterfaceAddress = Operations.createAddress("core-service", "management", "management-interface", "native-interface");
    private final ModelNode nativeSecurityRealmAddress = Operations.createAddress("core-service", "management", "security-realm", "native-realm");

    private final String defaultUserKey = "wildfly.sasl.local-user.default-user";

    @Override
    public void setup(final ManagementClient managementClient) throws Exception {

        final ModelControllerClient client = managementClient.getControllerClient();
        final Operations.CompositeOperationBuilder compositeOp = Operations.CompositeOperationBuilder.create();

        // Add the socket binding for the native-interface
        ModelNode op = Operations.createAddOperation(nativeSocketBindingAddress);
        op.get("port").set(9999);
        op.get("interface").set("management");
        compositeOp.addStep(op.clone());

        // Add the native-interface
        compositeOp.addStep(Operations.createAddOperation(nativeSecurityRealmAddress));

        // Add the native-interface local authentication
        final ModelNode nativeRealmLocalAuthAddress = nativeSecurityRealmAddress.clone().add("authentication", "local");
        op = Operations.createAddOperation(nativeRealmLocalAuthAddress);
        op.get("default-user").set("$local");
        compositeOp.addStep(op.clone());

        // Add the native interface
        op = Operations.createAddOperation(nativeInterfaceAddress);
        op.get("security-realm").set("native-realm");
        op.get("socket-binding").set("management-native");
        compositeOp.addStep(op.clone());

        // Undefine Elytron local-auth
        op = Operations.createReadResourceOperation(saslFactoryAddress);
        ModelNode result = client.execute(op);
        if (Operations.isSuccessfulOutcome(result)) {
            op = Operations.createOperation("map-remove", saslFactoryAddress);
            op.get("name").set("properties");
            op.get("key").set(defaultUserKey);
            compositeOp.addStep(op.clone());
        }

        // Undefine the legacy local-auth
        op = Operations.createReadResourceOperation(managementRealmAddress);
        result = client.execute(op);
        if (Operations.isSuccessfulOutcome(result)) {
            compositeOp.addStep(Operations.createUndefineAttributeOperation(managementRealmAddress, "default-user"));

        }

        executeForSuccess(client, compositeOp.build());

        // Use the current client to execute the reload, but the native client to ensure the reload is complete
        ServerReload.executeReloadAndWaitForCompletion(client, ServerReload.TIMEOUT, false, protocol, host, port);
    }

    @Override
    public void tearDown(final ManagementClient managementClient) throws Exception {
        try (final ModelControllerClient client = createNativeClient()) {

            final Operations.CompositeOperationBuilder compositeOp = Operations.CompositeOperationBuilder.create();

            // Remove the native interface
            compositeOp.addStep(Operations.createRemoveOperation(nativeInterfaceAddress));
            // Remove the native-interface
            compositeOp.addStep(Operations.createRemoveOperation(nativeSecurityRealmAddress));
            // Remove the socket binding for the native-interface
            compositeOp.addStep(Operations.createRemoveOperation(nativeSocketBindingAddress));

            // Re-enable Elytron local-auth
            ModelNode op = Operations.createReadResourceOperation(saslFactoryAddress);
            ModelNode result = client.execute(op);
            if (Operations.isSuccessfulOutcome(result)) {
                op = Operations.createOperation("map-put", saslFactoryAddress);
                op.get("name").set("properties");
                op.get("key").set(defaultUserKey);
                op.get("value").set("$local");
                compositeOp.addStep(op.clone());
            }

            // Re-enable the legacy local-auth
            op = Operations.createReadResourceOperation(managementRealmAddress);
            result = client.execute(op);
            if (Operations.isSuccessfulOutcome(result)) {
                ///core-service=management/security-realm=ManagementRealm/authentication=local:undefine-attribute(name=default-user)
                compositeOp.addStep(Operations.createWriteAttributeOperation(managementRealmAddress, "default-user", "$local"));

            }

            executeForSuccess(client, compositeOp.build());

            // Use the native client to execute the reload, completion waiting should create a new http+remote client
            ServerReload.executeReloadAndWaitForCompletion(client);
        }

    }

    private ModelNode executeForSuccess(final ModelControllerClient client, final Operation op) throws IOException {
        final ModelNode result = client.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(Operations.getFailureDescription(result).asString());
        }
        return Operations.readResult(result);
    }

    private ModelControllerClient createNativeClient() {
        return ModelControllerClient.Factory.create(
                new ModelControllerClientConfiguration.Builder()
                        .setProtocol(protocol)
                        .setHostName(host)
                        .setPort(port)
                        .build()
        );
    }
}
