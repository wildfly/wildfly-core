/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CALLER_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USER;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;

/**
 * Utility code related to multistep operations.
 *
 * @author Brian Stansberry
 */
public final class MultistepUtil {

    /** Prevent instantiation */
    private MultistepUtil() {}

    /**
     * Adds a step to the given {@link OperationContext} for each operation included in the given map, either
     * using for each step a response node provided in the {@code responses} map, or if the {@code responses} map is empty,
     * creating them and storing them in the {@code responses} map. The response objects are not tied into the overall response
     * to the operation associated with {@code context}. It is the responsibility of the caller to do that.
     * <p>
     * <strong>NOTE:</strong> The given {@code operations} map must provide an iterator over its entry set that provides the entries in the
     * order in which the operations should execute. A {@link LinkedHashMap} is the typical choice.
     *
     * @param context the {@code OperationContext}. Cannot be {@code null}
     * @param operations the operations, each value of which must be a proper OBJECT type model node with a structure describing an operation.
     * @param responses  a map of the response nodes, the keys for which match the keys in the {@code operations} param.
     *                   Cannot be {@code null} but may be empty in which case this method will
     *                   create the response nodes and store them in this map.
     * @param <T> the type of the keys in the maps
     * @throws OperationFailedException  if there is a problem registering a step for any of the operations
     */
    @SuppressWarnings("unused")
    public static <T> void recordOperationSteps(final OperationContext context, final Map<T, ModelNode> operations,
                                                final Map<T, ModelNode> responses) throws OperationFailedException {
        recordOperationSteps(context, operations, responses, OperationHandlerResolver.DEFAULT, false, true);
    }

    /**
     * This is a specialized version of the other variant of this method that allows a pluggable strategy
     * for resolving the {@link OperationStepHandler} to use for the added steps. It is not expected to
     * be needed by users outside the WildFly Core kernel.
     *
     * @param <T> the type of the keys in the maps
     * @param context the {@code OperationContext}. Cannot be {@code null}
     * @param operations the operations, each value of which must be a proper OBJECT type model node with a structure describing an operation.
     * @param responses  a map of the response nodes, the keys for which match the keys in the {@code operations} param.
     *                   Cannot be {@code null} but may be empty in which case this method will
     *                   create the response nodes and store them in this map.
     * @param handlerResolver an object that can provide the {@code OperationStepHandler} to use for the operation
     * @param adjustAddresses {@code true} if the address of each operation should be adjusted to become a child of the context's
     *                                {@link OperationContext#getCurrentAddress() current address}
     * @param rejectPrivateOperations {@code true} if an {@link OperationFailedException} should be thrown if the
     *                                            {@link OperationEntry} for any of the {@code operations} is
     *                                            {@link OperationEntry.EntryType#PRIVATE}
     *
     * @throws OperationFailedException  if there is a problem registering a step for any of the operations
     *
     */
    public static <T> void recordOperationSteps(final OperationContext context,
        final Map<T, ModelNode> operations,
        final Map<T, ModelNode> responses,
        final OperationHandlerResolver handlerResolver,
        final boolean adjustAddresses,
        final boolean rejectPrivateOperations) throws OperationFailedException {

        assert responses != null;
        assert responses.isEmpty() || operations.size() == responses.size();

        boolean responsesProvided = !responses.isEmpty();

        PathAddress currentAddress = adjustAddresses ? context.getCurrentAddress() : null;

        List<OpData> opdatas = new ArrayList<>();
        OpData previousOpData = null;
        for (Map.Entry<T, ModelNode> entry : operations.entrySet()) {
            ModelNode response = responsesProvided ? responses.get(entry.getKey()) : new ModelNode();
            assert  response != null : "No response provided for " + entry.getValue();

            ModelNode op = entry.getValue();

            PathAddress stepAddress = PathAddress.pathAddress(op.get(OP_ADDR));

            if (adjustAddresses) {
                stepAddress = currentAddress.append(stepAddress);
                op.get(OP_ADDR).set(stepAddress.toModelNode());
            }

            // If a previous op turned on deferred resolution (i.e. an extension add) or
            // turned on requiring re-resolution (i.e. an extension remove) then pass that on
            boolean allowDeferredResolution = previousOpData != null && previousOpData.allowDeferredResolution;
            boolean requireReResolution = previousOpData != null && previousOpData.requireReResolution;

            OpData opData = getOpData(context, op, response, stepAddress, handlerResolver, rejectPrivateOperations,
                    allowDeferredResolution, requireReResolution);
            // Reverse the order for addition to the context
            opdatas.add(0, opData);
            previousOpData = opData;

            if (!responsesProvided) {
                responses.put(entry.getKey(), response);
            }
        }

        for (OpData opData : opdatas) {
            context.addModelStep(opData.response, opData.operation, opData.definition, opData.handler, true);
        }
    }

    private static OpData getOpData(OperationContext context, ModelNode subOperation, ModelNode response, PathAddress stepAddress,
                                    OperationHandlerResolver handlerResolver, boolean rejectPrivateOperations,
                                    boolean allowDeferredResolution,
                                    boolean requireReResolution) throws OperationFailedException {
        ImmutableManagementResourceRegistration registry = context.getRootResourceRegistration();
        String stepOpName = subOperation.require(OP).asString();
        OperationDefinition operationDefinition;
        OperationStepHandler osh;
        OperationEntry operationEntry = registry.getOperationEntry(stepAddress, stepOpName);
        ControllerLogger.MGMT_OP_LOGGER.tracef("Getting OpDate for op %s at %s with deferred resolution %s", stepOpName, stepAddress, allowDeferredResolution);
        if (operationEntry == null) {
            ImmutableManagementResourceRegistration child = registry.getSubModel(stepAddress);
            if (child == null) {
                if (allowDeferredResolution && isSubsystem(stepAddress)) {
                    // No MRR now but perhaps there will be later when this op actually executes.
                    // So set things up to try then.
                    ControllerLogger.MGMT_OP_LOGGER.tracef("Deferred resolution for op %s at %s", stepOpName, stepAddress);
                    osh = new DeferredResolutionHandler(subOperation, response, stepAddress, handlerResolver, rejectPrivateOperations);
                    // Supply a dummy definition. For no well thought out reason I'm using the original op name
                    // and thus a dynamic op definition. Probably could be an OD constant with a fixed arbitary name
                    // like "deferred-op-resolution"
                    operationDefinition = SimpleOperationDefinitionBuilder.of(stepOpName, NonResolvingResourceDescriptionResolver.INSTANCE).build();
                } else {
                    ControllerLogger.MGMT_OP_LOGGER.tracef("No resource registration for op %s at %s", stepOpName, stepAddress);
                    throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.noSuchResourceType(stepAddress));
                }
            } else {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.noHandlerForOperation(stepOpName, stepAddress));
            }
        } else {
            if (operationEntry.getType() == OperationEntry.EntryType.PRIVATE
                    && (rejectPrivateOperations
                        || (subOperation.hasDefined(OPERATION_HEADERS, CALLER_TYPE)
                        && USER.equals(subOperation.get(OPERATION_HEADERS, CALLER_TYPE).asString())))) {
                // Not allowed; respond as if there is no such operation
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.noHandlerForOperation(stepOpName, stepAddress));
            }
            operationDefinition = operationEntry.getOperationDefinition();
            if (requireReResolution && isSubsystem(stepAddress)) {
                // A previous step will remove an extension before this subsystem step runs.
                // Get the currently registered handler before executing for real

                ControllerLogger.MGMT_OP_LOGGER.tracef("Re-resolution for op %s at %s", stepOpName, stepAddress);
                osh = new DeferredResolutionHandler(subOperation, response, stepAddress, handlerResolver, rejectPrivateOperations);
            } else {
                osh = handlerResolver.getOperationStepHandler(stepOpName, stepAddress, subOperation, operationEntry);
            }
        }

        // If this step is adding or removing an extension, subsequent steps need to deal with the possibility
        // that MRRs are coming/going. So track that. If we're already tracking, keep doing it.
        boolean allowDeferred = allowDeferredResolution || (ADD.equals(stepOpName) && isExtensionAddress(stepAddress));
        boolean requireReRes = requireReResolution || (REMOVE.equals(stepOpName) && isExtensionAddress(stepAddress));

        return new OpData(subOperation, operationDefinition, osh, response, allowDeferred, requireReRes);
    }

    private static boolean isSubsystem(PathAddress stepAddress) {
        int size = stepAddress.size();
        if (size == 1) {
            return SUBSYSTEM.equals(stepAddress.getElement(0).getKey());
        } else if (size > 1) {
            String firstKey = stepAddress.getElement(0).getKey();
            return SUBSYSTEM.equals(stepAddress.getElement(1).getKey())
                    && (PROFILE.equals(firstKey) || HOST.equals(firstKey));
        }
        return false;
    }

    private static boolean isExtensionAddress(PathAddress address) {
        return (address.size() == 1 && EXTENSION.equals(address.getElement(0).getKey())
                    || (address.size() == 2 && EXTENSION.equals(address.getElement(1).getKey()) && HOST.equals(address.getElement(0).getKey())));
    }

    private static class OpData {
        private final ModelNode operation;
        private final OperationDefinition definition;
        private final OperationStepHandler handler;
        private final ModelNode response;
        private final boolean allowDeferredResolution;
        private final boolean requireReResolution;

        private OpData(ModelNode operation, OperationDefinition definition, OperationStepHandler handler, ModelNode response,
                       boolean allowDeferredResolution, boolean requireReResolution) {
            this.definition = definition;
            this.operation = operation;
            this.handler = handler;
            this.response = response;
            this.allowDeferredResolution = allowDeferredResolution;
            this.requireReResolution = requireReResolution;
        }
    }

    /**
     * Store the params from a call to {@code getOpData} in an OSH that, when it
     * executes, tries again to getOpData and if successful adds the desired step.
     */
    private static class DeferredResolutionHandler implements OperationStepHandler {

        private final ModelNode deferredOperation;
        private final ModelNode deferredResponse;
        private final PathAddress deferredAddress;
        private final OperationHandlerResolver handlerResolver;
        private final boolean rejectPrivateOperations;

        private DeferredResolutionHandler(ModelNode deferredOperation,
                                          ModelNode deferredResponse,
                                          PathAddress deferredAddress,
                                          OperationHandlerResolver handlerResolver,
                                          boolean rejectPrivateOperations) {
            this.deferredOperation = deferredOperation;
            this.deferredResponse = deferredResponse;
            this.deferredAddress = deferredAddress;
            this.handlerResolver = handlerResolver;
            this.rejectPrivateOperations = rejectPrivateOperations;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            // Try again to get the OpData but this time allowDeferredResolution == false so it will
            // fail if the MRR is still not available
            OpData opData = getOpData(context, deferredOperation, deferredResponse, deferredAddress, handlerResolver,
                    rejectPrivateOperations, false, false);
            context.addModelStep(opData.response, opData.operation, opData.definition, opData.handler, true);
        }
    }

    /**
     * Resolves an {@link OperationStepHandler} to use given a set of inputs.
     */
    public interface OperationHandlerResolver {

        OperationHandlerResolver DEFAULT = new OperationHandlerResolver() {};

        default OperationStepHandler getOperationStepHandler(String operationName, PathAddress address, ModelNode operation, OperationEntry operationEntry) {
            return operationEntry.getOperationHandler();
        }

    }
}
