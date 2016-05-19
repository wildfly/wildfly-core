/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.CHILD_TYPE;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.INCLUDE_SINGLETONS;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.validation.ParametersValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.operations.global.GlobalOperationHandlers.AbstractMultiTargetHandler} querying the children names for a given "child-type".
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ReadChildrenNamesHandler extends GlobalOperationHandlers.AbstractMultiTargetHandler {

    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(READ_CHILDREN_NAMES_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(CHILD_TYPE, INCLUDE_SINGLETONS)
            .setReadOnly()
            .setRuntimeOnly()
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.STRING)
            .build();

    static OperationStepHandler INSTANCE = new ReadChildrenNamesHandler();

    private final ParametersValidator validator = new ParametersValidator() {
        @Override
        public void validate(final ModelNode operation) throws OperationFailedException {
            if (!operation.hasDefined(ModelDescriptionConstants.CHILD_TYPE)) {
                throw ControllerLogger.ROOT_LOGGER.nullNotAllowed(ModelDescriptionConstants.CHILD_TYPE);
            }
            super.validate(operation);
        }
    };

    private ReadChildrenNamesHandler() {
        validator.registerValidator(GlobalOperationAttributes.CHILD_TYPE.getName(), new StringLengthValidator(1));
    }

    @Override
    public void doExecute(final OperationContext context, final ModelNode operation, final FilteredData filteredData, boolean ignoreMissingResource) throws OperationFailedException {
        doExecuteInternal(context, operation, filteredData);
    }

    private void doExecuteInternal(final OperationContext context, final ModelNode operation, final FilteredData filteredData) throws OperationFailedException {
        validator.validate(operation);
        final PathAddress address = context.getCurrentAddress();
        final String childType = CHILD_TYPE.resolveModelAttribute(context, operation).asString();

        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS, false);
        ImmutableManagementResourceRegistration registry = context.getResourceRegistration();
        Map<String, Set<String>> childAddresses = GlobalOperationHandlers.getChildAddresses(context, address, registry, resource, childType);
        Set<String> childNames = childAddresses.get(childType);
        if (childNames == null) {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.unknownChildType(childType));
        }
        final boolean singletons = INCLUDE_SINGLETONS.resolveModelAttribute(context, operation).asBoolean(false);
        if (singletons && isSingletonResource(registry, childType)) {
            Set<PathElement> childTypes = registry.getChildAddresses(PathAddress.EMPTY_ADDRESS);
            for (PathElement child : childTypes) {
                if (childType.equals(child.getKey())) {
                    childNames.add(child.getValue());
                }
            }
        }
        // Sort the result
        childNames = new TreeSet<>(childNames);
        ModelNode result = context.getResult();
        result.setEmptyList();
        PathAddress childAddress = address.append(PathElement.pathElement(childType));
        ModelNode op = Util.createEmptyOperation(READ_RESOURCE_OPERATION, childAddress);
        op.get(OPERATION_HEADERS).set(operation.get(OPERATION_HEADERS));
        ModelNode opAddr = op.get(OP_ADDR);
        ModelNode childProperty = opAddr.require(address.size());
        Set<Action.ActionEffect> actionEffects = EnumSet.of(Action.ActionEffect.ADDRESS);

        final FilteredData fd = filteredData == null ? new FilteredData(address) : filteredData;

        for (String childName : childNames) {
            childProperty.set(childType, new ModelNode(childName));

            AuthorizationResult.Decision ar = context.authorize(op, actionEffects).getDecision();
            if (ar == AuthorizationResult.Decision.PERMIT) {
                result.add(childName);
            } else {
                fd.addAccessRestrictedResource(childAddress);
            }
        }

        if (fd.hasFilteredData()) {
            context.getResponseHeaders().get(ACCESS_CONTROL).set(fd.toModelNode());
        }
    }

    private boolean isSingletonResource(final ImmutableManagementResourceRegistration registry, final String key) {
        return registry.getSubModel(PathAddress.pathAddress(PathElement.pathElement(key))) == null;
    }
}
