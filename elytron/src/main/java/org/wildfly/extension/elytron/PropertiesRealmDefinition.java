/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;


import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.BASE64;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HEX;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.UTF_8;
import static org.wildfly.extension.elytron.ElytronExtension.ISO_8601_FORMAT;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.RELATIVE_TO;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathName;
import static org.wildfly.extension.elytron.RealmDefinitions.createBruteForceRealmTransformer;
import static org.wildfly.extension.elytron.SecurityActions.doPrivileged;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.spec.AlgorithmParameterSpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.function.LongSupplier;

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
import org.jboss.as.controller.operations.validation.CharsetValidator;
import org.jboss.as.controller.operations.validation.StringAllowedValuesValidator;
import org.jboss.as.controller.registry.AttributeAccess;
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
import org.wildfly.common.function.ExceptionBiConsumer;
import org.wildfly.extension.elytron.TrivialResourceDefinition.Builder;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.security.auth.SupportLevel;
import org.wildfly.security.auth.realm.LegacyPropertiesSecurityRealm;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.auth.server.event.RealmEvent;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.evidence.Evidence;
import org.wildfly.security.password.spec.Encoding;

/**
 * A {@link ResourceDefinition} for a {@link SecurityRealm} backed by properties files.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class PropertiesRealmDefinition {

    static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PATH, FileAttributeDefinitions.PATH)
            .setRequired(true)
            .build();

    private static final SimpleAttributeDefinition PLAIN_TEXT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PLAIN_TEXT, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.FALSE)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    private static final SimpleAttributeDefinition DIGEST_REALM_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DIGEST_REALM_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final ObjectTypeAttributeDefinition USERS_PROPERTIES = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.USERS_PROPERTIES, PATH, RELATIVE_TO, DIGEST_REALM_NAME, PLAIN_TEXT)
        .setRequired(true)
        .setRestartAllServices()
        .build();

    static final ObjectTypeAttributeDefinition GROUPS_PROPERTIES = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.GROUPS_PROPERTIES, PATH, RELATIVE_TO)
        .setRequired(false)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition GROUPS_ATTRIBUTE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.GROUPS_ATTRIBUTE, ModelType.STRING, true)
        .setDefaultValue(new ModelNode(ElytronDescriptionConstants.GROUPS))
        .setAllowExpression(true)
        .setRestartAllServices()
        .build();

    private static final SimpleAttributeDefinition SYNCHRONIZED = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SYNCHRONIZED, ModelType.STRING)
        .setStorageRuntime()
        .build();

    static final SimpleAttributeDefinition HASH_ENCODING = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.HASH_ENCODING, ModelType.STRING, true)
            .setDefaultValue(new ModelNode(HEX))
            .setValidator(new StringAllowedValuesValidator(BASE64, HEX))
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition HASH_CHARSET = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.HASH_CHARSET, ModelType.STRING, true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setValidator(new CharsetValidator())
            .setDefaultValue(new ModelNode(UTF_8))
            .setAllowExpression(true)
            .build();

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { USERS_PROPERTIES, GROUPS_PROPERTIES, GROUPS_ATTRIBUTE, HASH_ENCODING, HASH_CHARSET };

    // Resource Resolver

    private static final StandardResourceDescriptionResolver RESOURCE_RESOLVER = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.PROPERTIES_REALM);

    // Operations

    private static final SimpleOperationDefinition LOAD = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.LOAD, RESOURCE_RESOLVER)
            .setRuntimeOnly()
            .build();

    private static final AbstractAddStepHandler ADD = new TrivialAddHandler<SecurityRealm>(SecurityRealm.class, SECURITY_REALM_RUNTIME_CAPABILITY) {

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
            final String hashEncoding = HASH_ENCODING.resolveModelAttribute(context, model).asString();
            final String hashCharset = HASH_CHARSET.resolveModelAttribute(context, model).asString();

            ModelNode usersProperties = USERS_PROPERTIES.resolveModelAttribute(context, model);
            usersPath = PATH.resolveModelAttribute(context, usersProperties).asStringOrNull();
            usersRelativeTo = RELATIVE_TO.resolveModelAttribute(context, usersProperties).asStringOrNull();
            digestRealmName = DIGEST_REALM_NAME.resolveModelAttribute(context, usersProperties).asStringOrNull();
            plainText = PLAIN_TEXT.resolveModelAttribute(context, usersProperties).asBoolean();

            ModelNode groupsProperties = GROUPS_PROPERTIES.resolveModelAttribute(context, model);
            if (groupsProperties.isDefined()) {
                groupsPath = PATH.resolveModelAttribute(context, groupsProperties).asStringOrNull();
                groupsRelativeTo = RELATIVE_TO.resolveModelAttribute(context, groupsProperties).asStringOrNull();
            } else {
                groupsPath = null;
                groupsRelativeTo = null;
            }

            final InjectedValue<PathManager> pathManagerInjector = new InjectedValue<>();

            if (usersRelativeTo != null || groupsRelativeTo != null) {
                serviceBuilder.addDependency(PathManagerService.SERVICE_NAME, PathManager.class, pathManagerInjector);
                if (usersRelativeTo != null) {
                    serviceBuilder.requires(pathName(usersRelativeTo));
                }
                if (groupsRelativeTo != null) {
                    serviceBuilder.requires(pathName(groupsRelativeTo));
                }
            }

            Function<SecurityRealm, SecurityRealm> realmTransformer =
                createBruteForceRealmTransformer(context.getCurrentAddressValue(), SecurityRealm.class, serviceBuilder);
            return new ValueSupplier<SecurityRealm>() {

                private final List<Handle> callbackHandles = new ArrayList<>();

                @Override
                public SecurityRealm get() throws StartException {
                    File usersFile = resolveFileLocation(usersPath, usersRelativeTo);
                    File groupsFile = groupsPath != null ? resolveFileLocation(groupsPath, groupsRelativeTo) : null;

                    try (InputStream usersInputStream = new FileInputStream(usersFile);
                            InputStream groupsInputStream = groupsFile != null ? new FileInputStream(groupsFile) : null) {
                        LegacyPropertiesSecurityRealm baseRealm = LegacyPropertiesSecurityRealm.builder()
                                .setUsersStream(usersInputStream)
                                .setGroupsStream(groupsInputStream)
                                .setPlainText(plainText)
                                .setGroupsAttribute(groupsAttribute)
                                .setDefaultRealm(digestRealmName)
                                .setHashEncoding(BASE64.equalsIgnoreCase(hashEncoding) ? Encoding.BASE64 : Encoding.HEX)
                                .setHashCharset(Charset.forName(hashCharset))
                                .build();

                        return new RealmWrapper(realmTransformer.apply(baseRealm), usersFile, groupsFile, baseRealm::getLoadTime, baseRealm::load);

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
                    for (Handle h : callbackHandles) {
                        h.remove();
                    }
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

    static ResourceDefinition create(boolean serverOrHostController) {
        Builder builder = TrivialResourceDefinition.builder()
                .setPathKey(ElytronDescriptionConstants.PROPERTIES_REALM)
                .setResourceDescriptionResolver(RESOURCE_RESOLVER)
                .setAddHandler(ADD)
                .setAttributes(ATTRIBUTES)
                .setRuntimeCapabilities(SECURITY_REALM_RUNTIME_CAPABILITY);

        if (serverOrHostController) {
            builder.addReadOnlyAttribute(SYNCHRONIZED, new PropertiesRuntimeHandler(false) {

                @Override
                void performRuntime(OperationContext context, RealmWrapper securityRealm) throws OperationFailedException {
                    SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_FORMAT);
                    context.getResult().set(sdf.format(new Date(securityRealm.getLoadTime())));
                }
            });
        }

        builder.addOperation(LOAD, new PropertiesRuntimeHandler(true) {

            @Override
            void performRuntime(OperationContext context, RealmWrapper securityRealm) throws OperationFailedException {
                securityRealm.reload();
            }
        });

        return builder.build();
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

        private final SecurityRealm delegate;
        private final File usersFile;
        private final File groupsFile;
        private final LongSupplier loadTimeSupplier;
        private final ExceptionBiConsumer<InputStream, InputStream, IOException> propertiesFileLoader;

        RealmWrapper(SecurityRealm delegate, File usersFile, File groupsFile, LongSupplier loadTimeSupplier,
                ExceptionBiConsumer<InputStream, InputStream, IOException>  propertiesFileLoader) {
            this.delegate = delegate;
            this.usersFile = usersFile;
            this.groupsFile = groupsFile;
            this.loadTimeSupplier = loadTimeSupplier;
            this.propertiesFileLoader = propertiesFileLoader;
        }

        @Override
        public RealmIdentity getRealmIdentity(Principal principal) throws RealmUnavailableException {
            try {
                reloadIfNeeded();
                return delegate.getRealmIdentity(principal);
            } catch (IOException e) {
                throw new RealmUnavailableException(e);
            }
        }

        @Override
        public RealmIdentity getRealmIdentity(Evidence evidence) throws RealmUnavailableException {
            try {
                reloadIfNeeded();
                return delegate.getRealmIdentity(evidence);
            } catch (IOException e) {
                throw new RealmUnavailableException(e);
            }
        }

        @Override
        public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName)
                throws RealmUnavailableException {
            return delegate.getCredentialAcquireSupport(credentialType, algorithmName);
        }

        @Override
        public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec)
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
            return loadTimeSupplier.getAsLong();
        }

        void reloadIfNeeded() throws IOException {
            long loadTime = loadTimeSupplier.getAsLong();
            if (shouldReload(loadTime)) {
                synchronized(this) {
                    loadTime = loadTimeSupplier.getAsLong();
                    if (shouldReload(loadTime)) {
                        reloadInternal();
                    }
                }
            }
        }

        boolean shouldReload(long loadTime) {
            return doPrivileged((PrivilegedAction<Boolean>) () -> loadTime < usersFile.lastModified() || (groupsFile != null && loadTime < groupsFile.lastModified()));
        }

        void reload() throws OperationFailedException {
            try {
                reloadInternal();
            } catch (IOException e) {
                throw ROOT_LOGGER.unableToReLoadPropertiesFiles(e);
            }
        }

        void reloadInternal() throws IOException {
            try (InputStream usersInputStream = new FileInputStream(usersFile);
                    InputStream groupsInputStream = groupsFile != null ? new FileInputStream(groupsFile) : null) {
                propertiesFileLoader.accept(usersInputStream, groupsInputStream);
            }
        }

    }

}

