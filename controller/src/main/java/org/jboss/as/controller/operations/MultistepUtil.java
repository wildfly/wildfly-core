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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
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
     * Adds a step to the given {@link OperationContext} for each operation included in the given {@code operations} list, either
     * using for each step a response node provided in the {@code responses} list, or if the {@code responses} list is empty,
     * creating them and storing them in the {@code responses} list. The response objects are not tied into the overall response
     * to the operation associated with {@code context}. It is the responsibility of the caller to do that.
     *
     * @param context the {@code OperationContext}. Cannot be {@code null}
     * @param operations the list of operations, each element of which must be a proper OBJECT type model node with a structure describing an operation
     * @param responses  a list of response nodes to use for each operation, each element of which corresponds to the operation in the equivalent position
     *         in the {@code operations} list. Cannot be {@code null} but may be empty in which case this method will
     *                   create the response nodes and add them to this list.
     * @throws OperationFailedException if there is a problem registering a step for any of the operations
     */
    public static void recordOperationSteps(final OperationContext context, final List<ModelNode> operations,
                                                       final List<ModelNode> responses) throws OperationFailedException {
        assert responses.isEmpty() || operations.size() == responses.size();

        boolean responsesProvided = !responses.isEmpty();

        LinkedHashMap<Integer, ModelNode> operationMap = new LinkedHashMap<>();
        Map<Integer, ModelNode> responseMap  = new LinkedHashMap<>();
        int i = 0;
        for (ModelNode op : operations) {
            operationMap.put(i, op);
            if (responsesProvided) {
                ModelNode response = responses.get(i);
                assert response != null : "No response provided for " + i;
                responseMap.put(i, response);
            }
            i++;
        }
        recordOperationSteps(context, operationMap, responseMap, OperationHandlerResolver.DEFAULT, false);

        if (!responsesProvided) {
            for (ModelNode response : responseMap.values()) {
                responses.add(response);
            }
        }
    }

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
    public static <T> void recordOperationSteps(final OperationContext context, final Map<T, ModelNode> operations,
                                                                       final Map<T, ModelNode> responses) throws OperationFailedException {
        recordOperationSteps(context, operations, responses, OperationHandlerResolver.DEFAULT, false);
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
     *
     * @throws OperationFailedException  if there is a problem registering a step for any of the operations
     *
     */
    public static <T> void recordOperationSteps(final OperationContext context,
                                                final Map<T, ModelNode> operations,
                                                final Map<T, ModelNode> responses,
                                                final OperationHandlerResolver handlerResolver,
                                                final boolean adjustAddresses) throws OperationFailedException {

        assert responses != null;
        assert responses.isEmpty() || operations.size() == responses.size();

        boolean responsesProvided = !responses.isEmpty();

        PathAddress currentAddress = adjustAddresses ? context.getCurrentAddress() : null;

        List<OpData> opdatas = new ArrayList<>();
        for (Map.Entry<T, ModelNode> entry : operations.entrySet()) {
            ModelNode response = responsesProvided ? responses.get(entry.getKey()) : new ModelNode();
            assert  response != null : "No response provided for " + entry.getValue();

            ModelNode op = entry.getValue();

            PathAddress stepAddress = PathAddress.pathAddress(op.get(OP_ADDR));

            if (adjustAddresses) {
                stepAddress = currentAddress.append(stepAddress);
                op.get(OP_ADDR).set(stepAddress.toModelNode());
            }

            OpData opData = getOpData(context, op, response, stepAddress, handlerResolver);
            // Reverse the order for addition to the context
            opdatas.add(0, opData);

            if (!responsesProvided) {
                responses.put(entry.getKey(), response);
            }
        }

        for (OpData opData : opdatas) {
            context.addModelStep(opData.response, opData.operation, opData.definition, opData.handler, true);
        }
    }

    private static OpData getOpData(OperationContext context, ModelNode subOperation, ModelNode response, PathAddress stepAddress,
                                               OperationHandlerResolver handlerResolver) throws OperationFailedException {
        ImmutableManagementResourceRegistration registry = context.getRootResourceRegistration();
        String stepOpName = subOperation.require(OP).asString();
        OperationEntry operationEntry = registry.getOperationEntry(stepAddress, stepOpName);
        if (operationEntry == null) {
            ImmutableManagementResourceRegistration child = registry.getSubModel(stepAddress);
            if (child == null) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.noSuchResourceType(stepAddress));
            } else {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.noHandlerForOperation(stepOpName, stepAddress));
            }
        }
        OperationStepHandler osh = handlerResolver.getOperationStepHandler(stepOpName, stepAddress, subOperation, operationEntry);
        return new OpData(subOperation, operationEntry.getOperationDefinition(), osh, response);
    }

    private static class OpData {
        private final ModelNode operation;
        private final OperationDefinition definition;
        private final OperationStepHandler handler;
        private final ModelNode response;

        private OpData(ModelNode operation, OperationDefinition definition, OperationStepHandler handler, ModelNode response) {
            this.definition = definition;
            this.operation = operation;
            this.handler = handler;
            this.response = response;
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
