/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.deployment.scanner;

import static java.security.AccessController.doPrivileged;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OWNER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.server.deployment.scanner.logging.DeploymentScannerLogger.ROOT_LOGGER;

import java.io.IOException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.jboss.as.controller.LocalModelControllerClient;
import org.jboss.as.controller.ModelControllerClientFactory;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.server.deployment.scanner.api.DeploymentOperations;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
* Default implementation of {@link DeploymentOperations}.
*
* @author Stuart Douglas
*/
final class DefaultDeploymentOperations implements DeploymentOperations {

    private final LocalModelControllerClient controllerClient;

    DefaultDeploymentOperations(final ModelControllerClientFactory clientFactory, final Executor executor) {
        // We need to run with RBAC SuperUser rights, but don't need to invoke private operations
        this.controllerClient = clientFactory.createSuperUserClient(executor, false);
    }

    @Override
    public Future<ModelNode> deploy(final ModelNode operation, final ExecutorService executorService) {
        return privilegedExecution().execute(controllerClient::executeAsync, operation);
    }

    @Override
    public Map<String, Boolean> getDeploymentsStatus() {
        final ModelNode op = Util.getEmptyOperation(READ_CHILDREN_RESOURCES_OPERATION, new ModelNode());
        op.get(CHILD_TYPE).set(DEPLOYMENT);
        ModelNode response = privilegedExecution().execute(controllerClient::execute, op);

        // Ensure the operation succeeded before we use the result
        if(response.get(OUTCOME).isDefined() && !SUCCESS.equals(response.get(OUTCOME).asString()))
           throw ROOT_LOGGER.deployModelOperationFailed(response.get(FAILURE_DESCRIPTION).asString());

        final ModelNode result = response.get(RESULT);
        final Map<String, Boolean> deployments = new HashMap<String, Boolean>();
        if (result.isDefined()) {
            for (Property property : result.asPropertyList()) {
                deployments.put(property.getName(), property.getValue().get(ENABLED).asBoolean(false));
            }
        }
        return deployments;
    }

    @Override
    public void close() throws IOException {
        controllerClient.close();
    }

    @Override
    public Set<String> getUnrelatedDeployments(ModelNode owner) {
        final ModelNode op = Util.getEmptyOperation(READ_CHILDREN_RESOURCES_OPERATION, new ModelNode());
        op.get(CHILD_TYPE).set(DEPLOYMENT);
        ModelNode response = privilegedExecution().execute(controllerClient::execute, op);

        // Ensure the operation succeeded before we use the result
        if(response.get(OUTCOME).isDefined() && !SUCCESS.equals(response.get(OUTCOME).asString()))
           throw ROOT_LOGGER.deployModelOperationFailed(response.get(FAILURE_DESCRIPTION).asString());

        final ModelNode result = response.get(RESULT);
        final Set<String> deployments = new HashSet<String>();
        if (result.isDefined()) {
            for (Property property : result.asPropertyList()) {
                if(!owner.equals(property.getValue().get(OWNER))) {
                    deployments.add(property.getName());
                }
            }
        }
        return deployments;
    }

    /** Provides function execution in a doPrivileged block if a security manager is checking privileges */
    private static Execution privilegedExecution() {
        return WildFlySecurityManager.isChecking() ? Execution.PRIVILEGED : Execution.NON_PRIVILEGED;
    }

    /** Executes a function */
    private interface Execution {
        <T, R> R execute(Function<T, R> function, T t);

        Execution NON_PRIVILEGED = new Execution() {
            @Override
            public <T, R> R execute(Function<T, R> function, T t) {
                return function.apply(t);
            }
        };

        Execution PRIVILEGED = new Execution() {
            @Override
            public <T, R> R execute(Function<T, R> function, T t) {
                try {
                    return doPrivileged((PrivilegedExceptionAction<R>) () -> NON_PRIVILEGED.execute(function, t) );
                } catch (PrivilegedActionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    } else if (cause instanceof Error) {
                        throw (Error) cause;
                    } else {
                        // Not possible as Function doesn't throw any checked exception
                        throw new RuntimeException(cause);
                    }
                }
            }
        };

    }
}
