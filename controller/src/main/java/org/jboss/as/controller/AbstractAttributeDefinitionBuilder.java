/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.version.FeatureStream;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Provides a builder API for creating an {@link org.jboss.as.controller.AttributeDefinition}.
 *
 * @param <BUILDER> the specific subclass type returned by the various builder API methods
 * @param <ATTRIBUTE> the type of {@link org.jboss.as.controller.AttributeDefinition} produced by the {@link #build()} method
 *
 * @author Tomaz Cerar
 */
@SuppressWarnings("unchecked")
public abstract class AbstractAttributeDefinitionBuilder<BUILDER extends AbstractAttributeDefinitionBuilder<BUILDER, ATTRIBUTE>, ATTRIBUTE extends AttributeDefinition> {

    private final String name;
    private final ModelType type;
    private String xmlName;
    private boolean allowNull;
    private boolean allowExpression;
    private ModelNode defaultValue;
    private MeasurementUnit measurementUnit;
    private String[] alternatives;
    private String[] requires;
    private ModelNode[] allowedValues;
    private ParameterCorrector corrector;
    private ParameterValidator validator;
    private int minSize = 0;
    private boolean minSizeSet;
    private int maxSize = Integer.MAX_VALUE;
    private boolean maxSizeSet;
    private AttributeAccess.Flag[] flags;
    private AttributeMarshaller attributeMarshaller = null;
    private boolean resourceOnly = false;
    private DeprecationData deprecated = null;
    private AccessConstraintDefinition[] accessConstraints;
    private Boolean nullSignificant;
    private AttributeParser parser;
    private String attributeGroup;
    private CapabilityReferenceRecorder referenceRecorder;
    private Map<String, ModelNode> arbitraryDescriptors = null;
    private ModelNode undefinedMetricValue;
    private FeatureStream stream = FeatureStream.FEATURE_DEFAULT;

    private static final AccessConstraintDefinition[] ZERO_CONSTRAINTS = new AccessConstraintDefinition[0];

    /**
     * Creates a builder for an attribute with the give name and type. Equivalent to
     * {@link #AbstractAttributeDefinitionBuilder(String, org.jboss.dmr.ModelType, boolean) AbstractAttributeDefinitionBuilder(attributeName, type, false}
     * @param attributeName the {@link AttributeDefinition#getName() name} of the attribute. Cannot be {@code null}
     * @param type the {@link AttributeDefinition#getType() type} of the attribute. Cannot be {@code null}
     */
    public AbstractAttributeDefinitionBuilder(final String attributeName, final ModelType type) {
        this(attributeName, type, false);
    }

    /**
     * Creates a builder for an attribute with the give name and type and nullability setting.
     * @param attributeName the {@link AttributeDefinition#getName() name} of the attribute. Cannot be {@code null}
     * @param type the {@link AttributeDefinition#getType() type} of the attribute. Cannot be {@code null}
     * @param optional {@code true} if the attribute {@link AttributeDefinition#isNillable() allows undefined values} in the absence of {@link #setAlternatives(String...) alternatives}
     */
    public AbstractAttributeDefinitionBuilder(final String attributeName, final ModelType type, final boolean optional) {
        this.name = attributeName;
        this.type = type;
        this.allowNull = optional;
        this.xmlName = name;
    }

    /**
     * Creates a builder populated with the values of an existing attribute definition.
     *
     * @param basis the existing attribute definition. Cannot be {@code null}
     */
    public AbstractAttributeDefinitionBuilder(final AttributeDefinition basis) {
        this(null, basis);
    }

    /**
     * Creates a builder populated with the values of an existing attribute definition, with an optional
     * change of the attribute's name.
     *
     * @param attributeName the {@link AttributeDefinition#getName() name} of the attribute,
     *                      or {@code null} if the name from {@code basis} should be used
     * @param basis the existing attribute definition. Cannot be {@code null}
     */
    public AbstractAttributeDefinitionBuilder(final String attributeName, final AttributeDefinition basis) {
        this.name = attributeName != null ? attributeName : basis.getName();
        this.xmlName = basis.getXmlName();
        this.defaultValue = basis.getDefaultValue();
        this.type = basis.getType();
        this.allowNull = basis.isNillable();
        this.allowExpression = basis.isAllowExpression();
        this.measurementUnit = basis.getMeasurementUnit();
        this.corrector = basis.getCorrector();
        this.validator = basis.getValidator();
        this.alternatives = basis.getAlternatives();
        this.requires = basis.getRequires();
        this.attributeMarshaller = basis.getMarshaller();
        this.resourceOnly = basis.isResourceOnly();
        this.deprecated = basis.getDeprecationData();
        this.nullSignificant = basis.getNilSignificant();
        List<AccessConstraintDefinition> acl = basis.getAccessConstraints();
        this.accessConstraints = acl.toArray(new AccessConstraintDefinition[acl.size()]);
        this.parser = basis.getParser();
        Set<AttributeAccess.Flag> basisFlags = basis.getFlags();
        this.flags = basisFlags.toArray(new AttributeAccess.Flag[basisFlags.size()]);
        if (!basis.getAllowedValues().isEmpty()) {
            List<ModelNode> basisAllowedValues = basis.getAllowedValues();
            this.allowedValues = basisAllowedValues.toArray(new ModelNode[basisAllowedValues.size()]);
        }
        this.attributeGroup = basis.getAttributeGroup();
        if(!basis.getArbitraryDescriptors().isEmpty()) {
            this.arbitraryDescriptors = new HashMap<>(basis.getArbitraryDescriptors());
        }
        this.referenceRecorder = basis.getReferenceRecorder();
        this.stream = basis.getFeatureStream();
    }

    /**
     * Create the {@link org.jboss.as.controller.AttributeDefinition}
     * @return the attribute definition. Will not return {@code null}
     */
    public abstract ATTRIBUTE build();

    /**
     * Sets the {@link AttributeDefinition#getXmlName() xml name} for the attribute, which is only needed
     * if the name used for the attribute is different from its ordinary
     * {@link AttributeDefinition#getName() name in the model}. If not set the default value is the name
     * passed to the builder constructor.
     *
     * @param xmlName the xml name. {@code null} is allowed
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setXmlName(String xmlName) {
        this.xmlName = xmlName == null ? this.name : xmlName;
        return (BUILDER) this;
    }

    /**
     * Sets whether the attribute should {@link AttributeDefinition#isRequired() require a defined value}
     * in the absence of {@link #setAlternatives(String...) alternatives}.
     * If not set the default value is the value provided to the builder constructor, or {@code true}
     * if no value is provided.
     *
     * @param required {@code true} if undefined values should not be allowed in the absence of alternatives
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setRequired(boolean required) {
        this.allowNull = !required;
        return (BUILDER) this;
    }

    /**
     * Sets whether the attribute should {@link AttributeDefinition#isAllowExpression() allow expressions}
     * If not set the default value is {@code false}.
     *
     * @param allowExpression {@code true} if expression values should be allowed
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setAllowExpression(boolean allowExpression) {
        this.allowExpression = allowExpression;
        return (BUILDER) this;
    }

    /**
     * Sets a {@link AttributeDefinition#getDefaultValue() default value} to use for the attribute if no
     * user-provided value is available.
     * @param defaultValue the default value, or {@code null} if no default should be used
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setDefaultValue(ModelNode defaultValue) {
        this.defaultValue = (defaultValue == null || !defaultValue.isDefined()) ? null : defaultValue;
        return (BUILDER) this;
    }

    /**
     * Sets a {@link AttributeDefinition#getMeasurementUnit() measurement unit} to describe the unit in
     * which a numeric attribute is expressed.
     * @param unit the unit. {@code null} is allowed
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setMeasurementUnit(MeasurementUnit unit) {
        this.measurementUnit = unit;
        return (BUILDER) this;
    }

    /**
     * Sets a {@link org.jboss.as.controller.ParameterCorrector} to use to adjust any user provided values
     * before {@link org.jboss.as.controller.AttributeDefinition#validateOperation(ModelNode) validation}
     * occurs.
     * @param corrector the corrector. May be {@code null}
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setCorrector(ParameterCorrector corrector) {
        this.corrector = corrector;
        return (BUILDER) this;
    }

    /**
     * Sets the validator that should be used to validate attribute values. The resulting attribute definition
     * will wrap this validator in one that enforces the attribute's
     * {@link AttributeDefinition#isNillable() allow null} and
     * {@link AttributeDefinition#isAllowExpression() allow expression} settings, so the given {@code validator}
     * need not be properly configured for those validations.
     * @param validator the validator. {@code null} is allowed
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setValidator(ParameterValidator validator) {
        this.validator = validator;
        return (BUILDER) this;
    }

    /**
     * Sets {@link AttributeDefinition#getAlternatives() names of alternative attributes} that should not
     * be defined if this attribute is defined.
     * @param alternatives the attribute names
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setAlternatives(String... alternatives) {
        this.alternatives = alternatives;
        return (BUILDER) this;
    }

    /**
     * Adds {@link AttributeDefinition#getAlternatives() names of alternative attributes} that should not
     * be defined if this attribute is defined.
     * @param alternatives the attribute names
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER addAlternatives(String... alternatives) {
        if (this.alternatives == null) {
            this.alternatives = alternatives;
        } else {
            String[] newAlternatives = Arrays.copyOf(this.alternatives, this.alternatives.length + alternatives.length);
            System.arraycopy(alternatives, 0, newAlternatives, this.alternatives.length, alternatives.length);
            this.alternatives = newAlternatives;
        }
        return (BUILDER) this;
    }

    /**
     * Removes {@link AttributeDefinition#getAlternatives() names of alternative attributes} from the set of those that should not
     * be defined if this attribute is defined.
     * @param alternatives the attribute names
     * @return a builder that can be used to continue building the attribute definition
     */
    public final BUILDER removeAlternatives(String... alternatives) {
        if (this.alternatives == null) {
            return (BUILDER) this;
        } else {
            for (String alternative : alternatives) {
                if (isAlternativePresent(alternative)) {
                    int length = this.alternatives.length;
                    String[] newAlternatives = new String[length - 1];
                    int k = 0;
                    for (String alt : this.alternatives) {
                        if (!alt.equals(alternative)) {
                            newAlternatives[k] = alt;
                            k++;
                        }
                    }
                    this.alternatives = newAlternatives;
                }
            }
        }
        return (BUILDER) this;
    }

    /**
     * Checks if an alternative has been recorded in {@link AttributeDefinition#getAlternatives() names of alternative attributes}
     * @param alternative the alternative
     * @return a builder that can be used to continue building the attribute definition
     */
    private boolean isAlternativePresent(final String alternative) {
        if (alternatives == null) {
            return false;
        }
        for (String alt : alternatives) {
            if (alt.equals(alternative)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds {@link AttributeDefinition#getArbitraryDescriptors() arbitrary descriptor}.
     * @param arbitraryDescriptor the arbitrary descriptor name.
     * @param value the value of the arbitrary descriptor.
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER addArbitraryDescriptor(String arbitraryDescriptor, ModelNode value) {
        if (this.arbitraryDescriptors == null) {
            this.arbitraryDescriptors = Collections.singletonMap(arbitraryDescriptor, value);
        } else {
            if (this.arbitraryDescriptors.size() == 1) {
                this.arbitraryDescriptors = new HashMap<>(this.arbitraryDescriptors);
            }
            arbitraryDescriptors.put(arbitraryDescriptor, value);
        }
        return (BUILDER) this;
    }

    @SuppressWarnings("WeakerAccess")
    public Map<String, ModelNode> getArbitraryDescriptors() {
        return arbitraryDescriptors;
    }

    /**
     * Sets {@link AttributeDefinition#getRequires() names of required attributes} that must
     * be defined if this attribute is defined.
     * @param requires the attribute names
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setRequires(String... requires) {
        this.requires = requires;
        return (BUILDER) this;
    }

    /**
     * Sets the {@link AttributeAccess.Flag special purpose flags} that are relevant to the attribute
     * @param flags the flags
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setFlags(AttributeAccess.Flag... flags) {
        this.flags = flags;
        return (BUILDER) this;
    }

    /**
     * Adds a {@link AttributeAccess.Flag special purpose flag} that is relevant to the attribute
     * @param flag the flag
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER addFlag(final AttributeAccess.Flag flag) {
        if (flags == null) {
            flags = new AttributeAccess.Flag[]{flag};
        } else {
            final int i = flags.length;
            flags = Arrays.copyOf(flags, i + 1);
            flags[i] = flag;
        }
        return (BUILDER) this;
    }

    /**
     * Removes a {@link AttributeAccess.Flag special purpose flag} from the set of those relevant to the attribute
     * @param flag the flag
     * @return a builder that can be used to continue building the attribute definition
     */
    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    public BUILDER removeFlag(final AttributeAccess.Flag flag) {
        if (!isFlagPresent(flag)) {
            return (BUILDER) this; //if not present no need to remove
        }
        final int length = flags.length;
        final AttributeAccess.Flag[] newFlags = new AttributeAccess.Flag[length - 1];
        int k = 0;
        for (AttributeAccess.Flag flag1 : flags) {
            if (flag1 != flag) {
                newFlags[k] = flag1;
                k++;
            }
        }
        flags = newFlags;
        return (BUILDER) this;
    }

    /**
     * Checks if a {@link AttributeAccess.Flag special purpose flag} has been recorded as relevant to the attribute
     * @param flag the flag
     * @return a builder that can be used to continue building the attribute definition
     */
    @SuppressWarnings("WeakerAccess")
    protected boolean isFlagPresent(final AttributeAccess.Flag flag) {
        if (flags == null) { return false; }
        for (AttributeAccess.Flag f : flags) {
            if (f.equals(flag)) { return true; }
        }
        return false;
    }

    /**
     * Adds the {@link AttributeAccess.Flag#STORAGE_RUNTIME} flag and removes any conflicting flag.
     *
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setStorageRuntime() {
        removeFlag(AttributeAccess.Flag.STORAGE_CONFIGURATION);
        return addFlag(AttributeAccess.Flag.STORAGE_RUNTIME);
    }

    /**
     * Adds the {@link AttributeAccess.Flag#RUNTIME_SERVICE_NOT_REQUIRED} flag.
     *
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setRuntimeServiceNotRequired() {
        return addFlag(AttributeAccess.Flag.RUNTIME_SERVICE_NOT_REQUIRED);
    }

    /**
     * Adds the {@link AttributeAccess.Flag#RESTART_ALL_SERVICES} flag and removes any conflicting flag.
     *
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setRestartAllServices() {
        removeFlag(AttributeAccess.Flag.RESTART_NONE);
        removeFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES);
        removeFlag(AttributeAccess.Flag.RESTART_JVM);
        return addFlag(AttributeAccess.Flag.RESTART_ALL_SERVICES);
    }

    /**
     * Adds the {@link AttributeAccess.Flag#RESTART_JVM} flag and removes any conflicting flag.
     *
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setRestartJVM() {
        removeFlag(AttributeAccess.Flag.RESTART_NONE);
        removeFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES);
        removeFlag(AttributeAccess.Flag.RESTART_ALL_SERVICES);
        return addFlag(AttributeAccess.Flag.RESTART_JVM);
    }

    /**
     * Sets a maximum size for a collection-type attribute or one whose value is a string or byte[].
     * The value represents the maximum number of elements in the collection, or the maximum length of
     * the string or array. <strong>It does not represent a maximum value for a numeric attribute and should
     * not be configured for numeric attributes.</strong>
     * @param maxSize the maximum size
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setMaxSize(final int maxSize) {
        this.maxSize = maxSize;
        this.maxSizeSet = true;
        return (BUILDER) this;
    }

    /**
     * Gets the {@link #getMaxSize() max size} if {@link #setMaxSize(int)} has been called, otherwise {@code null}.
     * This is a workaround needed because maxSize is protected, preventing changing its type to Integer to allow null
     * values needed to discriminate unconfigured defaults from configured values that match the default.
     * @return the minimum size or {@code null} if it was not explicitly set
     */
    Integer getConfiguredMaxSize()  {
        return maxSizeSet ? maxSize : null;
    }

    /**
     * Sets a minimum size description for a collection-type attribute or one whose value is a string or byte[].
     * The value represents the minimum number of elements in the collection, or the minimum length of
     * the string or array. <strong>It does not represent a minimum value for a numeric attribute and should
     * not be configured for numeric attributes.</strong>
     * @param minSize the minimum size
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setMinSize(final int minSize) {
        this.minSize = minSize;
        this.minSizeSet = true;
        return (BUILDER) this;
    }

    /**
     * Gets the {@link #getMinSize() min size} if {@link #setMinSize(int)} has been called, otherwise {@code null}.
     * This is a workaround needed because minSize is protected, preventing changing its type to Integer to allow null
     * values needed to discriminate unconfigured defaults from configured values that match the default.
     * @return the minimum size or {@code null} if it was not explicitly set
     */
    Integer getConfiguredMinSize()  {
        return minSizeSet ? minSize : null;
    }

    /**
     * Sets a custom {@link org.jboss.as.controller.AttributeMarshaller} to use for marshalling the attribute to xml.
     * If not set, a {@link org.jboss.as.controller.DefaultAttributeMarshaller} will be used.
     * @param marshaller the marshaller. Can be {@code null}
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setAttributeMarshaller(AttributeMarshaller marshaller) {
        this.attributeMarshaller = marshaller;
        return (BUILDER) this;
    }
    /**
     * Sets a custom {@link org.jboss.as.controller.AttributeParser} to use for parsing attribute from xml.
     * If not set, a {@link org.jboss.as.controller.AttributeParser#SIMPLE} will be used.
     * @param parser the parser. Can be {@code null}
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setAttributeParser(AttributeParser parser) {
        this.parser = parser;
        return (BUILDER) this;
    }

    /**
     * Marks an attribute as only relevant to a resource, and not a valid parameter to an "add" operation that
     * creates that resource. Typically used for legacy "name" attributes that display the final value in the
     * resource's address as an attribute.
     *
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setResourceOnly() {
        this.resourceOnly = true;
        return (BUILDER) this;
    }

    /**
     * Marks the attribute as deprecated since the given API version. This is equivalent to calling
     * {@link #setDeprecated(ModelVersion, boolean)} with the {@code notificationUseful} parameter
     * set to {@code true}.
     *
     * @param since the API version, with the API being the one (core or a subsystem) in which the attribute is used
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setDeprecated(ModelVersion since) {
        return setDeprecated(since, true);
    }

    /**
     * Marks the attribute as deprecated since the given API version, with the ability to configure that
     * notifications to the user (e.g. via a log message) about deprecation of the attribute should not be emitted.
     * Notifying the user should only be done if the user can take some action in response. Advising that
     * something will be removed in a later release is not useful if there is no alternative in the
     * current release. If the {@code notificationUseful} param is {@code true} the text
     * description of the attribute deprecation available from the {@code read-resource-description}
     * management operation should provide useful information about how the user can avoid using
     * the attribute.
     *
     * @param since the API version, with the API being the one (core or a subsystem) in which the attribute is used
     * @param notificationUseful whether actively advising the user about the deprecation is useful
     *
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setDeprecated(ModelVersion since, boolean notificationUseful) {
        this.deprecated = new DeprecationData(since, notificationUseful);
        return (BUILDER) this;
    }

    /**
     * Marks that support for use of an expression for the attribute's value is deprecated and
     * may be removed in a future release.
     *
     * @return a builder that can be used to continue building the attribute definition
     */
    public final BUILDER setExpressionsDeprecated() {
        return addFlag(AttributeAccess.Flag.EXPRESSIONS_DEPRECATED);
    }

    /**
     * Sets access constraints to use with the attribute
     * @param accessConstraints the constraints
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setAccessConstraints(AccessConstraintDefinition... accessConstraints) {
        this.accessConstraints = accessConstraints;
        return (BUILDER) this;
    }

    /**
     * Adds an access constraint to the set used with the attribute
     * @param accessConstraint the constraint
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER addAccessConstraint(final AccessConstraintDefinition accessConstraint) {
        if (accessConstraints == null) {
            accessConstraints = new AccessConstraintDefinition[] {accessConstraint};
        } else {
            accessConstraints = Arrays.copyOf(accessConstraints, accessConstraints.length + 1);
            accessConstraints[accessConstraints.length - 1] = accessConstraint;
        }
        return (BUILDER) this;
    }

    /**
     * Sets whether an access control check is required to implicitly set an attribute to {@code undefined}
     * in a resource "add" operation. "Implicitly" setting an attribute refers to not providing a value for
     * it in the add operation, leaving the attribute in an undefined state. If not set
     * the default value is whether the attribute {@link AttributeDefinition#isRequired()} () is not required} and
     * has a {@link AttributeDefinition#getDefaultValue() default value}.
     *
     * @param nullSignificant {@code true} if an undefined value is significant; {@code false} if it is not significant,
     *                                    even if a default value is configured
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setNullSignificant(boolean nullSignificant) {
        this.nullSignificant = nullSignificant;
        return (BUILDER) this;
    }

    /**
     * Sets the name of the attribute group with which this attribute is associated.
     *
     * @param attributeGroup the attribute group name. Cannot be an empty string but can be {@code null}
     *                       if the attribute is not associated with a group.
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setAttributeGroup(String attributeGroup) {
        assert attributeGroup == null || attributeGroup.length() > 0;
        this.attributeGroup = attributeGroup;
        return (BUILDER) this;
    }

    /**
     * Sets allowed values for attribute
     *
     * @param allowedValues values that are legal as part in this attribute
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setAllowedValues(ModelNode ... allowedValues) {
        assert allowedValues!= null;
        this.allowedValues = allowedValues;
        return (BUILDER) this;
    }

    /**
     * Sets allowed values for attribute
     *
     * @param allowedValues values that are legal as part in this attribute
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setAllowedValues(String ... allowedValues) {
        assert allowedValues!= null;
        this.allowedValues = new ModelNode[allowedValues.length];
        for (int i = 0; i < allowedValues.length; i++) {
            this.allowedValues[i] = new ModelNode(allowedValues[i]);
        }
        return (BUILDER) this;
    }/**
     * Sets allowed values for attribute
     *
     * @param allowedValues values that are legal as part in this attribute
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setAllowedValues(int ... allowedValues) {
        assert allowedValues!= null;
        this.allowedValues = new ModelNode[allowedValues.length];
        for (int i = 0; i < allowedValues.length; i++) {
            this.allowedValues[i] = new ModelNode(allowedValues[i]);
        }
        return (BUILDER) this;
    }

    /**
     * Records that this attribute's value represents a reference to an instance of a
     * {@link org.jboss.as.controller.capability.RuntimeCapability#isDynamicallyNamed() dynamic capability}.
     * <p>
     * This method is a convenience method equivalent to calling
     * {@link #setCapabilityReference(CapabilityReferenceRecorder)}
     * passing in a {@link org.jboss.as.controller.CapabilityReferenceRecorder.DefaultCapabilityReferenceRecorder}
     * constructed using the parameters passed to this method.
     *
     * @param referencedCapability the name of the dynamic capability the dynamic portion of whose name is
     *                             represented by the attribute's value
     * @param dependentCapability  the capability that depends on {@code referencedCapability}
     * @return the builder
     * @see AttributeDefinition#addCapabilityRequirements(OperationContext, org.jboss.as.controller.registry.Resource, ModelNode)
     * @see AttributeDefinition#removeCapabilityRequirements(OperationContext, org.jboss.as.controller.registry.Resource, ModelNode)
     */
    public BUILDER setCapabilityReference(String referencedCapability, RuntimeCapability<?> dependentCapability) {
        if (dependentCapability.isDynamicallyNamed()) {
            return setCapabilityReference(referencedCapability, dependentCapability.getName());
        } else {
            referenceRecorder = new CapabilityReferenceRecorder.DefaultCapabilityReferenceRecorder(referencedCapability, dependentCapability.getName());
            return (BUILDER) this;
        }
    }

    /**
     * Records that this attribute's value represents a reference to an instance of a
     * {@link org.jboss.as.controller.capability.RuntimeCapability#isDynamicallyNamed() dynamic capability}.
     * <p>
     * This method is a convenience method equivalent to calling
     * {@link #setCapabilityReference(CapabilityReferenceRecorder)}
     * passing in a {@link CapabilityReferenceRecorder.ContextDependencyRecorder}
     * constructed using the parameters passed to this method.
     * <p>
     * <strong>NOTE:</strong> This method of recording capability references is only suitable for use in attributes
     * only used in resources that themselves expose a single capability.
     * If your resource exposes more than single you should use {@link #setCapabilityReference(RuntimeCapability, String, AttributeDefinition...)} variant
     * When the capability requirement is registered, the dependent capability will be that capability.
     *
     * @param referencedCapability the name of the dynamic capability the dynamic portion of whose name is
     *                             represented by the attribute's value
     * @return the builder
     * @see AttributeDefinition#addCapabilityRequirements(OperationContext, org.jboss.as.controller.registry.Resource, ModelNode)
     * @see AttributeDefinition#removeCapabilityRequirements(OperationContext, org.jboss.as.controller.registry.Resource, ModelNode)
     */
    public BUILDER setCapabilityReference(String referencedCapability) {
        referenceRecorder = new CapabilityReferenceRecorder.ContextDependencyRecorder(referencedCapability);
        return (BUILDER) this;
    }

    /**
     * Records that this attribute's value represents a reference to an instance of a
     * {@link org.jboss.as.controller.capability.RuntimeCapability#isDynamicallyNamed() dynamic capability}.
     * <p>
     * This method is a convenience method equivalent to calling * {@link #setCapabilityReference(CapabilityReferenceRecorder)}
     * passing in a {@link CapabilityReferenceRecorder.CompositeAttributeDependencyRecorder}
     * constructed using the parameters passed to this method.
     * <p>
     * <strong>NOTE:</strong> This method of recording capability references is only suitable for use in attributes
     * only used in resources that themselves expose a single capability.
     * If your resource exposes more than single capability, you should use {@link #setCapabilityReference(RuntimeCapability, String, AttributeDefinition...)}
     * When the capability requirement
     * is registered, the dependent capability will be that capability.
     *
     * @param referencedCapability the name of the dynamic capability the dynamic portion of whose name is
     *                             represented by the attribute's value
     *  @param dependantAttributes attribute from same resource that will be used to derive multiple dynamic parts for the dependant capability
     * @return the builder
     * @see AttributeDefinition#addCapabilityRequirements(OperationContext, org.jboss.as.controller.registry.Resource, ModelNode)
     * @see AttributeDefinition#removeCapabilityRequirements(OperationContext, org.jboss.as.controller.registry.Resource, ModelNode)
     */
    public BUILDER setCapabilityReference(String referencedCapability, AttributeDefinition ... dependantAttributes) {
        referenceRecorder = new CapabilityReferenceRecorder.CompositeAttributeDependencyRecorder(referencedCapability, dependantAttributes);
        return (BUILDER) this;
    }

    /**
     * Records that this attribute's value represents a reference to an instance of a
     * {@link org.jboss.as.controller.capability.RuntimeCapability#isDynamicallyNamed() dynamic capability}.
     * <p>
     * This method is a convenience method equivalent to calling {@link #setCapabilityReference(CapabilityReferenceRecorder)}
     * passing in a {@link CapabilityReferenceRecorder.CompositeAttributeDependencyRecorder}
     * constructed using the parameters passed to this method.
     * <p>
     * When the capability requirement is registered, the dependent capability will be that capability.
     *
     * @param capability requirement capability
     * @param referencedCapability the name of the dynamic capability the dynamic portion of whose name is
     *                             represented by the attribute's value
     * @param dependantAttributes attributes on resource which will be used for registering capability reference, can be multiple.
     * @return the builder
     * @see AttributeDefinition#addCapabilityRequirements(OperationContext, org.jboss.as.controller.registry.Resource, ModelNode)
     * @see AttributeDefinition#removeCapabilityRequirements(OperationContext, org.jboss.as.controller.registry.Resource, ModelNode)
     */
    public BUILDER setCapabilityReference(RuntimeCapability capability, String referencedCapability, AttributeDefinition ... dependantAttributes) {
        referenceRecorder = new CapabilityReferenceRecorder.CompositeAttributeDependencyRecorder(capability, referencedCapability, dependantAttributes);
        return (BUILDER) this;
    }

    /**
     * Records that this attribute's value represents a reference to an instance of a
     * {@link org.jboss.as.controller.capability.RuntimeCapability#isDynamicallyNamed() dynamic capability}.
     * <p>
     * This method is a convenience method equivalent to calling
     * {@link #setCapabilityReference(CapabilityReferenceRecorder)}
     * passing in a {@link org.jboss.as.controller.CapabilityReferenceRecorder.DefaultCapabilityReferenceRecorder}
     * constructed using the parameters passed to this method.
     *
     * @param referencedCapability the name of the dynamic capability the dynamic portion of whose name is
     *                             represented by the attribute's value
     * @param dependentCapability  the name of the capability that depends on {@code referencedCapability}
     * @return the builder
     * @see AttributeDefinition#addCapabilityRequirements(OperationContext, org.jboss.as.controller.registry.Resource, ModelNode)
     * @see AttributeDefinition#removeCapabilityRequirements(OperationContext, org.jboss.as.controller.registry.Resource, ModelNode)
     */
    public BUILDER setCapabilityReference(String referencedCapability, String dependentCapability) {
        referenceRecorder = new CapabilityReferenceRecorder.DefaultCapabilityReferenceRecorder(referencedCapability, dependentCapability);
        return (BUILDER) this;
    }

    /**
     * Records that this attribute's value represents a reference to an instance of a
     * {@link org.jboss.as.controller.capability.RuntimeCapability#isDynamicallyNamed() dynamic capability} and assigns the
     * object that should be used to handle adding and removing capability requirements.
     *
     * @param referenceRecorder recorder to handle adding and removing capability requirements. May be {@code null}
     * @return the builder
     *
     * @see AttributeDefinition#addCapabilityRequirements(OperationContext, org.jboss.as.controller.registry.Resource, ModelNode)
     * @see AttributeDefinition#removeCapabilityRequirements(OperationContext, org.jboss.as.controller.registry.Resource, ModelNode)
     */
    public BUILDER setCapabilityReference(CapabilityReferenceRecorder referenceRecorder) {
        this.referenceRecorder = referenceRecorder;
        return (BUILDER)this;
    }

    /**
     * Sets a {@link AttributeDefinition#getUndefinedMetricValue()  default value} to use for the
     * metric if no runtime value is available (e.g. we are a server running in admin-only mode).
     *
     * @param undefinedMetricValue the default value, or {@code null} if no default should be used
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setUndefinedMetricValue(ModelNode undefinedMetricValue) {
        this.undefinedMetricValue = (undefinedMetricValue == null || !undefinedMetricValue.isDefined()) ? null : undefinedMetricValue;
        return (BUILDER) this;
    }

    /**
     * Defines the feature stream for which this attribute should be registered.
     * @param stream a feature stream
     * @return a reference to this builder
     */
    public BUILDER setFeatureStream(FeatureStream stream) {
        this.stream = stream;
        return (BUILDER) this;
    }

    public String getName() {
        return name;
    }

    public ModelType getType() {
        return type;
    }

    public String getXmlName() {
        return xmlName;
    }

    public boolean isNillable() {
        return allowNull;
    }

    public boolean isAllowExpression() {
        return allowExpression;
    }

    public ModelNode getDefaultValue() {
        return defaultValue;
    }

    @SuppressWarnings("WeakerAccess")
    public MeasurementUnit getMeasurementUnit() {
        return measurementUnit;
    }

    public String[] getAlternatives() {
        return copyStrings(alternatives);
    }

    public String[] getRequires() {
        return copyStrings(requires);
    }

    @SuppressWarnings("WeakerAccess")
    public ParameterCorrector getCorrector() {
        return corrector;
    }

    public ParameterValidator getValidator() {
        return validator;
    }

    public int getMinSize() {
        return minSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public AttributeAccess.Flag[] getFlags() {
        return copyFlags(flags);
    }

    @SuppressWarnings("WeakerAccess")
    public AttributeMarshaller getAttributeMarshaller() {
        return attributeMarshaller;
    }

    @SuppressWarnings("WeakerAccess")
    public boolean isResourceOnly() {
        return resourceOnly;
    }

    public DeprecationData getDeprecated() {
        return deprecated;
    }

    public AccessConstraintDefinition[] getAccessConstraints() {
        return copyConstraints(accessConstraints);
    }

    @SuppressWarnings("WeakerAccess")
    public Boolean getNullSignificant() {
        return nullSignificant;
    }

    @SuppressWarnings("WeakerAccess")
    public ModelNode getUndefinedMetricValue() {
        return undefinedMetricValue;
    }

    public AttributeParser getParser() {
        return parser;
    }

    public String getAttributeGroup() {
        return attributeGroup;
    }

    public ModelNode[] getAllowedValues() {
        return allowedValues;
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    protected final CapabilityReferenceRecorder getCapabilityReferenceRecorder() {
        return referenceRecorder;
    }

    public FeatureStream getFeatureStream() {
        return this.stream;
    }

    private String[] copyStrings(String[] toCopy) {
        if (toCopy == null) {
            return null;
        }
        String[] result = new String[toCopy.length];
        System.arraycopy(toCopy, 0, result, 0, toCopy.length);
        return result;
    }

    private AttributeAccess.Flag[] copyFlags(AttributeAccess.Flag[] toCopy) {
        if (toCopy == null) {
            return null;
        }
        AttributeAccess.Flag[] result = new AttributeAccess.Flag[toCopy.length];
        System.arraycopy(toCopy, 0, result, 0, toCopy.length);
        return result;
    }

    private AccessConstraintDefinition[] copyConstraints(AccessConstraintDefinition[] toCopy) {
        if (toCopy == null) {
            return null;
        }
        if (toCopy.length == 0){
            return ZERO_CONSTRAINTS;
        }
        AccessConstraintDefinition[] result = new AccessConstraintDefinition[toCopy.length];
        System.arraycopy(toCopy, 0, result, 0, toCopy.length);
        return result;
    }

}
