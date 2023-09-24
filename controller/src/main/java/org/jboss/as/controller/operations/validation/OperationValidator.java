/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.validation;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ALTERNATIVES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX_LENGTH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MIN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MIN_LENGTH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUEST_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUIRED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE_TYPE;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.OperationEntry.EntryType;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Validates operations against the model controllers description providers
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class OperationValidator {

    private final ExpressionResolver expressionResolver;
    private final ImmutableManagementResourceRegistration root;
    private final boolean validateDescriptions;
    private final boolean includeOperationInError;
    private final boolean exitOnError;

    public OperationValidator(final ImmutableManagementResourceRegistration root) {
        this(ExpressionResolver.SIMPLE, root, true, true, true);
    }

    public OperationValidator(final ExpressionResolver expressionResolver, final ImmutableManagementResourceRegistration root, boolean validateDescriptions, boolean includeOperationInError, boolean exitOnError) {
        this.expressionResolver = expressionResolver;
        this.root = root;
        this.validateDescriptions = validateDescriptions;
        this.includeOperationInError = includeOperationInError;
        this.exitOnError = exitOnError;
    }

    /**
     * Validates operations against their description providers
     *
     * @param operations The operations to validate
     * @throws IllegalArgumentException if any operation is not valid
     */
    public void validateOperations(final List<ModelNode> operations) {
        if (operations == null) {
            return;
        }

        for (ModelNode operation : operations) {
            try {
                validateOperation(operation);
            } catch (RuntimeException e) {
                if (exitOnError) {
                    throw e;
                } else {
                    System.out.println("---- Operation validation error:");
                    System.out.println(e.getMessage());
                }

            }
        }
    }

    /**
     * Validates an operation against its description provider
     *
     * @param operation The operation to validate
     * @throws IllegalArgumentException if the operation is not valid
     */
    public void validateOperation(final ModelNode operation) {
        if (operation == null) {
            return;
        }
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        final String name = operation.get(OP).asString();

        OperationEntry entry = root.getOperationEntry(address, name);
        if (entry == null) {
            throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.noOperationEntry(name, address));
        }
        //noinspection ConstantConditions
        if (entry.getType() == EntryType.PRIVATE || entry.getFlags().contains(OperationEntry.Flag.HIDDEN)) {
            return;
        }
        if (entry.getOperationHandler() == null) {
            throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.noOperationHandler(name, address));
        }
        final DescriptionProvider provider = getDescriptionProvider(operation);
        final ModelNode description = provider.getModelDescription(null);

        final Map<String, ModelNode> describedProperties = getDescribedRequestProperties(operation, description);
        final Map<String, ModelNode> actualParams = getActualRequestProperties(operation);

        checkActualOperationParamsAreDescribed(operation, describedProperties, actualParams);
        checkAllRequiredPropertiesArePresent(description, operation, describedProperties, actualParams);
        checkParameterTypes(description, operation, describedProperties, actualParams);

        //TODO check ranges
    }

    private Map<String, ModelNode> getDescribedRequestProperties(final ModelNode operation, final ModelNode description){
        final Map<String, ModelNode> requestProperties = new HashMap<>();
        if (description.hasDefined(REQUEST_PROPERTIES)) {
            for (String key : description.get(REQUEST_PROPERTIES).keys()) {
                ModelNode desc = description.get(REQUEST_PROPERTIES, key);
                if (!desc.isDefined()) {
                    throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.invalidDescriptionUndefinedRequestProperty(key, getPathAddress(operation), desc));
                }
                requestProperties.put(key, desc);
            }
        }
        return requestProperties;
    }

    private Map<String, ModelNode> getActualRequestProperties(final ModelNode operation) {
        final Map<String, ModelNode> requestProperties = new HashMap<>();
        for (String key : operation.keys()) {
            if (key.equals(OP) || key.equals(OP_ADDR) || key.equals(OPERATION_HEADERS)) {
                continue;
            }
            requestProperties.put(key, operation.get(key));
        }
        return requestProperties;
    }

    private void checkActualOperationParamsAreDescribed(final ModelNode operation, final Map<String, ModelNode> describedProperties, final Map<String, ModelNode> actualParams) {
        for (String paramName : actualParams.keySet()) {
            final ModelNode param = actualParams.get(paramName);
            if(! param.isDefined()) {
                continue;
            }
            if(param.getType() == ModelType.OBJECT && param.keys().isEmpty()) {
                return;
            }
            if (!describedProperties.containsKey(paramName)) {
                throw ControllerLogger.ROOT_LOGGER.validationFailedActualParameterNotDescribed(paramName, describedProperties.keySet(), formatOperationForMessage(operation));
            }
        }
    }

    private void checkAllRequiredPropertiesArePresent(final ModelNode description, final ModelNode operation, final Map<String, ModelNode> describedProperties, final Map<String, ModelNode> actualParams) {
        for (String paramName : describedProperties.keySet()) {
            final ModelNode described = describedProperties.get(paramName);
            final boolean required;
            if (described.hasDefined(REQUIRED)) {
                if (ModelType.BOOLEAN != described.get(REQUIRED).getType()) {
                    throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.invalidDescriptionRequiredFlagIsNotABoolean(paramName, getPathAddress(operation), description));
                    required = false;
                } else {
                    required = described.get(REQUIRED).asBoolean();
                }
            } else {
                required = true;
            }
            Collection<ModelNode> alternatives = null;
            if(described.hasDefined(ALTERNATIVES)) {
                alternatives = described.get(ALTERNATIVES).asList();
            }
            final boolean exist = actualParams.containsKey(paramName) && actualParams.get(paramName).isDefined();
            final String alternative = hasAlternative(actualParams.keySet(), alternatives);
            final boolean alternativeExist = alternative != null && actualParams.get(alternative).isDefined();
            if (required) {
                if(!exist && !alternativeExist) {
                    throw ControllerLogger.ROOT_LOGGER.validationFailedRequiredParameterNotPresent(paramName, formatOperationForMessage(operation));
                }
            }
            if(exist && alternativeExist) {
                throw ControllerLogger.ROOT_LOGGER.validationFailedRequiredParameterPresentAsWellAsAlternative(alternative, paramName, formatOperationForMessage(operation));
            }
        }
    }

    private void checkParameterTypes(final ModelNode description, final ModelNode operation, final Map<String, ModelNode> describedProperties, final Map<String, ModelNode> actualParams) {
        for (String paramName : actualParams.keySet()) {
            final ModelNode value = actualParams.get(paramName);
            if(!value.isDefined()) {
                continue;
            }
            if(value.getType() == ModelType.OBJECT && value.keys().isEmpty()) {
                return;
            }
            final ModelNode typeNode = describedProperties.get(paramName).get(TYPE);
            if (!typeNode.isDefined()) {
                throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.invalidDescriptionNoParamTypeInDescription(paramName, getPathAddress(operation), description));
                return;
            }
            final ModelType modelType;
            try {
                modelType = Enum.valueOf(ModelType.class, typeNode.asString());
            } catch (Exception e) {
                throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.invalidDescriptionInvalidParamTypeInDescription(paramName, getPathAddress(operation), description));
                return;
            }

            try {
                checkType(modelType, value);
            } catch (IllegalArgumentException e) {
                throw ControllerLogger.ROOT_LOGGER.validationFailedCouldNotConvertParamToType(paramName, modelType, formatOperationForMessage(operation));
            }
            checkRange(operation, description, paramName, modelType, describedProperties.get(paramName), value);
            checkList(operation, paramName, describedProperties.get(paramName), value);
        }
    }

    private void checkRange(final ModelNode operation, final ModelNode description, final String paramName, final ModelType modelType, final ModelNode describedProperty, final ModelNode value) {
        if (!value.isDefined() || value.getType() == ModelType.EXPRESSION) {
            return;
        }
        if (describedProperty.hasDefined(MIN)) {
            switch (modelType) {
                case BIG_DECIMAL: {
                    final BigDecimal min;
                    try {
                        min = describedProperty.get(MIN).asBigDecimal();
                    } catch (IllegalArgumentException e) {
                        throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.invalidDescriptionMinMaxForParameterHasWrongType(MIN, paramName, ModelType.BIG_DECIMAL, getPathAddress(operation), description));
                        return;
                    }
                    if (value.asBigDecimal().compareTo(min) < 0) {
                        throw ControllerLogger.ROOT_LOGGER.validationFailedValueIsSmallerThanMin(value.asBigDecimal(), paramName, min, formatOperationForMessage(operation));
                    }
                }
                break;
                case BIG_INTEGER: {
                    final BigInteger min;
                    try {
                        min = describedProperty.get(MIN).asBigInteger();
                    } catch (IllegalArgumentException e) {
                        throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.invalidDescriptionMinMaxForParameterHasWrongType(MIN, paramName, ModelType.BIG_INTEGER, getPathAddress(operation), description));
                        return;
                    }
                    if (value.asBigInteger().compareTo(min) < 0) {
                        throw ControllerLogger.ROOT_LOGGER.validationFailedValueIsSmallerThanMin(value.asBigInteger(), paramName, min, formatOperationForMessage(operation));
                    }
                }
                break;
                case DOUBLE: {
                    final double min;
                    try {
                        min = describedProperty.get(MIN).asDouble();
                    } catch (IllegalArgumentException e) {
                        throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.invalidDescriptionMinMaxForParameterHasWrongType(MIN, paramName, ModelType.DOUBLE, getPathAddress(operation), description));
                        return;
                    }
                    if (value.asDouble() < min) {
                        throw ControllerLogger.ROOT_LOGGER.validationFailedValueIsSmallerThanMin(value.asDouble(), paramName, min, formatOperationForMessage(operation));
                    }
                }
                break;
                case INT: {
                    final int min;
                    try {
                        min = describedProperty.get(MIN).asInt();
                    } catch (IllegalArgumentException e) {
                        throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.invalidDescriptionMinMaxForParameterHasWrongType(MIN, paramName, ModelType.INT, getPathAddress(operation), description));
                        return;
                    }
                    if (value.asInt() < min) {
                        throw ControllerLogger.ROOT_LOGGER.validationFailedValueIsSmallerThanMin(value.asInt(), paramName, min, formatOperationForMessage(operation));
                    }
                }
                break;
                case LONG: {
                    final long min;
                    try {
                        min = describedProperty.get(MIN).asLong();
                    } catch (IllegalArgumentException e) {
                        throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.invalidDescriptionMinMaxForParameterHasWrongType(MIN, paramName, ModelType.LONG, getPathAddress(operation), description));
                        return;
                    }
                    if (value.asLong() < describedProperty.get(MIN).asLong()) {
                        throw ControllerLogger.ROOT_LOGGER.validationFailedValueIsSmallerThanMin(value.asLong(), paramName, min, formatOperationForMessage(operation));
                    }
                }
                break;
            }
        }
        if (describedProperty.hasDefined(MAX)) {
            switch (modelType) {
                case BIG_DECIMAL: {
                    final BigDecimal max;
                    try {
                        max = describedProperty.get(MAX).asBigDecimal();
                    } catch (IllegalArgumentException e) {
                        throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.invalidDescriptionMinMaxForParameterHasWrongType(MAX, paramName, ModelType.BIG_DECIMAL, getPathAddress(operation), description));
                        return;
                    }
                    if (value.asBigDecimal().compareTo(max) > 0) {
                        throw ControllerLogger.ROOT_LOGGER.validationFailedValueIsGreaterThanMax(value.asBigDecimal(), paramName, max, formatOperationForMessage(operation));
                    }
                }
                break;
                case BIG_INTEGER: {
                    final BigInteger max;
                    try {
                        max = describedProperty.get(MAX).asBigInteger();
                    } catch (IllegalArgumentException e) {
                        throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.invalidDescriptionMinMaxForParameterHasWrongType(MAX, paramName, ModelType.BIG_INTEGER, getPathAddress(operation), description));
                        return;
                    }
                    if (value.asBigInteger().compareTo(max) > 0) {
                        throw ControllerLogger.ROOT_LOGGER.validationFailedValueIsGreaterThanMax(value.asBigInteger(), paramName, max, formatOperationForMessage(operation));
                    }
                }
                break;
                case DOUBLE: {
                    final double max;
                    try {
                        max = describedProperty.get(MAX).asDouble();
                    } catch (IllegalArgumentException e) {
                        throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.invalidDescriptionMinMaxForParameterHasWrongType(MAX, paramName, ModelType.DOUBLE, getPathAddress(operation), description));
                        return;
                    }
                    if (value.asDouble() > max) {
                        throw ControllerLogger.ROOT_LOGGER.validationFailedValueIsGreaterThanMax(value.asDouble(), paramName, max, formatOperationForMessage(operation));
                    }
                }
                break;
                case INT: {
                    final int max;
                    try {
                        max = describedProperty.get(MAX).asInt();
                    } catch (IllegalArgumentException e) {
                        throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.invalidDescriptionMinMaxForParameterHasWrongType(MAX, paramName, ModelType.INT, getPathAddress(operation), description));
                        return;
                    }
                    if (value.asInt() > max) {
                        throw ControllerLogger.ROOT_LOGGER.validationFailedValueIsGreaterThanMax(value.asInt(), paramName, max, formatOperationForMessage(operation));
                    }
                }
                break;
                case LONG: {
                    final long max;
                    try {
                        max = describedProperty.get(MAX).asLong();
                    } catch (IllegalArgumentException e) {
                        throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.invalidDescriptionMinMaxForParameterHasWrongType(MAX, paramName, ModelType.LONG, getPathAddress(operation), description));
                        return;
                    }
                    if (value.asLong() > describedProperty.get(MAX).asLong()) {
                        throw ControllerLogger.ROOT_LOGGER.validationFailedValueIsGreaterThanMax(value.asLong(), paramName, max, formatOperationForMessage(operation));
                    }
                }
                break;
            }
        }
        if (describedProperty.hasDefined(MIN_LENGTH)) {
            final int minLength;
            try {
                minLength = describedProperty.get(MIN_LENGTH).asInt();
            } catch (IllegalArgumentException e) {
                throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.invalidDescriptionMinMaxLengthForParameterHasWrongType(MIN_LENGTH, paramName, getPathAddress(operation), description));
                return;
            }
            switch (modelType) {
            case LIST:
                if (value.asList().size() < minLength) {
                    throw ControllerLogger.ROOT_LOGGER.validationFailedValueIsShorterThanMinLength(value.asList().size(), paramName, minLength, formatOperationForMessage(operation));
                }
                break;
            case BYTES:
                if (value.asBytes().length < minLength) {
                    throw ControllerLogger.ROOT_LOGGER.validationFailedValueIsShorterThanMinLength(value.asBytes().length, paramName, minLength, formatOperationForMessage(operation));
                }
                break;
            case STRING:
                if (value.asString().length() < minLength) {
                    throw ControllerLogger.ROOT_LOGGER.validationFailedValueIsShorterThanMinLength(value.asString().length(), paramName, minLength, formatOperationForMessage(operation));
                }
                break;
            }
        }
        if (describedProperty.hasDefined(MAX_LENGTH)) {
            final int maxLength;
            try {
                maxLength = describedProperty.get(MAX_LENGTH).asInt();
            } catch (IllegalArgumentException e) {
                throwOrWarnAboutDescriptorProblem(ControllerLogger.ROOT_LOGGER.invalidDescriptionMinMaxLengthForParameterHasWrongType(MAX_LENGTH, paramName, getPathAddress(operation), description));
                return;
            }
            switch (modelType) {
            case LIST:
                if (value.asList().size() > maxLength) {
                    throw ControllerLogger.ROOT_LOGGER.validationFailedValueIsLongerThanMaxLength(value.asList().size(), paramName, maxLength, formatOperationForMessage(operation));
                }
                break;
            case BYTES:
                if (value.asBytes().length > maxLength) {
                    throw ControllerLogger.ROOT_LOGGER.validationFailedValueIsLongerThanMaxLength(value.asBytes().length, paramName, maxLength, formatOperationForMessage(operation));
                }
                break;
            case STRING:
                if (value.asString().length() > maxLength) {
                    throw ControllerLogger.ROOT_LOGGER.validationFailedValueIsLongerThanMaxLength(value.asString().length(), paramName, maxLength, formatOperationForMessage(operation));
                }
                break;
            }
        }
    }


    private void checkType(final ModelType modelType, final ModelNode value) {
        ModelNode resolved;
        try {
            resolved = expressionResolver.resolveExpressions(value);
        } catch (OperationFailedException | ExpressionResolver.ExpressionResolutionUserException e) {
            // Dealing with an unresolvable expression is beyond what this class can do.
            // So fall through and see what happens. Basically if modelType is EXPRESSION or STRING
            // it will pass, otherwise an IAE will be thrown
            resolved = value;
        }
        switch (modelType) {
            case BIG_DECIMAL:
                resolved.asBigDecimal();
                break;
            case BIG_INTEGER:
                resolved.asBigInteger();
                break;
            case BOOLEAN:
                resolved.asBoolean();
                break;
            case BYTES:
                resolved.asBytes();
                break;
            case DOUBLE:
                resolved.asDouble();
                break;
            case EXPRESSION:
                value.asString();
                break;
            case INT:
                resolved.asInt();
                break;
            case LIST:
                value.asList();
                break;
            case LONG:
                resolved.asLong();
                break;
            case OBJECT:
                value.asObject();
                break;
            case PROPERTY:
                value.asProperty();
                break;
            case STRING:
                //noinspection DuplicateBranchesInSwitch
                value.asString();
                break;
            case TYPE:
                resolved.asType();
                break;
        }
    }

    private void checkList(final ModelNode operation, final String paramName, final ModelNode describedProperty, final ModelNode value) {
        if (describedProperty.get(TYPE).asType() == ModelType.LIST) {
            if (describedProperty.hasDefined(VALUE_TYPE) && describedProperty.get(VALUE_TYPE).getType() == ModelType.TYPE) {
                ModelType elementType = describedProperty.get(VALUE_TYPE).asType();
                for (ModelNode element : value.asList()) {
                    try {
                        checkType(elementType, element);
                    } catch (IllegalArgumentException e) {
                        throw ControllerLogger.ROOT_LOGGER.validationFailedInvalidElementType(paramName, elementType, formatOperationForMessage(operation));
                    }
                }
            }
        }
    }

    private DescriptionProvider getDescriptionProvider(final ModelNode operation) {
        if (!operation.hasDefined(OP)) {
            throw ControllerLogger.ROOT_LOGGER.validationFailedOperationHasNoField(OP, formatOperationForMessage(operation));
        }
        if (!operation.hasDefined(OP_ADDR)) {
            throw ControllerLogger.ROOT_LOGGER.validationFailedOperationHasNoField(OP_ADDR, formatOperationForMessage(operation));
        }
        final String name = operation.get(OP).asString();
        if (name == null || name.trim().length() == 0) {
            throw ControllerLogger.ROOT_LOGGER.validationFailedOperationHasANullOrEmptyName(formatOperationForMessage(operation));
        }

        final PathAddress addr = getPathAddress(operation);

        final DescriptionProvider provider = root.getOperationDescription(addr, name);
        if (provider == null) {
            throw ControllerLogger.ROOT_LOGGER.validationFailedNoOperationFound(name, addr, formatOperationForMessage(operation));
        }
        return provider;
    }

    private String hasAlternative(final Set<String> keys, Collection<ModelNode> alternatives) {
        if(alternatives == null || alternatives.isEmpty()) {
            return null;
        }
        for(final ModelNode alternative : alternatives) {
            if(keys.contains(alternative.asString())) {
                return alternative.asString();
            }
        }
        return null;
    }

    private PathAddress getPathAddress(ModelNode operation) {
        return PathAddress.pathAddress(operation.get(OP_ADDR));
    }

    /**
     * Throws an exception or logs a message
     *
     * @param message The message for the exception or the log message. Must be internationalized
     */
    private void throwOrWarnAboutDescriptorProblem(String message) {
        if (validateDescriptions) {
            throw new IllegalArgumentException(message);
        }
        ControllerLogger.ROOT_LOGGER.warn(message);
    }

    private String formatOperationForMessage(ModelNode operation) {
        if (includeOperationInError) {
            return operation.asString();
        }
        return "";
    }
}
