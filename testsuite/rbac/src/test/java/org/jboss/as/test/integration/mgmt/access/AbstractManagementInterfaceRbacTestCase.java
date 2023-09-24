/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.mgmt.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import jakarta.inject.Inject;

import org.jboss.as.test.integration.management.interfaces.ManagementInterface;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
public abstract class AbstractManagementInterfaceRbacTestCase {
    private static final Logger logger = Logger.getLogger(AbstractManagementInterfaceRbacTestCase.class);
    private static final Map<String, ManagementInterface> clients = new HashMap<>();
    @Inject
    protected static ManagementClient managementClient;

    @AfterClass
    public static void cleanUpClients() {
        for (ManagementInterface client : clients.values()) {
            try {
                client.close();
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
        }
        clients.clear();
    }

    public ManagementInterface getClientForUser(String userName) {
        ManagementInterface result = clients.get(userName);
        if (result == null) {
            result = createClient(userName);
            clients.put(userName, result);
        }
        return result;
    }

    protected abstract ManagementInterface createClient(String userName);

    public static void removeClientForUser(String userName) throws IOException {
        ManagementInterface client = clients.remove(userName);
        if (client != null) {
            client.close();
        }
    }

    protected static ManagementClient getManagementClient() {
        return managementClient;
    }

    protected ModelNode executeForResult(final ManagementInterface client, final ModelNode operation) throws UnsuccessfulOperationException {
        final ModelNode result = client.execute(operation);
        checkSuccessful(result, operation);
        return result.get(RESULT);
    }

    private void checkSuccessful(final ModelNode result,
                                 final ModelNode operation) throws UnsuccessfulOperationException {
        if (!SUCCESS.equals(result.get(OUTCOME).asString())) {
            logger.error("Operation " + operation + " did not succeed. Result was " + result);
            throw new UnsuccessfulOperationException(result.get(
                    FAILURE_DESCRIPTION).toString());
        }
    }
}
