/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

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
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.security.auth.realm.JaasSecurityRealm;
import org.wildfly.security.auth.server.SecurityRealm;

import javax.security.auth.callback.CallbackHandler;
import java.io.File;
import java.security.PrivilegedExceptionAction;

import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.resolveClassLoader;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathName;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathResolver;
import static org.wildfly.extension.elytron.SecurityActions.doPrivileged;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

/**
 * A {@link ResourceDefinition} for a {@link SecurityRealm} backed by a JAAS LoginContext.
 */
public class JaasRealmDefinition extends SimpleResourceDefinition {

    private static final SimpleAttributeDefinition ENTRY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ENTRY, ModelType.STRING, false)
            .setRequired(true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PATH, FileAttributeDefinitions.PATH)
            .setAttributeGroup(ElytronDescriptionConstants.FILE)
            .setRequired(false)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition RELATIVE_TO = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RELATIVE_TO, FileAttributeDefinitions.RELATIVE_TO)
            .setAttributeGroup(ElytronDescriptionConstants.FILE)
            .setRestartAllServices()
            .setRequires(ElytronDescriptionConstants.PATH)
            .build();

    static final SimpleAttributeDefinition MODULE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MODULE, ModelType.STRING, false)
            .setRequired(false)
            .setRestartAllServices()
            .build();

    private static final SimpleAttributeDefinition CALLBACK_HANDLER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CALLBACK_HANDLER, ModelType.STRING, true)
            .setRequired(false)
            .setRestartAllServices()
            .build();

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{ENTRY, PATH, RELATIVE_TO, MODULE, CALLBACK_HANDLER};

    private static final AbstractAddStepHandler ADD = new JaasRealmDefinition.RealmAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, SECURITY_REALM_RUNTIME_CAPABILITY);

    JaasRealmDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.JAAS_REALM), ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.JAAS_REALM))
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(SECURITY_REALM_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        AbstractWriteAttributeHandler write = new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, write);
        }
    }

    private static class RealmAddHandler extends BaseAddHandler {

        private RealmAddHandler() {
            super(SECURITY_REALM_RUNTIME_CAPABILITY);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<Void> runtimeCapability = SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            String jaasConfigPath = PATH.resolveModelAttribute(context, model).asStringOrNull();
            String relativeTo = RELATIVE_TO.resolveModelAttribute(context, model).asStringOrNull();
            String callbackHandlerClassName = CALLBACK_HANDLER.resolveModelAttribute(context, model).asStringOrNull();
            String entryName = ENTRY.resolveModelAttribute(context, model).asString();
            final String module = MODULE.resolveModelAttribute(context, model).asStringOrNull();

            CallbackHandler callbackhandler = null;
            ClassLoader classLoader;
            try {
                classLoader = doPrivileged((PrivilegedExceptionAction<ClassLoader>) () -> resolveClassLoader(module));
                if (callbackHandlerClassName != null) {
                    Class<?> typeClazz = classLoader.loadClass(callbackHandlerClassName);
                    callbackhandler = (CallbackHandler) typeClazz.getDeclaredConstructor().newInstance();
                }
            } catch (Exception e) {
                throw ROOT_LOGGER.failedToLoadCallbackhandlerFromProvidedModule();
            }

            final InjectedValue<PathManager> pathManagerInjector = new InjectedValue<>();

            CallbackHandler finalCallbackHandler = callbackhandler;
            TrivialService<SecurityRealm> jaasRealmService = new TrivialService<>(
                    new TrivialService.ValueSupplier<SecurityRealm>() {
                        private FileAttributeDefinitions.PathResolver pathResolver;

                        @Override
                        public SecurityRealm get() throws StartException {
                            String rootPath = null;
                            if (jaasConfigPath != null) {
                                pathResolver = pathResolver();
                                File jaasConfigFile = pathResolver.path(jaasConfigPath).relativeTo(relativeTo, pathManagerInjector.getOptionalValue()).resolve();
                                if (!jaasConfigFile.exists()) {
                                    throw ROOT_LOGGER.jaasFileDoesNotExist(jaasConfigFile.getPath());
                                }
                                rootPath = jaasConfigFile.getPath();
                            }
                            return new JaasSecurityRealm(entryName, rootPath, classLoader, finalCallbackHandler);
                        }

                        @Override
                        public void dispose() {
                            if (pathResolver != null) {
                                pathResolver.clear();
                                pathResolver = null;
                            }
                        }
                    });

            ServiceName realmName = runtimeCapability.getCapabilityServiceName(SecurityRealm.class);
            ServiceBuilder<SecurityRealm> serviceBuilder = serviceTarget.addService(realmName, jaasRealmService);

            if (relativeTo != null) {
                serviceBuilder.addDependency(PathManagerService.SERVICE_NAME, PathManager.class, pathManagerInjector);
                serviceBuilder.requires(pathName(relativeTo));
            }

            commonDependencies(serviceBuilder)
                    .setInitialMode(ServiceController.Mode.ACTIVE)
                    .install();
        }
    }
}
