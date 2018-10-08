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
public abstract class AbstractAttributeDefinitionBuilder<BUILDER extends AbstractAttributeDefinitionBuilder, ATTRIBUTE extends AttributeDefinition> {

    /** @deprecated use {@link #getName()} as this field will be made private in a future release */
    @Deprecated
    protected String name;
    /** @deprecated use {@link #getType()} as this field will be made private in a future release */
    @Deprecated
    protected ModelType type;
    /** @deprecated use {@link #getXmlName()} as this field will be made private in a future release */
    @Deprecated
    protected String xmlName;
    /** @deprecated use {@link #isAllowNull()} as this field will be made private in a future release */
    @Deprecated
    protected boolean allowNull;
    /** @deprecated use {@link #isAllowExpression()} as this field will be made private in a future release */
    @Deprecated
    protected boolean allowExpression;
    /** @deprecated use {@link #getDefaultValue()} as this field will be made private in a future release */
    @Deprecated
    protected ModelNode defaultValue;
    /** @deprecated use {@link #getMeasurementUnit()} as this field will be made private in a future release */
    @Deprecated
    protected MeasurementUnit measurementUnit;
    /** @deprecated use {@link #getAlternatives()} as this field will be made private in a future release */
    @Deprecated
    protected String[] alternatives;
    /** @deprecated use {@link #getRequires()} as this field will be made private in a future release */
    @Deprecated
    protected String[] requires;
    /** @deprecated use {@link #getAllowedValues()} as this field will be made private in a future release */
    @Deprecated
    protected ModelNode[] allowedValues;
    /** @deprecated use {@link #getCorrector()} as this field will be made private in a future release */
    @Deprecated
    protected ParameterCorrector corrector;
    /** @deprecated use {@link #getValidator()} as this field will be made private in a future release */
    @Deprecated
    protected ParameterValidator validator;
    /** @deprecated use {@link #isValidateNull()} as this field will be made private in a future release */
    @Deprecated
    protected boolean validateNull = true;
    /** @deprecated use {@link #getName()} as this field will be made private in a future release */
    @Deprecated
    protected int minSize = 0;
    private boolean minSizeSet;
    /** @deprecated use {@link #getMaxSize()} as this field will be made private in a future release */
    @Deprecated
    protected int maxSize = Integer.MAX_VALUE;
    private boolean maxSizeSet;
    /** @deprecated use {@link #getFlags()} as this field will be made private in a future release */
    @Deprecated
    protected AttributeAccess.Flag[] flags;
    /** @deprecated use {@link #getAttributeMarshaller()} as this field will be made private in a future release */
    @Deprecated
    protected AttributeMarshaller attributeMarshaller = null;
    /** @deprecated use {@link #isResourceOnly()} as this field will be made private in a future release */
    @Deprecated
    protected boolean resourceOnly = false;
    /** @deprecated use {@link #getDeprecated()} as this field will be made private in a future release */
    @Deprecated
    protected DeprecationData deprecated = null;
    /** @deprecated use {@link #getAccessConstraints()} as this field will be made private in a future release */
    @Deprecated
    protected AccessConstraintDefinition[] accessConstraints;
    /** @deprecated use {@link #getNullSignificant()} as this field will be made private in a future release */
    @Deprecated
    protected Boolean nullSignificant;
    /** @deprecated use {@link #getParser()} as this field will be made private in a future release */
    @Deprecated
    protected AttributeParser parser;
    /** @deprecated use {@link #getAttributeGroup()} as this field will be made private in a future release */
    @Deprecated
    protected String attributeGroup;
    /** @deprecated use {@link #getCapabilityReferenceRecorder()} as this field will be made private in a future release */
    @Deprecated
    protected CapabilityReferenceRecorder referenceRecorder;
    /** @deprecated use {@link #getArbitraryDescriptors()} as this field will be made private in a future release */
    @Deprecated
    protected Map<String, ModelNode> arbitraryDescriptors = null;
    private ModelNode undefinedMetricValue;

    private static AccessConstraintDefinition[] ZERO_CONSTRAINTS = new AccessConstraintDefinition[0];

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
     * @param optional {@code true} if the attribute {@link AttributeDefinition#isAllowNull() allows undefined values} in the absence of {@link #setAlternatives(String...) alternatives}
     */
    @SuppressWarnings("deprecation")
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
    @SuppressWarnings("deprecation")
    public AbstractAttributeDefinitionBuilder(final String attributeName, final AttributeDefinition basis) {
        this.name = attributeName != null ? attributeName : basis.getName();
        this.xmlName = basis.getXmlName();
        this.defaultValue = basis.getDefaultValue();
        this.type = basis.getType();
        this.allowNull = basis.isAllowNull();
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
        Set<AttributeAccess.Flag> basisFlags = basis.getImmutableFlags();
        this.flags = basisFlags.toArray(new AttributeAccess.Flag[basisFlags.size()]);
        if (basis.getAllowedValues().size() > 0) {
            List<ModelNode> basisAllowedValues = basis.getAllowedValues();
            this.allowedValues = basisAllowedValues.toArray(new ModelNode[basisAllowedValues.size()]);
        }
        this.attributeGroup = basis.getAttributeGroup();
        if(!basis.getArbitraryDescriptors().isEmpty()) {
            this.arbitraryDescriptors = new HashMap<>(basis.getArbitraryDescriptors());
        }
        this.referenceRecorder = basis.getReferenceRecorder();
    }

    /**
     * Create the {@link org.jboss.as.controller.AttributeDefinition}
     * @return the attribute definition. Will not return {@code null}
     */
    public abstract ATTRIBUTE build();

    /**
     * Sets the {@link AttributeDefinition#getName() name} for the attribute, which is only needed
     * if the attribute was created from an existing {@link SimpleAttributeDefinition} using
     * {@link SimpleAttributeDefinitionBuilder#create(org.jboss.as.controller.SimpleAttributeDefinition)}
     * method.
     *
     * @param name the attribute's name. {@code null} is not allowed
     * @return a builder that can be used to continue building the attribute definition
     *
     * @deprecated may be removed at any time; the name should be immutable
     */
    @Deprecated
    public BUILDER setName(String name) {
        assert name != null;
        //noinspection deprecation
        this.name = name;
        return (BUILDER) this;
    }

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
        //noinspection deprecation
        this.xmlName = xmlName == null ? this.name : xmlName;
        return (BUILDER) this;
    }

    /**
     * Sets the {@link AttributeDefinition#getType() type} for the attribute.
     * @param type the type. {@code null} is not allowed
     * @return a builder that can be used to continue building the attribute definition
     *
     * @deprecated may be removed at any time; the type should be immutable
     */
    @Deprecated
    public BUILDER setType(ModelType type) {
        //noinspection deprecation
        this.type = type;
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
        //noinspection deprecation
        this.allowNull = !required;
        return (BUILDER) this;
    }

    /**
     * Inverse of the preferred {@link #setRequired(boolean)}; sets whether the attribute should
     * {@link AttributeDefinition#isAllowNull() allow undefined values}
     * in the absence of {@link #setAlternatives(String...) alternatives}.
     * If not set the default value is the value provided to the builder constructor, or {@code false}
     * if no value is provided.
     *
     * @param allowNull {@code true} if undefined values should be allowed in the absence of alternatives
     * @return a builder that can be used to continue building the attribute definition
     *
     * @deprecated use {@link #setRequired(boolean)}
     */
    @Deprecated
    public BUILDER setAllowNull(boolean allowNull) {
        this.allowNull = allowNull;
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
        //noinspection deprecation
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
        //noinspection deprecation
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
        //noinspection deprecation
        this.measurementUnit = unit;
        return (BUILDER) this;
    }

    /**
     * Sets a {@link org.jboss.as.controller.ParameterCorrector} to use to adjust any user provided values
     * before {@link org.jboss.as.controller.AttributeDefinition#validateOperation(org.jboss.dmr.ModelNode, boolean) validation}
     * occurs.
     * @param corrector the corrector. May be {@code null}
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setCorrector(ParameterCorrector corrector) {
        //noinspection deprecation
        this.corrector = corrector;
        return (BUILDER) this;
    }

    /**
     * Sets the validator that should be used to validate attribute values. The resulting attribute definition
     * will wrap this validator in one that enforces the attribute's
     * {@link AttributeDefinition#isAllowNull() allow null} and
     * {@link AttributeDefinition#isAllowExpression() allow expression} settings, so the given {@code validator}
     * need not be properly configured for those validations.
     * @param validator the validator. {@code null} is allowed
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setValidator(ParameterValidator validator) {
        //noinspection deprecation
        this.validator = validator;
        return (BUILDER) this;
    }

    /**
     * Has no effect. The behavior of {@link AttributeDefinition} now is to allow undefined values
     * for {@link #setRequired(boolean) required} attributes if alternatives have been declared via
     * {@link #setAlternatives(String...)} or {@link #addAlternatives(String...)}. Handling such
     * situations was the original purpose for this setting.
     *
     * @param validateNull ignored
     * @return a builder that can be used to continue building the attribute definition
     *
     * @deprecated has no effect
     */
    @Deprecated
    public BUILDER setValidateNull(boolean validateNull) {
        this.validateNull = validateNull;
        return (BUILDER) this;
    }

    /**
     * Sets {@link AttributeDefinition#getAlternatives() names of alternative attributes} that should not
     * be defined if this attribute is defined.
     * @param alternatives the attribute names
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setAlternatives(String... alternatives) {
        //noinspection deprecation
        this.alternatives = alternatives;
        return (BUILDER) this;
    }

    /**
     * Adds {@link AttributeDefinition#getAlternatives() names of alternative attributes} that should not
     * be defined if this attribute is defined.
     * @param alternatives the attribute names
     * @return a builder that can be used to continue building the attribute definition
     */
    @SuppressWarnings("deprecation")
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
    @SuppressWarnings("deprecation")
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
    @SuppressWarnings("deprecation")
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
    @SuppressWarnings("deprecation")
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
        //noinspection deprecation
        return arbitraryDescriptors;
    }

    /**
     * Sets {@link AttributeDefinition#getRequires() names of required attributes} that must
     * be defined if this attribute is defined.
     * @param requires the attribute names
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setRequires(String... requires) {
        //noinspection deprecation
        this.requires = requires;
        return (BUILDER) this;
    }

    /**
     * Sets the {@link AttributeAccess.Flag special purpose flags} that are relevant to the attribute
     * @param flags the flags
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setFlags(AttributeAccess.Flag... flags) {
        //noinspection deprecation
        this.flags = flags;
        return (BUILDER) this;
    }

    /**
     * Adds a {@link AttributeAccess.Flag special purpose flag} that is relevant to the attribute
     * @param flag the flag
     * @return a builder that can be used to continue building the attribute definition
     */
    @SuppressWarnings("deprecation")
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
    @SuppressWarnings({"deprecation", "UnusedReturnValue", "WeakerAccess"})
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
        //noinspection deprecation
        flags = newFlags;
        return (BUILDER) this;
    }

    /**
     * Checks if a {@link AttributeAccess.Flag special purpose flag} has been recorded as relevant to the attribute
     * @param flag the flag
     * @return a builder that can be used to continue building the attribute definition
     */
    @SuppressWarnings({"deprecation", "WeakerAccess"})
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
        //noinspection deprecation
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
        //noinspection deprecation
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
        //noinspection deprecation
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
        //noinspection deprecation
        return minSizeSet ? minSize : null;
    }

    /**
     * Sets a custom {@link org.jboss.as.controller.AttributeMarshaller} to use for marshalling the attribute to xml.
     * If not set, a {@link org.jboss.as.controller.DefaultAttributeMarshaller} will be used.
     * @param marshaller the marshaller. Can be {@code null}
     * @return a builder that can be used to continue building the attribute definition
     */
    public BUILDER setAttributeMarshaller(AttributeMarshaller marshaller) {
        //noinspection deprecation
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
        //noinspection deprecation
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
        //noinspection deprecation
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
        //noinspection deprecation
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
        //noinspection deprecation
        this.accessConstraints = accessConstraints;
        return (BUILDER) this;
    }

    /**
     * Adds an access constraint to the set used with the attribute
     * @param accessConstraint the constraint
     * @return a builder that can be used to continue building the attribute definition
     */
    @SuppressWarnings("deprecation")
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
        //noinspection deprecation
        this.nullSignificant = nullSignificant;
        return (BUILDER) this;
    }

    /**
     * @deprecated Use {@link #setNullSignificant(boolean)}.
     */
    @Deprecated
    public BUILDER setNullSignficant(boolean nullSignficant) {
        return setNullSignificant(nullSignficant);
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
        //noinspection deprecation
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
        //noinspection deprecation
        this.allowedValues = allowedValues;
        return (BUILDER) this;
    }

    /**
     * Sets allowed values for attribute
     *
     * @param allowedValues values that are legal as part in this attribute
     * @return a builder that can be used to continue building the attribute definition
     */
    @SuppressWarnings("deprecation")
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
    @SuppressWarnings("deprecation")
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
            //noinspection deprecation
            return setCapabilityReference(referencedCapability, dependentCapability.getName(), false);
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
        //noinspection deprecation
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
        //noinspection deprecation
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
        //noinspection deprecation
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
     * @param dynamicDependent     {@code true} if {@code dependentCapability} is a dynamic capability, the dynamic
     *                             portion of which comes from the name of the resource with which
     *                             the attribute is associated
     * @return the builder
     * @deprecated Use {@link #setCapabilityReference(String, RuntimeCapability)} instead.
     */
    @Deprecated
    public BUILDER setCapabilityReference(String referencedCapability, String dependentCapability, boolean dynamicDependent) {
        //noinspection deprecation
        referenceRecorder = new CapabilityReferenceRecorder.DefaultCapabilityReferenceRecorder(referencedCapability, dependentCapability, dynamicDependent);
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
        //noinspection deprecation
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
        //noinspection deprecation
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


    public String getName() {
        //noinspection deprecation
        return name;
    }

    public ModelType getType() {
        //noinspection deprecation
        return type;
    }

    public String getXmlName() {
        //noinspection deprecation
        return xmlName;
    }

    public boolean isAllowNull() {
        //noinspection deprecation
        return allowNull;
    }

    public boolean isAllowExpression() {
        //noinspection deprecation
        return allowExpression;
    }

    public ModelNode getDefaultValue() {
        //noinspection deprecation
        return defaultValue;
    }

    @SuppressWarnings("WeakerAccess")
    public MeasurementUnit getMeasurementUnit() {
        //noinspection deprecation
        return measurementUnit;
    }

    public String[] getAlternatives() {
        //noinspection deprecation
        return copyStrings(alternatives);
    }

    public String[] getRequires() {
        //noinspection deprecation
        return copyStrings(requires);
    }

    @SuppressWarnings("WeakerAccess")
    public ParameterCorrector getCorrector() {
        //noinspection deprecation
        return corrector;
    }

    public ParameterValidator getValidator() {
        //noinspection deprecation
        return validator;
    }

    /** @deprecated meaningless and not used. */
    @Deprecated
    public boolean isValidateNull() {
        return validateNull;
    }

    public int getMinSize() {
        //noinspection deprecation
        return minSize;
    }

    public int getMaxSize() {
        //noinspection deprecation
        return maxSize;
    }

    public AttributeAccess.Flag[] getFlags() {
        //noinspection deprecation
        return copyFlags(flags);
    }

    @SuppressWarnings("WeakerAccess")
    public AttributeMarshaller getAttributeMarshaller() {
        //noinspection deprecation
        return attributeMarshaller;
    }

    @SuppressWarnings("WeakerAccess")
    public boolean isResourceOnly() {
        //noinspection deprecation
        return resourceOnly;
    }

    public DeprecationData getDeprecated() {
        //noinspection deprecation
        return deprecated;
    }

    public AccessConstraintDefinition[] getAccessConstraints() {
        //noinspection deprecation
        return copyConstraints(accessConstraints);
    }

    @SuppressWarnings("WeakerAccess")
    public Boolean getNullSignificant() {
        //noinspection deprecation
        return nullSignificant;
    }

    @SuppressWarnings("WeakerAccess")
    public ModelNode getUndefinedMetricValue() {
        return undefinedMetricValue;
    }

    public AttributeParser getParser() {
        //noinspection deprecation
        return parser;
    }

    public String getAttributeGroup() {
        //noinspection deprecation
        return attributeGroup;
    }

    public ModelNode[] getAllowedValues() {
        //noinspection deprecation
        return allowedValues;
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    protected final CapabilityReferenceRecorder getCapabilityReferenceRecorder() {
        //noinspection deprecation
        return referenceRecorder;
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
