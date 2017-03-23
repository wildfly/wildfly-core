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

import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronExtension.ISO_8601_FORMAT;
import static org.wildfly.extension.elytron.ElytronExtension.asStringIfDefined;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.RELATIVE_TO;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathName;
import static org.wildfly.extension.elytron.RealmDefinitions.CASE_SENSITIVE;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathEntry;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManager.Callback.Handle;
import org.jboss.as.controller.services.path.PathManager.Event;
import org.jboss.as.controller.services.path.PathManager.PathEventContext;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.security.auth.SupportLevel;
import org.wildfly.security.auth.realm.LegacyPropertiesSecurityRealm;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.auth.server.event.RealmEvent;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.evidence.Evidence;

/**
 * A {@link ResourceDefinition} for a {@link SecurityRealm} backed by properties files.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class PropertiesRealmDefinition extends TrivialResourceDefinition {

    static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PATH, FileAttributeDefinitions.PATH)
            .setRequired(true)
            .build();

    static final SimpleAttributeDefinition PLAIN_TEXT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PLAIN_TEXT, ModelType.BOOLEAN, true)
            .setDefaultValue(new ModelNode(false))
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition DIGEST_REALM_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DIGEST_REALM_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final ObjectTypeAttributeDefinition USERS_PROPERTIES = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.USERS_PROPERTIES, PATH, RELATIVE_TO, DIGEST_REALM_NAME, PLAIN_TEXT)
        .setAllowNull(false)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .build();

    static final ObjectTypeAttributeDefinition GROUPS_PROPERTIES = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.GROUPS_PROPERTIES, PATH, RELATIVE_TO)
        .setAllowNull(true)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .build();

    static final SimpleAttributeDefinition GROUPS_ATTRIBUTE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.GROUPS_ATTRIBUTE, ModelType.STRING, true)
        .setDefaultValue(new ModelNode(ElytronDescriptionConstants.GROUPS))
        .setAllowExpression(true)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .build();

    static final SimpleAttributeDefinition SYNCHRONIZED = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SYNCHRONIZED, ModelType.STRING)
        .setStorageRuntime()
        .build();

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { USERS_PROPERTIES, GROUPS_PROPERTIES, GROUPS_ATTRIBUTE, CASE_SENSITIVE };

    // Resource Resolver

    static final StandardResourceDescriptionResolver RESOURCE_RESOLVER = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.PROPERTIES_REALM);

    // Operations

    static final SimpleOperationDefinition LOAD = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.LOAD, RESOURCE_RESOLVER)
            .build();

    private static final AbstractAddStepHandler ADD = new TrivialAddHandler<SecurityRealm>(SecurityRealm.class, ATTRIBUTES, SECURITY_REALM_RUNTIME_CAPABILITY) {

        @Override
        protected ValueSupplier<SecurityRealm> getValueSupplier(ServiceBuilder<SecurityRealm> serviceBuilder,
                OperationContext context, ModelNode model) throws OperationFailedException {

            final String usersPath;
            final String usersRelativeTo;
            final String groupsPath;
            final String groupsRelativeTo;
            final boolean plainText;
            final String digestRealmName;
            final String groupsAttribute = GROUPS_ATTRIBUTE.resolveModelAttribute(context, model).asString();

            ModelNode usersProperties = USERS_PROPERTIES.resolveModelAttribute(context, model);
            usersPath = asStringIfDefined(context, PATH, usersProperties);
            usersRelativeTo = asStringIfDefined(context, RELATIVE_TO, usersProperties);
            digestRealmName = asStringIfDefined(context, DIGEST_REALM_NAME, usersProperties);
            plainText = PLAIN_TEXT.resolveModelAttribute(context, usersProperties).asBoolean();

            ModelNode groupsProperties = GROUPS_PROPERTIES.resolveModelAttribute(context, model);
            if (groupsProperties.isDefined()) {
                groupsPath = asStringIfDefined(context, PATH, groupsProperties);
                groupsRelativeTo = asStringIfDefined(context, RELATIVE_TO, groupsProperties);
            } else {
                groupsPath = null;
                groupsRelativeTo = null;
            }

            final InjectedValue<PathManager> pathManagerInjector = new InjectedValue<>();

            if (usersRelativeTo != null || groupsRelativeTo != null) {
                serviceBuilder.addDependency(PathManagerService.SERVICE_NAME, PathManager.class, pathManagerInjector);
                if (usersRelativeTo != null) {
                    serviceBuilder.addDependency(pathName(usersRelativeTo));
                }
                if (groupsRelativeTo != null) {
                    serviceBuilder.addDependency(pathName(groupsRelativeTo));
                }
            }

            return new ValueSupplier<SecurityRealm>() {

                private final List<Handle> callbackHandles = new ArrayList<>();

                @Override
                public SecurityRealm get() throws StartException {
                    File usersFile = resolveFileLocation(usersPath, usersRelativeTo);
                    File groupsFile = groupsPath != null ? resolveFileLocation(groupsPath, groupsRelativeTo) : null;

                    try (InputStream usersInputStream = new FileInputStream(usersFile);
                            InputStream groupsInputStream = groupsFile != null ? new FileInputStream(groupsFile) : null) {
                        return new RealmWrapper(LegacyPropertiesSecurityRealm.builder()
                                .setUsersStream(usersInputStream)
                                .setGroupsStream(groupsInputStream)
                                .setPlainText(plainText)
                                .setGroupsAttribute(groupsAttribute)
                                .setDefaultRealm(digestRealmName)
                                .build(), usersFile, groupsFile);

                    } catch (FileNotFoundException e) {
                        throw ROOT_LOGGER.propertyFilesDoesNotExist(e.getMessage());
                    } catch (RealmUnavailableException e) {
                        throw ROOT_LOGGER.propertyFileIsInvalid(e.getMessage(), e.getCause());
                    } catch (IOException e) {
                        throw ROOT_LOGGER.unableToLoadPropertiesFiles(e, usersFile.toString(), groupsFile != null ? groupsFile.toString() : null);
                    }
                }

                @Override
                public void dispose() {
                    callbackHandles.forEach(h -> h.remove());
                }

                private File resolveFileLocation(String path, String relativeTo) {
                    final File resolvedPath;
                    if (relativeTo != null) {
                        PathManager pathManager =  pathManagerInjector.getValue();
                        resolvedPath = new File(pathManager.resolveRelativePathEntry(path, relativeTo));
                        Handle callbackHandle = pathManager.registerCallback(relativeTo, new org.jboss.as.controller.services.path.PathManager.Callback() {

                            @Override
                            public void pathModelEvent(PathEventContext eventContext, String name) {
                                if (eventContext.isResourceServiceRestartAllowed() == false) {
                                    eventContext.reloadRequired();
                                }
                            }

                            @Override
                            public void pathEvent(Event event, PathEntry pathEntry) {
                                // Service dependencies should trigger a stop and start.
                            }
                        }, Event.REMOVED, Event.UPDATED);
                        callbackHandles.add(callbackHandle);
                    } else {
                        resolvedPath = new File(path);
                    }

                    return resolvedPath;
                }

            };
        }

    };

    PropertiesRealmDefinition() {
        super(ElytronDescriptionConstants.PROPERTIES_REALM, RESOURCE_RESOLVER, ADD, ATTRIBUTES, SECURITY_REALM_RUNTIME_CAPABILITY);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);

        resourceRegistration.registerReadOnlyAttribute(SYNCHRONIZED, new PropertiesRuntimeHandler(false) {

            @Override
            void performRuntime(OperationContext context, RealmWrapper securityRealm) throws OperationFailedException {
                SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_FORMAT);
                context.getResult().set(sdf.format(new Date(securityRealm.getLoadTime())));
            }
        });
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);

        resourceRegistration.registerOperationHandler(LOAD, new PropertiesRuntimeHandler(true) {

            @Override
            void performRuntime(OperationContext context, RealmWrapper securityRealm) throws OperationFailedException {
                securityRealm.reload();
            }
        });
    }

    abstract static class PropertiesRuntimeHandler extends ElytronRuntimeOnlyHandler {

        private final boolean writeAccess;

        PropertiesRuntimeHandler(final boolean writeAccess) {
            this.writeAccess = writeAccess;
        }

        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            ServiceName securityRealmName = SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue()).getCapabilityServiceName();

            ServiceController<SecurityRealm> serviceContainer = getRequiredService(context.getServiceRegistry(writeAccess), securityRealmName, SecurityRealm.class);
            State serviceState;
            if ((serviceState = serviceContainer.getState()) != State.UP) {
                throw ROOT_LOGGER.requiredServiceNotUp(securityRealmName, serviceState);
            }

            SecurityRealm securityRealm = serviceContainer.getValue();

            assert securityRealm instanceof RealmWrapper;

            performRuntime(context, (RealmWrapper) securityRealm);
        }

        abstract void performRuntime(OperationContext context, RealmWrapper securityRealm) throws OperationFailedException;

    }

    private static final class RealmWrapper implements SecurityRealm {

        private final LegacyPropertiesSecurityRealm delegate;
        private final File usersFile;
        private final File groupsFile;

        RealmWrapper(LegacyPropertiesSecurityRealm delegate, File usersFile, File groupsFile) {
            this.delegate = delegate;
            this.usersFile = usersFile;
            this.groupsFile = groupsFile;
        }

        @Override
        public RealmIdentity getRealmIdentity(Principal principal) throws RealmUnavailableException {
            return delegate.getRealmIdentity(principal);
        }

        @Override
        public RealmIdentity getRealmIdentity(Evidence evidence) throws RealmUnavailableException {
            return delegate.getRealmIdentity(evidence);
        }

        @Override
        public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName)
                throws RealmUnavailableException {
            return delegate.getCredentialAcquireSupport(credentialType, algorithmName);
        }

        @Override
        public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName)
                throws RealmUnavailableException {
            return delegate.getEvidenceVerifySupport(evidenceType, algorithmName);
        }

        @Override
        public void handleRealmEvent(RealmEvent event) {
            delegate.handleRealmEvent(event);
        }

        long getLoadTime() {
            return delegate.getLoadTime();
        }

        void reload() throws OperationFailedException {
            try (InputStream usersInputStream = new FileInputStream(usersFile);
                    InputStream groupsInputStream = groupsFile != null ? new FileInputStream(groupsFile) : null) {
                delegate.load(usersInputStream, groupsInputStream);
            } catch (IOException e) {
                throw ROOT_LOGGER.unableToReLoadPropertiesFiles(e);
            }
        }

    }

}

