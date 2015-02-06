/*
* JBoss, Home of Professional Open Source.
* Copyright 2012, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.controller.services.path;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class PathResourceDefinition extends SimpleResourceDefinition {

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
                .build();

    static final SimpleAttributeDefinition PATH_NAMED =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.PATH, ModelType.STRING, true)
                .setAllowExpression(true)
                .setValidator(new StringLengthValidator(1, true))
                .build();

    static final SimpleAttributeDefinition READ_ONLY =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.READ_ONLY, ModelType.BOOLEAN, true)
                .setDefaultValue(new ModelNode(false))
                .setStorageRuntime()
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


    protected final PathManagerService pathManager;
    private final boolean specified;
    private final boolean services;
    private final boolean resolvable;

    private PathResourceDefinition(PathManagerService pathManager, ResourceDescriptionResolver resolver, PathAddHandler addHandler, PathRemoveHandler removeHandler, boolean specified, boolean services, boolean resolvable) {
        super(PATH_ADDRESS, resolver, addHandler, removeHandler);
        this.pathManager = pathManager;
        this.specified = specified;
        this.services = services;
        this.resolvable = resolvable;
    }

    public static PathResourceDefinition createResolvableSpecified(PathManagerService pathManager){
        return new SpecifiedPathResourceDefinition(pathManager, true);
    }
    public static PathResourceDefinition createSpecified(PathManagerService pathManager) {
        return new SpecifiedPathResourceDefinition(pathManager, false);
    }

    public static PathResourceDefinition createNamed(PathManagerService pathManager) {
        return new NamedPathResourceDefinition(pathManager, false);
    }

    public static PathResourceDefinition createSpecifiedNoServices(PathManagerService pathManager) {
        return new SpecifiedNoServicesPathResourceDefinition(pathManager, false);
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
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(NAME, ReadResourceNameOperationStepHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(RELATIVE_TO, null, new PathWriteAttributeHandler(pathManager, RELATIVE_TO, services));
        SimpleAttributeDefinition pathAttr = specified ? PATH_SPECIFIED : PATH_NAMED;
        resourceRegistration.registerReadWriteAttribute(pathAttr, null, new PathWriteAttributeHandler(pathManager, pathAttr, services));
        resourceRegistration.registerReadOnlyAttribute(READ_ONLY, null);
    }

    private static class SpecifiedPathResourceDefinition extends PathResourceDefinition {
        SpecifiedPathResourceDefinition(PathManagerService pathManager, boolean resolvable){
            super(pathManager,
                    ControllerResolver.getResolver(SPECIFIED_PATH_RESOURCE_PREFIX),
                    PathAddHandler.createSpecifiedInstance(pathManager),
                    PathRemoveHandler.createSpecifiedInstance(pathManager),
                    true,
                    true,
                    resolvable);
        }
    }

    private static class NamedPathResourceDefinition extends PathResourceDefinition {
        NamedPathResourceDefinition(PathManagerService pathManager, boolean resolvable){
            super(pathManager,
                    ControllerResolver.getResolver(NAMED_PATH_RESOURCE_PREFIX),
                    PathAddHandler.createNamedInstance(pathManager),
                    PathRemoveHandler.createNamedInstance(pathManager),
                    false,
                    false,
                    resolvable);
        }
    }

    private static class SpecifiedNoServicesPathResourceDefinition extends PathResourceDefinition {
        SpecifiedNoServicesPathResourceDefinition(PathManagerService pathManager, boolean resolvable){
            super(pathManager,
                    ControllerResolver.getResolver(SPECIFIED_PATH_RESOURCE_PREFIX),
                    PathAddHandler.createSpecifiedNoServicesInstance(pathManager),
                    PathRemoveHandler.createSpecifiedNoServicesInstance(pathManager),
                    true,
                    false,
                    resolvable);
        }
    }
}
