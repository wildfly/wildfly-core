/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.version.Quality;
import org.jboss.dmr.ModelType;

/**
 * Builder for helping construct {@link SimpleOperationDefinition}
 *
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
public class SimpleOperationDefinitionBuilder {

    private static AttributeDefinition[] NO_ATTRIBUTES = new AttributeDefinition[0];

    public static SimpleOperationDefinitionBuilder of(String name, ResourceDescriptionResolver resolver) {
        return new SimpleOperationDefinitionBuilder(name, resolver);
    }

    ResourceDescriptionResolver resolver;
    ResourceDescriptionResolver attributeResolver;
    protected String name;
    protected OperationEntry.EntryType entryType = OperationEntry.EntryType.PUBLIC;
    protected EnumSet<OperationEntry.Flag> flags = EnumSet.noneOf(OperationEntry.Flag.class);
    protected AttributeDefinition[] parameters = NO_ATTRIBUTES;
    protected ModelType replyType;
    protected ModelType replyValueType;
    protected boolean replyAllowNull;
    protected DeprecationData deprecationData = null;
    protected AttributeDefinition[] replyParameters = NO_ATTRIBUTES;
    protected AccessConstraintDefinition[] accessConstraints;
    DescriptionProvider descriptionProvider;
    Quality quality = Quality.DEFAULT;

    public SimpleOperationDefinitionBuilder(String name, ResourceDescriptionResolver resolver) {
        this.name = name;
        this.resolver = resolver;
    }


    public SimpleOperationDefinition build() {
        if (attributeResolver == null) {
            attributeResolver = resolver;
        }
        return internalBuild(resolver, attributeResolver);
    }

    protected SimpleOperationDefinition internalBuild(ResourceDescriptionResolver resolver, ResourceDescriptionResolver attributeResolver) {
        return new SimpleOperationDefinition(this);
    }

    protected static EnumSet<OperationEntry.Flag> getFlagsSet(OperationEntry.Flag... vararg) {
        EnumSet<OperationEntry.Flag> result = EnumSet.noneOf(OperationEntry.Flag.class);
        if (vararg != null && vararg.length > 0) {
            Collections.addAll(result, vararg);
        }
        return result;
    }

    public SimpleOperationDefinitionBuilder setEntryType(OperationEntry.EntryType entryType) {
        this.entryType = entryType;
        return this;
    }

    public SimpleOperationDefinitionBuilder setPrivateEntry() {
        this.entryType = OperationEntry.EntryType.PRIVATE;
        return this;
    }

    public SimpleOperationDefinitionBuilder withFlags(EnumSet<OperationEntry.Flag> flags) {
        this.flags.addAll(flags);
        return this;
    }

    public SimpleOperationDefinitionBuilder withFlags(OperationEntry.Flag... flags) {
        this.flags.addAll(getFlagsSet(flags));
        return this;
    }

    public SimpleOperationDefinitionBuilder withFlag(OperationEntry.Flag flag) {
        this.flags.add(flag);
        return this;
    }

    public SimpleOperationDefinitionBuilder setRuntimeOnly() {
        return withFlag(OperationEntry.Flag.RUNTIME_ONLY);
    }

    public SimpleOperationDefinitionBuilder setReadOnly() {
        return withFlag(OperationEntry.Flag.READ_ONLY);
    }

    public SimpleOperationDefinitionBuilder setParameters(AttributeDefinition... parameters) {//todo add validation for same param name
        this.parameters = parameters;
        return this;
    }

    public SimpleOperationDefinitionBuilder addParameter(AttributeDefinition parameter) {
        int i = parameters.length;
        parameters = Arrays.copyOf(parameters, i + 1);
        parameters[i] = parameter;
        return this;
    }

    public SimpleOperationDefinitionBuilder setReplyType(ModelType replyType) {
        this.replyType = replyType;
        return this;
    }

    public SimpleOperationDefinitionBuilder setReplyValueType(ModelType replyValueType) {
        this.replyValueType = replyValueType;
        return this;
    }

    public SimpleOperationDefinitionBuilder allowReturnNull() {
        this.replyAllowNull = true;
        return this;
    }

    /**
     * Marks the operation as deprecated since the given API version. This is equivalent to calling
     * {@link #setDeprecated(ModelVersion, boolean)} with the {@code notificationUseful} parameter
     * set to {@code true}.
     * @param since the API version, with the API being the one (core or a subsystem) in which the attribute is used
     * @return a builder that can be used to continue building the attribute definition
     */
    public SimpleOperationDefinitionBuilder setDeprecated(ModelVersion since) {
        return setDeprecated(since, true);
    }

    /**
     * Marks the attribute as deprecated since the given API version, with the ability to configure that
     * notifications to the user (e.g. via a log message) about deprecation of the operation should not be emitted.
     * Notifying the user should only be done if the user can take some action in response. Advising that
     * something will be removed in a later release is not useful if there is no alternative in the
     * current release. If the {@code notificationUseful} param is {@code true} the text
     * description of the operation deprecation available from the {@code read-operation-description}
     * management operation should provide useful information about how the user can avoid using
     * the operation.
     *
     * @param since the API version, with the API being the one (core or a subsystem) in which the attribute is used
     * @param notificationUseful whether actively advising the user about the deprecation is useful
     * @return a builder that can be used to continue building the attribute definition
     */
    public SimpleOperationDefinitionBuilder setDeprecated(ModelVersion since, boolean notificationUseful) {
        this.deprecationData = new DeprecationData(since, notificationUseful);
        return this;
    }

    public SimpleOperationDefinitionBuilder setReplyParameters(AttributeDefinition... replyParameters) {
        this.replyParameters = replyParameters;
        return this;
    }

    public SimpleOperationDefinitionBuilder setAttributeResolver(ResourceDescriptionResolver resolver) {
        this.attributeResolver = resolver;
        return this;
    }

    public SimpleOperationDefinitionBuilder setAccessConstraints(AccessConstraintDefinition... accessConstraints) {
        this.accessConstraints = accessConstraints;
        return this;
    }

    public SimpleOperationDefinitionBuilder addAccessConstraint(final AccessConstraintDefinition accessConstraint) {
        if (accessConstraints == null) {
            accessConstraints = new AccessConstraintDefinition[] {accessConstraint};
        } else {
            accessConstraints = Arrays.copyOf(accessConstraints, accessConstraints.length + 1);
            accessConstraints[accessConstraints.length - 1] = accessConstraint;
        }
        return this;
    }

    public SimpleOperationDefinitionBuilder setDescriptionProvider(DescriptionProvider provider){
        this.descriptionProvider = provider;
        return this;
    }

    public SimpleOperationDefinitionBuilder setQuality(Quality quality) {
        this.quality = quality;
        return this;
    }
}
