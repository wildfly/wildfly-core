/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.ignored;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IGNORED_RESOURCE_TYPE;

import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PrimitiveListAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for an ignored domain resource type.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class IgnoredDomainTypeResourceDefinition extends SimpleResourceDefinition {

    public static final SimpleAttributeDefinition WILDCARD = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.WILDCARD, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.FALSE).setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).build();

    public static final ListAttributeDefinition NAMES = new PrimitiveListAttributeDefinition.Builder(ModelDescriptionConstants.NAMES, ModelType.STRING)
            .setRequired(false)
            .setElementValidator(new StringLengthValidator(1))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    IgnoredDomainTypeResourceDefinition() {
        super(new Parameters(PathElement.pathElement(IGNORED_RESOURCE_TYPE), HostResolver.getResolver(IGNORED_RESOURCE_TYPE))
                .setAddHandler(new IgnoredDomainTypeAddHandler())
                .setRemoveHandler(new IgnoredDomainTypeRemoveHandler())
                .setAddRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        OperationStepHandler writeHandler = new IgnoredDomainTypeWriteAttributeHandler();
        resourceRegistration.registerReadWriteAttribute(WILDCARD, null, writeHandler);
        resourceRegistration.registerReadWriteAttribute(NAMES, null, writeHandler);
    }
}
