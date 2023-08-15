/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.elytron.common;

import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import org.jboss.as.controller.AbstractAttributeDefinitionBuilder;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron.common.util.ElytronCommonMessages;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.x500.cert.acme.AcmeAccount;
import org.wildfly.security.x500.cert.acme.AcmeClientSpi;
import org.wildfly.security.x500.cert.acme.AcmeException;
import org.wildfly.security.x500.cert.acme.AcmeMetadata;
import org.wildfly.security.x500.cert.acme.CertificateAuthority;

/**
 * A {@link ResourceDefinition} for a single certificate authority account.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class CertificateAuthorityAccountDefinition extends SimpleResourceDefinition {

    public static final StringListAttributeDefinition CONTACT_URLS = new StringListAttributeDefinition.Builder(ElytronCommonConstants.CONTACT_URLS)
            .setRequired(false)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition CERTIFICATE_AUTHORITY = new CertificateAuthorityDefinitionBuilder(ElytronCommonConstants.CERTIFICATE_AUTHORITY, ModelType.STRING, true)
            .setDefaultValue(new ModelNode(CertificateAuthority.LETS_ENCRYPT.getName()))
            .setAllowExpression(true)
            .setRestartAllServices()
            .setCapabilityReference(ElytronCommonCapabilities.CERTIFICATE_AUTHORITY_CAPABILITY, ElytronCommonCapabilities.CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY)
            .build();

    public static final SimpleAttributeDefinition KEY_STORE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.KEY_STORE, ModelType.STRING, false)
            .setAttributeGroup(ElytronCommonConstants.ACCOUNT_KEY)
            .setMinSize(1)
            .setRestartAllServices()
            .setCapabilityReference(ElytronCommonCapabilities.KEY_STORE_CAPABILITY, ElytronCommonCapabilities.CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY)
            .build();

    public static final SimpleAttributeDefinition ALIAS = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.ALIAS, ModelType.STRING, false)
            .setAttributeGroup(ElytronCommonConstants.ACCOUNT_KEY)
            .setMinSize(1)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final ObjectTypeAttributeDefinition CREDENTIAL_REFERENCE = CredentialReference.getAttributeBuilder(true, true)
            .setAttributeGroup(ElytronCommonConstants.ACCOUNT_KEY)
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { CERTIFICATE_AUTHORITY, CONTACT_URLS, KEY_STORE, ALIAS, CREDENTIAL_REFERENCE };

    public static final SimpleAttributeDefinition AGREE_TO_TERMS_OF_SERVICE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.AGREE_TO_TERMS_OF_SERVICE, ModelType.BOOLEAN, false)
            .setAllowExpression(true)
            .build();

    public static final SimpleAttributeDefinition STAGING = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.STAGING, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    // Resource resolver

    private final StandardResourceDescriptionResolver resourceResolver;

    private static final AcmeClientSpi acmeClient;
    static {
        acmeClient = loadAcmeClient();
    }

    private static AcmeClientSpi loadAcmeClient() {
        for (AcmeClientSpi acmeClient : ServiceLoader.load(AcmeClientSpi.class, ElytronCommonMessages.class.getClassLoader())) {
            return acmeClient;
        }
        throw ElytronCommonMessages.ROOT_LOGGER.unableToInstatiateAcmeClientSpiImplementation();
    }

    private static class CertificateAuthorityAttributeDefinition extends SimpleAttributeDefinition {

        CertificateAuthorityAttributeDefinition(AbstractAttributeDefinitionBuilder<?, ? extends CertificateAuthorityAttributeDefinition> builder) {
            super(builder);
        }

        @Override
        public void addCapabilityRequirements(OperationContext context, Resource resource, ModelNode attributeValue) {
            // Check the existence of certificate authority only if it is not null or LetsEncrypt, since LetsEncrypt does not have to be added
            if (attributeValue.asStringOrNull() != null && !attributeValue.asString().equalsIgnoreCase(CertificateAuthority.LETS_ENCRYPT.getName())) {
                super.addCapabilityRequirements(context, resource, attributeValue);
            }
        }
    }

    private static class CertificateAuthorityDefinitionBuilder extends AbstractAttributeDefinitionBuilder<CertificateAuthorityDefinitionBuilder, CertificateAuthorityAttributeDefinition> {

        CertificateAuthorityDefinitionBuilder(String attributeName, ModelType type, boolean optional) {
            super(attributeName, type, optional);
        }

        @Override
        public CertificateAuthorityAttributeDefinition build() {
            return new CertificateAuthorityAttributeDefinition(this);
        }
    }

    private static class CertificateAuthorityAccountAddHandler extends ElytronCommonBaseAddHandler {
        private final Class<?> extensionClass;

        private CertificateAuthorityAccountAddHandler(final Class<?> extensionClass) {
            super(ElytronCommonCapabilities.CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY, ATTRIBUTES);
            this.extensionClass = extensionClass;
        }

        @Override
        protected String getSubsystemCapability() {
            return ElytronCommonDefinitions.getSubsystemCapability(extensionClass);
        }

        @Override
        protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws  OperationFailedException {
            super.populateModel(context, operation, resource);
            handleCredentialReferenceUpdate(context, resource.getModel());
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            ModelNode model = resource.getModel();
            String certificateAuthorityName = CERTIFICATE_AUTHORITY.resolveModelAttribute(context, model).asString();
            final String alias = ALIAS.resolveModelAttribute(context, model).asString();
            final String keyStoreName = KEY_STORE.resolveModelAttribute(context, model).asString();
            ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier = null;
            if (CREDENTIAL_REFERENCE.resolveModelAttribute(context, operation).isDefined()) {
                credentialSourceSupplier = CredentialReference.getCredentialSourceSupplier(context, CREDENTIAL_REFERENCE, operation, null);
            }
            final List<ModelNode> contactUrls = CONTACT_URLS.resolveModelAttribute(context, model).asListOrEmpty();
            final List<String> contactUrlsList = new ArrayList<>(contactUrls.size());
            for (ModelNode contactUrl : contactUrls) {
                contactUrlsList.add(contactUrl.asString());
            }

            AcmeAccountService acmeAccountService = new AcmeAccountService(certificateAuthorityName, contactUrlsList, alias, keyStoreName);
            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<Void> certificateAuthorityAccountRuntimeCapability = ElytronCommonCapabilities.CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName acmeAccountServiceName = certificateAuthorityAccountRuntimeCapability.getCapabilityServiceName(AcmeAccount.class);
            ServiceBuilder<AcmeAccount> acmeAccountServiceBuilder = serviceTarget.addService(acmeAccountServiceName, acmeAccountService).setInitialMode(ServiceController.Mode.ACTIVE);
            acmeAccountService.getCredentialSourceSupplierInjector().inject(credentialSourceSupplier);

            String keyStoreCapabilityName = RuntimeCapability.buildDynamicCapabilityName(ElytronCommonCapabilities.KEY_STORE_CAPABILITY, keyStoreName);
            acmeAccountServiceBuilder.addDependency(context.getCapabilityServiceName(keyStoreCapabilityName, KeyStore.class), KeyStore.class, acmeAccountService.getKeyStoreInjector());
            if (certificateAuthorityName.equalsIgnoreCase(CertificateAuthority.LETS_ENCRYPT.getName())) {
                ElytronCommonDefinitions.commonRequirements(extensionClass, acmeAccountServiceBuilder).install();
            } else {
                acmeAccountServiceBuilder.requires(ElytronCommonCapabilities.CERTIFICATE_AUTHORITY_RUNTIME_CAPABILITY.getCapabilityServiceName(certificateAuthorityName));
                ElytronCommonDefinitions.commonRequirements(extensionClass, acmeAccountServiceBuilder).install();
            }
        }

        @Override
        protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
            rollbackCredentialStoreUpdate(CREDENTIAL_REFERENCE, context, resource);
        }
    }

    private static final AbstractWriteAttributeHandler WRITE = new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTES);

    public static CertificateAuthorityAccountDefinition configure(final Class<?> extensionClass) {
        StandardResourceDescriptionResolver caAcctResourceResolver = ElytronCommonDefinitions.getResourceDescriptionResolver(extensionClass, ElytronCommonConstants.CERTIFICATE_AUTHORITY_ACCOUNT);

        CertificateAuthorityAccountAddHandler caAcctAddHandler = new CertificateAuthorityAccountAddHandler(extensionClass);
        TrivialCapabilityServiceRemoveHandler caAcctRemoveHandler = new TrivialCapabilityServiceRemoveHandler(extensionClass, caAcctAddHandler, ElytronCommonCapabilities.CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY);

        return new CertificateAuthorityAccountDefinition(caAcctResourceResolver, caAcctAddHandler, caAcctRemoveHandler);
    }

    protected CertificateAuthorityAccountDefinition(StandardResourceDescriptionResolver caAcctResourceResolver, CertificateAuthorityAccountAddHandler caAcctAddHandler,
                                                  ElytronCommonTrivialCapabilityServiceRemoveHandler caAcctRemoveHandler) {
        super(new Parameters(PathElement.pathElement(ElytronCommonConstants.CERTIFICATE_AUTHORITY_ACCOUNT), caAcctResourceResolver)
                .setAddHandler(caAcctAddHandler)
                .setRemoveHandler(caAcctRemoveHandler)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(ElytronCommonCapabilities.CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY));

        this.resourceResolver = caAcctResourceResolver;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, WRITE);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);

        if (ElytronCommonDefinitions.isServerOrHostController(resourceRegistration)) { // server-only operations

            // Create an account with the certificate authority
            CreateAccountHandler.register(resourceRegistration, resourceResolver);

            // Update an account with the certificate authority
            UpdateAccountHandler.register(resourceRegistration, resourceResolver);

            // Change the account key
            ChangeAccountKeyHandler.register(resourceRegistration, resourceResolver);

            // Deactivate the account key
            DeactivateAccountHandler.register(resourceRegistration, resourceResolver);

            // Get the metadata associated with the certificate authority
            GetMetadataHandler.register(resourceRegistration, resourceResolver);
        }
    }

    protected static class CreateAccountHandler extends ElytronRuntimeOnlyHandler {

        protected static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronCommonConstants.CREATE_ACCOUNT, descriptionResolver)
                            .setParameters(AGREE_TO_TERMS_OF_SERVICE, STAGING)
                            .setRuntimeOnly()
                            .build(),
                    new CertificateAuthorityAccountDefinition.CreateAccountHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            boolean agreeToTermsOfService = AGREE_TO_TERMS_OF_SERVICE.resolveModelAttribute(context, operation).asBoolean();
            boolean staging = STAGING.resolveModelAttribute(context, operation).asBoolean();
            AcmeAccount acmeAccount = getAcmeAccount(context, staging);

            try {
                acmeAccount.setTermsOfServiceAgreed(agreeToTermsOfService);
                boolean created = acmeClient.createAccount(acmeAccount, staging);
                if (! created) {
                    throw ElytronCommonMessages.ROOT_LOGGER.certificateAuthorityAccountAlreadyExists(ElytronCommonConstants.UPDATE_ACCOUNT, ElytronCommonConstants.CHANGE_ACCOUNT_KEY);
                }
            } catch (AcmeException e) {
                throw ElytronCommonMessages.ROOT_LOGGER.unableToCreateAccountWithCertificateAuthority(e, e.getLocalizedMessage());
            }
        }
    }

    protected static class UpdateAccountHandler extends ElytronRuntimeOnlyHandler {
        protected static final SimpleAttributeDefinition UPDATE_AGREE_TO_TERMS_OF_SERVICE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.AGREE_TO_TERMS_OF_SERVICE, ModelType.BOOLEAN, true)
                .setAllowExpression(true)
                .build();

        protected static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronCommonConstants.UPDATE_ACCOUNT, descriptionResolver)
                            .setParameters(AGREE_TO_TERMS_OF_SERVICE, STAGING)
                            .setRuntimeOnly()
                            .build(),
                    new CertificateAuthorityAccountDefinition.UpdateAccountHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            Boolean agreeToTermsOfService = UPDATE_AGREE_TO_TERMS_OF_SERVICE.resolveModelAttribute(context, operation).asBooleanOrNull();
            boolean staging = STAGING.resolveModelAttribute(context, operation).asBoolean();
            AcmeAccount acmeAccount = getAcmeAccount(context, staging);

            try {
                if (agreeToTermsOfService != null) {
                    acmeClient.updateAccount(acmeAccount, staging, agreeToTermsOfService.booleanValue(), acmeAccount.getContactUrls());
                } else {
                    acmeClient.updateAccount(acmeAccount, staging, acmeAccount.getContactUrls());
                }
            } catch (AcmeException e) {
                throw ElytronCommonMessages.ROOT_LOGGER.unableToUpdateAccountWithCertificateAuthority(e, e.getLocalizedMessage());
            }
        }
    }

    protected static class ChangeAccountKeyHandler extends ElytronRuntimeOnlyHandler {

        protected static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronCommonConstants.CHANGE_ACCOUNT_KEY, descriptionResolver)
                            .setParameters(STAGING)
                            .setRuntimeOnly()
                            .build(),
                    new CertificateAuthorityAccountDefinition.ChangeAccountKeyHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            boolean staging = STAGING.resolveModelAttribute(context, operation).asBoolean();
            AcmeAccountService acmeAccountService = getAcmeAccountService(context);
            AcmeAccount acmeAccount = getAcmeAccount(acmeAccountService, staging);

            try {
                acmeClient.changeAccountKey(acmeAccount, staging);
                acmeAccountService.saveCertificateAuthorityAccountKey(context);
            } catch (AcmeException e) {
                throw ElytronCommonMessages.ROOT_LOGGER.unableToChangeAccountKeyWithCertificateAuthority(e, e.getLocalizedMessage());
            }
        }
    }

    protected static class DeactivateAccountHandler extends ElytronRuntimeOnlyHandler {

        protected static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronCommonConstants.DEACTIVATE_ACCOUNT, descriptionResolver)
                            .setParameters(STAGING)
                            .setRuntimeOnly()
                            .build(),
                    new CertificateAuthorityAccountDefinition.DeactivateAccountHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            boolean staging = STAGING.resolveModelAttribute(context, operation).asBoolean();
            AcmeAccount acmeAccount = getAcmeAccount(context, staging);

            try {
                acmeClient.deactivateAccount(acmeAccount, staging);
            } catch (AcmeException e) {
                throw ElytronCommonMessages.ROOT_LOGGER.unableToDeactivateAccountWithCertificateAuthority(e, e.getLocalizedMessage());
            }
        }
    }

    protected static class GetMetadataHandler extends ElytronRuntimeOnlyHandler {

        protected static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronCommonConstants.GET_METADATA, descriptionResolver)
                            .setParameters(STAGING)
                            .setRuntimeOnly()
                            .build(),
                    new CertificateAuthorityAccountDefinition.GetMetadataHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            boolean staging = STAGING.resolveModelAttribute(context, operation).asBoolean();
            AcmeAccount acmeAccount = getAcmeAccount(context, staging);

            try {
                AcmeMetadata metadata = acmeClient.getMetadata(acmeAccount, staging);
                if (metadata != null) {
                    ModelNode result = context.getResult();
                    if (metadata.getTermsOfServiceUrl() != null) {
                        result.get(ElytronCommonConstants.TERMS_OF_SERVICE).set(new ModelNode(metadata.getTermsOfServiceUrl()));
                    }
                    if (metadata.getWebsiteUrl() != null) {
                        result.get(ElytronCommonConstants.WEBSITE).set(new ModelNode(metadata.getWebsiteUrl()));
                    }
                    String[] caaIdentitiesArray = metadata.getCAAIdentities();
                    ModelNode caaIdentities = new ModelNode();
                    if (caaIdentitiesArray != null && caaIdentitiesArray.length != 0) {
                        for (int i = 0; i < caaIdentitiesArray.length; i++) {
                            caaIdentities.add(caaIdentitiesArray[i]);
                        }
                        result.get(ElytronCommonConstants.CAA_IDENTITIES).set(caaIdentities);
                    }
                    result.get(ElytronCommonConstants.EXTERNAL_ACCOUNT_REQUIRED).set(metadata.isExternalAccountRequired());
                }
            } catch (AcmeException e) {
                throw ElytronCommonMessages.ROOT_LOGGER.unableToGetCertificateAuthorityMetadata(e, e.getLocalizedMessage());
            }
        }
    }

    public static ModifiableKeyStoreService getModifiableKeyStoreService(OperationContext context, String keyStoreName) throws OperationFailedException {
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
        return getModifiableKeyStoreService(serviceRegistry, keyStoreName);
    }

    public static ModifiableKeyStoreService getModifiableKeyStoreService(ServiceRegistry serviceRegistry, String keyStoreName) throws OperationFailedException {
        RuntimeCapability<Void> runtimeCapability = ElytronCommonCapabilities.KEY_STORE_RUNTIME_CAPABILITY.fromBaseCapability(keyStoreName);
        ServiceName serviceName = runtimeCapability.getCapabilityServiceName();

        ServiceController<KeyStore> serviceContainer = ElytronCommonDefinitions.getRequiredService(serviceRegistry, serviceName, KeyStore.class);
        ServiceController.State serviceState = serviceContainer.getState();
        if (serviceState != ServiceController.State.UP) {
            throw ElytronCommonMessages.ROOT_LOGGER.requiredServiceNotUp(serviceName, serviceState);
        }

        return (ModifiableKeyStoreService) serviceContainer.getService();
    }

    private static AcmeAccountService getAcmeAccountService(OperationContext context) throws OperationFailedException {
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
        PathAddress currentAddress = context.getCurrentAddress();
        RuntimeCapability<Void> runtimeCapability = ElytronCommonCapabilities.CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY.fromBaseCapability(currentAddress.getLastElement().getValue());
        ServiceName serviceName = runtimeCapability.getCapabilityServiceName();

        ServiceController<AcmeAccount> serviceContainer = ElytronCommonDefinitions.getRequiredService(serviceRegistry, serviceName, AcmeAccount.class);
        ServiceController.State serviceState = serviceContainer.getState();
        if (serviceState != ServiceController.State.UP) {
            throw ElytronCommonMessages.ROOT_LOGGER.requiredServiceNotUp(serviceName, serviceState);
        }

        return (AcmeAccountService) serviceContainer.getService();
    }

    private static AcmeAccount getAcmeAccount(final OperationContext context, boolean staging) throws OperationFailedException {
        return getAcmeAccount(getAcmeAccountService(context), staging);
    }

    private static AcmeAccount getAcmeAccount(AcmeAccountService acmeAccountService, boolean staging) {
        AcmeAccount acmeAccount = acmeAccountService.getValue();
        return AdvancedModifiableKeyStoreDecorator.resetAcmeAccount(acmeAccount, staging);
    }
}
