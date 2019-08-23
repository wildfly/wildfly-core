/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.patching.management;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTACHED_STREAMS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILESYSTEM_PATH;

import java.io.IOException;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PrimitiveListAttributeDefinition;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.patching.Constants;
import org.jboss.as.patching.installation.Identity;
import org.jboss.as.patching.installation.PatchableTarget;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Emanuel Muckenhuber
 */
class PatchResourceDefinition extends SimpleResourceDefinition {

    public static final String NAME = "patching";
    private static final String RESOURCE_NAME = PatchResourceDefinition.class.getPackage().getName() + ".LocalDescriptions";
    static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.CORE_SERVICE, NAME);

    private static ResourceDescriptionResolver getResourceDescriptionResolver(final String keyPrefix) {
        return new StandardResourceDescriptionResolver(keyPrefix, RESOURCE_NAME, PatchResourceDefinition.class.getClassLoader(), true, false);
    }

    private static final AttributeDefinition VERSION = SimpleAttributeDefinitionBuilder.create("version", ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    private static final AttributeDefinition CUMULATIVE_PATCH_ID = SimpleAttributeDefinitionBuilder.create(Constants.CUMULATIVE, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    private static final AttributeDefinition PATCHES = PrimitiveListAttributeDefinition.Builder.of(Constants.PATCHES, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    // Patch operation
    static final AttributeDefinition EXCLUDE_AGEDOUT = SimpleAttributeDefinitionBuilder.create(Constants.EXCLUDE_AGED_OUT, ModelType.BOOLEAN)
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .build();
    private static final AttributeDefinition INPUT_STREAM_IDX_DEF = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.INPUT_STREAM_INDEX, ModelType.INT)
            .setDefaultValue(ModelNode.ZERO)
            .setRequired(false)
            .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
            .addArbitraryDescriptor(ATTACHED_STREAMS, ModelNode.TRUE)
            .build();
    private static final AttributeDefinition OVERRIDE_MODULES = SimpleAttributeDefinitionBuilder.create(Constants.OVERRIDE_MODULES, ModelType.BOOLEAN)
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .build();
    private static final AttributeDefinition OVERRIDE_ALL = SimpleAttributeDefinitionBuilder.create(Constants.OVERRIDE_ALL, ModelType.BOOLEAN)
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .build();
    private static final AttributeDefinition OVERRIDE = PrimitiveListAttributeDefinition.Builder.of(Constants.OVERRIDE, ModelType.STRING)
            .setRequired(false)
            .build();
    private static final AttributeDefinition PRESERVE = PrimitiveListAttributeDefinition.Builder.of(Constants.PRESERVE, ModelType.STRING)
            .setRequired(false)
            .build();

    private static final OperationDefinition PATCH = new SimpleOperationDefinitionBuilder(Constants.PATCH, getResourceDescriptionResolver(PatchResourceDefinition.NAME))
            .addParameter(INPUT_STREAM_IDX_DEF)
            .addParameter(OVERRIDE_ALL)
            .addParameter(OVERRIDE_MODULES)
            .addParameter(OVERRIDE)
            .addParameter(PRESERVE)
            .build();

    static final AttributeDefinition PATCH_ID = SimpleAttributeDefinitionBuilder.create(Constants.PATCH_ID, ModelType.STRING)
            .build();

    static final AttributeDefinition PATCH_ID_OPTIONAL = SimpleAttributeDefinitionBuilder.create(Constants.PATCH_ID, ModelType.STRING, true)
            .build();

    static final AttributeDefinition RESET_CONFIGURATION = SimpleAttributeDefinitionBuilder.create(Constants.RESET_CONFIGURATION, ModelType.BOOLEAN)
            .setRequired(true)
            .build();

    static final AttributeDefinition ROLLBACK_TO = SimpleAttributeDefinitionBuilder.create(Constants.ROLLBACK_TO, ModelType.BOOLEAN)
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .build();

    static final AttributeDefinition VERBOSE = SimpleAttributeDefinitionBuilder.create(Constants.VERBOSE, ModelType.BOOLEAN)
            .setDefaultValue(ModelNode.FALSE)
            .setRequired(false)
            .build();

    private static final OperationDefinition ROLLBACK = new SimpleOperationDefinitionBuilder(Constants.ROLLBACK, getResourceDescriptionResolver(PatchResourceDefinition.NAME))
            .addParameter(PATCH_ID)
            .addParameter(ROLLBACK_TO)
            .addParameter(RESET_CONFIGURATION)
            .addParameter(OVERRIDE_ALL)
            .addParameter(OVERRIDE_MODULES)
            .addParameter(OVERRIDE)
            .addParameter(PRESERVE)
            .build();

    private static final OperationDefinition ROLLBACK_LAST = new SimpleOperationDefinitionBuilder(Constants.ROLLBACK_LAST, getResourceDescriptionResolver(PatchResourceDefinition.NAME))
            .addParameter(RESET_CONFIGURATION)
            .addParameter(OVERRIDE_ALL)
            .addParameter(OVERRIDE_MODULES)
            .addParameter(OVERRIDE)
            .addParameter(PRESERVE)
            .build();

    private static final OperationDefinition SHOW_HISTORY = new SimpleOperationDefinitionBuilder(Constants.SHOW_HISTORY, getResourceDescriptionResolver(PatchResourceDefinition.NAME))
            .addParameter(EXCLUDE_AGEDOUT)
            .build();

    private static final OperationDefinition AGEOUT_HISTORY = new SimpleOperationDefinitionBuilder(Constants.AGEOUT_HISTORY, getResourceDescriptionResolver(PatchResourceDefinition.NAME))
            .build();

    private static final OperationDefinition PATCH_INFO = new SimpleOperationDefinitionBuilder(Constants.PATCH_INFO, getResourceDescriptionResolver(PatchResourceDefinition.NAME))
            .addParameter(PATCH_ID_OPTIONAL)
            .addParameter(VERBOSE)
            .setReplyType(ModelType.OBJECT)
            .setReplyParameters(
                    PATCH_ID,
                    SimpleAttributeDefinitionBuilder.create(Constants.TYPE, ModelType.STRING).build(),
                    SimpleAttributeDefinitionBuilder.create(Constants.IDENTITY_NAME, ModelType.STRING).build(),
                    SimpleAttributeDefinitionBuilder.create(Constants.IDENTITY_VERSION, ModelType.STRING).build(),
                    SimpleAttributeDefinitionBuilder.create(Constants.DESCRIPTION, ModelType.STRING).build(),
                    SimpleAttributeDefinitionBuilder.create(Constants.LINK, ModelType.STRING).setRequired(false).build(),
                    ObjectListAttributeDefinition.Builder.of(Constants.ELEMENTS,
                            new ObjectTypeAttributeDefinition.Builder(Constants.ELEMENTS,
                                    PATCH_ID,
                                    SimpleAttributeDefinitionBuilder.create(Constants.TYPE, ModelType.STRING).build(),
                                    SimpleAttributeDefinitionBuilder.create(Constants.NAME, ModelType.STRING).build(),
                                    SimpleAttributeDefinitionBuilder.create(Constants.DESCRIPTION, ModelType.STRING).build())
                            .build()).build()
                    )
            .build();

    static final ResourceDefinition INSTANCE = new PatchResourceDefinition();

    private PatchResourceDefinition() {
        super(new Parameters(PATH, getResourceDescriptionResolver(NAME))
                .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.PATCHING)
                .setFeature(false)  // TODO this is not needed when setRuntime is set but just in case I'm wrong and removing it will break urgent Galleon work I'm leaving it
                .setRuntime()
        );
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registry) {
        super.registerAttributes(registry);
        registerPatchingAttributes(registry);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration registry) {
        super.registerOperations(registry);
        registerPatchingOperations(registry, true);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        registerPatchingChildren(resourceRegistration);

        final StandardResourceDescriptionResolver resolver = new StandardResourceDescriptionResolver("patching.patch-stream", RESOURCE_NAME, PatchResourceDefinition.class.getClassLoader());
        resourceRegistration.registerSubModel(new SimpleResourceDefinition(new Parameters(PathElement.pathElement("patch-stream"), resolver)
                .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.PATCHING)
                .setRuntime()
        ) {

            @Override
            public void registerAttributes(final ManagementResourceRegistration registry) {
                super.registerAttributes(registry);
                registerPatchingAttributes(registry);
            }

            @Override
            public void registerOperations(ManagementResourceRegistration registry) {
                PatchResourceDefinition.this.registerPatchingOperations(registry, false);
            }

            @Override
            public void registerChildren(ManagementResourceRegistration registry) {
                PatchResourceDefinition.this.registerPatchingChildren(registry);
            }
        });
    }

    private void registerPatchingOperations(ManagementResourceRegistration registry, boolean patchOp) {
        registry.registerOperationHandler(ROLLBACK, LocalPatchRollbackHandler.INSTANCE);
        registry.registerOperationHandler(ROLLBACK_LAST, LocalPatchRollbackLastHandler.INSTANCE);
        if(patchOp) {
            registry.registerOperationHandler(PATCH, LocalPatchOperationStepHandler.INSTANCE);
        }
        registry.registerOperationHandler(SHOW_HISTORY, LocalShowHistoryHandler.INSTANCE);
        registry.registerOperationHandler(AGEOUT_HISTORY, LocalAgeoutHistoryHandler.INSTANCE);
        registry.registerOperationHandler(PATCH_INFO, PatchInfoHandler.INSTANCE);
    }

    private void registerPatchingAttributes(ManagementResourceRegistration registry) {

        registry.registerReadOnlyAttribute(VERSION, new PatchAttributeReadHandler() {
            @Override
            void handle(ModelNode result, Identity info) {
                result.set(info.getVersion());
            }
        });
        registry.registerReadOnlyAttribute(CUMULATIVE_PATCH_ID, new PatchAttributeReadHandler() {
            @Override
            void handle(ModelNode result, Identity info) throws IOException {
                result.set(info.loadTargetInfo().getCumulativePatchID());
            }
        });
        registry.registerReadOnlyAttribute(PATCHES, new PatchAttributeReadHandler() {
            @Override
            void handle(ModelNode result, Identity info) throws IOException {
                result.setEmptyList();
                for (final String id : info.loadTargetInfo().getPatchIDs()) {
                    result.add(id);
                }
            }
        });
    }

    private void registerPatchingChildren(ManagementResourceRegistration registry) {

        StandardResourceDescriptionResolver resolver = new StandardResourceDescriptionResolver("patching.layer", "org.jboss.as.patching.management.LocalDescriptions", PatchResourceDefinition.class.getClassLoader());
        registry.registerSubModel(new SimpleResourceDefinition(new Parameters(PathElement.pathElement("layer"), resolver)
                .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.PATCHING)
                .setRuntime()
        ){

            @Override
            public void registerAttributes(final ManagementResourceRegistration resource) {
                resource.registerReadOnlyAttribute(CUMULATIVE_PATCH_ID, new ElementProviderAttributeReadHandler.LayerAttributeReadHandler() {
                    @Override
                    void handle(ModelNode result, PatchableTarget layer) throws OperationFailedException {
                        try {
                            result.set(layer.loadTargetInfo().getCumulativePatchID());
                        } catch (IOException e) {
                            throw new OperationFailedException(PatchLogger.ROOT_LOGGER.failedToLoadInfo(layer.getName()), e);
                        }
                    }
                });
                resource.registerReadOnlyAttribute(PATCHES, new ElementProviderAttributeReadHandler.LayerAttributeReadHandler() {
                    @Override
                    void handle(ModelNode result, PatchableTarget layer) throws OperationFailedException {
                        result.setEmptyList();
                        try {
                            for (final String id : layer.loadTargetInfo().getPatchIDs()) {
                                result.add(id);
                            }
                        } catch (IOException e) {
                            throw new OperationFailedException(PatchLogger.ROOT_LOGGER.failedToLoadInfo(layer.getName()), e);
                        }
                    }
                });
            }
        });

        resolver = new StandardResourceDescriptionResolver("patching.addon", "org.jboss.as.patching.management.LocalDescriptions", PatchResourceDefinition.class.getClassLoader());
        registry.registerSubModel(new SimpleResourceDefinition(new Parameters(PathElement.pathElement("addon"), resolver)
                .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.PATCHING)
                .setRuntime()
        ) {
            @Override
            public void registerAttributes(final ManagementResourceRegistration resource) {
                resource.registerReadOnlyAttribute(CUMULATIVE_PATCH_ID, new ElementProviderAttributeReadHandler.AddOnAttributeReadHandler() {
                    @Override
                    void handle(ModelNode result, PatchableTarget addon) throws OperationFailedException {
                        try {
                            result.set(addon.loadTargetInfo().getCumulativePatchID());
                        } catch (IOException e) {
                            throw new OperationFailedException(PatchLogger.ROOT_LOGGER.failedToLoadInfo(addon.getName()), e);
                        }
                    }
                });
                resource.registerReadOnlyAttribute(PATCHES, new ElementProviderAttributeReadHandler.AddOnAttributeReadHandler() {
                    @Override
                    void handle(ModelNode result, PatchableTarget addon) throws OperationFailedException {
                        result.setEmptyList();
                        try {
                            for (final String id : addon.loadTargetInfo().getPatchIDs()) {
                                result.add(id);
                            }
                        } catch (IOException e) {
                            throw new OperationFailedException(PatchLogger.ROOT_LOGGER.failedToLoadInfo(addon.getName()), e);
                        }
                    }
                });
            }
        });

    }
}

