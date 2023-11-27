/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.services.path;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FILESYSTEM_PATH;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Definition of a resource type that represents a logical filesystem path.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class PathResourceDefinition extends SimpleResourceDefinition {

    public static final RuntimeCapability<Void> PATH_CAPABILITY = RuntimeCapability.Builder.of(PathManager.PATH_SERVICE_DESCRIPTOR)
            .setAllowMultipleRegistrations(true) // both /host=master/path=x and /path=x are legal and in the same scope
                                                 // In a better world we'd only set this true in an HC process
                                                 // but that's more trouble than I want to take. Adding a path
                                                 // twice in a server will fail in MODEL due to the dup resource anyway
            .build();

    private static final String SPECIFIED_PATH_RESOURCE_PREFIX = "specified_path";
    private static final String NAMED_PATH_RESOURCE_PREFIX = "named_path";

    public static final PathElement PATH_ADDRESS = PathElement.pathElement(ModelDescriptionConstants.PATH);

    static final SimpleAttributeDefinition NAME =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.NAME, ModelType.STRING, false)
                .setValidator(new StringLengthValidator(1, false))
                .setResourceOnly()
                .build();

    static final SimpleAttributeDefinition PATH_SPECIFIED =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.PATH, ModelType.STRING, false)
                .setAllowExpression(true)
                .setValidator(new StringLengthValidator(1, false))
                .addArbitraryDescriptor(FILESYSTEM_PATH, ModelNode.TRUE)
                .build();

    static final SimpleAttributeDefinition PATH_NAMED =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.PATH, ModelType.STRING, true)
                .setAllowExpression(true)
                .setValidator(new StringLengthValidator(1, true))
                .build();

    static final SimpleAttributeDefinition READ_ONLY =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.READ_ONLY, ModelType.BOOLEAN, true)
                .setDefaultValue(ModelNode.FALSE)
                .setStorageRuntime()
                .setRuntimeServiceNotRequired()
                .build();
    /**
     * A path attribute definition
     */
    public static final SimpleAttributeDefinition PATH = SimpleAttributeDefinitionBuilder.create(PATH_SPECIFIED).build();

    /**
     * A relative-to attribute definition
     */
    public static final SimpleAttributeDefinition RELATIVE_TO =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.RELATIVE_TO, ModelType.STRING, true)
                    .setValidator(new StringLengthValidator(1, true))
                    .build();

    /**
     * Variant of {@link #RELATIVE_TO} used by path resources themselves. RELATIVE_TO has been used over time
     * in other resources as a kind of shared attribute definition, and for those use cases the capability
     * reference configuration we need in the path resource is not appropriate.
     */
    static final SimpleAttributeDefinition RELATIVE_TO_LOCAL = new SimpleAttributeDefinitionBuilder(RELATIVE_TO)
            .setCapabilityReference(PATH_CAPABILITY.getName(), PATH_CAPABILITY)
            .build();

    private final PathManagerService pathManager;
    private final boolean specified;
    private final boolean resolvable;

    /**
     * Creates a PathResourceDefinition.
     *  @param pathManager  the path manager. May be {@code null} if the type of path only represents
     *                      configuration used by other processes and therefore does not
     *                      involve interaction with the path manager
     * @param resolver      resolver for text descriptions. Cannot be {@code null}
     * @param addHandler    handler for the add operation. Cannot be {@code null}
     * @param removeHandler handler for the remove operation. Cannot be {@code null}
     * @param specified     {@code true} if the resource requires details of the path specification;
*                           {@code false} if it can represent a simple logical placeholder for the path
     * @param resolvable    {@code true} if the {@code read-resource} management operation for the resource
*                                should support the {@code resolve-expresssions} parameter
     */
    private PathResourceDefinition(PathManagerService pathManager, ResourceDescriptionResolver resolver, PathAddHandler addHandler, PathRemoveHandler removeHandler, boolean specified, boolean resolvable) {
        super(new Parameters(PATH_ADDRESS, resolver)
                .setAddHandler(addHandler)
                .setRemoveHandler(removeHandler)
                .setCapabilities(PATH_CAPABILITY)
        );
        this.pathManager = pathManager;
        this.specified = specified;
        this.resolvable = resolvable;
    }

    /**
     * Creates a resource definition for a path resource that must have the path specified, but
     * for which the {@code read-resource} management operation should support the
     * {@code resolve-expresssions} parameter.
     * @param pathManager the path manager. Cannot be {@code null}
     * @return the resource definition
     */
    public static PathResourceDefinition createResolvableSpecified(PathManagerService pathManager){
        return new SpecifiedPathResourceDefinition(pathManager, true);
    }

    /**
     * Creates a resource definition for a path resource that must have the path specified, but
     * for which the {@code read-resource} management operation should not support the
     * {@code resolve-expresssions} parameter.
     * @param pathManager the path manager. Cannot be {@code null}
     * @return the resource definition
     */
    public static PathResourceDefinition createSpecified(PathManagerService pathManager) {
        return new SpecifiedPathResourceDefinition(pathManager, false);
    }

    /**
     * Creates a resource definition for a path resource that does not require that the path details
     * be specified. Interaction with the path manager will not be part of the execution of management
     * operations. Only for use by the kernel.
     *
     * @return the resource definition
     */
    public static PathResourceDefinition createNamed() {
        return new NamedPathResourceDefinition(false);
    }

    /**
     * Creates a resource definition for a path resource that must have the path specified, but
     * for which interaction with the path manager should not be part of the execution of management
     * operations.  Only for use by the kernel.
     *
     * @return the resource definition
     */
    public static PathResourceDefinition createSpecifiedNoServices() {
        return new SpecifiedNoServicesPathResourceDefinition(false);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration interfaces) {
        super.registerOperations(interfaces);
        if( resolvable ) {
            interfaces.registerOperationHandler(org.jboss.as.controller.operations.global.ReadResourceHandler.RESOLVE_DEFINITION,
                    org.jboss.as.controller.operations.global.ReadResourceHandler.RESOLVE_INSTANCE, true);
            interfaces.registerOperationHandler(org.jboss.as.controller.operations.global.ReadAttributeHandler.RESOLVE_DEFINITION,
                    org.jboss.as.controller.operations.global.ReadAttributeHandler.RESOLVE_INSTANCE, true);
            interfaces.registerOperationHandler(org.jboss.as.controller.operations.global.ReadAttributeGroupHandler.RESOLVE_DEFINITION,
                    org.jboss.as.controller.operations.global.ReadAttributeGroupHandler.RESOLVE_INSTANCE, true);
        }
        if( this.pathManager != null ) {
            PathInfoHandler.registerOperation(interfaces, PathInfoHandler.Builder.of(pathManager).addAttribute(PATH_SPECIFIED, RELATIVE_TO).build());
        }
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(NAME, ReadResourceNameOperationStepHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(RELATIVE_TO_LOCAL, null, new PathWriteAttributeHandler(pathManager, RELATIVE_TO_LOCAL));
        SimpleAttributeDefinition pathAttr = specified ? PATH_SPECIFIED : PATH_NAMED;
        resourceRegistration.registerReadWriteAttribute(pathAttr, null, new PathWriteAttributeHandler(pathManager, pathAttr));
        resourceRegistration.registerReadOnlyAttribute(READ_ONLY, null);
    }

    private static class SpecifiedPathResourceDefinition extends PathResourceDefinition {
        SpecifiedPathResourceDefinition(PathManagerService pathManager, boolean resolvable){
            super(pathManager,
                    ControllerResolver.getResolver(SPECIFIED_PATH_RESOURCE_PREFIX),
                    PathAddHandler.createSpecifiedInstance(pathManager),
                    PathRemoveHandler.createSpecifiedInstance(pathManager),
                    true,
                    resolvable);
        }
    }

    private static class NamedPathResourceDefinition extends PathResourceDefinition {
        NamedPathResourceDefinition(boolean resolvable){
            super(null,
                    ControllerResolver.getResolver(NAMED_PATH_RESOURCE_PREFIX),
                    PathAddHandler.createNamedInstance(),
                    PathRemoveHandler.createNamedInstance(),
                    false,
                    resolvable);
        }
    }

    private static class SpecifiedNoServicesPathResourceDefinition extends PathResourceDefinition {
        SpecifiedNoServicesPathResourceDefinition(boolean resolvable){
            super(null,
                    ControllerResolver.getResolver(SPECIFIED_PATH_RESOURCE_PREFIX),
                    PathAddHandler.createSpecifiedNoServicesInstance(),
                    PathRemoveHandler.createSpecifiedNoServicesInstance(),
                    true,
                    resolvable);
        }
    }
}
