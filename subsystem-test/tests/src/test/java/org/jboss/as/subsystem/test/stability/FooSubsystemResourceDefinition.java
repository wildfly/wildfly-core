/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.stability;

import java.util.Collection;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredAddStepHandler;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelType;

/**
 * A resource definition containing an experimental attribute.
 * @author Paul Ferraro
 */
public class FooSubsystemResourceDefinition extends SimpleResourceDefinition {
    static final String SUBSYSTEM_NAME = "foo";
    static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, SUBSYSTEM_NAME);

    static final AttributeDefinition EXPERIMENTAL = new SimpleAttributeDefinitionBuilder("experimental", ModelType.STRING)
            .setRequired(false)
            .setStability(Stability.EXPERIMENTAL)
            .build();

    static final Collection<AttributeDefinition> ATTRIBUTES = List.of(EXPERIMENTAL);

    FooSubsystemResourceDefinition() {
        super(new Parameters(PATH, NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddHandler(ReloadRequiredAddStepHandler.INSTANCE)
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        for (AttributeDefinition attribute : ATTRIBUTES) {
            registration.registerReadWriteAttribute(attribute, null, ReloadRequiredWriteAttributeHandler.INSTANCE);
        }
    }

    @Override
    public void registerChildren(ManagementResourceRegistration registration) {
        registration.registerSubModel(new BarResourceDefinition());
    }
}
