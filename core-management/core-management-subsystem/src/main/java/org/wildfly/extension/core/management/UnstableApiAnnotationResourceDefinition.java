/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.core.management;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredAddStepHandler;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

import java.util.Collection;
import java.util.Collections;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNSTABLE_API_ANNOTATIONS;

/**
 * Resource to list all configuration changes.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class UnstableApiAnnotationResourceDefinition extends PersistentResourceDefinition {

    public static final Stability STABILITY = Stability.PREVIEW;
    public static final SimpleAttributeDefinition LEVEL = SimpleAttributeDefinitionBuilder.create(
            ModelDescriptionConstants.LEVEL, ModelType.STRING, true)
            .setValidator(EnumValidator.create(UnstableApiAnnotationLevel.class))
            .setDefaultValue(new ModelNode(UnstableApiAnnotationLevel.LOG.name()))
            .build();
    public static final PathElement PATH = PathElement.pathElement(SERVICE, UNSTABLE_API_ANNOTATIONS);
    static final ResourceRegistration RESOURCE_REGISTRATION = ResourceRegistration.of(PATH, STABILITY);
    static final UnstableApiAnnotationResourceDefinition INSTANCE = new UnstableApiAnnotationResourceDefinition();

    private UnstableApiAnnotationResourceDefinition() {
        super(
                new Parameters(
                            RESOURCE_REGISTRATION,
                            CoreManagementExtension.getResourceDescriptionResolver(UNSTABLE_API_ANNOTATIONS))
                        .setAddHandler(ReloadRequiredAddStepHandler.INSTANCE)
                        .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE));
    }


    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadWriteAttribute(LEVEL, null, ReloadRequiredWriteAttributeHandler.INSTANCE);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Collections.emptyList();
    }

    public enum UnstableApiAnnotationLevel {
        LOG,
        ERROR
    }

}
