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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTACHED_STREAMS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILESYSTEM_PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UUID;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.ParameterCorrector;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
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
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.DeploymentFileRepository;
import org.jboss.as.repository.ExplodedContentException;
import org.jboss.as.repository.TypedInputStream;
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
                            .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
                            .addArbitraryDescriptor(ATTACHED_STREAMS, ModelNode.TRUE)
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
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.CONTENT, ModelType.BYTES, true)
            .setValidator(new HashValidator(true))
            .build();

     public static final SimpleAttributeDefinition STREAM_ATTRIBUTE =
            SimpleAttributeDefinitionBuilder.create("stream", ModelType.STRING, true)
                .setStorageRuntime()
                .setRuntimeServiceNotRequired()
                .build();

    private final ContentRepository contentRepository;
    private final OperationStepHandler addHandler;
    private static final SimpleOperationDefinition READ_CONTENT_OP_DEFINITION =
            new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.READ_CONTENT,
                    ControllerResolver.getResolver(ModelDescriptionConstants.DEPLOYMENT_OVERLAY, ModelDescriptionConstants.CONTENT))
            .setDeprecated(ModelVersion.create(5, 0, 0))
            .build();
    private static final SimpleOperationDefinition ADD_OP_DEFINITION =
                new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.ADD,
                        ControllerResolver.getResolver(ModelDescriptionConstants.DEPLOYMENT_OVERLAY))
                .setParameters(CONTENT_PARAMETER)
                .build();
    private static final AttributeDefinition[] ATTRIBUTES = {CONTENT_ATTRIBUTE};

    public static AttributeDefinition[] attributes() {
        return  ATTRIBUTES.clone();
    }

    public DeploymentOverlayContentDefinition(final ContentRepository contentRepository, final DeploymentFileRepository remoteRepository) {
        super(DeploymentOverlayModel.CONTENT_PATH,
                ControllerResolver.getResolver(ModelDescriptionConstants.DEPLOYMENT_OVERLAY, ModelDescriptionConstants.CONTENT),
                null,
                new DeploymentOverlayContentRemove(contentRepository));
        this.contentRepository = contentRepository;
        addHandler = new DeploymentOverlayContentAdd(contentRepository, remoteRepository);
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        for (AttributeDefinition attr : ATTRIBUTES) {
            resourceRegistration.registerReadOnlyAttribute(attr, null);
        }
        resourceRegistration.registerReadOnlyAttribute(STREAM_ATTRIBUTE, new DeploymentOverlayReadContentHandler(contentRepository));
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(READ_CONTENT_OP_DEFINITION, new ReadContentHandler(contentRepository));
        resourceRegistration.registerOperationHandler(ADD_OP_DEFINITION, addHandler);
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

    private static class DeploymentOverlayReadContentHandler implements OperationStepHandler {

        protected final ContentRepository contentRepository;

        public DeploymentOverlayReadContentHandler(final ContentRepository contentRepository) {
            this.contentRepository = contentRepository;
        }

        @Override
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
            Resource resource = context.getOriginalRootResource();
            for (final PathElement element : address) {
                resource = resource.getChild(element);
            }
            byte[] contentHash = resource.getModel().get(CONTENT).asBytes();
            try {
                TypedInputStream inputStream = contentRepository.readContent(contentHash, "");
                String uuid = context.attachResultStream(inputStream.getContentType(), inputStream);
                context.getResult().get(UUID).set(uuid);
            } catch (ExplodedContentException ex) {
                throw new RuntimeException(ex.getMessage(), ex);
            }
        }
    }
}
