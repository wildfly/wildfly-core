/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.controller.descriptions.DefaultOperationDescriptionProvider;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;

/**
 * Defining characteristics of operation in a {@link org.jboss.as.controller.registry.Resource}
 * SimpleOperationDefinition is simplest implementation that uses {@link DefaultOperationDescriptionProvider} for generating description of operation
 * if more complex DescriptionProvider user should extend this class or {@link OperationDefinition} and provide its own {@link DescriptionProvider}
 *
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
public class SimpleOperationDefinition extends OperationDefinition {

    private final ResourceDescriptionResolver resolver;
    private final ResourceDescriptionResolver attributeResolver;

    protected SimpleOperationDefinition(SimpleOperationDefinitionBuilder builder) {
        super(builder);
        this.resolver = builder.resolver;
        this.attributeResolver = builder.attributeResolver;
    }

    @Override
    public DescriptionProvider getDescriptionProvider() {
        if (entryType == OperationEntry.EntryType.PRIVATE || flags.contains(OperationEntry.Flag.HIDDEN)) {
            return PRIVATE_PROVIDER;
        }
        if (descriptionProvider !=null) {
            return descriptionProvider;
        }
        return new DefaultOperationDescriptionProvider(this, this.resolver, this.attributeResolver);
    }

    private static final DescriptionProvider PRIVATE_PROVIDER = locale -> new ModelNode();

}
