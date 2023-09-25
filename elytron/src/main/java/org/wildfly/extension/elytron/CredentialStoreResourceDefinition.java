/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.security.CredentialReference.getCredentialSource;
import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
import static org.wildfly.extension.elytron.Capabilities.CREDENTIAL_STORE_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.CREDENTIAL_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.CREDENTIAL_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PROVIDERS_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PROVIDERS_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathName;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathResolver;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartException;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.common.function.ExceptionRunnable;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron.FileAttributeDefinitions.PathResolver;
import org.wildfly.security.EmptyProvider;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.SecretKeyCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStore.CredentialSourceProtectionParameter;
import org.wildfly.security.credential.store.CredentialStoreException;
import org.wildfly.security.credential.store.impl.KeyStoreCredentialStore;

/**
 * A {@link ResourceDefinition} for a CredentialStore.
 *
 * @author <a href="mailto:pskopek@redhat.com">Peter Skopek</a>
 */
final class CredentialStoreResourceDefinition extends AbstractCredentialStoreResourceDefinition {

    // KeyStore backed credential store supported attributes
    private static final String CS_KEY_STORE_TYPE_ATTRIBUTE = "keyStoreType";
    private static final List<String> filebasedKeystoreTypes = Collections.unmodifiableList(Arrays.asList("JKS", "JCEKS", "PKCS12"));

    static final SimpleAttributeDefinition LOCATION = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.LOCATION, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.IMPLEMENTATION)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .setDeprecated(ModelVersion.create(13))
            .setAlternatives(ElytronDescriptionConstants.PATH)
            .build();

    static final SimpleAttributeDefinition MODIFIABLE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MODIFIABLE, ModelType.BOOLEAN, true)
            .setAttributeGroup(ElytronDescriptionConstants.IMPLEMENTATION)
            .setDefaultValue(ModelNode.TRUE)
            .setAllowExpression(false)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition CREATE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CREATE, ModelType.BOOLEAN, true)
            .setAttributeGroup(ElytronDescriptionConstants.IMPLEMENTATION)
            .setAllowExpression(false)
            .setDefaultValue(ModelNode.FALSE)
            .setRestartAllServices()
            .build();

    static final SimpleMapAttributeDefinition IMPLEMENTATION_PROPERTIES = new SimpleMapAttributeDefinition.Builder(ElytronDescriptionConstants.IMPLEMENTATION_PROPERTIES, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.IMPLEMENTATION)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final ObjectTypeAttributeDefinition CREDENTIAL_REFERENCE = CredentialReference.getAttributeDefinition(true);

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
            .setAllowExpression(false)
            .setMinSize(1)
            .setRestartAllServices()
            .setCapabilityReference(PROVIDERS_CAPABILITY, CREDENTIAL_STORE_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition OTHER_PROVIDERS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.OTHER_PROVIDERS, ModelType.STRING, true)
            .setAttributeGroup(ElytronDescriptionConstants.IMPLEMENTATION)
            .setAllowExpression(false)
            .setMinSize(1)
            .setRestartAllServices()
            .setCapabilityReference(PROVIDERS_CAPABILITY, CREDENTIAL_STORE_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition RELATIVE_TO = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RELATIVE_TO, ModelType.STRING, true)
            .setAllowExpression(false)
            .setMinSize(1)
            .setAttributeGroup(ElytronDescriptionConstants.FILE)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PATH, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setAttributeGroup(ElytronDescriptionConstants.FILE)
            .setRestartAllServices()
            .setAlternatives(ElytronDescriptionConstants.LOCATION)
            .build();

    // Resource Resolver
    private static final StandardResourceDescriptionResolver RESOURCE_RESOLVER = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.CREDENTIAL_STORE);

    // Operations parameters


    static final SimpleAttributeDefinition KEY_SIZE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_SIZE, ModelType.INT, true)
            .setMinSize(1)
            .setDefaultValue(new ModelNode(256))
            .setAllowedValues(128, 192, 256)
            .build();

    static final SimpleAttributeDefinition ADD_ENTRY_TYPE;
    static final SimpleAttributeDefinition REMOVE_ENTRY_TYPE;

    static {
        String[] addEntryTypes = new String[] { PasswordCredential.class.getCanonicalName() };
        ADD_ENTRY_TYPE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ENTRY_TYPE, ModelType.STRING, true)
                .setAllowedValues(addEntryTypes)
                .build();
        String[] removeEntryTypes = new String[] { PasswordCredential.class.getCanonicalName(), PasswordCredential.class.getSimpleName(),
                                                   SecretKeyCredential.class.getCanonicalName(), SecretKeyCredential.class.getSimpleName()};
        REMOVE_ENTRY_TYPE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ENTRY_TYPE, ModelType.STRING, true)
                .setAllowedValues(removeEntryTypes)
                .setDefaultValue(new ModelNode(PasswordCredential.class.getSimpleName()))
                .build();
    }

    static final SimpleAttributeDefinition SECRET_VALUE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SECRET_VALUE, ModelType.STRING, true)
            .setMinSize(0)
            .build();

    // Operations

    private static final SimpleOperationDefinition ADD_ALIAS = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.ADD_ALIAS, OPERATION_RESOLVER)
            .setParameters(ALIAS, ADD_ENTRY_TYPE, SECRET_VALUE)
            .setRuntimeOnly()
            .build();

    private static final SimpleOperationDefinition REMOVE_ALIAS = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.REMOVE_ALIAS, OPERATION_RESOLVER)
            .setParameters(ALIAS, REMOVE_ENTRY_TYPE)
            .setRuntimeOnly()
            .build();

    private static final SimpleOperationDefinition SET_SECRET = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.SET_SECRET, OPERATION_RESOLVER)
            .setParameters(ALIAS, ADD_ENTRY_TYPE, SECRET_VALUE)
            .setRuntimeOnly()
            .build();

    private static final SimpleOperationDefinition GENERATE_SECRET_KEY = new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.GENERATE_SECRET_KEY, OPERATION_RESOLVER)
            .setParameters(ALIAS, KEY_SIZE)
            .setRuntimeOnly()
            .build();

    private static final AttributeDefinition[] CONFIG_ATTRIBUTES = new AttributeDefinition[] {LOCATION, PATH, CREATE, MODIFIABLE, IMPLEMENTATION_PROPERTIES, CREDENTIAL_REFERENCE, TYPE, PROVIDER_NAME, PROVIDERS, OTHER_PROVIDERS, RELATIVE_TO};

    private static final CredentialStoreAddHandler ADD = new CredentialStoreAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, CREDENTIAL_STORE_RUNTIME_CAPABILITY);


    CredentialStoreResourceDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.CREDENTIAL_STORE), RESOURCE_RESOLVER)
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_NONE)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_NONE)
                .setCapabilities(CREDENTIAL_STORE_RUNTIME_CAPABILITY)
        );
    }

    @Override
    protected AttributeDefinition[] getAttributeDefinitions() {
        return CONFIG_ATTRIBUTES;
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration); // Always needed to register add / remove.

        boolean isServerOrHostController = isServerOrHostController(resourceRegistration);
        Map<String, CredentialStoreRuntimeOperation> operationMethods = new HashMap<>();

        operationMethods.put(ElytronDescriptionConstants.READ_ALIASES, this::readAliasesOperation);
        if (isServerOrHostController) {
            operationMethods.put(ElytronDescriptionConstants.ADD_ALIAS, this::addAliasOperation);
            operationMethods.put(ElytronDescriptionConstants.REMOVE_ALIAS, this::removeAliasOperation);
            operationMethods.put(ElytronDescriptionConstants.SET_SECRET, this::setSecretOperation);
            operationMethods.put(ElytronDescriptionConstants.EXPORT_SECRET_KEY, this::exportSecretKeyOperation);
            operationMethods.put(ElytronDescriptionConstants.GENERATE_SECRET_KEY, this::generateSecretKeyOperation);
            operationMethods.put(ElytronDescriptionConstants.IMPORT_SECRET_KEY, this::importSecretKeyOperation);
        }

        OperationStepHandler operationHandler = new CredentialStoreRuntimeHandler(operationMethods);
        resourceRegistration.registerOperationHandler(READ_ALIASES, operationHandler); // MAPPED
        if (isServerOrHostController) {
            resourceRegistration.registerOperationHandler(ADD_ALIAS, operationHandler); // Mapped
            resourceRegistration.registerOperationHandler(REMOVE_ALIAS, operationHandler); // Mapped
            resourceRegistration.registerOperationHandler(SET_SECRET, operationHandler); // Mapped
            resourceRegistration.registerOperationHandler(GENERATE_SECRET_KEY, operationHandler);
            resourceRegistration.registerOperationHandler(EXPORT_SECRET_KEY, operationHandler);
            resourceRegistration.registerOperationHandler(IMPORT_SECRET_KEY, operationHandler);
            resourceRegistration.registerOperationHandler(RELOAD, RELOAD_HANDLER);
        }
    }

    /*
     * Operation Handler Methods
     */

    void addAliasOperation(OperationContext context, ModelNode operation, CredentialStore credentialStore) throws OperationFailedException {
        try {
            try {
                String alias = ALIAS.resolveModelAttribute(context, operation).asString();
                String entryType = ADD_ENTRY_TYPE.resolveModelAttribute(context, operation).asStringOrNull();
                String secretValue = SECRET_VALUE.resolveModelAttribute(context, operation).asStringOrNull();
                if (entryType == null || entryType.equals(PasswordCredential.class.getCanonicalName())) {
                    if (credentialStore.exists(alias, PasswordCredential.class)) {
                        throw ROOT_LOGGER.credentialAlreadyExists(alias, PasswordCredential.class.getName());
                    }
                    storeSecret(credentialStore, alias, secretValue);
                } else {
                    String credentialStoreName = CredentialStoreResourceDefinition.credentialStoreName(operation);
                    throw ROOT_LOGGER.credentialStoreEntryTypeNotSupported(credentialStoreName, entryType);
                }
            } catch (CredentialStoreException e) {
                throw ROOT_LOGGER.unableToCompleteOperation(e, dumpCause(e));
            }
        } catch (RuntimeException e) {
            throw new OperationFailedException(e);
        }
    }

    void removeAliasOperation(OperationContext context, ModelNode operation, CredentialStore credentialStore) throws OperationFailedException {
        String entryType = REMOVE_ENTRY_TYPE.resolveModelAttribute(context, operation).asString();
        Class<? extends Credential> credentialType = fromEntryType(entryType);

        super.removeAliasOperation(context, operation, credentialStore, credentialType);
    }

    void setSecretOperation(OperationContext context, ModelNode operation, CredentialStore credentialStore) throws OperationFailedException {
        try {
            try {
                String alias = ALIAS.resolveModelAttribute(context, operation).asString();
                String entryType = ADD_ENTRY_TYPE.resolveModelAttribute(context, operation).asStringOrNull();
                String secretValue = SECRET_VALUE.resolveModelAttribute(context, operation).asStringOrNull();

                if (entryType == null || entryType.equals(PasswordCredential.class.getCanonicalName())) {
                    if ( ! credentialStore.exists(alias, PasswordCredential.class)) {
                        throw ROOT_LOGGER.credentialDoesNotExist(alias, PasswordCredential.class.getName());
                    }
                    storeSecret(credentialStore, alias, secretValue);
                    context.addResponseWarning(Level.WARNING, ROOT_LOGGER.reloadDependantServices());
                } else {
                    String credentialStoreName = CredentialStoreResourceDefinition.credentialStoreName(operation);
                    throw ROOT_LOGGER.credentialStoreEntryTypeNotSupported(credentialStoreName, entryType);
                }
            } catch (CredentialStoreException e) {
                throw ROOT_LOGGER.unableToCompleteOperation(e, dumpCause(e));
            }
        } catch (RuntimeException e) {
            throw new OperationFailedException(e);
        }
    }

    protected void generateSecretKeyOperation(OperationContext context, ModelNode operation, CredentialStore credentialStore) throws OperationFailedException {
        final int keySize = KEY_SIZE.resolveModelAttribute(context, operation).asInt();

        generateSecretKeyOperation(context, operation, credentialStore, keySize);
    }

    static String credentialStoreName(ModelNode operation) {
        String credentialStoreName = null;
        PathAddress pa = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR));
        for (int i = pa.size() - 1; i > 0; i--) {
            PathElement pe = pa.getElement(i);
            if (ElytronDescriptionConstants.CREDENTIAL_STORE.equals(pe.getKey())) {
                credentialStoreName = pe.getValue();
                break;
            }
        }

        if (credentialStoreName == null) {
            throw ROOT_LOGGER.operationAddressMissingKey(ElytronDescriptionConstants.CREDENTIAL_STORE);
        }

        return credentialStoreName;
    }

    private static Class<? extends Credential> fromEntryType(final String entryTyoe) {
        if (PasswordCredential.class.getCanonicalName().equals(entryTyoe) || PasswordCredential.class.getSimpleName().equals(entryTyoe)) {
            return PasswordCredential.class;
        } else if (SecretKeyCredential.class.getCanonicalName().equals(entryTyoe) || SecretKeyCredential.class.getSimpleName().equals(entryTyoe)) {
            return SecretKeyCredential.class;
        }

        return null;
    }

    private static class CredentialStoreAddHandler extends DoohickeyAddHandler<CredentialStore> {

        private CredentialStoreAddHandler() {
            super(CREDENTIAL_STORE_RUNTIME_CAPABILITY, CONFIG_ATTRIBUTES, CREDENTIAL_STORE_API_CAPABILITY);
        }

        @Override
        protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws  OperationFailedException {
            super.populateModel(context, operation, resource);
            handleCredentialReferenceUpdate(context, resource.getModel());
        }

        @Override
        protected ElytronDoohickey<CredentialStore> createDoohickey(PathAddress resourceAddress) {
            return new CredentialStoreDoohickey(resourceAddress);
        }

        @Override
        protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
            rollbackCredentialStoreUpdate(CREDENTIAL_REFERENCE, context, resource);
        }
    }

    static class CredentialStoreDoohickey extends AbstractCredentialStoreDoohickey {

        private final String name;
        private volatile String location;
        private volatile boolean modifiable;
        private volatile String type;
        private volatile String providers;
        private volatile String otherProviders;
        private volatile String providerName;
        private volatile String relativeTo;
        private volatile Map<String, String> credentialStoreAttributes;
        private volatile ModelNode model; // It would be nice to eliminate but credential reference performs resolution
                                          // and use of values in a single step.

        private volatile ExceptionRunnable<GeneralSecurityException> reloader;

        protected CredentialStoreDoohickey(PathAddress resourceAddress) {
            super(resourceAddress);
            this.name = resourceAddress.getLastElement().getValue();
        }

        @Override
        protected void resolveRuntime(ModelNode model, OperationContext context) throws OperationFailedException {
            location = PATH.resolveModelAttribute(context, model).asStringOrNull();
            if (location == null) {
                location = LOCATION.resolveModelAttribute(context, model).asStringOrNull();
            }
            credentialStoreAttributes = new HashMap<>();
            modifiable =  MODIFIABLE.resolveModelAttribute(context, model).asBoolean();
            credentialStoreAttributes.put(ElytronDescriptionConstants.MODIFIABLE, Boolean.toString(modifiable));
            boolean create = CREATE.resolveModelAttribute(context, model).asBoolean();
            credentialStoreAttributes.put(ElytronDescriptionConstants.CREATE, Boolean.toString(create));
            ModelNode implAttrModel = IMPLEMENTATION_PROPERTIES.resolveModelAttribute(context, model);
            if (implAttrModel.isDefined()) {
                for (String s : implAttrModel.keys()) {
                    credentialStoreAttributes.put(s, implAttrModel.require(s).asString());
                }
            }
            type = TYPE.resolveModelAttribute(context, model).asStringOrNull();
            providers = PROVIDERS.resolveModelAttribute(context, model).asStringOrNull();
            otherProviders = OTHER_PROVIDERS.resolveModelAttribute(context, model).asStringOrNull();
            providerName = PROVIDER_NAME.resolveModelAttribute(context, model).asStringOrNull();
            relativeTo = RELATIVE_TO.resolveModelAttribute(context, model).asStringOrNull();
            if (type == null || type.equals(KeyStoreCredentialStore.KEY_STORE_CREDENTIAL_STORE)) {
                credentialStoreAttributes.putIfAbsent(CS_KEY_STORE_TYPE_ATTRIBUTE, "JCEKS");
            }

            String implAttrKeyStoreType = credentialStoreAttributes.get(CS_KEY_STORE_TYPE_ATTRIBUTE);
            if (location == null && implAttrKeyStoreType != null && filebasedKeystoreTypes.contains(implAttrKeyStoreType.toUpperCase(Locale.ENGLISH))) {
                throw ROOT_LOGGER.filebasedKeystoreLocationMissing(implAttrKeyStoreType);
            }
            this.model = model;
        }

        @Override
        protected ExceptionSupplier<CredentialStore, StartException> prepareServiceSupplier(OperationContext context,
                CapabilityServiceBuilder<?> serviceBuilder) throws OperationFailedException {

            final Supplier<PathManagerService> pathManager;
            if (relativeTo != null) {
                pathManager = serviceBuilder.requires(PathManagerService.SERVICE_NAME);
                serviceBuilder.requires(pathName(relativeTo));
            } else {
                pathManager = null;
            }

            final Supplier<Provider[]> providerSupplier;
            if (providers != null) {
                String providersCapabilityName = RuntimeCapability.buildDynamicCapabilityName(PROVIDERS_CAPABILITY, providers);
                ServiceName providerLoaderServiceName = context.getCapabilityServiceName(providersCapabilityName,
                        Provider[].class);

                providerSupplier = serviceBuilder.requires(providerLoaderServiceName);
            } else {
                providerSupplier = null;
            }

            final Supplier<Provider[]> otherProviderSupplier;
            if (otherProviders != null) {
                String providersCapabilityName = RuntimeCapability.buildDynamicCapabilityName(PROVIDERS_CAPABILITY,
                        otherProviders);
                ServiceName otherProvidersLoaderServiceName = context.getCapabilityServiceName(providersCapabilityName,
                        Provider[].class);

                otherProviderSupplier = serviceBuilder.requires(otherProvidersLoaderServiceName);
            } else {
                otherProviderSupplier = null;
            }

            ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier = CredentialReference
                    .getCredentialSourceSupplier(context, CredentialStoreResourceDefinition.CREDENTIAL_REFERENCE, model,
                            serviceBuilder);

            return new ExceptionSupplier<CredentialStore, StartException>() {

                @Override
                public CredentialStore get() throws StartException {
                    try {
                        if (location != null) {
                            PathResolver pathResolver = pathResolver();
                            pathResolver.path(location);
                            if (relativeTo != null) {
                                pathResolver.relativeTo(relativeTo, pathManager.get());
                            }
                            File resolved = pathResolver.resolve();
                            pathResolver.clear();
                            credentialStoreAttributes.put(ElytronDescriptionConstants.LOCATION, resolved.getAbsolutePath());
                        } else {
                            credentialStoreAttributes.put(ElytronDescriptionConstants.LOCATION, null);
                        }

                        ROOT_LOGGER.tracef("starting CredentialStore:  name = %s", name);

                        CredentialStore cs = getCredentialStoreInstance(providerSupplier != null ? providerSupplier.get() : null);
                        Provider[] otherProvidersArr = otherProviderSupplier != null ? otherProviderSupplier.get() : null;
                        if (ROOT_LOGGER.isTraceEnabled()) {
                            ROOT_LOGGER.tracef(
                                    "initializing CredentialStore:  name = %s  type = %s  provider = %s  otherProviders = %s  attributes = %s",
                                    name, type, providerName, Arrays.toString(otherProvidersArr), credentialStoreAttributes);
                        }

                        CredentialSourceProtectionParameter credentialSource = resolveCredentialStoreProtectionParameter(name,
                                credentialSourceSupplier != null ? credentialSourceSupplier.get() : null);
                        reloader = new ExceptionRunnable<GeneralSecurityException>() {

                            @Override
                            public void run() throws GeneralSecurityException {
                                synchronized (EmptyProvider.getInstance()) {
                                    cs.initialize(credentialStoreAttributes, credentialSource, otherProvidersArr);
                                }
                            }
                        };
                        reloader.run();

                        return cs;
                    } catch (Exception e) {
                        throw ROOT_LOGGER.unableToStartService(e);
                    }
                }

            };

        }

        @Override
        protected CredentialStore createImmediately(OperationContext foreignContext) throws OperationFailedException {
            File resolvedPath = null;
            if (location != null) {
                resolvedPath = resolveRelativeToImmediately(location, relativeTo, foreignContext);
                credentialStoreAttributes.put(ElytronDescriptionConstants.LOCATION, resolvedPath.getAbsolutePath());
            }

            Provider[] providers = null;
            if (this.providers != null) {
                ExceptionFunction<OperationContext, Provider[], OperationFailedException> providerApi = foreignContext
                        .getCapabilityRuntimeAPI(PROVIDERS_API_CAPABILITY, this.providers, ExceptionFunction.class);
                providers = providerApi.apply(foreignContext);
            }

            Provider[] otherProviders;
            if (this.otherProviders != null) {
                ExceptionFunction<OperationContext, Provider[], OperationFailedException> providerApi = foreignContext
                        .getCapabilityRuntimeAPI(PROVIDERS_API_CAPABILITY, this.otherProviders, ExceptionFunction.class);
                otherProviders = providerApi.apply(foreignContext);
            } else {
                otherProviders = null;
            }

            CredentialSource credentialSource = getCredentialSource(foreignContext, CREDENTIAL_REFERENCE, model);

            try {
                CredentialStore credentialStore = getCredentialStoreInstance(providers);

                CredentialSourceProtectionParameter protectionParamter = resolveCredentialStoreProtectionParameter(name, credentialSource);
                reloader = new ExceptionRunnable<GeneralSecurityException>() {

                    @Override
                    public void run() throws GeneralSecurityException {
                        synchronized (EmptyProvider.getInstance()) {
                            credentialStore.initialize(credentialStoreAttributes, protectionParamter, otherProviders);
                        }
                    }
                };
                reloader.run();

                return credentialStore;
            } catch (GeneralSecurityException | IOException e) {
                throw ROOT_LOGGER.unableToInitialiseCredentialStore(e);
            }

        }

        @Override
        protected void reload(OperationContext context) throws GeneralSecurityException, OperationFailedException {
            if (reloader != null) {
                reloader.run();
            } else {
                super.apply(context);
            }
        }

        private CredentialStore getCredentialStoreInstance(Provider[] injectedProviders) throws CredentialStoreException, NoSuchAlgorithmException, NoSuchProviderException {
            String resolvedType = type != null ? type : KeyStoreCredentialStore.KEY_STORE_CREDENTIAL_STORE;
            if (providerName != null) {
                // directly specified provider
                return CredentialStore.getInstance(resolvedType, providerName);
            }

            if (ROOT_LOGGER.isTraceEnabled()) {
                ROOT_LOGGER.tracef("obtaining CredentialStore %s from providers %s", name, Arrays.toString(injectedProviders));
            }
            if (injectedProviders != null) {
                // injected provider list, select the first provider with corresponding type
                for (Provider p : injectedProviders) {
                    try {
                        return CredentialStore.getInstance(resolvedType, p);
                    } catch (NoSuchAlgorithmException ignore) {
                    }
                }

                throw ROOT_LOGGER.providerLoaderCannotSupplyProvider(providers, resolvedType);
            } else {
                // default provider
                return CredentialStore.getInstance(resolvedType);
            }
        }

        private static CredentialStore.CredentialSourceProtectionParameter resolveCredentialStoreProtectionParameter(String name, CredentialSource cs) throws IOException {
            if (cs != null) {
                Credential credential = cs.getCredential(PasswordCredential.class);

                ROOT_LOGGER.tracef("resolving CredentialStore %s ProtectionParameter from %s", name, credential);
                return credentialToCredentialSourceProtectionParameter(credential);
            } else {
                throw ROOT_LOGGER.credentialStoreProtectionParameterCannotBeResolved(name);
            }
        }

        private static CredentialStore.CredentialSourceProtectionParameter credentialToCredentialSourceProtectionParameter(Credential credential) {
            return new CredentialStore.CredentialSourceProtectionParameter(IdentityCredentials.NONE.withCredential(credential));
        }

    }
}
