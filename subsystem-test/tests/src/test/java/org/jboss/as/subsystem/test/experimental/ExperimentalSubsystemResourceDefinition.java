/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.experimental;

import java.util.Collection;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.FeatureStream;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredAddStepHandler;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

/**
 * A resource definition containing an experimental attribute.
 * @author Paul Ferraro
 */
public class ExperimentalSubsystemResourceDefinition extends SimpleResourceDefinition {
    static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, ExperimentalSubsystemExtension.SUBSYSTEM_NAME);

    static final AttributeDefinition EXPERIMENTAL = new SimpleAttributeDefinitionBuilder("experimental", ModelType.STRING).build();

    private final Collection<AttributeDefinition> attributes;

    ExperimentalSubsystemResourceDefinition(FeatureStream stream) {
        this(stream.enables(FeatureStream.EXPERIMENTAL) ? List.of(EXPERIMENTAL) : List.of());
    }

    private ExperimentalSubsystemResourceDefinition(Collection<AttributeDefinition> attributes) {
        super(new Parameters(PATH, NonResolvingResourceDescriptionResolver.INSTANCE).setAddHandler(new ReloadRequiredAddStepHandler(attributes)).setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE));
        this.attributes = attributes;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        OperationStepHandler handler = new ReloadRequiredWriteAttributeHandler(this.attributes);
        for (AttributeDefinition attribute : this.attributes) {
            registration.registerReadWriteAttribute(attribute, null, handler);
        }
    }

    @Override
    public void registerChildren(ManagementResourceRegistration registration) {
        if (registration.getFeatureStream().enables(FeatureStream.PREVIEW)) {
            registration.registerSubModel(new PreviewResourceDefinition());
        }
    }
}
