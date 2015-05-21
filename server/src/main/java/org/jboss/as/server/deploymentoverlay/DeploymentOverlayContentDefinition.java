/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.deploymentoverlay;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CONTENT;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.ParameterCorrector;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.operations.validation.BytesValidator;
import org.jboss.as.controller.operations.validation.MinMaxValidator;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.DeploymentFileRepository;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Stuart Douglas
 */
public class DeploymentOverlayContentDefinition extends SimpleResourceDefinition {

    public static final ObjectTypeAttributeDefinition CONTENT_PARAMETER =
            new ObjectTypeAttributeDefinition.Builder(ModelDescriptionConstants.CONTENT,
                    new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.INPUT_STREAM_INDEX, ModelType.INT, true)
                            .setValidator(new StringLengthValidator(0, true))
                            .build(),
                    new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.HASH, ModelType.BYTES, true)
                            .setValidator(new HashValidator(true))
                            .build(),
                    new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.BYTES, ModelType.BYTES, true)
                            .setValidator(new BytesValidator(1, Integer.MAX_VALUE, true))
                            .build(),
                    new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.URL, ModelType.STRING, true)
                            .setValidator(new StringLengthValidator(1, true))
                            .build())
            .setCorrector(ContentCorrector.INSTANCE)
            .build();

    public static final SimpleAttributeDefinition CONTENT_ATTRIBUTE =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.CONTENT, ModelType.BYTES, false)
            .setValidator(new HashValidator(true))
            .build();

    private final ContentRepository contentRepository;
    private final OperationStepHandler addHandler;
    private final SimpleOperationDefinition readContent;
    private static final AttributeDefinition[] ATTRIBUTES = {CONTENT_ATTRIBUTE};

    public static AttributeDefinition[] attributes() {
        return ATTRIBUTES.clone();
    }

    public DeploymentOverlayContentDefinition(final ContentRepository contentRepository, final DeploymentFileRepository remoteRepository) {
        super(DeploymentOverlayModel.CONTENT_PATH,
                ControllerResolver.getResolver(ModelDescriptionConstants.DEPLOYMENT_OVERLAY, ModelDescriptionConstants.CONTENT),
                null,
                new DeploymentOverlayContentRemove(contentRepository));
        this.contentRepository = contentRepository;
        readContent = new SimpleOperationDefinition(READ_CONTENT, getResourceDescriptionResolver());
        //Will be registered in registerOperations()
        addHandler = new DeploymentOverlayContentAdd(contentRepository, remoteRepository);
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition attr : ATTRIBUTES) {
            resourceRegistration.registerReadOnlyAttribute(attr, null);
        }
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        ReadContentHandler handler = new ReadContentHandler(contentRepository);
        resourceRegistration.registerOperationHandler(readContent, handler);

        OperationDefinition addDefinition =
                new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.ADD, ControllerResolver.getResolver(ModelDescriptionConstants.DEPLOYMENT_OVERLAY))
                        .setParameters(CONTENT_PARAMETER)
                        .build();
        resourceRegistration.registerOperationHandler(addDefinition, addHandler);
    }

    private static class HashValidator extends ModelTypeValidator implements MinMaxValidator {
        public HashValidator(boolean nillable) {
            super(ModelType.BYTES, nillable);
        }

        @Override
        public Long getMin() {
            return 20L;
        }

        @Override
        public Long getMax() {
            return 20L;
        }
    }

    private static class ContentCorrector implements ParameterCorrector {
        static final ParameterCorrector INSTANCE = new ContentCorrector();

        @Override
        public ModelNode correct(ModelNode newValue, ModelNode currentValue) {
            if (newValue.isDefined() && newValue.getType() == ModelType.BYTES) {
                //The generated add from the model sync does not wrap the hash, adjust that here
                ModelNode corrected = new ModelNode();
                corrected.get(HASH).set(newValue);
                return corrected;
            }
            return newValue;
        }
    }

}
