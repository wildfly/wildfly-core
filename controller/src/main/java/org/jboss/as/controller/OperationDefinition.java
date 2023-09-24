/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.registry.OperationEntry.Flag.immutableSetOf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelType;

/**
 * Defining characteristics of operation in a {@link org.jboss.as.controller.registry.Resource}
 *
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
public abstract class OperationDefinition {

    protected final String name;
    protected final OperationEntry.EntryType entryType;
    protected final Set<OperationEntry.Flag> flags;
    protected final AttributeDefinition[] parameters;
    protected final ModelType replyType;
    protected final ModelType replyValueType;
    protected final boolean replyAllowNull;
    protected final DeprecationData deprecationData;
    protected final AttributeDefinition[] replyParameters;
    protected final List<AccessConstraintDefinition> accessConstraints;
    protected final DescriptionProvider descriptionProvider;

    protected OperationDefinition(SimpleOperationDefinitionBuilder builder) {
        this.name = builder.name;
        this.entryType = builder.entryType;
        this.flags = immutableSetOf(builder.flags);
        this.parameters = builder.parameters;
        this.replyType = builder.replyType;
        this.replyValueType = builder.replyValueType;
        this.replyAllowNull = builder.replyAllowNull;
        this.deprecationData = builder.deprecationData;
        this.replyParameters = builder.replyParameters;
        if (builder.accessConstraints == null) {
            this.accessConstraints = Collections.<AccessConstraintDefinition>emptyList();
        } else {
            this.accessConstraints = Collections.unmodifiableList(Arrays.asList(builder.accessConstraints));
        }
        this.descriptionProvider = builder.descriptionProvider;

    }

    public String getName() {
        return name;
    }

    public OperationEntry.EntryType getEntryType() {
        return entryType;
    }

    /**
     * Gets an immutable set of any {@link OperationEntry.Flag flags} associated with the operation.
     * @return the flags. Will not return {@code null} be may be empty
     */
    public Set<OperationEntry.Flag> getFlags() {
        return flags;
    }

    public AttributeDefinition[] getParameters() {
        return parameters;
    }

    public ModelType getReplyType() {
        return replyType;
    }

    /**
     * Only required if the reply type is some form of collection.
     */
    public ModelType getReplyValueType() {
        return replyValueType;
    }

    public List<AccessConstraintDefinition> getAccessConstraints() {
        return accessConstraints;
    }

    public abstract DescriptionProvider getDescriptionProvider();

    public DeprecationData getDeprecationData() {
        return deprecationData;
    }

    public boolean isDeprecated(){
        return deprecationData != null;
    }

    public boolean isReplyAllowNull() {
        return replyAllowNull;
    }

    public AttributeDefinition[] getReplyParameters() {
        return replyParameters;
    }

}
