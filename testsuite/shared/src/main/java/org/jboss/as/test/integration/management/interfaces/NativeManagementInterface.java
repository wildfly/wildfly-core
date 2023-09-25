/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.interfaces;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.test.integration.management.rbac.RbacAdminCallbackHandler;
import org.jboss.dmr.ModelNode;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
public class NativeManagementInterface implements ManagementInterface {
    private static final Map<String, String> SASL_OPTIONS = Collections.singletonMap("SASL_DISALLOWED_MECHANISMS", "JBOSS-LOCAL-USER");

    private final ModelControllerClient client;

    public NativeManagementInterface(ModelControllerClient client) {
        this.client = client;
    }

    @Override
    public ModelNode execute(ModelNode operation) {
        try {
            return client.execute(operation);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ManagementInterface create(String host, int port, final String username, final String password) {
        ModelControllerClientConfiguration config = new ModelControllerClientConfiguration.Builder()
                .setHostName(host)
                .setPort(port)
                .setHandler(new RbacAdminCallbackHandler(username, password))
                .setSaslOptions(SASL_OPTIONS)
                .build();
        ModelControllerClient client = ModelControllerClient.Factory.create(config);
        return new NativeManagementInterface(client);
    }
}
