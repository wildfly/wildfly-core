/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.security.CredentialReference.getCredentialSource;
import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PROVIDERS_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PROVIDERS_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
import static org.wildfly.extension.elytron.ElytronExtension.ISO_8601_FORMAT;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.PATH;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.RELATIVE_TO;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathName;
import static org.wildfly.extension.elytron.ProviderAttributeDefinition.LOADED_PROVIDER;
import static org.wildfly.extension.elytron.ProviderAttributeDefinition.populateProvider;
import static org.wildfly.extension.elytron.ServiceStateDefinition.STATE;
import static org.wildfly.extension.elytron.ServiceStateDefinition.populateResponse;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.RollbackHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartException;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron.KeyStoreService.LoadKey;
import org.wildfly.security.EmptyProvider;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.keystore.AliasFilter;
import org.wildfly.security.keystore.AtomicLoadKeyStore;
import org.wildfly.security.keystore.FilteringKeyStore;
import org.wildfly.security.keystore.KeyStoreUtil;
import org.wildfly.security.keystore.UnmodifiableKeyStore;
import org.wildfly.security.provider.util.ProviderUtil;

/**
 * A {@link ResourceDefinition} for a single {@link KeyStore}.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
final class KeyStoreDefinition extends SimpleResourceDefinition {

    static final ServiceUtil<KeyStore> KEY_STORE_UTIL = ServiceUtil.newInstance(KEY_STORE_RUNTIME_CAPABILITY, ElytronDescriptionConstants.KEY_STORE, KeyStore.class);

    static final SimpleAttributeDefinition TYPE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.TYPE, ModelType.STRING, true)
        .setAttributeGroup(ElytronDescriptionConstants.IMPLEMENTATION)
        .setAllowExpression(true)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition PROVIDER_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROVIDER_NAME, ModelType.STRING, true)
        .setAttributeGroup(ElytronDescriptionConstants.IMPLEMENTATION)
        .setAllowExpression(true)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition PROVIDERS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROVIDERS, ModelType.STRING, true)
        .setAttributeGroup(ElytronDescriptionConstants.IMPLEMENTATION)
        .setMinSize(1)
        .setRestartAllServices()
        .setCapabilityReference(PROVIDERS_CAPABILITY, KEY_STORE_CAPABILITY)
        .build();

    static final ObjectTypeAttributeDefinition CREDENTIAL_REFERENCE = CredentialReference.getAttributeDefinition(true);

    static final SimpleAttributeDefinition REQUIRED = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REQUIRED, ModelType.BOOLEAN, true)
        .setDefaultValue(ModelNode.FALSE)
        .setAllowExpression(true)
        .setAttributeGroup(ElytronDescriptionConstants.FILE)
        .setRequires(ElytronDescriptionConstants.PATH)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition ALIAS_FILTER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALIAS_FILTER, ModelType.STRING, true)
        .setAllowExpression(true)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    // Resource Resolver

    private static final StandardResourceDescriptionResolver RESOURCE_RESOLVER = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.KEY_STORE);

    // Runtime Attributes

    private static final SimpleAttributeDefinition SIZE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SIZE, ModelType.INT)
        .setStorageRuntime()
        .build();

    private static final SimpleAttributeDefinition SYNCHRONIZED = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SYNCHRONIZED, ModelType.STRING)
        .setStorageRuntime()
        .build();

    private static final SimpleAttributeDefinition MODIFIED = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MODIFIED, ModelType.BOOLEAN)
        .setStorageRuntime()
        .build();

    // Operations

    private static final SimpleOperationDefinition LOAD = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.LOAD, RESOURCE_RESOLVER)
        .setRuntimeOnly()
        .build();

    private static final SimpleOperationDefinition STORE = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.STORE, RESOURCE_RESOLVER)
        .setRuntimeOnly()
        .build();

    private static final AttributeDefinition[] CONFIG_ATTRIBUTES = new AttributeDefinition[] { TYPE, PROVIDER_NAME, PROVIDERS, CREDENTIAL_REFERENCE, PATH, RELATIVE_TO, REQUIRED, ALIAS_FILTER };

    private static final KeyStoreAddHandler ADD = new KeyStoreAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, KEY_STORE_RUNTIME_CAPABILITY);

    KeyStoreDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.KEY_STORE), RESOURCE_RESOLVER)
            .setAddHandler(ADD)
            .setRemoveHandler(REMOVE)
            .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilities(KEY_STORE_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition current : CONFIG_ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, ElytronReloadRequiredWriteAttributeHandler.INSTANCE);
        }

        if (isServerOrHostController(resourceRegistration)) {
            resourceRegistration.registerReadOnlyAttribute(STATE, new ElytronRuntimeOnlyHandler() {

                @Override
                protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
                    ServiceName keyStoreName = KEY_STORE_UTIL.serviceName(operation);
                    ServiceController<?> serviceController = context.getServiceRegistry(false).getRequiredService(keyStoreName);

                    populateResponse(context.getResult(), serviceController);
                }

            });

            resourceRegistration.registerReadOnlyAttribute(SIZE, new KeyStoreRuntimeOnlyHandler(false) {

                @Override
                protected void performRuntime(ModelNode result, ModelNode operation, KeyStoreService keyStoreService) throws OperationFailedException {
                    try {
                        result.set(keyStoreService.getValue().size());
                    } catch (KeyStoreException e) {
                        throw ROOT_LOGGER.unableToAccessKeyStore(e);
                    }
                }
            });

            resourceRegistration.registerReadOnlyAttribute(SYNCHRONIZED, new KeyStoreRuntimeOnlyHandler(false) {

                @Override
                protected void performRuntime(ModelNode result, ModelNode operation, KeyStoreService keyStoreService) throws OperationFailedException {
                    SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_FORMAT);
                    result.set(sdf.format(new Date(keyStoreService.timeSynched())));
                }
            });

            resourceRegistration.registerReadOnlyAttribute(MODIFIED, new KeyStoreRuntimeOnlyHandler(false) {

                @Override
                protected void performRuntime(ModelNode result, ModelNode operation, KeyStoreService keyStoreService) throws OperationFailedException {
                    result.set(keyStoreService.isModified());
                }
            });

            resourceRegistration.registerReadOnlyAttribute(LOADED_PROVIDER, new KeyStoreRuntimeOnlyHandler(false) {

                @Override
                protected void performRuntime(ModelNode result, ModelNode operation, KeyStoreService keyStoreService)
                        throws OperationFailedException {
                    populateProvider(result, keyStoreService.getValue().getProvider(), false);
                }
            });
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(LOAD, PersistanceHandler.INSTANCE);
        if (isServerOrHostController(resourceRegistration)) {
            resourceRegistration.registerOperationHandler(STORE, PersistanceHandler.INSTANCE);
        }
    }

    private static class KeyStoreAddHandler extends BaseAddHandler {

        private KeyStoreAddHandler() {
            super(KEY_STORE_RUNTIME_CAPABILITY);
        }

        @Override
        protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws  OperationFailedException {
            super.populateModel(context, operation, resource);
            handleCredentialReferenceUpdate(context, resource.getModel());
        }

        @Override
        protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource)
                throws OperationFailedException {
            super.recordCapabilitiesAndRequirements(context, operation, resource);

            if (requiresRuntime(context)) {
                // Register the early-access key-store-api capability backed by the doohickey.
                KeyStoreDoohickey doohickey = new KeyStoreDoohickey(context.getCurrentAddress());
                context.registerCapability(RuntimeCapability.Builder
                        .<ExceptionFunction<OperationContext, KeyStore, OperationFailedException>> of(KEY_STORE_API_CAPABILITY, true,
                                doohickey)
                        .build().fromBaseCapability(context.getCurrentAddressValue()));
            }
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            final String name = context.getCurrentAddressValue();
            ModelNode model = resource.getModel();

            // Retrieve the doohickey registered during recordCapabilitiesAndRequirements and resolve its model.
            ExceptionFunction<OperationContext, KeyStore, OperationFailedException> runtimeApi =
                    context.getCapabilityRuntimeAPI(KEY_STORE_API_CAPABILITY, name, ExceptionFunction.class);
            KeyStoreDoohickey doohickey = (KeyStoreDoohickey) runtimeApi;
            doohickey.resolveRuntime(context);

            String providers = PROVIDERS.resolveModelAttribute(context, model).asStringOrNull();
            String providerName = PROVIDER_NAME.resolveModelAttribute(context, model).asStringOrNull();
            String type = TYPE.resolveModelAttribute(context, model).asStringOrNull();
            String path = PATH.resolveModelAttribute(context, model).asStringOrNull();
            String relativeTo = null;
            boolean required;
            String aliasFilter = ALIAS_FILTER.resolveModelAttribute(context, model).asStringOrNull();

            final KeyStoreService keyStoreService;
            if (path != null) {
                relativeTo = RELATIVE_TO.resolveModelAttribute(context, model).asStringOrNull();
                required = REQUIRED.resolveModelAttribute(context, model).asBoolean();
                keyStoreService = KeyStoreService.createFileBasedKeyStoreService(providerName, type, relativeTo, path, required, aliasFilter);
            } else {
                if (type == null) {
                    throw ROOT_LOGGER.filelessKeyStoreMissingType();
                }
                keyStoreService = KeyStoreService.createFileLessKeyStoreService(providerName, type, aliasFilter);
            }
            keyStoreService.setDoohickey(doohickey);
            doohickey.setKeyStoreService(keyStoreService);

            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<Void> runtimeCapability = KEY_STORE_RUNTIME_CAPABILITY.fromBaseCapability(name);
            ServiceName serviceName = runtimeCapability.getCapabilityServiceName(KeyStore.class);
            ServiceBuilder<KeyStore> serviceBuilder = serviceTarget.addService(serviceName, keyStoreService).setInitialMode(Mode.ACTIVE);

            serviceBuilder.addDependency(PathManagerService.SERVICE_NAME, PathManager.class, keyStoreService.getPathManagerInjector());
            if (relativeTo != null) {
                serviceBuilder.requires(pathName(relativeTo));
            }

            if (providers != null) {
                String providersCapabilityName = RuntimeCapability.buildDynamicCapabilityName(PROVIDERS_CAPABILITY, providers);
                ServiceName providerLoaderServiceName = context.getCapabilityServiceName(providersCapabilityName, Provider[].class);
                serviceBuilder.addDependency(providerLoaderServiceName, Provider[].class, keyStoreService.getProvidersInjector());
            }

            keyStoreService.getCredentialSourceSupplierInjector()
                    .inject(CredentialReference.getCredentialSourceSupplier(context, KeyStoreDefinition.CREDENTIAL_REFERENCE, model, serviceBuilder));

            commonDependencies(serviceBuilder).install();
        }

        @Override
        protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
            rollbackCredentialStoreUpdate(KeyStoreDefinition.CREDENTIAL_REFERENCE, context, resource);
        }
    }

    /**
     * {@link ElytronDoohickey} for a {@code key-store} resource.
     *
     * <p>Provides the {@code key-store-api} early-access capability so that dependent resources
     * (trust-manager, key-manager, etc.) can obtain a {@link KeyStore} before the MSC service has started.
     * When early initialisation runs, the loaded {@link AtomicLoadKeyStore} is cached here; the
     * {@link KeyStoreService} later reuses it in {@code start()} so the file is not read twice and the
     * {@link KeyStore} object returned by both the API and the service is the same instance.</p>
     */
    static class KeyStoreDoohickey extends ElytronDoohickey<KeyStore> {

        // Resolved model attributes — populated by resolveRuntime().
        private volatile String type;
        private volatile String providerName;
        private volatile String providers;
        private volatile String path;
        private volatile String relativeTo;
        private volatile boolean required;
        private volatile String aliasFilter;

        /**
         * The {@link AtomicLoadKeyStore} that underlies the cached doohickey value.  Set atomically
         * alongside {@code value} inside the doohickey lock (either the early-access path or the
         * service path).  {@link KeyStoreService#start()} reads this after {@code getForService()}
         * returns so that {@code this.keyStore} and {@code this.unmodifiableKeyStore} always refer to
         * the same underlying store — whichever path won the construction race.
         *
         * <p>Cleared automatically when {@link ElytronDoohickey#reset()} calls {@link #onReset()} so
         * that a service restart forces a fresh load.</p>
         */
        private volatile AtomicLoadKeyStore cachedAtomicKeyStore;
        private volatile File cachedResolvedPath;
        private volatile CredentialSource cachedCredentialSource;
        // Set when performRuntime creates the service. Early self-signed key managers retain a
        // supplier to this reference and resolve it only when the certificate is first needed.
        private volatile KeyStoreService keyStoreService;

        private final PathAddress resourceAddress;

        KeyStoreDoohickey(PathAddress resourceAddress) {
            super(resourceAddress);
            this.resourceAddress = resourceAddress;
        }

        /**
         * Returns the {@link AtomicLoadKeyStore} that was built and cached (by either the early-access
         * path or the service path), or {@code null} if construction has not yet run under the lock.
         * Called by {@link KeyStoreService#start()} after {@code getForService()} returns to obtain the
         * underlying store so that {@code this.keyStore} and {@code this.unmodifiableKeyStore} refer to
         * the same instance.
         */
        AtomicLoadKeyStore getCachedAtomicKeyStore() {
            return cachedAtomicKeyStore;
        }

        File getCachedResolvedPath() {
            return cachedResolvedPath;
        }

        /**
         * Records the {@link AtomicLoadKeyStore} built by the service-first path.  Called from within
         * the {@code getForService()} builder lambda in {@link KeyStoreService#startWithDoohickey()},
         * i.e. while the doohickey lock is held, so this write is visible to any subsequent reader
         * that acquires the same lock.
         */
        void setCachedServiceStore(AtomicLoadKeyStore aks, File file) {
            this.cachedAtomicKeyStore = aks;
            this.cachedResolvedPath = file;
        }

        /** Publishes the early store and its save inputs together while the Doohickey lock is held. */
        void cacheEarlyValue(AtomicLoadKeyStore aks, File file, CredentialSource credentialSource, KeyStore unmodifiable) {
            this.cachedAtomicKeyStore = aks;
            this.cachedResolvedPath = file;
            this.cachedCredentialSource = credentialSource;
            setValue(unmodifiable);
        }

        void setKeyStoreService(KeyStoreService service) {
            this.keyStoreService = service;
        }

        KeyStoreService getKeyStoreService() {
            return keyStoreService;
        }

        String getCachedResolvedAbsolutePath() {
            File file = cachedResolvedPath;
            return file != null ? file.getAbsolutePath() : null;
        }

        boolean shouldAutoGenerateSelfSignedCertificate(String host) {
            KeyStoreService service = keyStoreService;
            if (service != null && service.getValue() != null) {
                return service.shouldAutoGenerateSelfSignedCertificate(host);
            }
            return host != null && cachedResolvedPath != null && !cachedResolvedPath.exists();
        }

        void generateAndSaveSelfSignedCertificate(String host, char[] keyPassword) {
            File file = cachedResolvedPath;
            if (host == null || file == null || file.exists()) {
                return;
            }
            try {
                AtomicLoadKeyStore target = cachedAtomicKeyStore;
                if (target == null) {
                    throw new IllegalStateException("Early key-store value is no longer available");
                }
                KeyStoreService.addSelfSignedCertificate(target, file, host, keyPassword);
                try (FileOutputStream output = new FileOutputStream(file)) {
                    target.store(output, resolvePassword(cachedCredentialSource, file));
                }
            } catch (Exception e) {
                throw ROOT_LOGGER.failedToStoreGeneratedSelfSignedCertificate(e);
            }
        }

        /**
         * Clears the cached {@link AtomicLoadKeyStore} in lockstep with the primary doohickey value.
         * Called automatically by {@link ElytronDoohickey#reset()} so that callers never need to cast
         * or call a separate method.
         */
        @Override
        protected void onReset() {
            cachedAtomicKeyStore = null;
            cachedResolvedPath = null;
            cachedCredentialSource = null;
        }

        @Override
        protected void resolveRuntime(ModelNode model, OperationContext context) throws OperationFailedException {
            type        = TYPE.resolveModelAttribute(context, model).asStringOrNull();
            providerName = PROVIDER_NAME.resolveModelAttribute(context, model).asStringOrNull();
            providers   = PROVIDERS.resolveModelAttribute(context, model).asStringOrNull();
            path        = PATH.resolveModelAttribute(context, model).asStringOrNull();
            relativeTo  = path != null ? RELATIVE_TO.resolveModelAttribute(context, model).asStringOrNull() : null;
            required    = path != null && REQUIRED.resolveModelAttribute(context, model).asBoolean();
            aliasFilter = ALIAS_FILTER.resolveModelAttribute(context, model).asStringOrNull();
        }

        // -----------------------------------------------------------------------------------------
        // Early-access path (called from Stage.RUNTIME before the MSC service has started)
        // -----------------------------------------------------------------------------------------

        @Override
        protected KeyStore createImmediately(OperationContext foreignContext) throws OperationFailedException {
            // Resolve file path via the PathManager already running in the service registry.
            File resolvedPath = null;
            if (path != null) {
                resolvedPath = resolveRelativeToImmediately(path, relativeTo, foreignContext);
            }

            // Resolve optional providers via the providers-api capability.
            Provider[] resolvedProviders = null;
            if (providers != null) {
                @SuppressWarnings("unchecked")
                ExceptionFunction<OperationContext, Provider[], OperationFailedException> providerApi =
                        foreignContext.getCapabilityRuntimeAPI(PROVIDERS_API_CAPABILITY, providers, ExceptionFunction.class);
                resolvedProviders = providerApi.apply(foreignContext);
            }

            // Resolve the keystore password via the credential reference.
            CredentialSource credentialSource = getCredentialSource(foreignContext, CREDENTIAL_REFERENCE,
                    foreignContext.readResourceFromRoot(resourceAddress).getModel());

            try {
                AtomicLoadKeyStore aks = buildAtomicKeyStore(resolvedPath, resolvedProviders, credentialSource);
                // Build the canonical wrappers — the same objects will be returned by KeyStoreService.getValue().
                KeyStore intermediate = aliasFilter != null
                        ? FilteringKeyStore.filteringKeyStore(aks, AliasFilter.fromString(aliasFilter))
                        : aks;
                KeyStore unmodifiable = UnmodifiableKeyStore.unmodifiableKeyStore(intermediate);
                // Publish the wrapper, its atomic store, resolved file and credential source
                // together under the Doohickey lock. Service startup reuses the same store.
                cacheEarlyValue(aks, resolvedPath, credentialSource, unmodifiable);
                return unmodifiable;
            } catch (StartException | GeneralSecurityException | IOException e) {
                throw new OperationFailedException(e);
            }
        }

        // -----------------------------------------------------------------------------------------
        // Service path (called to wire MSC dependencies; the supplier is used only when value==null)
        // -----------------------------------------------------------------------------------------

        @Override
        protected ExceptionSupplier<KeyStore, StartException> prepareServiceSupplier(OperationContext context,
                CapabilityServiceBuilder<?> serviceBuilder) throws OperationFailedException {
            // KeyStoreDefinition uses KeyStoreService directly (not DoohickeyAddHandler/TrivialService),
            // so this method is never called.  Return a supplier that delegates to the service once started.
            throw new UnsupportedOperationException("KeyStoreDoohickey.prepareServiceSupplier should not be called; "
                    + "KeyStoreService manages its own MSC lifecycle.");
        }

        // -----------------------------------------------------------------------------------------
        // Shared key-store construction logic
        // -----------------------------------------------------------------------------------------

        /**
         * Builds and loads an {@link AtomicLoadKeyStore} from the resolved configuration.
         * Used both by {@link #createImmediately} (early path) and by {@link KeyStoreService#start}
         * (service path) when no pre-loaded store is available.
         */
        AtomicLoadKeyStore buildAtomicKeyStore(File resolvedPath, Provider[] resolvedProviders, CredentialSource credentialSource)
                throws GeneralSecurityException, IOException, StartException, OperationFailedException {

            char[] password = resolvePassword(credentialSource, resolvedPath);

            AtomicLoadKeyStore aks;
            if (resolvedPath != null && resolvedPath.exists()) {
                if (type != null) {
                    Provider provider = findProvider(resolvedProviders, type);
                    aks = AtomicLoadKeyStore.newInstance(type, provider);
                    try (FileInputStream is = new FileInputStream(resolvedPath)) {
                        aks.load(is, password);
                    }
                } else {
                    // auto-detect type from file
                    final Provider[] candidateProviders = resolvedProviders != null ? resolvedProviders : Security.getProviders();
                    try (FileInputStream is = new FileInputStream(resolvedPath)) {
                        KeyStore detected = KeyStoreUtil.loadKeyStore(() -> candidateProviders, providerName,
                                is, resolvedPath.getPath(), password);
                        if (detected == null) {
                            throw ROOT_LOGGER.unableToDetectKeyStore(resolvedPath.getPath());
                        }
                        aks = AtomicLoadKeyStore.atomize(detected);
                    }
                }
            } else {
                // No file — create an empty in-memory keystore.
                if (resolvedPath != null) {
                    // path configured but file missing
                    if (required) {
                        if (type == null) {
                            throw ROOT_LOGGER.nonexistingKeyStoreMissingType();
                        } else {
                            throw ROOT_LOGGER.keyStoreFileNotExists(resolvedPath.getAbsolutePath());
                        }
                    } else {
                        ROOT_LOGGER.keyStoreFileNotExistsButIgnored(resolvedPath.getAbsolutePath());
                    }
                }
                if (type == null) {
                    // Match the service-path behaviour exactly: use the JVM default type with no
                    // provider filter.  KeyStoreService uses AtomicLoadKeyStore.newInstance(defaultType)
                    // without consulting the configured providers injector for this case, so filtering
                    // by resolvedProviders here would produce a different result when a providers
                    // attribute is configured.
                    String effectiveType = KeyStore.getDefaultType();
                    ROOT_LOGGER.debugf(
                            "KeyStore (early path): path = %s does not exist and type is unset; "
                            + "creating empty %s keystore", resolvedPath, effectiveType);
                    aks = AtomicLoadKeyStore.newInstance(effectiveType);
                    synchronized (EmptyProvider.getInstance()) {
                        aks.load(null, password);
                    }
                    return aks;
                }
                Provider provider = findProvider(resolvedProviders, type);
                aks = AtomicLoadKeyStore.newInstance(type, provider);
                synchronized (EmptyProvider.getInstance()) {
                    aks.load(null, password);
                }
            }
            return aks;
        }

        private Provider findProvider(Provider[] candidates, String ksType) throws GeneralSecurityException, StartException {
            Provider[] pool = candidates != null ? candidates : Security.getProviders();
            Provider p = ProviderUtil.findProvider(() -> pool, providerName, KeyStore.class, ksType);
            if (p == null) {
                throw ROOT_LOGGER.noSuitableProvider(ksType);
            }
            return p;
        }

        private static char[] resolvePassword(CredentialSource cs, File resolvedPath) throws GeneralSecurityException, IOException {
            if (cs == null) {
                String pathStr = resolvedPath != null ? resolvedPath.getPath() : "null";
                throw ROOT_LOGGER.keyStorePasswordCannotBeResolved(pathStr);
            }
            org.wildfly.security.credential.PasswordCredential cred = cs.getCredential(
                    org.wildfly.security.credential.PasswordCredential.class);
            if (cred == null) {
                String pathStr = resolvedPath != null ? resolvedPath.getPath() : "null";
                throw ROOT_LOGGER.keyStorePasswordCannotBeResolved(pathStr);
            }
            org.wildfly.security.password.interfaces.ClearPassword cp =
                    cred.getPassword(org.wildfly.security.password.interfaces.ClearPassword.class);
            if (cp == null) {
                String pathStr = resolvedPath != null ? resolvedPath.getPath() : "null";
                throw ROOT_LOGGER.keyStorePasswordCannotBeResolved(pathStr);
            }
            return cp.getPassword();
        }

    }

    /*
     * Runtime Attribute and Operation Handlers
     */

    abstract static class KeyStoreRuntimeOnlyHandler extends ElytronRuntimeOnlyHandler {

        private final boolean serviceMustBeUp;
        private final boolean writeAccess;

        KeyStoreRuntimeOnlyHandler(final boolean serviceMustBeUp, final boolean writeAccess) {
            this.serviceMustBeUp = serviceMustBeUp;
            this.writeAccess = writeAccess;
        }

        KeyStoreRuntimeOnlyHandler(final boolean serviceMustBeUp) {
            this(serviceMustBeUp, false);
        }


        @Override
        protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
            ServiceName keyStoreName = KEY_STORE_UTIL.serviceName(operation);

            ServiceController<KeyStore> serviceContainer = getRequiredService(context.getServiceRegistry(writeAccess), keyStoreName, KeyStore.class);
            State serviceState;
            if ((serviceState = serviceContainer.getState()) != State.UP) {
                if (serviceMustBeUp) {
                    throw ROOT_LOGGER.requiredServiceNotUp(keyStoreName, serviceState);
                }
                return;
            }

            performRuntime(context.getResult(), context, operation, (KeyStoreService) serviceContainer.getService());
        }

        protected void performRuntime(ModelNode result, ModelNode operation,  KeyStoreService keyStoreService) throws OperationFailedException {}

        protected void performRuntime(ModelNode result, OperationContext context, ModelNode operation,  KeyStoreService keyStoreService) throws OperationFailedException {
            performRuntime(result, operation, keyStoreService);
        }

    }

    private static class PersistanceHandler extends KeyStoreRuntimeOnlyHandler {

        private static final PersistanceHandler INSTANCE = new PersistanceHandler();

        private PersistanceHandler() {
            super(true, true);
        }

        @Override
        protected void performRuntime(ModelNode result, OperationContext context, ModelNode operation, final KeyStoreService keyStoreService) throws OperationFailedException {
            String operationName = operation.require(OP).asString();
            switch (operationName) {
                case ElytronDescriptionConstants.LOAD:
                    final LoadKey loadKey = keyStoreService.load();
                    context.completeStep(new RollbackHandler() {

                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            keyStoreService.revertLoad(loadKey);
                        }
                    });
                    break;
                case ElytronDescriptionConstants.STORE:
                    keyStoreService.save();
                    break;
                default:
                    throw ROOT_LOGGER.invalidOperationName(operationName, ElytronDescriptionConstants.LOAD,
                            ElytronDescriptionConstants.STORE);
            }

        }

    }

}
