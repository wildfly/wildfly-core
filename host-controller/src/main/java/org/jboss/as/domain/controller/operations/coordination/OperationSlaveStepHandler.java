/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations.coordination;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CALLER_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_RESULTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXECUTE_FOR_COORDINATOR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USER;

import java.util.Map;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.domain.controller.operations.SyncModelOperationHandlerWrapper;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.dmr.ModelNode;

/**
 * Performs the host specific overall execution of an operation on a slave, on behalf of the domain controller.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class OperationSlaveStepHandler {

    private final LocalHostControllerInfo localHostControllerInfo;
    private final Map<String, ProxyController> serverProxies;
    private final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry;
    private final ExtensionRegistry extensionRegistry;
    private ServerInventory serverInventory;

    OperationSlaveStepHandler(final LocalHostControllerInfo localHostControllerInfo, Map<String, ProxyController> serverProxies,
                              final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry,
                              final ExtensionRegistry extensionRegistry) {
        this.localHostControllerInfo = localHostControllerInfo;
        this.serverProxies = serverProxies;
        this.ignoredDomainResourceRegistry = ignoredDomainResourceRegistry;
        this.extensionRegistry = extensionRegistry;
    }

    void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        ModelNode headers = operation.get(OPERATION_HEADERS);
        headers.remove(EXECUTE_FOR_COORDINATOR);

        if (headers.hasDefined(DomainControllerLockIdUtils.DOMAIN_CONTROLLER_LOCK_ID)) {
            int id = headers.remove(DomainControllerLockIdUtils.DOMAIN_CONTROLLER_LOCK_ID).asInt();
            context.attach(DomainControllerLockIdUtils.DOMAIN_CONTROLLER_LOCK_ID_ATTACHMENT, id);
        }

        final MultiPhaseLocalContext localContext = new MultiPhaseLocalContext(false);
        final HostControllerExecutionSupport hostControllerExecutionSupport = addSteps(context, operation, localContext);
        final boolean reloadRequired = hostControllerExecutionSupport.isReloadRequired();
        if (reloadRequired) {
            context.reloadRequired();
        }

        context.completeStep(new OperationContext.ResultHandler() {
            @Override
            public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                hostControllerExecutionSupport.complete(resultAction == OperationContext.ResultAction.ROLLBACK);

                if (resultAction == OperationContext.ResultAction.KEEP) {

                    // Replace the special format response ServerOperationsResolverHandler
                    // used to send the prepared response to the coordinator with one that
                    // has the final data. To save bandwidth we also drop any 'server-operations'
                    // that were in the prepared response as those are no longer relevant.
                    ModelNode result = context.getResult();
                    result.setEmptyObject();
                    ModelNode domainFormatted = hostControllerExecutionSupport.getFormattedDomainResult(localContext.getLocalResponse().get(RESULT));
                    result.get(DOMAIN_RESULTS).set(domainFormatted);
                } else {
                    if (reloadRequired) {
                        context.revertReloadRequired();
                    }
                    // The actual operation failed but make sure the result still gets formatted
                    if (hostControllerExecutionSupport.getDomainOperation() != null) {
                        ModelNode localResponse = localContext.getLocalResponse();
                        if (localResponse.has(FAILURE_DESCRIPTION)) {
                            context.getFailureDescription().set(localResponse.get(FAILURE_DESCRIPTION));
                        }
                        if (localResponse.has(RESULT)) {
                            context.getResult().set(localResponse.get(RESULT));
                        }
                    }
                }
            }
        });
    }

    HostControllerExecutionSupport addSteps(final OperationContext context, final ModelNode operation, final MultiPhaseLocalContext multiPhaseLocalContext) throws OperationFailedException {
        final PathAddress originalAddress = PathAddress.pathAddress(operation.get(OP_ADDR));
        final ImmutableManagementResourceRegistration originalRegistration = context.getResourceRegistration();

        final ModelNode localResponse = multiPhaseLocalContext.getLocalResponse();

        final HostControllerExecutionSupport hostControllerExecutionSupport =
                HostControllerExecutionSupport.Factory.create(context, operation, localHostControllerInfo.getLocalHostName(),
                        new LazyDomainModelProvider(context), ignoredDomainResourceRegistry, !localHostControllerInfo.isMasterDomainController() && localHostControllerInfo.isRemoteDomainControllerIgnoreUnaffectedConfiguration(),
                        extensionRegistry);
        ModelNode domainOp = hostControllerExecutionSupport.getDomainOperation();
        if (domainOp != null) {
            // Only require an existing registration if the domain op is not ignored
            if (originalRegistration == null) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.noSuchResourceType(originalAddress));
            }
            addBasicStep(context, domainOp, localResponse);
        }

        ServerOperationResolver resolver = new ServerOperationResolver(localHostControllerInfo.getLocalHostName(), serverProxies);
        ServerOperationsResolverHandler sorh = new ServerOperationsResolverHandler(
                resolver, hostControllerExecutionSupport, originalAddress, originalRegistration, multiPhaseLocalContext, this.serverInventory);
        context.addStep(sorh, OperationContext.Stage.DOMAIN);

        return hostControllerExecutionSupport;
    }

    /**
     * Directly handles the op in the standard way the default prepare step handler would
     * @param context the operation execution context
     * @param operation the operation
     * @throws OperationFailedException if no handler is registered for the operation
     */
    private void addBasicStep(OperationContext context, ModelNode operation, ModelNode localReponse) throws OperationFailedException {
        final String operationName = operation.require(OP).asString();
        final PathAddress pathAddress = PathAddress.pathAddress(operation.get(OP_ADDR));
        final OperationEntry entry = context.getRootResourceRegistration().getOperationEntry(pathAddress, operationName);
        if (entry != null) {
            if (!context.isBooting()
                    && entry.getType() == OperationEntry.EntryType.PRIVATE
                    && operation.hasDefined(OPERATION_HEADERS, CALLER_TYPE)
                    && USER.equals(operation.get(OPERATION_HEADERS, CALLER_TYPE).asString())) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.noHandlerForOperation(operationName, pathAddress));
            }
            if (context.isBooting() || localHostControllerInfo.isMasterDomainController()) {
                context.addModelStep(localReponse, operation, entry.getOperationDefinition(), entry.getOperationHandler(), false);
            } else {
                final OperationStepHandler wrapper;
                // For slave host controllers wrap the operation handler to synchronize missing configuration
                // TODO better configuration of ignore unaffected configuration
                if (localHostControllerInfo.isRemoteDomainControllerIgnoreUnaffectedConfiguration()) {
                    final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
                    wrapper = SyncModelOperationHandlerWrapper.wrapHandler(localHostControllerInfo.getLocalHostName(), operationName, address, entry);
                } else {
                    wrapper = entry.getOperationHandler();
                }
                context.addModelStep(localReponse, operation, entry.getOperationDefinition(), wrapper, false);
            }
        } else {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.noHandlerForOperation(operationName, pathAddress));
        }
    }

    /** Lazily provides a copy of the domain model */
    private static class LazyDomainModelProvider implements HostControllerExecutionSupport.DomainModelProvider {
        private final OperationContext context;
        private Resource domainModelResource;

        private LazyDomainModelProvider(OperationContext context) {
            this.context = context;
        }

        public Resource getDomainModel() {
            if (domainModelResource == null) {
                domainModelResource = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true);
            }
            return domainModelResource;
        }
    }

    public void setServerInventory(ServerInventory serverInventory) {
        this.serverInventory = serverInventory;
    }
}
