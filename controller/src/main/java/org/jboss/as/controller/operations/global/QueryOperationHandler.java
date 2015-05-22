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

package org.jboss.as.controller.operations.global;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.MapAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PrimitiveListAttributeDefinition;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;

/**
 *
 * @author Heiko Braun (c) 2011 Red Hat Inc.
 */
public final class QueryOperationHandler extends GlobalOperationHandlers.AbstractMultiTargetHandler {

    public static final QueryOperationHandler INSTANCE = new QueryOperationHandler();

    public enum Operator {
        AND, OR
    }

    public static final PropertiesAttributeDefinition WHERE_ATT = new PropertiesAttributeDefinition.Builder(ModelDescriptionConstants.WHERE, true)
            .setCorrector(MapAttributeDefinition.LIST_TO_MAP_CORRECTOR)
            .setValidator(new StringLengthValidator(1, true, true))
            .build();

    private static final AttributeDefinition OPERATOR_ATT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.OPERATOR, ModelType.STRING)
            .setAllowNull(true)
            .setDefaultValue(new ModelNode().set(Operator.AND.name()))
            .setValidator(EnumValidator.create(Operator.class, true, false))
            .build();

    private static final AttributeDefinition SELECT_ATT = new PrimitiveListAttributeDefinition.Builder(ModelDescriptionConstants.SELECT, ModelType.STRING)
            .setAllowNull(true)
            .build();


    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.QUERY, ControllerResolver.getResolver("global"))
            .addParameter(SELECT_ATT)
            .addParameter(WHERE_ATT)
                    //.addParameter(OPERATOR_ATT) // TODO for now it's implicitly Operator.AND
            .setReplyType(ModelType.LIST).setReplyValueType(ModelType.OBJECT)
            .setReadOnly()
            .build();

    private QueryOperationHandler() {
        super(null, true, new GlobalOperationHandlers.FilterPredicate() {
            @Override
            public boolean appliesTo(ModelNode item) {
                return !item.hasDefined(RESULT);
            }
        });
    }

    @Override
    void doExecute(final OperationContext parentContext, ModelNode operation, FilteredData filteredData, boolean ignoreMissingResources) throws OperationFailedException {

        WHERE_ATT.validateOperation(operation);
        OPERATOR_ATT.validateOperation(operation);
        SELECT_ATT.validateOperation(operation);


        ImmutableManagementResourceRegistration mrr = parentContext.getResourceRegistration();
        final OperationStepHandler readResourceHandler = mrr.getOperationHandler(
                PathAddress.EMPTY_ADDRESS,
                ModelDescriptionConstants.READ_RESOURCE_OPERATION
        );

        final ModelNode readResourceOp = new ModelNode();
        readResourceOp.get(ADDRESS).set(operation.get(ADDRESS));
        readResourceOp.get(OP).set(READ_RESOURCE_OPERATION);
        readResourceOp.get(INCLUDE_RUNTIME).set(true);

        // filter/reduce phase
        parentContext.addStep(operation, FilterReduceHandler.INSTANCE, OperationContext.Stage.MODEL);

        // map phase
        parentContext.addStep(readResourceOp, readResourceHandler, OperationContext.Stage.MODEL);

    }

    static class FilterReduceHandler implements OperationStepHandler {

        static final FilterReduceHandler INSTANCE = new FilterReduceHandler();
        private static final String UNDEFINED = "undefined";

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

            context.completeStep(
                    new OperationContext.ResultHandler() {
                        @Override
                        public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                            if(operation.hasDefined(WHERE)) {

                                try {
                                    Operator operator = operation.hasDefined(OPERATOR) ? Operator.valueOf(operation.get(OPERATOR).asString()) : Operator.AND;
                                    boolean matches = matchesFilter(context.getResult(), operation.get(WHERE), operator);
                                    // if the filter doesn't match we remove it from the response
                                    if(!matches)
                                        context.getResult().set(new ModelNode());
                                } catch (Throwable t) {
                                    context.getFailureDescription().set(t.getMessage());
                                    context.setRollbackOnly();
                                }

                            }

                            if( operation.hasDefined(SELECT)
                                    && context.hasResult()
                                    && context.getResult().isDefined() // exclude empty model nodes
                                    ){
                                try {
                                    ModelNode reduced = reduce(context.getResult(), operation.get(SELECT));
                                    context.getResult().set(reduced);
                                } catch (OperationFailedException e) {
                                    context.getFailureDescription().set(e.getMessage());
                                    context.setRollbackOnly();
                                }
                            }

                        }
                    }
            );

        }

        private static boolean matchesFilter(final ModelNode resource, final ModelNode filter, final Operator operator) {
            boolean isMatching = false;
            List<Property> filterProperties = filter.asPropertyList();
            List<Boolean> matches = new ArrayList<>(filterProperties.size());

            for (Property property : filterProperties) {

                final String filterName = property.getName();
                final ModelNode filterValue = property.getValue();

                boolean isEqual = false;

                if(!filterValue.isDefined() || filterValue.asString().equals(UNDEFINED))  {
                    // query for undefined attributes
                    isEqual = !resource.get(filterName).isDefined();
                }  else {

                    final ModelType targetValueType = resource.get(filterName).getType();

                    try {
                        // query for attribute values (throws exception when types don't match)
                        switch (targetValueType) {
                            case BOOLEAN:
                                isEqual = filterValue.asBoolean() == resource.get(filterName).asBoolean();
                                break;
                            case LONG:
                                isEqual = filterValue.asLong() == resource.get(filterName).asLong();
                                break;
                            case INT:
                                isEqual = filterValue.asInt() == resource.get(filterName).asInt();
                                break;
                            case DOUBLE:
                                isEqual = filterValue.asDouble() == resource.get(filterName).asDouble();
                                break;
                            default:
                                isEqual = filterValue.equals(resource.get(filterName));
                        }
                    } catch (IllegalArgumentException e) {
                        throw ControllerLogger.MGMT_OP_LOGGER.validationFailedCouldNotConvertParamToType(filterName, targetValueType, "");
                    }

                }

                if(isEqual) {
                    matches.add(resource.get(filterName).equals(filterValue));
                }

            }

            if (Operator.AND.equals(operator)) {
                // all matches must be true
                isMatching = matches.size() == filterProperties.size();

            } else if(Operator.OR.equals(operator)){
                // at least one match must be true
                for (Boolean match : matches) {
                    if (match) {
                        isMatching = true;
                        break;
                    }
                }
            }
            else {
                throw new IllegalArgumentException(
                        ControllerLogger.MGMT_OP_LOGGER.invalidValue(
                                operator.toString(),
                                OPERATOR,
                                Arrays.asList(Operator.values())
                        )
                );
            }


            return isMatching;
        }

        private ModelNode reduce(final ModelNode payload, final ModelNode attributes) throws OperationFailedException {

            ModelNode outcome = new ModelNode();

            for (ModelNode attribute : attributes.asList()) {
                String name = attribute.asString();
                ModelNode value = payload.get(name);
                if (value.isDefined()) {
                    outcome.get(name).set(value);
                }
            }

            return outcome;
        }
    }
}
