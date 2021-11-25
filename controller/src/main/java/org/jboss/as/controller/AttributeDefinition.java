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

package org.jboss.as.controller;

import static org.jboss.as.controller.registry.AttributeAccess.Flag.immutableSetOf;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.AccessConstraintDescriptionProviderUtil;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.validation.AllowedValuesValidator;
import org.jboss.as.controller.operations.validation.BytesValidator;
import org.jboss.as.controller.operations.validation.MinMaxValidator;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.operations.validation.NillableOrExpressionParameterValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * Defining characteristics of an attribute in a {@link org.jboss.as.controller.registry.Resource} or a
 * parameter or reply value type field in an {@link org.jboss.as.controller.OperationDefinition}, with utility
 * methods for validation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public abstract class AttributeDefinition {

    /** The {@link ModelType} types that reflect complex DMR structures -- {@code LIST}, {@code OBJECT}, {@code PROPERTY}} */
    protected static final Set<ModelType> COMPLEX_TYPES = Collections.unmodifiableSet(EnumSet.of(ModelType.LIST, ModelType.OBJECT, ModelType.PROPERTY));

    private final String name;
    private final String xmlName;
    private final ModelType type;
    private final boolean required;
    private final boolean allowExpression;
    private final ModelNode defaultValue;
    private final MeasurementUnit measurementUnit;
    private final String[] alternatives;
    private final String[] requires;
    private final ModelNode[] allowedValues;
    private final ParameterCorrector valueCorrector;
    private final ParameterValidator validator;
    private final Set<AttributeAccess.Flag> flags;
    /** @deprecated use {@link #getMarshaller()} as this will be made private in a future release*/
    @Deprecated
    protected final AttributeMarshaller attributeMarshaller;
    private final boolean resourceOnly;
    private final DeprecationData deprecationData;
    private final List<AccessConstraintDefinition> accessConstraints;
    private final Boolean nilSignificant;
    private final AttributeParser parser;
    private final String attributeGroup;
    private final ModelNode undefinedMetricValue;
    /** @deprecated use {@link #getReferenceRecorder()} ()} as this will be made private in a future release*/
    @Deprecated
    protected final CapabilityReferenceRecorder referenceRecorder;
    private final Map<String, ModelNode> arbitraryDescriptors;

    // NOTE: Standards for creating a constructor variant are:
    // 1) Don't.
    // 2) See 1)
    //
    // Use the constructor that takes a builder

    protected AttributeDefinition(AbstractAttributeDefinitionBuilder<?, ?> toCopy) {
        this(toCopy.getName(), toCopy.getXmlName(), toCopy.getDefaultValue(), toCopy.getType(),
                toCopy.isAllowNull(), toCopy.isAllowExpression(), toCopy.getMeasurementUnit(), toCopy.getCorrector(),
                wrapValidator(toCopy.getValidator(), toCopy.isAllowNull(), toCopy.getAlternatives(), toCopy.isAllowExpression(),
                        toCopy.getType(), toCopy.getConfiguredMinSize(), toCopy.getConfiguredMaxSize()),
                true, toCopy.getAlternatives(), toCopy.getRequires(), toCopy.getAttributeMarshaller(),
                toCopy.isResourceOnly(), toCopy.getDeprecated(),
                wrapConstraints(toCopy.getAccessConstraints()), toCopy.getNullSignificant(), toCopy.getParser(),
                toCopy.getAttributeGroup(), toCopy.getCapabilityReferenceRecorder(), toCopy.getAllowedValues(), toCopy.getArbitraryDescriptors(),
                toCopy.getUndefinedMetricValue(), immutableSetOf(toCopy.getFlags()));
    }

    private AttributeDefinition(String name, String xmlName, final ModelNode defaultValue, final ModelType type,
                                final boolean allowNull, final boolean allowExpression, final MeasurementUnit measurementUnit,
                                final ParameterCorrector valueCorrector, final ParameterValidator validator, final boolean validateNull,
                                final String[] alternatives, final String[] requires, AttributeMarshaller marshaller,
                                boolean resourceOnly, DeprecationData deprecationData, final List<AccessConstraintDefinition> accessConstraints,
                                Boolean nilSignificant, AttributeParser parser, final String attributeGroup, CapabilityReferenceRecorder referenceRecorder,
                                ModelNode[] allowedValues, final Map<String, ModelNode> arbitraryDescriptors, final ModelNode undefinedMetricValue, final Set<AttributeAccess.Flag> flags) {

        this.name = name;
        this.xmlName = xmlName == null ? name : xmlName;
        this.type = type;
        this.required = !allowNull;
        this.allowExpression = allowExpression;
        this.parser = parser != null ? parser : AttributeParser.SIMPLE;
        if (defaultValue != null && defaultValue.isDefined()) {
            this.defaultValue = defaultValue;
            this.defaultValue.protect();
        } else {
            this.defaultValue = null;
        }
        this.measurementUnit = measurementUnit;
        this.alternatives = alternatives;
        this.requires = requires;
        this.valueCorrector = valueCorrector;
        this.validator = validator;
        this.flags = flags;
        //noinspection deprecation
        this.attributeMarshaller = marshaller != null ? marshaller : AttributeMarshaller.SIMPLE;
        this.resourceOnly = resourceOnly;
        this.accessConstraints = accessConstraints;
        this.deprecationData = deprecationData;
        this.nilSignificant = nilSignificant;
        this.attributeGroup = attributeGroup;
        this.allowedValues = allowedValues;
        if (undefinedMetricValue != null && undefinedMetricValue.isDefined()) {
            this.undefinedMetricValue = undefinedMetricValue;
            this.undefinedMetricValue.protect();
        } else {
            this.undefinedMetricValue = null;
        }
        //noinspection deprecation
        this.referenceRecorder = referenceRecorder;
        if (arbitraryDescriptors != null && !arbitraryDescriptors.isEmpty()) {
            if (arbitraryDescriptors.size() == 1) {
                Map.Entry<String, ModelNode> entry = arbitraryDescriptors.entrySet().iterator().next();
                this.arbitraryDescriptors = Collections.singletonMap(entry.getKey(), entry.getValue());
            } else {
                this.arbitraryDescriptors = Collections.unmodifiableMap(new HashMap<>(arbitraryDescriptors));
            }
        } else {
            this.arbitraryDescriptors = Collections.emptyMap();
        }
    }

    private static ParameterValidator wrapValidator(ParameterValidator toWrap, boolean allowNull,
                                                    String[] alternatives, boolean allowExpression, ModelType type,
                                                    Integer minSize, Integer maxSize) {
        NillableOrExpressionParameterValidator result = null;
        boolean hasAlternatives = alternatives != null && alternatives.length > 0;
        boolean nullOK = allowNull || hasAlternatives;
        if (toWrap == null) {
            if (type == ModelType.STRING) {
                // If sizing was specified, use it. If unspecified use defaults we've used since early AS 7
                int min = minSize == null ? 1 : minSize;
                int max = maxSize == null ? Integer.MAX_VALUE : maxSize;
                toWrap = new StringLengthValidator(min, max, allowNull, allowExpression);
            } else if (type == ModelType.BYTES) {
                // If sizing was specified, use it. If unspecified use defaults equivalent to no min or max
                int min = minSize == null ? 0 : minSize;
                int max = maxSize == null ? Integer.MAX_VALUE : maxSize;
                toWrap = new BytesValidator(min, max, false); // don't need to allow null here; the wrapper will deal with null
            } else {
                toWrap = new ModelTypeValidator(type);
            }
        } else if (toWrap instanceof NillableOrExpressionParameterValidator) {
            // Avoid re-wrapping
            NillableOrExpressionParameterValidator current = (NillableOrExpressionParameterValidator) toWrap;
            if (allowExpression == current.isAllowExpression() &&
                    nullOK == current.getAllowNull()) {
                result = current;
            } else {
                toWrap = current.getDelegate();
            }
        }
        if (result == null) {
            result = new NillableOrExpressionParameterValidator(toWrap, nullOK, allowExpression);
        }

        return result;
    }

    private static List<AccessConstraintDefinition> wrapConstraints(AccessConstraintDefinition[] accessConstraints) {
        if (accessConstraints == null || accessConstraints.length == 0) {
            return Collections.<AccessConstraintDefinition>emptyList();
        } else {
            return Collections.unmodifiableList(Arrays.asList(accessConstraints));
        }
    }

    /**
     * The attribute's name in the management model.
     *
     * @return the name. Will not be {@code null}
     */
    public String getName() {
        return name;
    }

    /**
     * The attribute's name in the xml configuration. Not relevant for operation parameters and reply value types.
     *
     * @return the name. Will not be {@code null}, although it may not be relevant
     */
    public String getXmlName() {
        return xmlName;
    }

    /**
     * The expected {@link org.jboss.dmr.ModelType type} of the {@link org.jboss.dmr.ModelNode} that holds the
     * attribute data.
     * @return the type. Will not be {@code null}
     */
    public ModelType getType() {
        return type;
    }

    /**
     * Whether a {@link org.jboss.dmr.ModelNode} holding the value of this attribute can be
     * {@link org.jboss.dmr.ModelType#UNDEFINED} when all other attributes in the same overall
     * model that are {@link #getAlternatives() alternatives} of this attribute are undefined.
     * <p>
     * In a valid model an attribute that is required must be undefined if any alternative
     * is defined, so this method should not be used for checking if it is valid for
     * the attribute ever to have an undefined value. Use {@link #isNillable()} for that.
     *
     * @return {@code true} if an {@code undefined ModelNode} is invalid in the absence of
     *         alternatives; {@code false} if not
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * <strong>Inverse</strong> of {@link #isRequired()}.
     * <p>
     * In a valid model an attribute that is required must be undefined if any alternative
     * is defined, so this method should not be used for checking if it is valid for
     * the attribute ever to have an undefined value. Use {@link #isNillable()} for that.
     *
     * @return {@code true} if an {@code undefined ModelNode} is valid in the absence of
     *         alternatives; {@code false} if not
     *
     * @deprecated use either {@link #isRequired()} or {@link #isNillable()} depending on which provides the desired information
     */
    @Deprecated
    public boolean isAllowNull() {
        return !required;
    }

    /**
     * Whether a {@link org.jboss.dmr.ModelNode} holding the value of this attribute can be
     * {@link org.jboss.dmr.ModelType#UNDEFINED} in any situation. An attribute that ordinarily is
     * {@link #isRequired() required} may still be undefined in a given model if an
     * {@link #getAlternatives() alternative attribute} is defined.
     * <p>
     * This is equivalent to {@code !isRequired() || (getAlternatives() != null && getAlternatives().length > 0)}.
     *
     * @return {@code true} if an {@code undefined ModelNode} is valid; {@code false} if not
     */
    public boolean isNillable() {
        return !required || (alternatives != null && alternatives.length > 0);
    }

    /**
     * Gets whether an access control check is required to implicitly set an attribute to {@code undefined}
     * in a resource "add" operation. "Implicitly" setting an attribute refers to not providing a value for
     * it in the add operation, leaving the attribute in an undefined state. So, if a user attempts to
     * add a resource but does not define some attributes, a write permission check will be performed for
     * any attributes where this method returns {@code true}.
     * <p>
     * Generally this is {@code true} if {@link #isRequired() undefined is allowed} and a
     * {@link #getDefaultValue() default value} exists, although some instances may have a different setting.
     *
     * @return {@code true} if an {@code undefined} value is significant
     */
    public final boolean isNullSignificant() {
        if (nilSignificant != null) {
            return nilSignificant;
        }
        return !required && defaultValue != null && defaultValue.isDefined();
    }

    /**
     * Expose the raw value to {@link org.jboss.as.controller.AbstractAttributeDefinitionBuilder}
     * @return  the raw value
     *
     * @see #isNullSignificant()
     */
    Boolean getNilSignificant() {
        return nilSignificant;
    }

    /**
     * Whether a {@link org.jboss.dmr.ModelNode} holding the value of this attribute can be
     * {@link org.jboss.dmr.ModelType#EXPRESSION}.
     *
     * @return {@code true} if an {@code expression ModelNode} is valid; {@code false} if not
     */
    public boolean isAllowExpression() {
        return allowExpression;
    }

    /**
     * Gets the default value to use for the attribute if a value was not provided.
     *
     * @return the default value, or {@code null} if no defined value was provided
     */
    public ModelNode getDefaultValue() {
        return defaultValue;
    }

    /**
     * Gets the name of the attribute group with which this attribute is associated, if any.
     *
     * @return the name of the group, or {@code null} if the attribute is not associated with a group
     */
    public String getAttributeGroup() {
        return attributeGroup;
    }

    /**
     * The unit of measure in which an attribute with a numerical value is expressed.
     *
     * @return the measurement unit, or {@code null} if none is relevant
     */
    public MeasurementUnit getMeasurementUnit() {
        return measurementUnit;
    }

    /**
     * Gets the corrector used to correct values before checking that they comply with the attribute's definition.
     *
     * @return the corrector. May be {@code null}
     */
    public ParameterCorrector getCorrector() {
        return valueCorrector;
    }

    /**
     * Gets the validator used to validate that values comply with the attribute's definition.
     *
     * @return the validator. Will not be {@code null}
     */
    public ParameterValidator getValidator() {
        return validator;
    }

    /**
     * Gets whether the attribute definition is checking for {@link org.jboss.dmr.ModelNode#isDefined() undefined} values.
     * This will be {@code true} for attributes that are not {@link #isRequired()} (although the validation is
     * meaningless, since undefined is valid) and for required attributes that have {@link #getAlternatives() alternatives}.
     * <p>
     * Validation by the AttributeDefinition of required attributes with alternatives is not possible, as the necessary
     * context of the overall change being made is not available.
     *
     * @return {@code true} if validation will check undefined values.
     *
     * @deprecated  this is no longer configurable, so this getter may be removed in a future major release.
     */
    @Deprecated
    public boolean isValidatingNull() {
        return !required || alternatives == null || alternatives.length == 0;
    }

    /**
     * Gets the names of other attributes whose value must be {@code undefined} if this attribute's value is
     * defined, and vice versa.
     *
     * @return the alternative attribute names, or {@code null} if there are no such attributes
     */
    public String[] getAlternatives() {
        return alternatives;
    }

    /**
     * Gets the names of other attributes whose value must not be {@code undefined} if this attribute's value is
     * defined.
     *
     * @return the required attribute names, or {@code null} if there are no such attributes
     */
    public String[] getRequires() {
        return requires;
    }

    /**
     * Gets a set of any {@link org.jboss.as.controller.registry.AttributeAccess.Flag flags} used
     * to indicate special characteristics of the attribute
     *
     * @return the flags. Will not be {@code null} but may be empty.
     *
     * @deprecated In the next release, the return type of this method will become simply {@code Set} and the returned object will be immutable, so any callers should update their code to reflect that
     */
    @Deprecated
    public EnumSet<AttributeAccess.Flag> getFlags() {
        if (flags.isEmpty()) {
            return EnumSet.noneOf(AttributeAccess.Flag.class);
        }
        AttributeAccess.Flag[] array = flags.toArray(new AttributeAccess.Flag[flags.size()]);
        return array.length == 1 ? EnumSet.of(array[0]) : EnumSet.of(array[0], array);
    }

    /**
     * Provides an immutable variant of the set returned by {@link #getFlags()}.
     * @deprecated for internal use only; will be dropped when the semantic of {@link #getFlags()} is changed to return an immutable {@code Set}*/
    @Deprecated
    public Set<AttributeAccess.Flag> getImmutableFlags() {
        return flags;
    }

    /**
     * returns array with all allowed values
     * @return allowed values
     */
    public List<ModelNode> getAllowedValues() {
        if (allowedValues == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(this.allowedValues);
    }

    public Map<String, ModelNode> getArbitraryDescriptors() {
        return arbitraryDescriptors;
    }

    /**
     * Gets whether the given {@code resourceModel} has a value for this attribute that should be marshalled to XML.
     * <p>
     * This is the same as {@code isMarshallable(resourceModel, true)}.
     * </p>
     *
     * @param resourceModel the model, a non-null node of {@link ModelType#OBJECT}.
     *
     * @return {@code true} if the given {@code resourceModel} has a defined value under this attribute's {@link #getName()} () name}.
     */
    public boolean isMarshallable(final ModelNode resourceModel) {
        return getMarshaller().isMarshallable(this, resourceModel, true);
    }

    /**
     * Gets whether the given {@code resourceModel} has a value for this attribute that should be marshalled to XML.
     *
     * @param resourceModel the model, a non-null node of {@link ModelType#OBJECT}.
     * @param marshallDefault {@code true} if the value should be marshalled even if it matches the default value
     *
     * @return {@code true} if the given {@code resourceModel} has a defined value under this attribute's {@link #getName()} () name}
     * and {@code marshallDefault} is {@code true} or that value differs from this attribute's {@link #getDefaultValue() default value}.
     */
    public boolean isMarshallable(final ModelNode resourceModel, final boolean marshallDefault) {
        return getMarshaller().isMarshallable(this, resourceModel, marshallDefault);
    }

    /**
     * Finds a value in the given {@code operationObject} whose key matches this attribute's {@link #getName() name} and
     * validates it using this attribute's {@link #getValidator() validator}.
     *
     * @param operationObject model node of type {@link ModelType#OBJECT}, typically representing an operation request
     *
     * @return the value
     * @throws OperationFailedException if the value is not valid
     */
    public ModelNode validateOperation(final ModelNode operationObject) throws OperationFailedException {
        return validateOperation(operationObject, false);
    }

    /**
     * Finds a value in the given {@code operationObject} whose key matches this attribute's {@link #getName() name},
     * validates it using this attribute's {@link #getValidator() validator}, and, stores it under this attribute's name in the given {@code model}.
     *
     * @param operationObject model node of type {@link ModelType#OBJECT}, typically representing an operation request
     * @param model model node in which the value should be stored
     *
     * @throws OperationFailedException if the value is not valid
     */
    public final void validateAndSet(ModelNode operationObject, final ModelNode model) throws OperationFailedException {
        if (operationObject.hasDefined(name) && deprecationData != null && deprecationData.isNotificationUseful()) {
            ControllerLogger.DEPRECATED_LOGGER.attributeDeprecated(getName(),
                    PathAddress.pathAddress(operationObject.get(ModelDescriptionConstants.OP_ADDR)).toCLIStyleString());
        }


        // overriding the value of an attribute using an env var is performed only when the operation
        // containers a PathAddress. This method is also used for validating parameters (that are not necessary
        // providing an address (e.g. AbstractWriteAttributeHandler.execute)
        if (EnvVarAttributeOverrider.isEnabled() &&
                operationObject.has(ModelDescriptionConstants.OP_ADDR) &&
                ! COMPLEX_TYPES.contains(type)) {
            PathAddress pathAddress = PathAddress.pathAddress(operationObject.get(ModelDescriptionConstants.OP_ADDR));
            String overriddenValue = EnvVarAttributeOverrider.getOverriddenValueFromEnvVar(pathAddress, name);
            if (overriddenValue != null) {
                operationObject.get(name).set(overriddenValue);
            }
        }

        // AS7-6224 -- convert expression strings to ModelType.EXPRESSION *before* correcting
        ModelNode newValue = convertParameterExpressions(operationObject.get(name));
        final ModelNode correctedValue = correctValue(newValue, model.get(name));
        if (!correctedValue.equals(operationObject.get(name))) {
            operationObject.get(name).set(correctedValue);
        }
        ModelNode node = validateOperation(operationObject, true);
        if (node.getType() == ModelType.EXPRESSION
                && (referenceRecorder != null || flags.contains(AttributeAccess.Flag.EXPRESSIONS_DEPRECATED))) {
            ControllerLogger.DEPRECATED_LOGGER.attributeExpressionDeprecated(getName(),
                PathAddress.pathAddress(operationObject.get(ModelDescriptionConstants.OP_ADDR)).toCLIStyleString());
        }
        model.get(name).set(node);
    }

    private ModelNode convertToExpectedType(final ModelNode node) {
        ModelType nodeType = node.getType();
        if (nodeType == type || nodeType == ModelType.UNDEFINED || nodeType == ModelType.EXPRESSION || Util.isExpression(node.asString())) {
            return node;
        }
        switch (type) {
            case BIG_DECIMAL:
                return new ModelNode(node.asBigDecimal());
            case BIG_INTEGER:
                return new ModelNode(node.asBigInteger());
            case BOOLEAN:
                return new ModelNode(node.asBoolean());
            case BYTES:
                return new ModelNode(node.asBytes());
            case DOUBLE:
                return new ModelNode(node.asDouble());
            case INT:
                return new ModelNode(node.asInt());
            case LIST:
                return new ModelNode().set(node.asList());
            case LONG:
                return new ModelNode(node.asLong());
            case PROPERTY:
                return new ModelNode().set(node.asProperty());
            case TYPE:
                return new ModelNode(node.asType());
            case STRING:
                return new ModelNode(node.asString());
            case OBJECT:
                // Check for LIST of PROPERTY. If that is found convert.
                // But only convert if that specifically is found in order
                // to avoid odd unintended conversions (e.g. LIST of STRING, which DMR can convert to OBJECT)
                if (nodeType == ModelType.LIST) {
                    if (node.asInt() == 0) {
                        return new ModelNode().setEmptyObject();
                    }
                    ModelNode first = node.get(0);
                    if (first.getType() != ModelType.PROPERTY) {
                        return node;
                    }
                    // Now we know at least the first element is property, so
                    // we assume the rest are as well.
                    List<Property> propertyList;
                    try {
                        propertyList = node.asPropertyList();
                    } catch (IllegalArgumentException iae) {
                        // ignore. The validator allowed this node or we wouldn't be here,
                        // so just fall through and return the unconverted node
                        // Note this isn't expected to be a real world case
                        return node;
                    }
                    ModelNode result = new ModelNode().setEmptyObject();
                    for (Property prop : propertyList) {
                        result.get(prop.getName()).set(prop.getValue());
                    }
                    return result;
                }
                return node;
            default:
                return node;
        }
    }

    /**
     * Finds a value in the given {@code model} whose key matches this attribute's {@link #getName() name},
     * uses the given {@code context} to {@link OperationContext#resolveExpressions(org.jboss.dmr.ModelNode) resolve}
     * it and validates it using this attribute's {@link #getValidator() validator}. If the value is
     * undefined and a {@link #getDefaultValue() default value} is available, the default value is used.
     *
     * @param context the operation context
     * @param model model node of type {@link ModelType#OBJECT}, typically representing a model resource
     *
     * @return the resolved value, possibly the default value if the model does not have a defined value matching
     *              this attribute's name
     * @throws OperationFailedException if the value is not valid
     */
    public ModelNode resolveModelAttribute(final OperationContext context, final ModelNode model) throws OperationFailedException {
        // OperationContext is a subinterface of ExpressionResolver but that distinction
        // is not relevant to us so just use the method that takes ExpressionResolver
        return resolveModelAttribute((ExpressionResolver) context, model);
    }

    /**
     * Finds a value in the given {@code model} whose key matches this attribute's {@link #getName() name},
     * uses the given {@code resolver} to {@link ExpressionResolver#resolveExpressions(org.jboss.dmr.ModelNode)} resolve}
     * it and validates it using this attribute's {@link #getValidator() validator}. If the value is
     * undefined and a {@link #getDefaultValue() default value} is available, the default value is used.
     *
     * @param resolver the expression resolver
     * @param model model node of type {@link ModelType#OBJECT}, typically representing a model resource
     *
     * @return the resolved value, possibly the default value if the model does not have a defined value matching
     *              this attribute's name
     * @throws OperationFailedException if the value is not valid
     */
    public ModelNode resolveModelAttribute(final ExpressionResolver resolver, final ModelNode model) throws OperationFailedException {
        final ModelNode node = new ModelNode();
        if(model.has(name)) {
            node.set(model.get(name));
        }
        return resolveValue(resolver, node);
    }

    /**
     * Takes the given {@code value}, resolves it using the given {@code context}
     * and validates it using this attribute's {@link #getValidator() validator}. If the value is
     * undefined and a {@link #getDefaultValue() default value} is available, the default value is used.
     *
     * @param context the context to use to {@link OperationContext#resolveExpressions(org.jboss.dmr.ModelNode) resolve} the value
     * @param value a node that is expected to be a valid value for an attribute defined by this definition
     *
     * @return the resolved value, possibly the default value if {@code value} is not defined
     *
     * @throws OperationFailedException if the value is not valid
     */
    public ModelNode resolveValue(final OperationContext context, final ModelNode value) throws OperationFailedException {
        // OperationContext is a subinterface of ExpressionResolver but that distinction
        // is not relevant to us so just use the method that takes ExpressionResolver
        return resolveValue((ExpressionResolver) context, value);
    }

    /**
     * Takes the given {@code value}, resolves it using the given {@code resolver}
     * and validates it using this attribute's {@link #getValidator() validator}. If the value is
     * undefined and a {@link #getDefaultValue() default value} is available, the default value is used.
     *
     * @param resolver the expression resolver
     * @param value a node that is expected to be a valid value for an attribute defined by this definition
     *
     * @return the resolved value, possibly the default value if {@code value} is not defined
     *
     * @throws OperationFailedException if the value is not valid
     */
    public ModelNode resolveValue(final ExpressionResolver resolver, final ModelNode value) throws OperationFailedException {
        final ModelNode node = value.clone();
        if (!node.isDefined() && defaultValue != null && defaultValue.isDefined()) {
            node.set(defaultValue);
        }
        ModelNode resolved = resolver.resolveExpressions(node);
        resolved = parseResolvedValue(value, resolved);
        validator.validateParameter(name, resolved);
        return resolved;
    }

    /**
     * "Parse", if appropriate, an attribute value that may have been resolved by the expression resolver into
     * a different form, returning that new form, or returning {@code resolved} unchanged if no conversion
     * is appropriate. The default implementation does the latter.
     *
     * @param original the original value of the attribute before any resolution work was done
     * @param resolved the value of the attribute following resolution work
     *
     * @return the new form of the value or {@code resolved} itself, unchanged
     */
    ModelNode parseResolvedValue(ModelNode original, ModelNode resolved) {
        return resolved;
    }

    /**
     * Inverse of {@link #hasAlternative(org.jboss.dmr.ModelNode)}.
     *
     * @param operationObject an object {@code ModelNode} whose keys are attribute names.
     *
     * @return {@code true} if {@code operationObject} has no defined values for attributes configured as our alternatives
     */
    public boolean isAllowed(final ModelNode operationObject) {
        if(alternatives != null) {
            for(final String alternative : alternatives) {
                if(operationObject.hasDefined(alternative)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Gets whether this attribute must be defined in the given {@code operationObject}
     * @param operationObject an object {@code ModelNode} whose keys are attribute names.
     * @return {@code true} if this attribute is {@link #isRequired() required} and the given
     *         {@code operationObject} does not have any defined attributes configured as
     *         {@link #getAlternatives() alternatives} to this attribute
     */
    public boolean isRequired(final ModelNode operationObject) {
        return required && !hasAlternative(operationObject);
    }

    /**
     * Gets whether this attribute has {@link #getAlternatives() alternatives} configured and the given
     * {@code operationObject} has any of those alternatives defined.
     *
     * @param operationObject an object {@code ModelNode} whose keys are attribute names.
     *
     * @return {@code true} if {@code operationObject} has any defined values for attributes configured as our alternatives
     */
    public boolean hasAlternative(final ModelNode operationObject) {
        if(alternatives != null) {
            for(final String alternative : alternatives) {
                if(operationObject.hasDefined(alternative)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Marshalls the value from the given {@code resourceModel} as an xml element, if it
     * {@link #isMarshallable(org.jboss.dmr.ModelNode, boolean) is marshallable}.
     *
     * @param resourceModel the model, a non-null node of {@link org.jboss.dmr.ModelType#OBJECT}.
     * @param writer stream writer to use for writing the attribute
     * @throws javax.xml.stream.XMLStreamException if thrown by {@code writer}
     */
    public void marshallAsElement(final ModelNode resourceModel, final XMLStreamWriter writer) throws XMLStreamException {
        marshallAsElement(resourceModel, true, writer);
    }

    /**
     * Marshalls the value from the given {@code resourceModel} as an xml element, if it
     * {@link #isMarshallable(org.jboss.dmr.ModelNode, boolean) is marshallable}.
     *
     * @param resourceModel   the model, a non-null node of {@link org.jboss.dmr.ModelType#OBJECT}.
     * @param marshallDefault {@code true} if the value should be marshalled even if it matches the default value
     * @param writer          stream writer to use for writing the attribute
     * @throws javax.xml.stream.XMLStreamException if thrown by {@code writer}
     */
    public void marshallAsElement(final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
        if (getMarshaller().isMarshallableAsElement()) {
            getMarshaller().marshallAsElement(this, resourceModel, marshallDefault, writer);
        } else {
            throw ControllerLogger.ROOT_LOGGER.couldNotMarshalAttributeAsElement(getName());
        }
    }

    /**
     * Creates a returns a basic model node describing the attribute, after attaching it to the given overall resource
     * description model node.  The node describing the attribute is returned to make it easy to perform further
     * modification.
     *
     * @param bundle resource bundle to use for text descriptions
     * @param prefix prefix to prepend to the attribute name key when looking up descriptions
     * @param resourceDescription  the overall resource description
     * @return  the attribute description node
     */
    public ModelNode addResourceAttributeDescription(final ResourceBundle bundle, final String prefix, final ModelNode resourceDescription) {
        final ModelNode attr = getNoTextDescription(false);
        attr.get(ModelDescriptionConstants.DESCRIPTION).set(getAttributeTextDescription(bundle, prefix));
        final ModelNode result = resourceDescription.get(ModelDescriptionConstants.ATTRIBUTES, getName()).set(attr);
        ModelNode deprecated = addDeprecatedInfo(result);
        if (deprecated != null) {
            deprecated.get(ModelDescriptionConstants.REASON).set(getAttributeDeprecatedDescription(bundle, prefix));
        }
        addAccessConstraints(result, bundle.getLocale());
        return result;
    }

    /**
     * Creates a returns a basic model node describing the attribute, after attaching it to the given overall resource
     * description model node.  The node describing the attribute is returned to make it easy to perform further
     * modification.
     *
     * @param resourceDescription  the overall resource description
     * @param resolver provider of localized text descriptions
     * @param locale locale to pass to the resolver
     * @param bundle bundle to pass to the resolver
     * @return  the attribute description node
     */
    public ModelNode addResourceAttributeDescription(final ModelNode resourceDescription, final ResourceDescriptionResolver resolver,
                                                     final Locale locale, final ResourceBundle bundle) {
        final ModelNode attr = getNoTextDescription(false);
        final String description = resolver.getResourceAttributeDescription(getName(), locale, bundle);
        attr.get(ModelDescriptionConstants.DESCRIPTION).set(description);
        final ModelNode result = resourceDescription.get(ModelDescriptionConstants.ATTRIBUTES, getName()).set(attr);
        ModelNode deprecated = addDeprecatedInfo(result);
        if (deprecated != null) {
            deprecated.get(ModelDescriptionConstants.REASON).set(resolver.getResourceAttributeDeprecatedDescription(getName(), locale, bundle));
        }
        addAccessConstraints(result, locale);
        return result;
    }

    /**
     * Creates a returns a basic model node describing a parameter that sets this attribute, after attaching it to the
     * given overall operation description model node.  The node describing the parameter is returned to make it easy
     * to perform further modification.
     *
     * @param bundle resource bundle to use for text descriptions
     * @param prefix prefix to prepend to the attribute name key when looking up descriptions
     * @param operationDescription  the overall resource description
     * @return  the attribute description node
     */
    public ModelNode addOperationParameterDescription(final ResourceBundle bundle, final String prefix, final ModelNode operationDescription) {
        final ModelNode param = getNoTextDescription(true);
        param.get(ModelDescriptionConstants.DESCRIPTION).set(getAttributeTextDescription(bundle, prefix));
        final ModelNode result = operationDescription.get(ModelDescriptionConstants.REQUEST_PROPERTIES, getName()).set(param);
        ModelNode deprecated = addDeprecatedInfo(result);
        if (deprecated != null) {
            deprecated.get(ModelDescriptionConstants.REASON).set(getAttributeDeprecatedDescription(bundle, prefix));
        }
        return result;
    }

    /**
     * Creates a returns a basic model node describing a parameter that sets this attribute, after attaching it to the
     * given overall operation description model node.  The node describing the parameter is returned to make it easy
     * to perform further modification.
     *
     * @param resourceDescription  the overall resource description
     * @param operationName the operation name
     * @param resolver provider of localized text descriptions
     * @param locale locale to pass to the resolver
     * @param bundle bundle to pass to the resolver
     * @return  the attribute description node
     */
    public ModelNode addOperationParameterDescription(final ModelNode resourceDescription, final String operationName,
                                                      final ResourceDescriptionResolver resolver,
                                                      final Locale locale, final ResourceBundle bundle) {
        final ModelNode param = getNoTextDescription(true);
        final String description = resolver.getOperationParameterDescription(operationName, getName(), locale, bundle);
        param.get(ModelDescriptionConstants.DESCRIPTION).set(description);
        final ModelNode result = resourceDescription.get(ModelDescriptionConstants.REQUEST_PROPERTIES, getName()).set(param);
        ModelNode deprecated = addDeprecatedInfo(result);
        if (deprecated != null) {
            deprecated.get(ModelDescriptionConstants.REASON).set(resolver.getOperationParameterDeprecatedDescription(operationName, getName(), locale, bundle));
        }
        return result;
    }

    /**
     * Creates a returns a basic model node describing a parameter that sets this attribute, after attaching it to the
     * given overall operation description model node.  The node describing the parameter is returned to make it easy
     * to perform further modification.
     *
     * @param bundle resource bundle to use for text descriptions
     * @param prefix prefix to prepend to the attribute name key when looking up descriptions
     * @param operationDescription  the overall resource description
     * @return  the attribute description node
     */
    public ModelNode addOperationReplyDescription(final ResourceBundle bundle, final String prefix, final ModelNode operationDescription) {
        final ModelNode param = getNoTextDescription(true);
        param.get(ModelDescriptionConstants.DESCRIPTION).set(getAttributeTextDescription(bundle, prefix));
        final ModelNode result = operationDescription.get(ModelDescriptionConstants.REPLY_PROPERTIES, getName()).set(param);
        ModelNode deprecated = addDeprecatedInfo(result);
        if (deprecated != null) {
            deprecated.get(ModelDescriptionConstants.REASON).set(getAttributeDeprecatedDescription(bundle, prefix));
        }
        return result;
    }

    /**
     * Creates a returns a basic model node describing a parameter that sets this attribute, after attaching it to the
     * given overall operation description model node.  The node describing the parameter is returned to make it easy
     * to perform further modification.
     *
     * @param resourceDescription  the overall resource description
     * @param operationName the operation name
     * @param resolver provider of localized text descriptions
     * @param locale locale to pass to the resolver
     * @param bundle bundle to pass to the resolver
     * @return  the attribute description node
     */
    public ModelNode addOperationReplyDescription(final ModelNode resourceDescription, final String operationName,
                                                      final ResourceDescriptionResolver resolver,
                                                      final Locale locale, final ResourceBundle bundle) {
        final ModelNode param = getNoTextDescription(true);
        String description = resolver.getOperationReplyValueTypeDescription(operationName, locale, bundle, getName());
        param.get(ModelDescriptionConstants.DESCRIPTION).set(description);
        final ModelNode result = resourceDescription.get(ModelDescriptionConstants.REPLY_PROPERTIES, getName()).set(param);
        ModelNode deprecated = addDeprecatedInfo(result);
        if (deprecated != null) {
            deprecated.get(ModelDescriptionConstants.REASON).set(resolver.getOperationParameterDeprecatedDescription(operationName, getName(), locale, bundle));
        }
        return result;
    }

    /**
     * Gets localized text from the given {@link java.util.ResourceBundle} for the attribute.
     *
     * @param bundle the resource bundle. Cannot be {@code null}
     * @param prefix a prefix to dot-prepend to the attribute name to form a key to resolve in the bundle
     * @return the resolved text
     */
    public String getAttributeTextDescription(final ResourceBundle bundle, final String prefix) {
        final String bundleKey = prefix == null ? name : (prefix + "." + name);
        return bundle.getString(bundleKey);
    }

    /**
     * Gets localized deprecation text from the given {@link java.util.ResourceBundle} for the attribute.
     *
     * @param bundle the resource bundle. Cannot be {@code null}
     * @param prefix a prefix to dot-prepend to the attribute name to form a key to resolve in the bundle
     * @return the resolved text
     */
    public String getAttributeDeprecatedDescription(final ResourceBundle bundle, final String prefix) {
        String bundleKey = prefix == null ? name : (prefix + "." + name);
        bundleKey += "." + ModelDescriptionConstants.DEPRECATED;
        return bundle.getString(bundleKey);
    }

    /**
     * Adds attribute deprecation information, if relevant, to the given attribute description node
     * @param model the attribute description
     * @return the node added to {@code model} or {@code null} if no deprecation data was needed
     */
    public ModelNode addDeprecatedInfo(final ModelNode model) {
        if (deprecationData == null) { return null; }
        ModelNode deprecated = model.get(ModelDescriptionConstants.DEPRECATED);
        deprecated.get(ModelDescriptionConstants.SINCE).set(deprecationData.getSince().toString());
        /*String bundleKey = prefix == null ? name : (prefix + "." + name);
        bundleKey+="."+ModelDescriptionConstants.DEPRECATED;*/
        //deprecated.get(ModelDescriptionConstants.REASON).set(bundle.getString(bundleKey));
        deprecated.get(ModelDescriptionConstants.REASON);
        return deprecated;
    }

    /**
     * Gets descriptive metadata for this attribute, excluding free-from text
     * {@code description} fields.
     *
     * @param forOperation {@code true} if the metadata is for an operation parameter
     *                                 or reply value type
     * @return object node containing the descriptive metadata
     */
    public ModelNode getNoTextDescription(boolean forOperation) {
        final ModelNode result = new ModelNode();
        result.get(ModelDescriptionConstants.TYPE).set(type);
        result.get(ModelDescriptionConstants.DESCRIPTION); // placeholder
        if (attributeGroup != null && !forOperation) {
            result.get(ModelDescriptionConstants.ATTRIBUTE_GROUP).set(attributeGroup);
        }
        result.get(ModelDescriptionConstants.EXPRESSIONS_ALLOWED).set(isAllowExpression());
        result.get(ModelDescriptionConstants.REQUIRED).set(isRequired());
        result.get(ModelDescriptionConstants.NILLABLE).set(isNillable());
        if (!forOperation && nilSignificant != null) {
            if (nilSignificant) {
                result.get(ModelDescriptionConstants.NIL_SIGNIFICANT).set(true);
            }
        }
        if (defaultValue != null && defaultValue.isDefined()) {
            result.get(ModelDescriptionConstants.DEFAULT).set(defaultValue);
        }
        if (measurementUnit != null && measurementUnit != MeasurementUnit.NONE) {
            result.get(ModelDescriptionConstants.UNIT).set(measurementUnit.getName());
        }
        if (alternatives != null) {
            for(final String alternative : alternatives) {
                result.get(ModelDescriptionConstants.ALTERNATIVES).add(alternative);
            }
        }
        if (requires != null) {
            for(final String required : requires) {
                result.get(ModelDescriptionConstants.REQUIRES).add(required);
            }
        }

        if (getReferenceRecorder() != null) {
            String[] capabilityPatternElements = getReferenceRecorder().getRequirementPatternSegments(name, PathAddress.EMPTY_ADDRESS);
            result.get(ModelDescriptionConstants.CAPABILITY_REFERENCE).set(getReferenceRecorder().getBaseRequirementName());
            if(capabilityPatternElements.length > 0 && (capabilityPatternElements.length > 1 || !name.equals(capabilityPatternElements[0]))) {
                for(String patternElement : capabilityPatternElements) {
                    result.get(ModelDescriptionConstants.CAPABILITY_REFERENCE_PATTERN_ELEMENTS).add(patternElement);
                }
            }
        }

        if (validator instanceof MinMaxValidator) {
            MinMaxValidator minMax = (MinMaxValidator) validator;
            Long min = minMax.getMin();
            if (min != null) {
                switch (this.type) {
                    case STRING:
                    case LIST:
                    case OBJECT:
                    case BYTES:
                        result.get(ModelDescriptionConstants.MIN_LENGTH).set(min);
                        break;
                    default:
                        result.get(ModelDescriptionConstants.MIN).set(min);
                }
            }
            Long max = minMax.getMax();
            if (max != null) {
                switch (this.type) {
                    case STRING:
                    case LIST:
                    case OBJECT:
                    case BYTES:
                        result.get(ModelDescriptionConstants.MAX_LENGTH).set(max);
                        break;
                    default:
                        result.get(ModelDescriptionConstants.MAX).set(max);
                }
            }
        }
        addAllowedValuesToDescription(result, validator);
        arbitraryDescriptors.forEach((key, value) -> {
            assert !result.hasDefined(key); //You can't override an arbitrary descriptor set through other properties.
            result.get(key).set(value);
        });
        return result;
    }

    /**
     * Based on the given attribute value, add capability requirements. If this definition
     * is for an attribute whose value is or contains a reference to the name of some capability,
     * this method should record the addition of a requirement for the capability.
     * <p>
     * This is a no-op in this base class. Subclasses that support attribute types that can represent
     * capability references should override this method.
     * @param context the operation context
     * @param attributeValue the value of the attribute described by this object
     * @deprecated use @{link {@link #addCapabilityRequirements(OperationContext, Resource, ModelNode)}} variant
     */
    @Deprecated
    public void addCapabilityRequirements(OperationContext context, ModelNode attributeValue) {
        addCapabilityRequirements(context, null, attributeValue);
    }
    /**
     * Based on the given attribute value, add capability requirements. If this definition
     * is for an attribute whose value is or contains a reference to the name of some capability,
     * this method should record the addition of a requirement for the capability.
     * <p>
     * This is a no-op in this base class. Subclasses that support attribute types that can represent
     * capability references should override this method.
     *  @param context the operation context
     * @param resource
     * @param attributeValue the value of the attribute described by this object
     */
    public void addCapabilityRequirements(OperationContext context, Resource resource, ModelNode attributeValue) {
        @SuppressWarnings("deprecation")
        CapabilityReferenceRecorder refRecorder = getReferenceRecorder();
        if (refRecorder != null) {
            // We can't process expressions
            if (attributeValue.getType() != ModelType.EXPRESSION) {
                ModelNode value = attributeValue.isDefined() ? attributeValue : (defaultValue != null) ? defaultValue : new ModelNode();
                refRecorder.addCapabilityRequirements(context, resource, name, value.isDefined() ? value.asString() : null);
            }
        }
    }

    /**
     * Based on the given attribute value, remove capability requirements. If this definition
     * is for an attribute whose value is or contains a reference to the name of some capability,
     * this method should record the removal of a requirement for the capability.
     * <p>
     * This is a no-op in this base class. Subclasses that support attribute types that can represent
     * capability references should override this method.
     *
     * @param context        the operation context
     * @param attributeValue the value of the attribute described by this object
     * @deprecated use {@link #removeCapabilityRequirements(OperationContext, Resource, ModelNode)} variant
     */
    @Deprecated
    public void removeCapabilityRequirements(OperationContext context, ModelNode attributeValue) {
        removeCapabilityRequirements(context, null, attributeValue);
    }

    /**
     * Based on the given attribute value, remove capability requirements. If this definition
     * is for an attribute whose value is or contains a reference to the name of some capability,
     * this method should record the removal of a requirement for the capability.
     * <p>
     * This is a no-op in this base class. Subclasses that support attribute types that can represent
     * capability references should override this method.
     * @param context the operation context
     * @param resource resource from which capability requirement is to be removed from, <code>null</code> is legal value
     *                 in case that {@link CapabilityReferenceRecorder} doesn't require it.
     * @param attributeValue the value of the attribute described by this object
     */
    public void removeCapabilityRequirements(OperationContext context, Resource resource, ModelNode attributeValue) {
        @SuppressWarnings("deprecation")
        CapabilityReferenceRecorder refRecorder = getReferenceRecorder();
        if (refRecorder != null) {
            // We can't process expressions
            if (attributeValue.getType() != ModelType.EXPRESSION) {
                ModelNode value = attributeValue.isDefined() ? attributeValue : (defaultValue != null) ? defaultValue : new ModelNode();
                refRecorder.removeCapabilityRequirements(context, resource, name, value.isDefined() ? value.asString() : null);
            }
        }
    }

    /**
     * Based on the given attribute value, tell if attribute has any capability requirements.
     * If this definition is for an attribute whose value is or contains a reference to the name of some capability,
     * this method will return true otherwise false.
     * <p>
     * This is a no-op in this base class. Subclasses that support attribute types that can represent
     * capability references should override this method.
     * @return
     */
    public boolean hasCapabilityRequirements(){
        return getReferenceRecorder() != null;
    }

    protected CapabilityReferenceRecorder getReferenceRecorder(){
        //noinspection deprecation
        return referenceRecorder;
    }

    /**
     * Adds the allowed values. Override for attributes who should not use the allowed values.
     *
     * @param result the node to add the allowed values to
     * @param validator the validator to get the allowed values from
     */
    protected void addAllowedValuesToDescription(ModelNode result, ParameterValidator validator) {
        if (allowedValues != null) {
            for (ModelNode allowedValue : allowedValues) {
                result.get(ModelDescriptionConstants.ALLOWED).add(allowedValue);
            }
        } else if (validator instanceof AllowedValuesValidator) {
            AllowedValuesValidator avv = (AllowedValuesValidator) validator;
            List<ModelNode> allowed = avv.getAllowedValues();
            if (allowed != null) {
                for (ModelNode ok : allowed) {
                    result.get(ModelDescriptionConstants.ALLOWED).add(ok);
                }
            }
        }
    }

    /**
     * Corrects the value if the {@link ParameterCorrector value corrector} is not {@code null}. If the {@link
     * ParameterCorrector value corrector} is {@code null}, the {@code newValue} parameter is returned.
     *
     * @param newValue the new value.
     * @param oldValue the old value.
     *
     * @return the corrected value or the {@code newValue} if the {@link ParameterCorrector value corrector} is {@code
     *         null}.
     */
    protected final ModelNode correctValue(final ModelNode newValue, final ModelNode oldValue) {
        if (valueCorrector != null) {
            return valueCorrector.correct(newValue, oldValue);
        }
        return newValue;
    }

    /**
     * Examine the given operation parameter value for any expression syntax, converting the relevant node to
     * {@link ModelType#EXPRESSION} if such is supported.
     * <p>
     * This implementation checks if {@link #isAllowExpression() expressions are allowed} and if so, calls
     * {@link #convertStringExpression(ModelNode)} to convert a {@link ModelType#STRING} to a {@link ModelType#EXPRESSION}.
     * No other conversions are performed. For use cases requiring more complex behavior, a subclass that overrides
     * this method should be used.
     * </p>
     * <p>
     * If expressions are supported this implementation also checks if the {@link #getType() attribute type} is one of
     * the {@link #COMPLEX_TYPES complex DMR types}. If it is, an {@link IllegalStateException} is thrown, as this
     * implementation cannot properly handle such a combination, and a subclass that overrides this method should be used.
     * </p>
     *
     * @param parameter the node to examine. Cannot not be {@code null}
     * @return a node matching {@code parameter} but with expressions converted, or the original parameter if no
     *         conversion was performed. Will not return {@code null}
     *
     * @throws IllegalStateException if expressions are supported, but the {@link #getType() attribute type} is {@link #COMPLEX_TYPES complex}
     */
    protected ModelNode convertParameterExpressions(final ModelNode parameter) {
        if (isAllowExpression() && COMPLEX_TYPES.contains(type)) {
            // They need to subclass and override
            throw new IllegalStateException();
        }
        return isAllowExpression() ? convertStringExpression(parameter) : parameter;
    }

    /**
     * Checks if the given node is of {@link ModelType#STRING} with a string value that includes expression syntax.
     * If so returns a node of {@link ModelType#EXPRESSION}, else simply returns {@code node} unchanged
     *
     * @param node the node to examine. Will not be {@code null}
     * @return the node with expressions converted, or the original node if no conversion was performed
     *         Cannot return {@code null}
     */
    protected static ModelNode convertStringExpression(ModelNode node) {
        if (node.getType() == ModelType.STRING) {
            return ParseUtils.parsePossibleExpression(node.asString());
        }
        return node;
    }

    private ModelNode validateOperation(final ModelNode operationObject, final boolean immutableValue) throws OperationFailedException {

        ModelNode node = new ModelNode();
        if(operationObject.has(name)) {
            node.set(operationObject.get(name));
        }

        if (!immutableValue) {
            node = convertParameterExpressions(node);
            node = correctValue(node, node);
        }

        if (!node.isDefined() && defaultValue != null && defaultValue.isDefined()) {
            validator.validateParameter(name, defaultValue);
        } else {
            validator.validateParameter(name, node);
        }

        return convertToExpectedType(node);
    }

    /**
     *
     * @return AttributeMarshaller that provides means to marshal attribute to xml
     * @deprecated use {@link #getMarshaller()}
     */
    @Deprecated
    public AttributeMarshaller getAttributeMarshaller() {
        return attributeMarshaller;
    }

    /**
     *
     * @return attribute marshaller that can be used to persist attribute to XML
     */
    public AttributeMarshaller getMarshaller() {
        //noinspection deprecation
        return attributeMarshaller;
    }

    /**
     * Show if attribute is resource only which means it wont be part of add operations but only present on resource
     * @return true is attribute is resource only
     */
    public boolean isResourceOnly() {
        return resourceOnly;
    }

    /**
     *
     * @return true if attribute is deprecated
     */
    public boolean isDeprecated() {
        return deprecationData != null;
    }

    /**
     * return deprecation data if there is any
     * @return {@link DeprecationData}
     */
    public DeprecationData getDeprecationData() {
        return deprecationData;
    }

    public List<AccessConstraintDefinition> getAccessConstraints() {
        return accessConstraints;
    }

    protected void addAccessConstraints(ModelNode result, Locale locale) {
        AccessConstraintDescriptionProviderUtil.addAccessConstraints(result, accessConstraints, locale);
    }

    public AttributeParser getParser() {
        return parser;
    }

    /**
     * Gets the undefined metric value to use for the attribute if a value cannot be provided.
     *
     * @return the undefined metric value, or {@code null} if no undefined metric value was provided
     */
    public ModelNode getUndefinedMetricValue() {
        return undefinedMetricValue;
    }

    /**
     * Simple {@code Comparable} that encapsulates the name of an attribute and any attribute group,
     * ordering first one group (null group first) and then one attribute name.
     *
     * @author Brian Stansberry (c) 2014 Red Hat Inc.
     */
    public static final class NameAndGroup implements Comparable<NameAndGroup> {

        private final String name;
        private final String group;

        public NameAndGroup(AttributeDefinition ad) {
            this(ad.getName(), ad.getAttributeGroup());
        }

        public NameAndGroup(String name) {
            this.name = name;
            this.group = null;
        }

        public NameAndGroup(String name, String group) {
            this.name = name;
            this.group = group;
        }

        public String getName() {
            return name;
        }

        public String getGroup() {
            return group;
        }

        @Override
        public int compareTo(NameAndGroup o) {

            if (group == null) {
                if (o.group != null) {
                    return -1;
                }
            } else if (o.group == null) {
                return 1;
            } else {
                int groupComp = group.compareTo(o.group);
                if (groupComp != 0) {
                    return groupComp;
                }
            }
            return name.compareTo(o.name);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            NameAndGroup that = (NameAndGroup) o;

            return Objects.equals(this.group, that.group) && name.equals(that.name);

        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + (group != null ? group.hashCode() : 0);
            return result;
        }
    }
}
