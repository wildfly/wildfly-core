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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATOR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ExpressionResolver;
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
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

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
            .setAllowExpression(true)
            .build();

    private static final AttributeDefinition OPERATOR_ATT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.OPERATOR, ModelType.STRING)
            .setRequired(false)
            .setDefaultValue(new ModelNode().set(Operator.AND.name()))
            .setValidator(EnumValidator.create(Operator.class, true, false))
            .build();

    private static final AttributeDefinition SELECT_ATT = new PrimitiveListAttributeDefinition.Builder(ModelDescriptionConstants.SELECT, ModelType.STRING)
            .setRequired(false)
            .build();


    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.QUERY, ControllerResolver.getResolver("global"))
            .addParameter(SELECT_ATT)
            .addParameter(WHERE_ATT)
                    //.addParameter(OPERATOR_ATT) // TODO for now it's implicitly Operator.AND
            .setReplyType(ModelType.LIST).setReplyValueType(ModelType.OBJECT)
            .setReadOnly()
            .build();

    private QueryOperationHandler() {
        super(null, true, item -> !item.hasDefined(RESULT));
    }

    @Override
    void doExecute(final OperationContext parentContext, ModelNode operation, FilteredData filteredData, boolean ignoreMissingResources) throws OperationFailedException {

        final ModelNode where = WHERE_ATT.validateOperation(operation);
        // Use resolveModelAttribute for OPERATOR_ATT to pull out the default value
        final Operator operator = Operator.valueOf(OPERATOR_ATT.resolveModelAttribute(parentContext, operation).asString());
        final ModelNode select = SELECT_ATT.validateOperation(operation);


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
        parentContext.addStep(operation, new FilterReduceHandler(where, operator, select), OperationContext.Stage.MODEL);

        // map phase
        parentContext.addStep(readResourceOp, readResourceHandler, OperationContext.Stage.MODEL);

    }

    static class FilterReduceHandler implements OperationStepHandler {

        private static final String UNDEFINED = "undefined";

        private final ModelNode filter;
        private final Operator operator;
        private final ModelNode select;

        FilterReduceHandler(final ModelNode filter, final Operator operator, final ModelNode select) {
            this.filter = filter;
            this.operator = operator;
            this.select = select;
        }


        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

            context.completeStep(
                    new OperationContext.ResultHandler() {
                        @Override
                        public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                            if (context.hasResult() || filter.isDefined()) {
                                ModelNode result = context.getResult();
                                try {
                                    filterAndReduce(filter, operator, select, result);
                                } catch (OperationFailedException e) {
                                    if (!context.hasFailureDescription()) {
                                        context.getFailureDescription().set(e.getMessage());
                                    } // else there already was a failure; don't overwrite its message
                                }
                            } // else there is no filter to run, and 'reduce' will only run when
                              // the filtered result is defined, so no point calling filterAndReduce

                        }
                    }
            );

        }

        private static void filterAndReduce(final ModelNode filter, final Operator operator, final ModelNode select, final ModelNode result) throws OperationFailedException {

            assert result != null;

            if(filter.isDefined()) {
                boolean matches = matchesFilter(result, filter, operator);
                // if the filter doesn't match we remove it from the response
                if(!matches) {
                    result.set(new ModelNode());
                }
            }

            if( select.isDefined()
                    && result.isDefined() // exclude empty model nodes
                    ){
                ModelNode reduced = reduce(result, select);
                result.set(reduced);
            }

        }

        private static boolean matchesFilter(final ModelNode resource, final ModelNode filter, final Operator operator) throws OperationFailedException {
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
                        throw ControllerLogger.MGMT_OP_LOGGER.selectFailedCouldNotConvertAttributeToType(filterName, targetValueType);
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
                // This is just to catch programming errors where a new case isn't added above
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

        private static ModelNode reduce(final ModelNode payload, final ModelNode attributes) throws OperationFailedException {

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

    /**
     * Transformer for this operation for slave Host Controllers running versions prior to
     * WildFly Core 1.0 (i.e. AS 7, EAP 6 and WildFly 8).
     */
    public static final OperationTransformer TRANSFORMER = new OperationTransformer() {
        @Override
        public TransformedOperation transformOperation(TransformationContext context, PathAddress address, ModelNode operation) throws OperationFailedException {
            ModelNode transformedOp = Util.createEmptyOperation(READ_RESOURCE_OPERATION, address);
            transformedOp.get(INCLUDE_RUNTIME).set(true);
            ResultTransformer rt = new ResultTransformer(operation, address);
            return new TransformedOperation(transformedOp, rt);
        }
    };

    private static class ResultTransformer implements OperationResultTransformer {

        private final boolean multiTarget;
        private final ModelNode filter;
        private final Operator operator;
        private final ModelNode select;

        private ResultTransformer(ModelNode operation, PathAddress address) {
            this.multiTarget = address.isMultiTarget();
            try {
                this.filter = WHERE_ATT.validateOperation(operation);
                this.select = SELECT_ATT.validateOperation(operation);
                // Use resolveModelAttribute for OPERATOR_ATT to pull out the default value
                this.operator = Operator.valueOf(OPERATOR_ATT.resolveModelAttribute(ExpressionResolver.SIMPLE, operation).asString());
            } catch (OperationFailedException e) {
                // the validateOperation calls above would have already been invoked in QueryOperationHandler.execute
                // so this shouldn't happen
                throw new IllegalStateException(e);
            }
        }

        @Override
        public ModelNode transformResult(ModelNode response) {
            ControllerLogger.MGMT_OP_LOGGER.tracef("Transforming response %s", response);
            ModelNode transformedResponse;
            if (response.hasDefined(RESULT)) {
                transformedResponse = response.clone();
                if (multiTarget) {
                    ModelNode transformedResults = transformedResponse.get(RESULT);
                    for (int i = 0; i < transformedResults.asInt(); i++) {
                        ModelNode item = transformedResults.get(i);
                        transformResponseItem(item);
                        if (!item.hasDefined(RESULT)) {
                            // An undefined result means this one was filtered
                            // If "query" had actually run on the remote slave, this
                            // would not be in response, so we remove it now
                            transformedResults.remove(i);
                            i--;
                            ControllerLogger.MGMT_OP_LOGGER.tracef("Removed response item %s", item);
                        }
                    }
                } else {
                    transformResponseItem(transformedResponse);
                }
                ControllerLogger.MGMT_OP_LOGGER.tracef("Transformed response is %s", transformedResponse);
            } else {
                // no transformation is needed
                transformedResponse = response;
            }
            return transformedResponse;
        }

        private void transformResponseItem(ModelNode responseItem) {
            if (responseItem.hasDefined(RESULT) || filter.isDefined()) {
                ModelNode result = responseItem.get(RESULT);
                try {
                    FilterReduceHandler.filterAndReduce(filter, operator, select, result);
                    ControllerLogger.MGMT_OP_LOGGER.tracef("Transformed response item to %s", responseItem);
                } catch (OperationFailedException e) {
                    if (!responseItem.hasDefined(FAILURE_DESCRIPTION)) {
                        responseItem.get(FAILURE_DESCRIPTION).set(e.getMessage());
                    }  // else there already was a failure; don't overwrite its message
                }
            } // else there is no filter to run, and 'reduce' will only run when
              // the filtered result is defined, so no point calling filterAndReduce
        }
    }
}
