/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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
package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_CONTROL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.RECURSIVE;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.RECURSIVE_DEPTH;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.UnauthorizedException;
import org.jboss.as.controller._private.OperationFailedRuntimeException;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.ResourceNotAddressableException;
import org.jboss.as.controller.access.rbac.UnknowRoleException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.AliasEntry;
import org.jboss.as.controller.registry.AliasEntry.AliasContext;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.WildcardReadResourceDescriptionAddressHack;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Global {@code OperationHandler}s.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class GlobalOperationHandlers {

    public static final Set<String> STD_WRITE_OPS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(
                    ADD,
                    REMOVE,
                    WriteAttributeHandler.DEFINITION.getName(),
                    UndefineAttributeHandler.DEFINITION.getName(),
                    MapOperations.MAP_PUT_DEFINITION.getName(),
                    MapOperations.MAP_CLEAR_DEFINITION.getName(),
                    MapOperations.MAP_REMOVE_DEFINITION.getName(),
                    ListOperations.LIST_ADD_DEFINITION.getName(),
                    ListOperations.LIST_CLEAR_DEFINITION.getName(),
                    ListOperations.LIST_REMOVE_DEFINITION.getName())));

    public static final Set<String> STD_READ_OPS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(
                    ReadResourceHandler.DEFINITION.getName(),
                    ReadAttributeHandler.DEFINITION.getName(),
                    ReadAttributeGroupHandler.DEFINITION.getName(),
                    ReadResourceDescriptionHandler.DEFINITION.getName(),
                    ReadAttributeGroupNamesHandler.DEFINITION.getName(),
                    ReadChildrenNamesHandler.DEFINITION.getName(),
                    ReadChildrenTypesHandler.DEFINITION.getName(),
                    ReadChildrenResourcesHandler.DEFINITION.getName(),
                    ReadOperationNamesHandler.DEFINITION.getName(),
                    QueryOperationHandler.DEFINITION.getName(),
                    MapOperations.MAP_GET_DEFINITION.getName(),
                    ListOperations.LIST_GET_DEFINITION.getName(),
                    ReadOperationDescriptionHandler.DEFINITION.getName())));

    public static void registerGlobalOperations(ManagementResourceRegistration root, ProcessType processType) {
        if( processType.isHostController()) {
            root.registerOperationHandler(ReadResourceHandler.DEFINITION, ReadResourceHandler.INSTANCE, true);
            root.registerOperationHandler(ReadAttributeHandler.DEFINITION, ReadAttributeHandler.INSTANCE, true);
            root.registerOperationHandler(ReadAttributeGroupHandler.DEFINITION, ReadAttributeGroupHandler.INSTANCE, true);
        }else{
            root.registerOperationHandler(ReadResourceHandler.RESOLVE_DEFINITION, ReadResourceHandler.RESOLVE_INSTANCE, true);
            root.registerOperationHandler(ReadAttributeHandler.RESOLVE_DEFINITION, ReadAttributeHandler.RESOLVE_INSTANCE, true);
            root.registerOperationHandler(ReadAttributeGroupHandler.RESOLVE_DEFINITION, ReadAttributeGroupHandler.RESOLVE_INSTANCE, true);
        }

        root.registerOperationHandler(ReadResourceDescriptionHandler.DEFINITION, ReadResourceDescriptionHandler.INSTANCE, true);
        root.registerOperationHandler(ReadAttributeGroupNamesHandler.DEFINITION, ReadAttributeGroupNamesHandler.INSTANCE, true);
        root.registerOperationHandler(ReadChildrenNamesHandler.DEFINITION, ReadChildrenNamesHandler.INSTANCE, true);
        root.registerOperationHandler(ReadChildrenTypesHandler.DEFINITION, ReadChildrenTypesHandler.INSTANCE, true);
        root.registerOperationHandler(ReadChildrenResourcesHandler.DEFINITION, ReadChildrenResourcesHandler.INSTANCE, true);
        root.registerOperationHandler(ReadOperationNamesHandler.DEFINITION, ReadOperationNamesHandler.INSTANCE, true);
        root.registerOperationHandler(ReadOperationDescriptionHandler.DEFINITION, ReadOperationDescriptionHandler.INSTANCE, true);
        root.registerOperationHandler(QueryOperationHandler.DEFINITION, QueryOperationHandler.INSTANCE, true);

        //map operations
        root.registerOperationHandler(MapOperations.MAP_PUT_DEFINITION, MapOperations.MAP_PUT_HANDLER, true);
        root.registerOperationHandler(MapOperations.MAP_GET_DEFINITION, MapOperations.MAP_GET_HANDLER, true);
        root.registerOperationHandler(MapOperations.MAP_REMOVE_DEFINITION, MapOperations.MAP_REMOVE_HANDLER, true);
        root.registerOperationHandler(MapOperations.MAP_CLEAR_DEFINITION, MapOperations.MAP_CLEAR_HANDLER, true);
        //list operations
        root.registerOperationHandler(ListOperations.LIST_ADD_DEFINITION, ListOperations.LIST_ADD_HANDLER, true);
        root.registerOperationHandler(ListOperations.LIST_REMOVE_DEFINITION, ListOperations.LIST_REMOVE_HANDLER, true);
        root.registerOperationHandler(ListOperations.LIST_GET_DEFINITION, ListOperations.LIST_GET_HANDLER, true);
        root.registerOperationHandler(ListOperations.LIST_CLEAR_DEFINITION, ListOperations.LIST_CLEAR_HANDLER, true);

        root.registerOperationHandler(ReadResourceDescriptionHandler.CheckResourceAccessHandler.DEFINITION, new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                //Just use an empty operation handler here, this is a private operation and people who want to call it need to instantiate the step handler
                throw new OperationFailedException("This should never be called");
            }
        }, true);
        root.registerOperationHandler(ReadResourceDescriptionHandler.CheckResourceAccessHandler.DEFAULT_DEFINITION, new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                //Just use an empty operation handler here, this is a private operation and people who want to call it need to instantiate the step handler
                throw new OperationFailedException("This should never be called");
            }
        }, true);

        root.registerOperationHandler(WriteAttributeHandler.DEFINITION, WriteAttributeHandler.INSTANCE, true);
        root.registerOperationHandler(UndefineAttributeHandler.DEFINITION, UndefineAttributeHandler.INSTANCE, true);
    }

    public static final String CHECK_DEFAULT_RESOURCE_ACCESS = "check-default-resource-access";

    public static final String CHECK_RESOURCE_ACCESS = "check-resource-access";

    private GlobalOperationHandlers() {
        //
    }

    public abstract static class AbstractMultiTargetHandler implements OperationStepHandler {

        public static final ModelNode FAKE_OPERATION;

        static {
            final ModelNode resolve = new ModelNode();
            resolve.get(OP).set("resolve");
            resolve.get(OP_ADDR).setEmptyList();
            resolve.protect();
            FAKE_OPERATION = resolve;
        }

        private final FilteredData filteredData;

        private final boolean ignoreMissingResource;

        private final FilterPredicate predicate;

        private final boolean registryOnly;

        protected AbstractMultiTargetHandler() {
            this(null, false);
        }

        protected AbstractMultiTargetHandler(boolean registryOnly) {
            this(null, false, null, registryOnly);
        }

        protected AbstractMultiTargetHandler(FilteredData filteredData) {
            this(filteredData, false);
        }

        protected AbstractMultiTargetHandler(FilteredData filteredData, boolean ignoreMissingResource) {
            this(filteredData, ignoreMissingResource, null);
        }

        protected AbstractMultiTargetHandler(FilteredData filteredData, boolean ignoreMissingResource, FilterPredicate predicate) {
            this(filteredData, ignoreMissingResource, predicate, false);
        }

        private AbstractMultiTargetHandler(FilteredData filteredData, boolean ignoreMissingResource, FilterPredicate predicate, boolean registryOnly) {
            this.filteredData = filteredData;
            this.ignoreMissingResource = ignoreMissingResource;
            this.predicate = predicate;
            this.registryOnly = registryOnly;
        }

        protected FilteredData getFilteredData() {
            return filteredData;
        }

        @Override
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            final PathAddress address = context.getCurrentAddress();

            // In case if it's a multiTarget operation, resolve the address first
            // This only works for model resources, which can be resolved into a concrete addresses
            if (address.isMultiTarget()) {
                final FilteredData localFilteredData = filteredData == null ? new FilteredData(PathAddress.EMPTY_ADDRESS) : filteredData;
                // The final result should be a list of executed operations
                final ModelNode result = context.getResult().setEmptyList();

                // Trick the context to give us the model-root
                final OperationStepHandler delegateStepHandler = new OperationStepHandler() {
                    @Override
                    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                        doExecute(context, operation, localFilteredData, true);
                    }
                };
                final ModelNode fakeOperationResponse = new ModelNode();
                context.addStep(fakeOperationResponse, FAKE_OPERATION.clone(),
                        registryOnly ?
                            new RegistrationAddressResolver(operation, result, delegateStepHandler) :
                            new ModelAddressResolver(operation, result, localFilteredData, delegateStepHandler, predicate),
                        OperationContext.Stage.MODEL, true
                );
                context.completeStep(new MultiTargetResultHandler(fakeOperationResponse, localFilteredData, result));
            } else {
                doExecute(context, operation, filteredData, ignoreMissingResource);
            }
        }

        /**
         * Execute the actual operation if it is not addressed to multiple targets.
         *
         *
         * @param context      the operation context
         * @param operation    the original operation
         * @param filteredData tracking object for filtered data
         * @param ignoreMissingResource {@code false} if execution should throw
         *                          {@link org.jboss.as.controller.registry.Resource.NoSuchResourceException} if the
         *                          targeted resource does not exist; {@code true} if it should simply
         *                          not provide a result
         * @throws OperationFailedException
         */
        abstract void doExecute(OperationContext context, ModelNode operation, FilteredData filteredData, boolean ignoreMissingResource) throws OperationFailedException;

        private static class MultiTargetResultHandler implements OperationContext.ResultHandler {

            private final FilteredData localFilteredData;
            private final ModelNode result;
            private final ModelNode fakeOperationResponse;

            public MultiTargetResultHandler(ModelNode fakeOperationResponse, FilteredData localFilteredData, ModelNode result) {
                this.localFilteredData = localFilteredData;
                this.result = result;
                this.fakeOperationResponse = fakeOperationResponse;
            }

            @Override
            public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                if(fakeOperationResponse != null && fakeOperationResponse.hasDefined(FAILURE_DESCRIPTION)) {
                    context.getFailureDescription().set(fakeOperationResponse.get(FAILURE_DESCRIPTION));
                    return;
                }
                // Report on filtering
                if (localFilteredData.hasFilteredData()) {
                    context.getResponseHeaders().get(ACCESS_CONTROL).set(localFilteredData.toModelNode());
                }

                // Extract any failure info from the individual results and use them
                // to construct an overall failure description if necessary
                if (resultAction == OperationContext.ResultAction.ROLLBACK
                        && !context.hasFailureDescription() && result.isDefined()) {
                    String op = operation.require(OP).asString();
                    Map<PathAddress, ModelNode> failures = new HashMap<PathAddress, ModelNode>();
                    for (ModelNode resultItem : result.asList()) {
                        if (resultItem.hasDefined(FAILURE_DESCRIPTION)) {
                            final PathAddress failedAddress = PathAddress.pathAddress(resultItem.get(ADDRESS));
                            ModelNode failedDesc = resultItem.get(FAILURE_DESCRIPTION);
                            failures.put(failedAddress, failedDesc);
                        }
                    }

                    if (failures.size() == 1) {
                        Map.Entry<PathAddress, ModelNode> entry = failures.entrySet().iterator().next();
                        if (entry.getValue().getType() == ModelType.STRING) {
                            context.getFailureDescription().set(ControllerLogger.ROOT_LOGGER.wildcardOperationFailedAtSingleAddress(op, entry.getKey(), entry.getValue().asString()));
                        } else {
                            context.getFailureDescription().set(ControllerLogger.ROOT_LOGGER.wildcardOperationFailedAtSingleAddressWithComplexFailure(op, entry.getKey()));
                        }
                    } else if (failures.size() > 1) {
                        context.getFailureDescription().set(ControllerLogger.ROOT_LOGGER.wildcardOperationFailedAtMultipleAddresses(op, failures.keySet()));
                    }
                }
            }
        }
    }

    @FunctionalInterface
    interface FilterPredicate extends Predicate<ModelNode> {
    }

    private abstract static class AbstractAddressResolver implements OperationStepHandler {

        private static final FilterPredicate DEFAULT_PREDICATE = item -> !item.isDefined()
                || !item.hasDefined(OP_ADDR);


        private final ModelNode operation;
        private final ModelNode result;
        private final FilteredData filteredData;
        private final FilterPredicate predicate;
        private final OperationStepHandler handler; // handler bypassing further wildcard resolution

        public AbstractAddressResolver(final ModelNode operation, final ModelNode result,
                                    final OperationStepHandler delegate,
                                    final FilteredData filteredData,
                                    final FilterPredicate predicate) {
            this.operation = operation;
            this.result = result;
            this.handler = delegate;
            this.predicate = predicate == null ? DEFAULT_PREDICATE : predicate;
            this.filteredData = filteredData;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void execute(final OperationContext context, final ModelNode ignored) throws OperationFailedException {
            final PathAddress addr = PathAddress.pathAddress(operation.require(OP_ADDR));
            //This will only return the alias if this is for a read-resource-definition
            final PathAddress aliasAddr = WildcardReadResourceDescriptionAddressHack.detachAliasAddress(context, operation);
            final PathAddress address = aliasAddr == null ? addr : aliasAddr;

            execute(PathAddress.EMPTY_ADDRESS, address, context, context.getRootResourceRegistration(), true);
            context.completeStep(new OperationContext.ResultHandler() {
                @Override
                public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                    if (result.getType() == ModelType.LIST) {
                        boolean replace = false;
                        ModelNode replacement = new ModelNode().setEmptyList();
                        for (ModelNode item : result.asList()) {
                            if (predicate.test(item)) {
                                // item will be skipped and the result amended
                                replace = true;
                            } else {
                                replacement.add(item);
                            }
                        }
                        if (replace) {
                            result.set(replacement);
                        }
                    }
                }
            });
        }

        /**
         * Wraps a call to
         * {@link #execute(org.jboss.as.controller.PathAddress, org.jboss.as.controller.PathAddress, org.jboss.as.controller.OperationContext, org.jboss.as.controller.registry.ImmutableManagementResourceRegistration, boolean)}
         * with logic to handle authorization and missing resource exceptions.
         *
         * @param base the portion of the address that has already been processed
         * @param remaining the unprocessed portion of the original address
         * @param context  context of the request
         * @param registration the resource registration for the {@code base} address
         * @param ignoreMissing {@code true} if this is being called for an "optional" portion of an address tree
         *                                  and NoSuchResourceException should be ignored. A portion of an address
         *                                  tree is optional if it is associated with a wildcard or multi-segment
         *                                  element in {@code address}
         */
        protected void safeExecute(final PathAddress base, final PathAddress remaining, final OperationContext context,
                final ImmutableManagementResourceRegistration registration, boolean ignoreMissing) {
            try {
                ControllerLogger.MGMT_OP_LOGGER.tracef("safeExecute for %s, remaining is %s", base, remaining);
                execute(base, remaining, context, registration, ignoreMissing);
            } catch (UnauthorizedException e) {
                // equivalent to the resource not existing
                // Just report the failure to the filter and complete normally
                filteredData.addReadRestrictedResource(base);
                ControllerLogger.MGMT_OP_LOGGER.tracef("Caught UnauthorizedException in %s", this);
            } catch (ResourceNotAddressableException e) {
                // Just report the failure to the filter and complete normally
                filteredData.addAccessRestrictedResource(base);
                ControllerLogger.MGMT_OP_LOGGER.tracef("Caught ResourceNotAddressableException in %s", this);
            } catch (Resource.NoSuchResourceException e) {
                // It's possible this is a remote failure, in which case we
                // don't get ResourceNotAddressableException. So see if
                // it was due to any authorization denial
                ModelNode toAuthorize = Util.createEmptyOperation(READ_RESOURCE_OPERATION, base);
                AuthorizationResult.Decision decision = context.authorize(toAuthorize, EnumSet.of(Action.ActionEffect.ADDRESS)).getDecision();
                ControllerLogger.MGMT_OP_LOGGER.tracef("Caught NoSuchResourceException in %s. Authorization decision is %s", this, decision);
                if (decision == AuthorizationResult.Decision.DENY) {
                    // Just report the failure to the filter and complete normally
                    filteredData.addAccessRestrictedResource(base);
                } else if (!ignoreMissing) {
                    throw e;
                }
            }
        }

        /**
         * Resolve matching addresses for the portions of {@code address} equal to or below {@code base},
         * adding a step to invoke the {@code this.handler} for each real address that is a leaf. Wildcards
         * and multi-segment addresses are handled.
         *
         * @param base the portion of the address that has already been processed
         * @param remaining the unprocessed portion of the original address
         * @param context  context of the request
         * @param registration the resource registration for the {@code base} address
         * @param ignoreMissing {@code true} if this is being called for an "optional" portion of an address tree
         *                                  and NoSuchResourceException should be ignored. A portion of an address
         *                                  tree is optional if it is associated with a wildcard or multi-segment
         *                                  element in {@code address}
         */
        protected void execute(final PathAddress base, final PathAddress remaining, final OperationContext context,
                             final ImmutableManagementResourceRegistration registration, final boolean ignoreMissing) {

            // Check whether the operation needs to be dispatched to a remote proxy
            if (registration.isRemote()) {
                if (isWFCORE621Needed(registration, remaining)) {
                    executeWFCORE621(base, remaining, context, registration, ignoreMissing);
                } else {
                    executeRemote(base, remaining, context, registration, ignoreMissing);
                }
                // No further processing needed
                return;
            }

            if (!authorize(context, base, operation)) {
                return;
            }

            if (remaining.size() > 0) {
                final PathElement currentElement = remaining.getElement(0);
                final PathAddress newRemaining = remaining.subAddress(1);
                if (currentElement.isMultiTarget()) {
                    executeMultiTargetChildren(base, currentElement, newRemaining, context, registration, ignoreMissing);
                } else {
                    executeSingleTargetChild(base, currentElement, newRemaining, context, ignoreMissing);
                }
            } else {
                final ModelNode newOp = operation.clone();
                newOp.get(OP_ADDR).set(base.toModelNode());

                final ModelNode resultItem = this.result.add();
                ControllerLogger.MGMT_OP_LOGGER.tracef("Added ModelAddressResolver result item for %s", base);
                final ModelNode resultAddress = resultItem.get(OP_ADDR);

                final OperationStepHandler wrapper = new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        try {
                            handler.execute(context, operation);
                            context.completeStep(new OperationContext.ResultHandler() {
                                @Override
                                public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                                    ControllerLogger.MGMT_OP_LOGGER.tracef("ModelAddressResolver result for %s is %s", base, resultItem);
                                    if (resultItem.hasDefined(RESULT)) {
                                        resultAddress.set(base.toModelNode());
                                        if (resultItem.hasDefined(RESPONSE_HEADERS, ACCESS_CONTROL)) {
                                            ModelNode headers = resultItem.get(RESPONSE_HEADERS);
                                            ModelNode acc = headers.remove(ACCESS_CONTROL);
                                            if (headers.asInt() == 0) {
                                                resultItem.remove(RESPONSE_HEADERS);
                                            }
                                            filteredData.populate(acc, PathAddress.EMPTY_ADDRESS);
                                        }
                                    } else {
                                        resultItem.clear();
                                    }
                                }
                            });
                        } catch (Resource.NoSuchResourceException e) {
                            // just discard the result to avoid leaking the inaccessible address
                        }
                    }
                };
                context.addStep(resultItem, newOp, wrapper, OperationContext.Stage.MODEL, true);
            }
        }

        protected abstract void executeSingleTargetChild(PathAddress base, PathElement currentElement,
                                                         PathAddress newRemaining, OperationContext context, boolean ignoreMissing);

        protected abstract void executeMultiTargetChildren(PathAddress base, PathElement currentElement,
                                                           PathAddress newRemaining, OperationContext context,
                                                           ImmutableManagementResourceRegistration registration,
                                                           boolean ignoreMissing);

        /**
         * If not authorized, this will throw an exception for {@link ModelAddressResolver} for use with the
         * {@link ModelAddressResolver#safeExecute(PathAddress, PathAddress, OperationContext, ImmutableManagementResourceRegistration, boolean)}
         * method. For {@link RegistrationAddressResolver} it will return {@code false}. Otherwise it returns {@code true}
         *
         * @param context the operation context
         * @param base the path address
         * @param operation the operation
         * @return whether or not we were authorized
         */
        protected abstract boolean authorize(OperationContext context, PathAddress base, ModelNode operation);

        private boolean isWFCORE621Needed(ImmutableManagementResourceRegistration registration, PathAddress remaining) {
            if (remaining.size() > 0) {
                PathElement pe = remaining.getElement(0);
                if (pe.isMultiTarget() && RUNNING_SERVER.equals(pe.getKey())) {
                    // We only need this for WildFly 8 and earlier (including EAP 6),
                    // so that's proxied controllers running kernel version 1.x or 2.x
                    ModelVersion modelVersion = registration.getProxyController(PathAddress.EMPTY_ADDRESS).getKernelModelVersion();
                    return modelVersion.getMajor() < 3;
                }
            }
            return false;
        }

        private void executeWFCORE621(PathAddress base, PathAddress remaining, OperationContext context, ImmutableManagementResourceRegistration registration, boolean ignoreMissing) {

            ControllerLogger.MGMT_OP_LOGGER.tracef("Executing WFCORE-621 op for base %s and remaining %s", base, remaining);

            // We have distinct handling for WildFly 8
            // TODO a mixed domain of WildFly > 9 managing WildFly 8 is unlikely to work, so this can likely be dropped
            final boolean wildfly8 = registration.getProxyController(PathAddress.EMPTY_ADDRESS).getKernelModelVersion().getMajor() == 2;

            // We have a request for /host=foo/server=*[/...] targeted at a host that
            // doesn't have the WFCORE-282 fix available and thus can't handle that request.
            // So, we are going to execute a step to have it provide us the names of all
            // its servers, and then a step that will loop through the server names and
            // add the usual execution for each

            final ModelNode serverNameResponse = new ModelNode();
            final AtomicBoolean filtered = new AtomicBoolean(false);

            // We're adding steps to the top of the queue, so add the one that will use the server names first
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

                    ControllerLogger.MGMT_OP_LOGGER.tracef("Executing WFCORE-621 2nd step for base %s and remaining %s; filtered? %s serverNames=%s", base, remaining, filtered, serverNameResponse);

                    // If the read of server names was filtered or for some other reason we didn't get them, we are done.
                    if (filtered.get() || !serverNameResponse.hasDefined(RESULT)) {
                        return;
                    }

                    Set<String> targetServers = extractServerNames(serverNameResponse.get(RESULT), operation, remaining, wildfly8);

                    PathAddress afterServer = remaining.size() > 1 ? remaining.subAddress(1) : PathAddress.EMPTY_ADDRESS;
                    for (String targetServer : targetServers) {
                        PathAddress newBase = base.append(PathElement.pathElement(RUNNING_SERVER, targetServer));
                        safeExecute(newBase, afterServer, context, registration, ignoreMissing);
                    }
                }
            }, OperationContext.Stage.MODEL, true);

            // Now add the step to read the server names.
            // For WildFly 8 slaves we use read-children-resources because read-children-names includes
            // server names that have a server-config but aren't started. So in the handler above
            // we use the resource node to distinguish those cases
            final String opName = wildfly8 ? READ_CHILDREN_RESOURCES_OPERATION : READ_CHILDREN_NAMES_OPERATION;
            ModelNode op = Util.createEmptyOperation(opName, base);
            op.get(CHILD_TYPE).set(RUNNING_SERVER);
            OperationStepHandler proxyHandler = registration.getOperationHandler(PathAddress.EMPTY_ADDRESS, opName);
            // Use a custom handler to deal with the remote host not being readable (e.g. RBAC)
            OperationStepHandler filterableHandler = new FilterableRemoteOperationStepHandler(proxyHandler, base, filtered, filteredData, ignoreMissing);
            context.addStep(serverNameResponse, op, filterableHandler, OperationContext.Stage.MODEL, true);
        }

        private static Set<String> extractServerNames(ModelNode serverResultNode,
                                               ModelNode operation,
                                               PathAddress remaining,
                                               boolean wildfly8) {

            PathElement serverPE = remaining.getElement(0);
            Set<String> interestingServers = null;
            if (!serverPE.isWildcard()) {
                interestingServers = new HashSet<String>();
                Collections.addAll(interestingServers, serverPE.getSegments());
            }

            Set<String> result = new LinkedHashSet<>();
            if (wildfly8) { // TODO a mixed domain of WildFly > 9 managing WildFly 8 is unlikely to work, so all this can likely be dropped
                // The op we ran was read-children-resources, so we got back an object
                for (String serverName : serverResultNode.keys()) {
                    if (interestingServers == null || interestingServers.contains(serverName)) {
                        ModelNode serverVal = serverResultNode.get(serverName);
                        // If we get an undefined or empty node this indicates there's just a placeholder resource
                        // for a non-started server-config
                        boolean validServer = serverVal.isDefined() && serverVal.asInt() > 0;
                        if (!validServer && remaining.size() == 1) {
                            // Request was for the server node itself.
                            // Begin horrendous hacks for WildFly 8 support, where a runtime-only resource
                            // with a couple of attributes is available for non-started servers.
                            String opName = operation.get(OP).asString();
                            if (READ_ATTRIBUTE_OPERATION.equals(opName)) {
                                String attrName = operation.get(NAME).asString();
                                validServer = "launch-type".equals(attrName) || "server-state".equals(attrName);
                            } else if (READ_RESOURCE_OPERATION.equals(opName)) {
                                validServer = operation.hasDefined(INCLUDE_RUNTIME) && operation.get(INCLUDE_RUNTIME).asBoolean();
                            }
                        }
                        if (validServer) {
                            result.add(serverName);
                        }
                    }
                }
            } else {
                // EAP 6 case
                // The op we ran was read-children-names so we got back a list of string
                for (ModelNode serverNameNode : serverResultNode.asList()) {
                    String serverName = serverNameNode.asString();
                    if (interestingServers == null || interestingServers.contains(serverName)) {
                        result.add(serverName);
                    }
                }
            }
            return result;
        }

        private void executeRemote(final PathAddress base, final PathAddress remaining, OperationContext context, ImmutableManagementResourceRegistration registration, final boolean ignoreMissing) {
            // make sure the target address does not contain the unresolved elements of the address
            final ModelNode remoteOp = operation.clone();
            final PathAddress fullAddress = base.append(remaining);
            remoteOp.get(OP_ADDR).set(fullAddress.toModelNode());
            // Temp remote result
            final ModelNode resultItem = new ModelNode();

            final OperationStepHandler proxyHandler = registration.getOperationHandler(PathAddress.EMPTY_ADDRESS, operation.require(OP).asString());
            context.addStep(resultItem, remoteOp, new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    try {
                        // Execute the proxy step handler in a separate step
                        // so we have the final response available to our ResultHandler
                        ControllerLogger.MGMT_OP_LOGGER.tracef("sending ModelAddressResolver request %s to remote process using %s",
                                operation, proxyHandler);

                        final AtomicBoolean filtered = new AtomicBoolean(false);

                        context.addStep(new FilterableRemoteOperationStepHandler(proxyHandler, base, filtered, filteredData, ignoreMissing), OperationContext.Stage.MODEL, true);

                        context.completeStep(new OperationContext.ResultHandler() {

                            @Override
                            public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                                ControllerLogger.MGMT_OP_LOGGER.tracef("ModelAddressResolver response from remote process is %s",
                                        resultItem);

                                if (filtered.get()) {
                                    ControllerLogger.MGMT_OP_LOGGER.trace("Response was filtered");
                                    return;
                                }

                                // Determine the address prefix to prepend to RBAC responses from servers
                                PathAddress rbacPrefix = base.size() > 1 && base.getElement(1).getKey().equals(RUNNING_SERVER)
                                        ? base : PathAddress.EMPTY_ADDRESS;

                                // If there are multiple targets remaining, the result should be a list
                                if (remaining.isMultiTarget()) {
                                    if (resultItem.has(RESULT) && resultItem.get(RESULT).getType() == ModelType.LIST) {
                                        for (final ModelNode rr : resultItem.get(RESULT).asList()) {
                                            // Create a new result entry
                                            final ModelNode nr = result.add();
                                            final PathAddress address = PathAddress.pathAddress(rr.get(OP_ADDR));
                                            // Check whether the result of the remote target contains part of the base address
                                            // this might happen for hosts
                                            int max = Math.min(base.size(), address.size());
                                            int match = 0;
                                            for (int i = 0; i < max; i++) {
                                                final PathElement eb = base.getElement(i);
                                                final PathElement ea = address.getElement(i);
                                                if (eb.getKey().equals(ea.getKey())) {
                                                    match = i + 1;
                                                }
                                            }
                                            final PathAddress resolvedAddress = base.append(address.subAddress(match));
                                            ControllerLogger.MGMT_OP_LOGGER.tracef("recording multi-target ModelAddressResolver response " +
                                                    "to %s at %s", fullAddress, resolvedAddress);
                                            nr.get(OP_ADDR).set(resolvedAddress.toModelNode());
                                            nr.get(OUTCOME).set(rr.get(OUTCOME));
                                            nr.get(RESULT).set(rr.get(RESULT));

                                            if (rr.hasDefined(RESPONSE_HEADERS)) {
                                                ModelNode headers = rr.get(RESPONSE_HEADERS);
                                                ModelNode acc = headers.remove(ACCESS_CONTROL);
                                                if (headers.asInt() > 0) {
                                                    nr.get(RESPONSE_HEADERS).set(headers);
                                                }
                                                if (acc != null && acc.isDefined()) {
                                                    filteredData.populate(acc, rbacPrefix);
                                                    ControllerLogger.MGMT_OP_LOGGER.tracef("Populated local filtered data " +
                                                            "with remote access control headers %s from result item %s", acc, rr);
                                                }
                                            }
                                        }
                                        if (resultItem.hasDefined(RESPONSE_HEADERS, ACCESS_CONTROL)) {
                                            ModelNode acc = resultItem.get(RESPONSE_HEADERS, ACCESS_CONTROL);
                                            filteredData.populate(acc, PathAddress.EMPTY_ADDRESS);
                                        }
                                    }
                                } else {
                                    ControllerLogger.MGMT_OP_LOGGER.tracef("recording non-multi-target ModelAddressResolver response " +
                                            "to %s", fullAddress);
                                    final ModelNode nr = result.add();
                                    nr.get(OP_ADDR).set(fullAddress.toModelNode());
                                    nr.get(OUTCOME).set(resultItem.get(OUTCOME));
                                    nr.get(RESULT).set(resultItem.get(RESULT));

                                    if (resultItem.hasDefined(RESPONSE_HEADERS)) {
                                        ModelNode headers = resultItem.get(RESPONSE_HEADERS);
                                        ModelNode acc = headers.remove(ACCESS_CONTROL);
                                        if (headers.asInt() > 0) {
                                            nr.get(RESPONSE_HEADERS).set(headers);
                                        }
                                        if (acc != null && acc.isDefined()) {
                                            filteredData.populate(acc, PathAddress.EMPTY_ADDRESS);
                                            ControllerLogger.MGMT_OP_LOGGER.tracef("Populated local filtered data " +
                                                    "with remote access control headers %s from result item %s", acc, resultItem);
                                        }
                                    }
                                }
                            }
                        });
                    } catch (Resource.NoSuchResourceException e) {
                        // just discard the result to avoid leaking the inaccessible address
                    }
                }
            }, OperationContext.Stage.MODEL, true);
        }


    }

    private static final class ModelAddressResolver extends AbstractAddressResolver {
        public ModelAddressResolver(ModelNode operation, ModelNode result, FilteredData filteredData, OperationStepHandler delegate, FilterPredicate predicate) {
            super(operation, result, delegate, filteredData, predicate);
        }

        protected void executeMultiTargetChildren(PathAddress base, PathElement currentElement, PathAddress newRemaining, OperationContext context, ImmutableManagementResourceRegistration registration, boolean ignoreMissing) {
            final Resource resource = context.readResource(base, false);
            final String childType = currentElement.getKey().equals("*") ? null : currentElement.getKey();
            if (registration.isRemote()) {// || registration.isRuntimeOnly()) {
                // At least for proxies it should use the proxy operation handler
                throw new IllegalStateException();
            }
            // Get the available children
            final Map<String, Set<String>> resolved = getChildAddresses(context, base, registration, resource, childType);
            for (Map.Entry<String, Set<String>> entry : resolved.entrySet()) {
                final String key = entry.getKey();
                final Set<String> children = entry.getValue();
                if (children.isEmpty()) {
                    continue;
                }
                if (currentElement.isWildcard()) {
                    for (final String child : children) {
                        final PathElement e = PathElement.pathElement(key, child);
                        final PathAddress next = base.append(e);
                        // Either require the child or a remote target
                        final ImmutableManagementResourceRegistration nr = context.getResourceRegistration().getSubModel(next);
                        if (resource.hasChild(e) || (nr != null && nr.isRemote())) {
                            safeExecute(next, newRemaining, context, nr, true);
                        }
                    }
                } else {
                    String[] segments = currentElement.getSegments();
                    // If there's more than 1 segment, treat that like a wildcard, and don't
                    // fail on bits that disappear
                    boolean ignore = ignoreMissing || segments.length > 1;
                    for (final String segment : currentElement.getSegments()) {
                        if (children.contains(segment)) {
                            final PathElement e = PathElement.pathElement(key, segment);
                            final PathAddress next = base.append(e);
                            // Either require the child or a remote target
                            final ImmutableManagementResourceRegistration nr = context.getResourceRegistration().getSubModel(next);
                            if (resource.hasChild(e) || (nr != null && nr.isRemote())) {
                                safeExecute(next, newRemaining, context, nr, ignore);
                            }
                        }
                    }
                }
            }
        }

        @Override
        protected void executeSingleTargetChild(PathAddress base, PathElement currentElement, PathAddress newRemaining, OperationContext context, boolean ignoreMissing) {
            final PathAddress next = base.append(currentElement);
            // Either require the child or a remote target
            final Resource resource = context.readResource(base, false);
            final ImmutableManagementResourceRegistration nr = context.getResourceRegistration().getSubModel(next);
            if (resource.hasChild(currentElement) || (nr != null && nr.isRemote())) {
                safeExecute(next, newRemaining, context, nr, ignoreMissing);
            }
            //if we are on the wrong host no need to do anything
            else if(!resource.hasChild(currentElement)) {
               throw new Resource.NoSuchResourceException(currentElement);
            }
        }

        @Override
        protected boolean authorize(OperationContext context, PathAddress base, ModelNode operation) {
            try {
                context.readResource(base, false);
            } catch(UnknowRoleException ex) {
                context.getFailureDescription().set(ex.getMessage());
                return false;
            }
            //An exception will happen if not allowed
            return true;
        }
    }

    private static class FilterableRemoteOperationStepHandler implements OperationStepHandler {
        private final OperationStepHandler proxyHandler;
        private final PathAddress base;
        private final AtomicBoolean filtered;
        private final FilteredData filteredData;
        private final boolean ignoreMissing;

        public FilterableRemoteOperationStepHandler(OperationStepHandler proxyHandler, PathAddress base,
                                                    AtomicBoolean filtered, FilteredData filteredData, boolean ignoreMissing) {
            this.proxyHandler = proxyHandler;
            this.base = base;
            this.filtered = filtered;
            this.filteredData = filteredData;
            this.ignoreMissing = ignoreMissing;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            try {
                proxyHandler.execute(context, operation);
                ControllerLogger.MGMT_OP_LOGGER.tracef("Preliminary result for %s is %s", operation, context.hasResult() ? context.getResult() : null);
            } catch (UnauthorizedException e) {
                // equivalent to the resource not existing
                // Just report the failure to the filter and complete normally
                filteredData.addReadRestrictedResource(base);
                filtered.set(true);
                ControllerLogger.MGMT_OP_LOGGER.tracef("Caught UnauthorizedException in remote execution from %s", proxyHandler);
            } catch (ResourceNotAddressableException e) {
                // Just report the failure to the filter and complete normally
                filteredData.addAccessRestrictedResource(base);
                filtered.set(true);
                ControllerLogger.MGMT_OP_LOGGER.tracef("Caught ResourceNotAddressableException in remote execution from %s", proxyHandler);
            } catch (Resource.NoSuchResourceException e) {
                // It's possible this is a remote failure, in which case we
                // don't get ResourceNotAddressableException. So see if
                // it was due to any authorization denial

                ModelNode toAuthorize = operation.clone();
                toAuthorize.get(OP).set(READ_RESOURCE_DESCRIPTION_OPERATION);
                toAuthorize.get(OP_ADDR).set(base.toModelNode());
                AuthorizationResult.Decision decision = context.authorize(toAuthorize, EnumSet.of(Action.ActionEffect.ADDRESS)).getDecision();
                ControllerLogger.MGMT_OP_LOGGER.tracef("Caught NoSuchResourceException in remote execution from %s. Authorization decision is %s", proxyHandler, decision);
                if (decision == AuthorizationResult.Decision.DENY) {
                    // Just report the failure to the filter and complete normally
                    filtered.set(true);
                    if (filteredData != null) {
                        filteredData.addAccessRestrictedResource(base);
                    }
                } else if (!ignoreMissing) {
                    throw e;
                }
            }
        }
    }


    private static class RegistrationAddressResolver extends AbstractAddressResolver {

        RegistrationAddressResolver(final ModelNode operation, final ModelNode result, final OperationStepHandler delegate) {
            super(operation, result, delegate, null, null);
        }

        @Override
        protected void executeMultiTargetChildren(PathAddress base, PathElement currentElement, PathAddress newRemaining, OperationContext context, ImmutableManagementResourceRegistration registration, boolean ignoreMissing) {
            final String childType = currentElement.getKey().equals("*") ? null : currentElement.getKey();
            if (registration.isRemote()) {// || registration.isRuntimeOnly()) {
                // At least for proxies it should use the proxy operation handler
                throw new IllegalStateException();
            }

            final Set<PathElement> children = context.getResourceRegistration().getChildAddresses(base);
            if (children == null || children.isEmpty()) {
                throw new NoSuchResourceTypeException(base.append(currentElement));
            }

            boolean foundValid = false;
            PathAddress invalid = null;
            for (final PathElement path : children) {
                if (childType != null && !childType.equals(path.getKey())) {
                    continue;
                }
                // matches /host=xxx/server=*/... address
                final boolean isHostWildcardServerAddress = base.size() > 0 && base.getLastElement().getKey().equals(HOST) && path.getKey().equals(RUNNING_SERVER) && path.isWildcard();
                if (isHostWildcardServerAddress && newRemaining.size() > 0) {
                    //Trying to get e.g. /host=xxx/server=*/interface=public will fail, so make sure if there are remaining elements for
                    //a /host=master/server=* that we don't attempt to get those
                    continue;
                }
                final PathAddress next = base.append(path);
                final ImmutableManagementResourceRegistration nr = context.getResourceRegistration().getSubModel(next);
                try {
                    execute(next, newRemaining, context, nr, ignoreMissing);
                    foundValid = true;
                } catch (NoSuchResourceTypeException e) {
                    if (!foundValid) {
                        PathAddress failedAddr = e.getPathAddress();
                        // Store the failed address for error reporting, but only if
                        // 1) this is the first failure, or
                        // 2) The size of the failed address is larger than the currently
                        //    cached one, indicating there is some path that has a larger number
                        //    of valid elements than the currently cached path. So we want to
                        //    report that larger path
                        if (invalid == null || failedAddr.size() > invalid.size()) {
                            PathAddress newBase = base.append(currentElement);
                            invalid = newBase.append(failedAddr.subAddress(newBase.size()));
                        }
                    }
                }
            }

            if (!foundValid) {
                if (invalid == null) {
                    // No children matched currentElement
                    invalid = base.append(currentElement);
                }
                throw new NoSuchResourceTypeException(invalid);
            }
        }

        @Override
        protected void executeSingleTargetChild(PathAddress base, PathElement currentElement, PathAddress newRemaining, OperationContext context, boolean ignoreMissing) {
            final PathAddress next = base.append(currentElement);
            final ImmutableManagementResourceRegistration nr = context.getResourceRegistration().getSubModel(next);
            if (nr != null) {
                execute(next, newRemaining, context, nr, ignoreMissing);
            } else {
                throw new NoSuchResourceTypeException(next);
            }
        }

        @Override
        protected boolean authorize(OperationContext context, PathAddress base, ModelNode operation) {
            if (base.size() > 0) {
                PathElement element = base.getLastElement();
                if (!element.isWildcard() && (element.getKey().equals(HOST)/* || element.getKey().equals(RUNNING_SERVER)*/)) {
                    //Only do this for host resources. The rest of r-r-d does filtering itself. However, without
                    //this:
                    // - a slave host scoped role doing a /host=*:r-r-d will not get rid of the host=master resource
                    // - a master host scoped role doing a /host=*/server=*/subsystem=thing:r-r-d will not get rid of the host=slave resource
                    ModelNode toAuthorize = operation.clone();
                    toAuthorize.get(OP).set(READ_RESOURCE_DESCRIPTION_OPERATION);
                    toAuthorize.get(OP_ADDR).set(base.toModelNode());
                    AuthorizationResult.Decision decision = context.authorize(toAuthorize, EnumSet.of(Action.ActionEffect.ADDRESS)).getDecision();
                    return decision == AuthorizationResult.Decision.PERMIT;
                }
            }
            return true;
        }
    }

    /**
     * WFCORE-573 Wraps a response to a call where the expected resource may
     * have disappeared with a flag to record that that has happened.
     */
    static class AvailableResponse {
        boolean unavailable;
        final ModelNode response;

        AvailableResponse(ModelNode response) {
            this.response = response;
        }
    }

    /** WFCORE-573 Wrap a read-attribute call and handle disappearance of the target resource*/
    static class AvailableResponseWrapper implements OperationStepHandler {
        private final OperationStepHandler wrapped;
        private final AvailableResponse availableResponse;

        AvailableResponseWrapper(OperationStepHandler wrapped, AvailableResponse availableResponse) {
            this.wrapped = wrapped;
            this.availableResponse = availableResponse;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            try {
                wrapped.execute(context, operation);
            } catch (Resource.NoSuchResourceException e) {
                availableResponse.unavailable = true;
            }
        }
    }

    /**
     * Gets the addresses of the child resources under the given resource.
     *
     * @param context        the operation context
     * @param registry       registry entry representing the resource
     * @param resource       the current resource
     * @param validChildType a single child type to which the results should be limited. If {@code null} the result
     *                       should include all child types
     * @return map where the keys are the child types and the values are a set of child names associated with a type
     */
    static Map<String, Set<String>> getChildAddresses(final OperationContext context, final PathAddress addr, final ImmutableManagementResourceRegistration registry, Resource resource, final String validChildType) {

        Map<String, Set<String>> result = new HashMap<>();
        Predicate<String> validChildTypeFilter = childType -> (validChildType == null) || validChildType.equals(childType);

        if (resource != null) {
            for (String childType : registry.getChildNames(PathAddress.EMPTY_ADDRESS)) {
                if (validChildTypeFilter.test(childType)) {
                    List<String> list = new ArrayList<>();
                    for (String child : resource.getChildrenNames(childType)) {
                        if (registry.getSubModel(PathAddress.pathAddress(PathElement.pathElement(childType, child))) != null) {
                            list.add(child);
                        }
                    }
                    result.put(childType, new LinkedHashSet<>(list));
                }
            }
        }

        Set<PathElement> paths = registry.getChildAddresses(PathAddress.EMPTY_ADDRESS);
        for (PathElement path : paths) {
            String childType = path.getKey();
            if (validChildTypeFilter.test(childType)) {
                Set<String> children = result.get(childType);
                if (children == null) {
                    // WFLY-3306 Ensure we have an entry for any valid child type
                    children = new LinkedHashSet<>();
                    result.put(childType, children);
                }
                ImmutableManagementResourceRegistration childRegistration = registry.getSubModel(PathAddress.pathAddress(path));
                if (childRegistration != null) {
                    AliasEntry aliasEntry = childRegistration.getAliasEntry();
                    if (aliasEntry != null) {
                        PathAddress childAddr = addr.append(path);
                        PathAddress target = aliasEntry.convertToTargetAddress(childAddr, AliasContext.create(childAddr, context));
                        assert !childAddr.equals(target) : "Alias was not translated";
                        PathAddress targetParent = target.getParent();
                        Resource parentResource = context.readResourceFromRoot(targetParent, false);
                        if (parentResource != null) {
                            PathElement targetElement = target.getLastElement();
                            if (targetElement.isWildcard()) {
                                children.addAll(parentResource.getChildrenNames(targetElement.getKey()));
                            } else if (parentResource.hasChild(targetElement)) {
                                children.add(path.getValue());
                            }
                        }
                    }
                    if (!path.isWildcard() && childRegistration.isRemote()) {
                        children.add(path.getValue());
                    }
                }
            }
        }

        return result;
    }

    static Locale getLocale(OperationContext context, final ModelNode operation) throws OperationFailedException {
        if (!operation.hasDefined(GlobalOperationAttributes.LOCALE.getName())) {
            return null;
        }
        String unparsed = normalizeLocale(operation.get(GlobalOperationAttributes.LOCALE.getName()).asString());
        try {
            return LocaleResolver.resolveLocale(unparsed);
        } catch (IllegalArgumentException e) {
            reportInvalidLocaleFormat(context, e.getMessage());
            return null;
        }
    }

    static boolean getRecursive(OperationContext context, ModelNode op) throws OperationFailedException {
        // -1 means UNDEFINED
        ModelNode recursiveNode = RECURSIVE.resolveModelAttribute(context, op);
        final int recursiveValue = recursiveNode.isDefined() ? (recursiveNode.asBoolean() ? 1 : 0) : -1;
        final int recursiveDepthValue = RECURSIVE_DEPTH.resolveModelAttribute(context, op).asInt(-1);
        // WFCORE-76: We are recursing in this round IFF:
        //  Recursive is explicitly specified as TRUE and recursiveDepth is UNDEFINED
        //  Recursive is either TRUE or UNDEFINED and recursiveDepth is >0
        return recursiveValue > 0 && recursiveDepthValue == -1 || //
                recursiveValue != 0 && recursiveDepthValue > 0;
    }

    static void setNextRecursive(OperationContext context, ModelNode op, ModelNode nextOp) throws OperationFailedException {
        // -1 means UNDEFINED
        final int recursiveDepthValue = RECURSIVE_DEPTH.resolveModelAttribute(context, op).asInt(-1);
        // WFCORE-76: We are recursing in the next step IFF:
        //  Recursive is explicitly specified as TRUE and recursiveDepth is UNDEFINED; or
        //  Recursive is either TRUE or UNDEFINED and (recursiveDepth - 1) is >0

        // Recursive value carries through unchanged
        nextOp.get(RECURSIVE.getName()).set(op.get(RECURSIVE.getName()));
        switch(recursiveDepthValue) {
            case -1:
                // Undefined stays undefined
                nextOp.get(RECURSIVE_DEPTH.getName()).set(op.get(RECURSIVE_DEPTH.getName()));
                break;
            case 0:
                nextOp.get(RECURSIVE_DEPTH.getName()).set(recursiveDepthValue);
                break;
            default:
                nextOp.get(RECURSIVE_DEPTH.getName()).set(recursiveDepthValue - 1);
                break;
        }
    }

    private static String normalizeLocale(String toNormalize) {
        return ("zh_Hans".equalsIgnoreCase(toNormalize) || "zh-Hans".equalsIgnoreCase(toNormalize)) ? "zh_CN" : toNormalize;
    }

    private static void reportInvalidLocaleFormat(OperationContext context, String format) {
        String msg = ControllerLogger.ROOT_LOGGER.invalidLocaleString(format);
        ControllerLogger.MGMT_OP_LOGGER.debug(msg);
        // TODO report the problem to client via out-of-band message.
        // Enable this in 7.2 or later when there is time to test
        //context.report(MessageSeverity.WARN, msg);
    }


    private static final class NoSuchResourceTypeException extends OperationFailedRuntimeException {
        private final PathAddress pathAddress;

        private NoSuchResourceTypeException(PathAddress pathAddress) {
            super(ControllerLogger.ROOT_LOGGER.noSuchResourceType(pathAddress));
            this.pathAddress = pathAddress;
        }

        private PathAddress getPathAddress() {
            return pathAddress;
        }
    }
}
