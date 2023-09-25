/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations.coordination;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CALLER_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXECUTE_FOR_COORDINATOR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USER;
import static org.jboss.as.domain.controller.logging.DomainControllerLogger.HOST_CONTROLLER_LOGGER;
import static org.jboss.as.domain.controller.operations.coordination.OperationCoordinatorStepHandler.configureDomainUUID;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.dmr.ModelNode;

/**
 * Initial step handler for a {@link org.jboss.as.controller.ModelController} that is the model controller for a host controller.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class PrepareStepHandler  implements OperationStepHandler {

    private final LocalHostControllerInfo localHostControllerInfo;
    private final OperationCoordinatorStepHandler coordinatorHandler;
    private final OperationSlaveStepHandler slaveHandler;

    public PrepareStepHandler(final LocalHostControllerInfo localHostControllerInfo,
                              final Map<String, ProxyController> hostProxies,
                              final Map<String, ProxyController> serverProxies,
                              final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry,
                              final ExtensionRegistry extensionRegistry) {
        this.localHostControllerInfo = localHostControllerInfo;
        this.slaveHandler = new OperationSlaveStepHandler(localHostControllerInfo, serverProxies, ignoredDomainResourceRegistry, extensionRegistry);
        this.coordinatorHandler = new OperationCoordinatorStepHandler(localHostControllerInfo, hostProxies, serverProxies, slaveHandler);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        if (context.isBooting()) {
            executeDirect(context, operation);
        } else if (operation.hasDefined(OPERATION_HEADERS)
                && operation.get(OPERATION_HEADERS).hasDefined(EXECUTE_FOR_COORDINATOR)
                && operation.get(OPERATION_HEADERS).get(EXECUTE_FOR_COORDINATOR).asBoolean()) {
            // Coordinator wants us to execute locally and send result including the steps needed for execution on the servers
            slaveHandler.execute(context, operation);
        } else {
            // Assign a unique id to this operation to allow tying together of audit logs from various hosts/servers
            // impacted by it

            if (isServerOperation(operation)) {
                // Pass direct requests for the server through whether they come from the master or not
                // First, attach a domainUUID for audit logging
                configureDomainUUID(operation);
                executeDirect(context, operation);
            } else {
                coordinatorHandler.execute(context, operation);
            }
        }
    }

    public void setExecutorService(final ExecutorService executorService) {
        coordinatorHandler.setExecutorService(executorService);
    }

    private boolean isServerOperation(ModelNode operation) {
        PathAddress addr = PathAddress.pathAddress(operation.get(OP_ADDR));
        return addr.size() > 1
                && HOST.equals(addr.getElement(0).getKey())
                && localHostControllerInfo.getLocalHostName().equals(addr.getElement(0).getValue())
                && RUNNING_SERVER.equals(addr.getElement(1).getKey());
    }

    /**
     * Directly handles the op in the standard way the default prepare step handler would
     * @param context the operation execution context
     * @param operation the operation
     * @throws OperationFailedException if there is no handler registered for the operation
     */
    private void executeDirect(OperationContext context, ModelNode operation) throws OperationFailedException {
        if (HOST_CONTROLLER_LOGGER.isTraceEnabled()) {
            HOST_CONTROLLER_LOGGER.tracef("%s executing direct", getClass().getSimpleName());
        }
        executeDirectOperation(context, operation, false); // Don't bother checking private; this path is for boot or for calls routed to the server
    }

    static void executeDirectOperation(OperationContext context, ModelNode operation, boolean checkPrivate) throws OperationFailedException {
        final String operationName =  operation.require(OP).asString();

        final ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
        PathAddress pathAddress = PathAddress.pathAddress(operation.get(OP_ADDR));
        final OperationEntry stepEntry = context.getRootResourceRegistration().getOperationEntry(pathAddress, operationName);
        if (stepEntry != null) {
            boolean illegalPrivateStep = checkPrivate
                    && stepEntry.getType() == OperationEntry.EntryType.PRIVATE
                    && operation.hasDefined(OPERATION_HEADERS, CALLER_TYPE)
                    && USER.equals(operation.get(OPERATION_HEADERS, CALLER_TYPE).asString());
            if (illegalPrivateStep) {
                context.getFailureDescription().set(ControllerLogger.ROOT_LOGGER.noHandlerForOperation(operationName, pathAddress));
            } else {
                context.addModelStep(stepEntry.getOperationDefinition(), stepEntry.getOperationHandler(), false);
            }
        } else {
            if (! context.isBooting()) {
                if (registration == null) {
                    context.getFailureDescription().set(ControllerLogger.ROOT_LOGGER.noSuchResourceType(pathAddress));
                } else {
                    context.getFailureDescription().set(ControllerLogger.ROOT_LOGGER.noHandlerForOperation(operationName, pathAddress));
                }
            }
        }
    }

    public void setServerInventory(ServerInventory serverInventory) {
        this.slaveHandler.setServerInventory(serverInventory);
    }
}
