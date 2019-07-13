/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathName;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathResolver;

import java.nio.file.Path;
import java.security.KeyStore;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.FileAttributeDefinitions.PathResolver;
import org.wildfly.security.auth.realm.FileSystemSecurityRealm;
import org.wildfly.security.auth.server.NameRewriter;
import org.wildfly.security.auth.server.SecurityRealm;


/**
 * A {@link ResourceDefinition} for a {@link SecurityRealm} backed by a {@link KeyStore}.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
class FileSystemRealmDefinition extends SimpleResourceDefinition {

    static final SimpleAttributeDefinition PATH =
            new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PATH, FileAttributeDefinitions.PATH)
                    .setAttributeGroup(ElytronDescriptionConstants.FILE)
                    .setRequired(true)
                    .setRestartAllServices()
                    .build();

    static final SimpleAttributeDefinition RELATIVE_TO =
            new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RELATIVE_TO, FileAttributeDefinitions.RELATIVE_TO)
                    .setAttributeGroup(ElytronDescriptionConstants.FILE)
                    .setRestartAllServices()
                    .build();

    static final SimpleAttributeDefinition LEVELS =
            new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.LEVELS, ModelType.INT, true)
                    .setDefaultValue(new ModelNode(2))
                    .setAllowExpression(true)
                    .setRestartAllServices()
                    .build();

    static final SimpleAttributeDefinition ENCODED =
            new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ENCODED, ModelType.BOOLEAN, true)
                    .setDefaultValue(ModelNode.TRUE)
                    .setAllowExpression(true)
                    .setRestartAllServices()
                    .build();

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{PATH, RELATIVE_TO, LEVELS, ENCODED};

    private static final AbstractAddStepHandler ADD = new RealmAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY, SECURITY_REALM_RUNTIME_CAPABILITY);

    FileSystemRealmDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM),
                ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.FILESYSTEM_REALM))
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY, SECURITY_REALM_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        AbstractWriteAttributeHandler handler = new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition attr : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attr, null, handler);
        }
    }

    private static class RealmAddHandler extends BaseAddHandler {

        private RealmAddHandler() {
            super(SECURITY_REALM_RUNTIME_CAPABILITY, ATTRIBUTES);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();

            String address = context.getCurrentAddressValue();
            ServiceName mainServiceName = MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(address).getCapabilityServiceName();
            ServiceName aliasServiceName = SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(address).getCapabilityServiceName();

            final int levels = LEVELS.resolveModelAttribute(context, model).asInt();

            final boolean encoded = ENCODED.resolveModelAttribute(context, model).asBoolean();

            final String path = PATH.resolveModelAttribute(context, model).asString();
            final String relativeTo = RELATIVE_TO.resolveModelAttribute(context, model).asStringOrNull();

            final InjectedValue<PathManager> pathManagerInjector = new InjectedValue<>();
            final InjectedValue<NameRewriter> nameRewriterInjector = new InjectedValue<>();

            TrivialService<SecurityRealm> fileSystemRealmService = new TrivialService<>(
                    new TrivialService.ValueSupplier<SecurityRealm>() {

                        private PathResolver pathResolver;

                        @Override
                        public SecurityRealm get() throws StartException {
                            pathResolver = pathResolver();
                            Path rootPath = pathResolver.path(path).relativeTo(relativeTo, pathManagerInjector.getOptionalValue()).resolve().toPath();

                            NameRewriter nameRewriter = nameRewriterInjector.getOptionalValue();

                            return nameRewriter != null ?
                                    new FileSystemSecurityRealm(rootPath, nameRewriter, levels, encoded) :
                                    new FileSystemSecurityRealm(rootPath, NameRewriter.IDENTITY_REWRITER, levels, encoded);
                        }

                        @Override
                        public void dispose() {
                            if (pathResolver != null) {
                                pathResolver.clear();
                                pathResolver = null;
                            }
                        }

                    });

            ServiceBuilder<SecurityRealm> serviceBuilder = serviceTarget.addService(mainServiceName, fileSystemRealmService)
                    .addAliases(aliasServiceName);

            if (relativeTo != null) {
                serviceBuilder.addDependency(PathManagerService.SERVICE_NAME, PathManager.class, pathManagerInjector);
                serviceBuilder.requires(pathName(relativeTo));
            }
            serviceBuilder.install();
        }

    }

}
