/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.extension;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MICRO_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MINOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for an {@link org.jboss.as.controller.Extension}'s subsystem child resources.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ExtensionSubsystemResourceDefinition extends SimpleResourceDefinition {
    public static final ListAttributeDefinition XML_NAMESPACES = new StringListAttributeDefinition.Builder(ModelDescriptionConstants.XML_NAMESPACES)
            .setRequired(true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setElementValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, false))
            .build();

    public static final SimpleAttributeDefinition MAJOR_VERSION = new SimpleAttributeDefinitionBuilder(MANAGEMENT_MAJOR_VERSION, ModelType.INT, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    public static final SimpleAttributeDefinition MINOR_VERSION = new SimpleAttributeDefinitionBuilder(MANAGEMENT_MINOR_VERSION, ModelType.INT, true)
            .setValidator(new IntRangeValidator(0, Integer.MAX_VALUE, true, false))
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    public static final SimpleAttributeDefinition MICRO_VERSION = new SimpleAttributeDefinitionBuilder(MANAGEMENT_MICRO_VERSION, ModelType.INT, true)
            .setValidator(new IntRangeValidator(0, Integer.MAX_VALUE, true, false))
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    private final Stability stability;

    ExtensionSubsystemResourceDefinition() {
        this(Stability.DEFAULT);
    }

    ExtensionSubsystemResourceDefinition(Stability stability) {
        super(new Parameters(PathElement.pathElement(SUBSYSTEM), ControllerResolver.getResolver(EXTENSION, SUBSYSTEM)).setRuntime());
        this.stability = stability;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(MAJOR_VERSION, null);
        resourceRegistration.registerReadOnlyAttribute(MINOR_VERSION, null);
        resourceRegistration.registerReadOnlyAttribute(MICRO_VERSION, null);
        resourceRegistration.registerReadOnlyAttribute(XML_NAMESPACES, null);
    }

    @Override
    public Stability getStability() {
        return this.stability;
    }
}
