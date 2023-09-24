/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.management.client.content;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.BytesValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a resource that represents a named bit of re-usable DMR.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ManagedDMRContentResourceDefinition extends SimpleResourceDefinition {

    public static final AttributeDefinition HASH = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.HASH, ModelType.BYTES, false)
            .setValidator(BytesValidator.createSha1(false))
            .build();

    private final AttributeDefinition contentDefinition;

    public static ManagedDMRContentResourceDefinition create(final String childType,
                                               final ParameterValidator contentValidator,
                                               final ResourceDescriptionResolver descriptionResolver) {
        return new ManagedDMRContentResourceDefinition(childType, getContentAttributeDefinition(contentValidator), descriptionResolver);
    }

    private ManagedDMRContentResourceDefinition(final String childType,
                                               final AttributeDefinition contentDefinition,
                                               final ResourceDescriptionResolver descriptionResolver) {
        super(new Parameters(PathElement.pathElement(childType), descriptionResolver)
                .setAddHandler(null)
                .setRemoveHandler(ManagedDMRContentRemoveHandler.INSTANCE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_NONE)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_NONE));
        this.contentDefinition = contentDefinition;
    }


    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(HASH, null);
        resourceRegistration.registerReadWriteAttribute(contentDefinition, null, new ManagedDMRContentWriteAttributeHandler(contentDefinition));
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);

        OperationDefinition addOD = SimpleOperationDefinitionBuilder.of("add", getResourceDescriptionResolver()).addParameter(contentDefinition).build();
        resourceRegistration.registerOperationHandler(addOD, new ManagedDMRContentAddHandler(contentDefinition));

        final ManagedDMRContentStoreHandler handler = new ManagedDMRContentStoreHandler(contentDefinition, getResourceDescriptionResolver());
        resourceRegistration.registerOperationHandler(handler.getDefinition(), handler);
    }

    private static AttributeDefinition getContentAttributeDefinition(final ParameterValidator contentValidator) {
        return SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.CONTENT, ModelType.OBJECT)
                .setStorageRuntime()
                .setRuntimeServiceNotRequired()
                .setValidator(contentValidator)
                .build();
    }
}
