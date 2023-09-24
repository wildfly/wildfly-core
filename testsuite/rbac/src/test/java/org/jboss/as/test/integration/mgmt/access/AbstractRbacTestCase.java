/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.mgmt.access;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.test.integration.management.rbac.RbacAdminCallbackHandler;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.junit.AfterClass;
import org.wildfly.core.testrunner.ManagementClient;

/**
 * Base class for RBAC tests.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class AbstractRbacTestCase {

    private static final Map<String, ModelControllerClient> clients = new HashMap<String, ModelControllerClient>();

    private static final Map<String, String> SASL_OPTIONS = Collections.singletonMap("SASL_DISALLOWED_MECHANISMS", "JBOSS-LOCAL-USER");

    @AfterClass
    public static void cleanUpClients() throws IOException, MgmtOperationException {
        for (ModelControllerClient client : clients.values()) {
            try {
                client.close();
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
        clients.clear();
    }

    @Inject
    protected static ManagementClient managementClient;

    public ModelControllerClient getClientForUser(String userName) throws UnknownHostException {
        ModelControllerClient result = clients.get(userName);
        if (result == null) {
            result = createClient(userName);
            clients.put(userName, result);
        }
        return result;
    }

    private ModelControllerClient createClient(String userName) throws UnknownHostException {
        ModelControllerClientConfiguration config = new ModelControllerClientConfiguration.Builder()
                .setProtocol(managementClient.getMgmtProtocol())
                .setHostName(managementClient.getMgmtAddress())
                .setPort(managementClient.getMgmtPort())
                .setHandler(new RbacAdminCallbackHandler(userName))
                .setSaslOptions(SASL_OPTIONS)
                .build();

        return ModelControllerClient.Factory.create(config);
    }

    public static void removeClientForUser(String userName) throws IOException {
        ModelControllerClient client = clients.remove(userName);
        if (client != null) {
            client.close();
        }
    }

    protected ManagementClient getManagementClient() {
        return managementClient;
    }

}
