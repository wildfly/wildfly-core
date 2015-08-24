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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.RECURSIVE;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.RECURSIVE_DEPTH;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.UnauthorizedException;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.ResourceNotAddressableException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.AliasEntry;
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


    public static void registerGlobalOperations(ManagementResourceRegistration root, ProcessType processType) {
        if( processType == ProcessType.HOST_CONTROLLER) {
            root.registerOperationHandler(org.jboss.as.controller.operations.global.ReadResourceHandler.DEFINITION,
                    org.jboss.as.controller.operations.global.ReadResourceHandler.INSTANCE, true);
            root.registerOperationHandler(org.jboss.as.controller.operations.global.ReadAttributeHandler.DEFINITION,
                    org.jboss.as.controller.operations.global.ReadAttributeHandler.INSTANCE, true);
            root.registerOperationHandler(ReadAttributeGroupHandler.DEFINITION, ReadAttributeGroupHandler.INSTANCE, true);
        }else{
            root.registerOperationHandler(org.jboss.as.controller.operations.global.ReadResourceHandler.RESOLVE_DEFINITION,
                    org.jboss.as.controller.operations.global.ReadResourceHandler.RESOLVE_INSTANCE, true);
            root.registerOperationHandler(org.jboss.as.controller.operations.global.ReadAttributeHandler.RESOLVE_DEFINITION,
                    org.jboss.as.controller.operations.global.ReadAttributeHandler.RESOLVE_INSTANCE, true);
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

        if (processType != ProcessType.DOMAIN_SERVER) {
            root.registerOperationHandler(org.jboss.as.controller.operations.global.WriteAttributeHandler.DEFINITION,
                    org.jboss.as.controller.operations.global.WriteAttributeHandler.INSTANCE, true);
            root.registerOperationHandler(org.jboss.as.controller.operations.global.UndefineAttributeHandler.DEFINITION,
                    org.jboss.as.controller.operations.global.UndefineAttributeHandler.INSTANCE, true);
        }
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

        private static final FilterPredicate DEFAULT_PREDICATE = new FilterPredicate() {
            @Override
            public boolean appliesTo(ModelNode item) {
                return !item.isDefined()
                        || !item.hasDefined(OP_ADDR);
            }
        };

        private final FilteredData filteredData;

        private final boolean ignoreMissingResource;

        private final FilterPredicate predicate;

        protected AbstractMultiTargetHandler() {
            this(null, false);
        }

        protected AbstractMultiTargetHandler(FilteredData filteredData) {
            this(filteredData, false);
        }

        protected AbstractMultiTargetHandler(FilteredData filteredData, boolean ignoreMissingResource) {
            this.filteredData = filteredData;
            this.ignoreMissingResource = ignoreMissingResource;
            this.predicate = DEFAULT_PREDICATE;
        }

        protected AbstractMultiTargetHandler(FilteredData filteredData, boolean ignoreMissingResource, FilterPredicate predicate) {
            this.filteredData = filteredData;
            this.ignoreMissingResource = ignoreMissingResource;
            this.predicate = predicate;
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
                context.addStep(new ModelNode(), FAKE_OPERATION.clone(),
                        new ModelAddressResolver(operation, result, localFilteredData,
                                new OperationStepHandler() {
                                    @Override
                                    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                                        doExecute(context, operation, localFilteredData, true);
                                    }
                                }, predicate),
                        OperationContext.Stage.MODEL, true
                );
                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {

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
                });
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
    }

    interface FilterPredicate {
        boolean appliesTo(ModelNode result);
    }

    public static final class ModelAddressResolver implements OperationStepHandler {

        private final ModelNode operation;
        private final ModelNode result;
        private final FilteredData filteredData;
        private final FilterPredicate predicate;
        private final OperationStepHandler handler; // handler bypassing further wildcard resolution

        public ModelAddressResolver(final ModelNode operation, final ModelNode result,
                                    final FilteredData filteredData,
                                    final OperationStepHandler delegate,
                                    final FilterPredicate predicate) {
            this.operation = operation;
            this.result = result;
            this.handler = delegate;
            this.predicate = predicate;
            this.filteredData = filteredData;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void execute(final OperationContext context, final ModelNode ignored) throws OperationFailedException {
            final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
            execute(PathAddress.EMPTY_ADDRESS, address, context, context.getRootResourceRegistration(), false);
            context.completeStep(new OperationContext.ResultHandler() {
                @Override
                public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                    if (result.getType() == ModelType.LIST) {
                        boolean replace = false;
                        ModelNode replacement = new ModelNode().setEmptyList();
                        for (ModelNode item : result.asList()) {
                            if (predicate.appliesTo(item)) {
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
        private void safeExecute(final PathAddress base, final PathAddress remaining, final OperationContext context,
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
        private void execute(final PathAddress base, final PathAddress remaining, final OperationContext context,
                             final ImmutableManagementResourceRegistration registration, final boolean ignoreMissing) {

            // Check whether the operation needs to be dispatched to a remote proxy
            if (registration.isRemote()) {
                // make sure the target address does not contain the unresolved elements of the address
                final ModelNode remoteOp = operation.clone();
                final PathAddress fullAddress = base.append(remaining);
                remoteOp.get(OP_ADDR).set(fullAddress.toModelNode());
                // Temp remote result
                final ModelNode resultItem = new ModelNode();

                final OperationStepHandler delegate = registration.getOperationHandler(PathAddress.EMPTY_ADDRESS, operation.require(OP).asString());
                context.addStep(resultItem, remoteOp, new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        try {
                            // Execute the proxy step handler in a separate step
                            // so we have the final response available to our ResultHandler
                            ControllerLogger.MGMT_OP_LOGGER.tracef("sending ModelAddressResolver request %s to remote process using %s",
                                    operation, delegate);

                            final AtomicBoolean filtered = new AtomicBoolean(false);

                            context.addStep(new OperationStepHandler() {
                                @Override
                                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                                    try {
                                        delegate.execute(context, operation);
                                    } catch (UnauthorizedException e) {
                                        // equivalent to the resource not existing
                                        // Just report the failure to the filter and complete normally
                                        filteredData.addReadRestrictedResource(base);
                                        filtered.set(true);
                                        ControllerLogger.MGMT_OP_LOGGER.tracef("Caught UnauthorizedException in remote execution from %s", delegate);
                                    } catch (ResourceNotAddressableException e) {
                                        // Just report the failure to the filter and complete normally
                                        filteredData.addAccessRestrictedResource(base);
                                        filtered.set(true);
                                        ControllerLogger.MGMT_OP_LOGGER.tracef("Caught ResourceNotAddressableException in remote execution from %s", delegate);
                                    } catch (Resource.NoSuchResourceException e) {
                                        // It's possible this is a remote failure, in which case we
                                        // don't get ResourceNotAddressableException. So see if
                                        // it was due to any authorization denial
                                        ModelNode toAuthorize = Util.createEmptyOperation(READ_RESOURCE_OPERATION, base);
                                        AuthorizationResult.Decision decision = context.authorize(toAuthorize, EnumSet.of(Action.ActionEffect.ADDRESS)).getDecision();
                                        ControllerLogger.MGMT_OP_LOGGER.tracef("Caught NoSuchResourceException in remote execution from %s. Authorization decision is %s", delegate, decision);
                                        if (decision == AuthorizationResult.Decision.DENY) {
                                            // Just report the failure to the filter and complete normally
                                            filteredData.addAccessRestrictedResource(base);
                                            filtered.set(true);
                                        } else if (!ignoreMissing) {
                                            throw e;
                                        }
                                    }
                                }
                            }, OperationContext.Stage.MODEL, true);

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
                                                    if (acc.isDefined()) {
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
                                            if (acc.isDefined()) {
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

                // No further processing needed
                return;
            }

            // Require a resource, if it's not a remote target
            final Resource resource = context.readResource(base, false);
            if (remaining.size() > 0) {
                final PathElement element = remaining.getElement(0);
                final PathAddress newRemaining = remaining.subAddress(1);
                if (element.isMultiTarget()) {
                    final String childType = element.getKey().equals("*") ? null : element.getKey();
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
                        if (element.isWildcard()) {
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
                            String[] segments = element.getSegments();
                            // If there's more than 1 segment, treat that like a wildcard, and don't
                            // fail on bits that disappear
                            boolean ignore = ignoreMissing || segments.length > 1;
                            for (final String segment : element.getSegments()) {
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
                } else {
                    final PathAddress next = base.append(element);
                    // Either require the child or a remote target
                    final ImmutableManagementResourceRegistration nr = context.getResourceRegistration().getSubModel(next);
                    if (resource.hasChild(element) || (nr != null && nr.isRemote())) {
                        safeExecute(next, newRemaining, context, nr, ignoreMissing);
                    }
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
    }

    static class RegistrationAddressResolver implements OperationStepHandler {

        private final ModelNode operation;
        private final ModelNode result;
        private final OperationStepHandler handler; // handler bypassing further wildcard resolution
        private final boolean checkAlias;

        RegistrationAddressResolver(final ModelNode operation, final ModelNode result, final boolean checkAlias, final OperationStepHandler delegate) {
            this.operation = operation;
            this.result = result;
            this.checkAlias = checkAlias;
            this.handler = delegate;
        }

        @Override
        public void execute(final OperationContext context, final ModelNode ignored) throws OperationFailedException {
            final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
            final PathAddress aliasAddr = checkAlias ? WildcardReadResourceDescriptionAddressHack.detachAliasAddress(context, operation) : null;
            execute(aliasAddr == null ? address : aliasAddr, PathAddress.EMPTY_ADDRESS, context);
        }

        void execute(final PathAddress address, PathAddress base, final OperationContext context) {
            final PathAddress current = address.subAddress(base.size());
            final Iterator<PathElement> iterator = current.iterator();
            if (iterator.hasNext()) {
                final PathElement element = iterator.next();
                if (element.isMultiTarget()) {
                    final Set<PathElement> children = context.getResourceRegistration().getChildAddresses(base);
                    if (children == null || children.isEmpty()) {
                        return;
                    }
                    final String childType = element.getKey().equals("*") ? null : element.getKey();
                    for (final PathElement path : children) {
                        if (childType != null && !childType.equals(path.getKey())) {
                            continue;
                        }
                        execute(address, base.append(path), context);
                    }
                } else {
                    execute(address, base.append(element), context);
                }
            } else {
                //final String operationName = operation.require(OP).asString();
                final ModelNode newOp = operation.clone();
                newOp.get(OP_ADDR).set(base.toModelNode());

                final ModelNode result = this.result.add();
                result.get(OP_ADDR).set(base.toModelNode());
                context.addStep(result, newOp, handler, OperationContext.Stage.MODEL, true);
            }
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

        Map<String, Set<String>> result = new HashMap<String, Set<String>>();
        Set<PathElement> elements = registry.getChildAddresses(PathAddress.EMPTY_ADDRESS);
        for (PathElement element : elements) {
            String childType = element.getKey();
            if (validChildType != null && !validChildType.equals(childType)) {
                continue;
            }
            final ImmutableManagementResourceRegistration childRegistration = registry.getSubModel(PathAddress.pathAddress(element));
            final AliasEntry aliasEntry = childRegistration.getAliasEntry();

            Set<String> set = result.get(childType);
            if (set == null) {
                set = new LinkedHashSet<String>();
                result.put(childType, set);
            }

            if (aliasEntry == null) {
                if (resource != null && resource.hasChildren(childType)) {
                    Set<String> childNames = resource.getChildrenNames(childType);
                    if (element.isWildcard()) {
                        set.addAll(childNames);
                    } else if (childNames.contains(element.getValue())) {
                        set.add(element.getValue());
                    }
                }
            } else {
                PathAddress target = aliasEntry.convertToTargetAddress(addr.append(element));
                PathAddress targetParent = target.subAddress(0, target.size() - 1);
                Resource parentResource = context.readResourceFromRoot(targetParent, false);
                if (parentResource != null) {
                    PathElement targetElement = target.getLastElement();
                    if (targetElement.isWildcard()) {
                        set.addAll(parentResource.getChildrenNames(targetElement.getKey()));
                    } else if (parentResource.hasChild(targetElement)) {
                        set.add(element.getValue());
                    }
                }
            }
            if (!element.isWildcard()) {
                ImmutableManagementResourceRegistration childReg = registry.getSubModel(PathAddress.pathAddress(element));
                if (childReg != null && childReg.isRemote()) {
                    set.add(element.getValue());
                }
            }
        }

        // WFLY-3306 Ensure we have an entry for any valid child type
        for (String type : registry.getChildNames(PathAddress.EMPTY_ADDRESS)) {
            if ((validChildType == null || validChildType.equals(type))
                && !result.containsKey(type)) {
                result.put(type, Collections.<String>emptySet());
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

    static boolean getRecursive(OperationContext context, ModelNode op) throws OperationFailedException
    {
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

    static void setNextRecursive(OperationContext context, ModelNode op, ModelNode nextOp) throws OperationFailedException
    {
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


}
