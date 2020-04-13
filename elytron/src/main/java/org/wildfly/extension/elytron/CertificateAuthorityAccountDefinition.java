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

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
import static org.wildfly.extension.elytron.AdvancedModifiableKeyStoreDecorator.resetAcmeAccount;
import static org.wildfly.extension.elytron.Capabilities.CERTIFICATE_AUTHORITY_ACCOUNT_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.CERTIFICATE_AUTHORITY_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.CERTIFICATE_AUTHORITY_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonRequirements;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractAttributeDefinitionBuilder;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
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
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;
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
class CertificateAuthorityAccountDefinition extends SimpleResourceDefinition {

    static final StringListAttributeDefinition CONTACT_URLS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.CONTACT_URLS)
            .setRequired(false)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition CERTIFICATE_AUTHORITY = new CertificateAuthorityDefinitionBuilder(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY, ModelType.STRING, true)
            .setDefaultValue(new ModelNode(CertificateAuthority.LETS_ENCRYPT.getName()))
            .setAllowExpression(true)
            .setRestartAllServices()
            .setCapabilityReference(CERTIFICATE_AUTHORITY_CAPABILITY, CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition KEY_STORE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_STORE, ModelType.STRING, false)
            .setAttributeGroup(ElytronDescriptionConstants.ACCOUNT_KEY)
            .setMinSize(1)
            .setRestartAllServices()
            .setCapabilityReference(KEY_STORE_CAPABILITY, CERTIFICATE_AUTHORITY_ACCOUNT_CAPABILITY, true)
            .build();

    static final SimpleAttributeDefinition ALIAS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALIAS, ModelType.STRING, false)
            .setAttributeGroup(ElytronDescriptionConstants.ACCOUNT_KEY)
            .setMinSize(1)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final ObjectTypeAttributeDefinition CREDENTIAL_REFERENCE = CredentialReference.getAttributeBuilder(true, true)
            .setAttributeGroup(ElytronDescriptionConstants.ACCOUNT_KEY)
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { CERTIFICATE_AUTHORITY, CONTACT_URLS, KEY_STORE, ALIAS, CREDENTIAL_REFERENCE };

    static final SimpleAttributeDefinition AGREE_TO_TERMS_OF_SERVICE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AGREE_TO_TERMS_OF_SERVICE, ModelType.BOOLEAN, false)
            .setAllowExpression(true)
            .build();

    static final SimpleAttributeDefinition STAGING = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.STAGING, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    private static final AcmeClientSpi acmeClient;
    static {
        acmeClient = loadAcmeClient();
    }

    private static AcmeClientSpi loadAcmeClient() {
        for (AcmeClientSpi acmeClient : ServiceLoader.load(AcmeClientSpi.class, ElytronSubsystemMessages.class.getClassLoader())) {
            return acmeClient;
        }
        throw ROOT_LOGGER.unableToInstatiateAcmeClientSpiImplementation();
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

    private static final AbstractAddStepHandler ADD = new CertificateAuthorityAccountAddHandler();

    private static class CertificateAuthorityAccountAddHandler extends BaseAddHandler {

        private CertificateAuthorityAccountAddHandler() {
            super(CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY, ATTRIBUTES);
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
            RuntimeCapability<Void> certificateAuthorityAccountRuntimeCapability = CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName acmeAccountServiceName = certificateAuthorityAccountRuntimeCapability.getCapabilityServiceName(AcmeAccount.class);
            ServiceBuilder<AcmeAccount> acmeAccountServiceBuilder = serviceTarget.addService(acmeAccountServiceName, acmeAccountService).setInitialMode(ServiceController.Mode.ACTIVE);
            acmeAccountService.getCredentialSourceSupplierInjector().inject(credentialSourceSupplier);

            String keyStoreCapabilityName = RuntimeCapability.buildDynamicCapabilityName(KEY_STORE_CAPABILITY, keyStoreName);
            acmeAccountServiceBuilder.addDependency(context.getCapabilityServiceName(keyStoreCapabilityName, KeyStore.class), KeyStore.class, acmeAccountService.getKeyStoreInjector());
            if (certificateAuthorityName.equalsIgnoreCase(CertificateAuthority.LETS_ENCRYPT.getName())) {
                commonRequirements(acmeAccountServiceBuilder).install();
            } else {
                acmeAccountServiceBuilder.requires(CERTIFICATE_AUTHORITY_RUNTIME_CAPABILITY.getCapabilityServiceName(certificateAuthorityName));
                commonRequirements(acmeAccountServiceBuilder).install();
            }
        }

        @Override
        protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
            rollbackCredentialStoreUpdate(CREDENTIAL_REFERENCE, context, resource);
        }
    }

    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY);

    private static final AbstractWriteAttributeHandler WRITE = new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTES);

    CertificateAuthorityAccountDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT), ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT))
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY));
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
        ResourceDescriptionResolver resolver = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT);

        if (isServerOrHostController(resourceRegistration)) { // server-only operations

            // Create an account with the certificate authority
            CreateAccountHandler.register(resourceRegistration, resolver);

            // Update an account with the certificate authority
            UpdateAccountHandler.register(resourceRegistration, resolver);

            // Change the account key
            ChangeAccountKeyHandler.register(resourceRegistration, resolver);

            // Deactivate the account key
            DeactivateAccountHandler.register(resourceRegistration, resolver);

            // Get the metadata associated with the certificate authority
            GetMetadataHandler.register(resourceRegistration, resolver);
        }
    }

    static class CreateAccountHandler extends ElytronRuntimeOnlyHandler {

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.CREATE_ACCOUNT, descriptionResolver)
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
                    throw ROOT_LOGGER.certificateAuthorityAccountAlreadyExists(ElytronDescriptionConstants.UPDATE_ACCOUNT, ElytronDescriptionConstants.CHANGE_ACCOUNT_KEY);
                }
            } catch (AcmeException e) {
                throw ROOT_LOGGER.unableToCreateAccountWithCertificateAuthority(e, e.getLocalizedMessage());
            }
        }
    }

    static class UpdateAccountHandler extends ElytronRuntimeOnlyHandler {
        static final SimpleAttributeDefinition UPDATE_AGREE_TO_TERMS_OF_SERVICE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AGREE_TO_TERMS_OF_SERVICE, ModelType.BOOLEAN, true)
                .setAllowExpression(true)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.UPDATE_ACCOUNT, descriptionResolver)
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
                throw ROOT_LOGGER.unableToUpdateAccountWithCertificateAuthority(e, e.getLocalizedMessage());
            }
        }
    }

    static class ChangeAccountKeyHandler extends ElytronRuntimeOnlyHandler {

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.CHANGE_ACCOUNT_KEY, descriptionResolver)
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
                throw ROOT_LOGGER.unableToChangeAccountKeyWithCertificateAuthority(e, e.getLocalizedMessage());
            }
        }
    }

    static class DeactivateAccountHandler extends ElytronRuntimeOnlyHandler {

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.DEACTIVATE_ACCOUNT, descriptionResolver)
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
                throw ROOT_LOGGER.unableToDeactivateAccountWithCertificateAuthority(e, e.getLocalizedMessage());
            }
        }
    }

    static class GetMetadataHandler extends ElytronRuntimeOnlyHandler {

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.GET_METADATA, descriptionResolver)
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
                        result.get(ElytronDescriptionConstants.TERMS_OF_SERVICE).set(new ModelNode(metadata.getTermsOfServiceUrl()));
                    }
                    if (metadata.getWebsiteUrl() != null) {
                        result.get(ElytronDescriptionConstants.WEBSITE).set(new ModelNode(metadata.getWebsiteUrl()));
                    }
                    String[] caaIdentitiesArray = metadata.getCAAIdentities();
                    ModelNode caaIdentities = new ModelNode();
                    if (caaIdentitiesArray != null && caaIdentitiesArray.length != 0) {
                        for (int i = 0; i < caaIdentitiesArray.length; i++) {
                            caaIdentities.add(caaIdentitiesArray[i]);
                        }
                        result.get(ElytronDescriptionConstants.CAA_IDENTITIES).set(caaIdentities);
                    }
                    result.get(ElytronDescriptionConstants.EXTERNAL_ACCOUNT_REQUIRED).set(metadata.isExternalAccountRequired());
                }
            } catch (AcmeException e) {
                throw ROOT_LOGGER.unableToGetCertificateAuthorityMetadata(e, e.getLocalizedMessage());
            }
        }
    }

    static ModifiableKeyStoreService getModifiableKeyStoreService(OperationContext context, String keyStoreName) throws OperationFailedException {
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
        return getModifiableKeyStoreService(serviceRegistry, keyStoreName);
    }

    static ModifiableKeyStoreService getModifiableKeyStoreService(ServiceRegistry serviceRegistry, String keyStoreName) throws OperationFailedException {
        RuntimeCapability<Void> runtimeCapability = KEY_STORE_RUNTIME_CAPABILITY.fromBaseCapability(keyStoreName);
        ServiceName serviceName = runtimeCapability.getCapabilityServiceName();

        ServiceController<KeyStore> serviceContainer = getRequiredService(serviceRegistry, serviceName, KeyStore.class);
        ServiceController.State serviceState = serviceContainer.getState();
        if (serviceState != ServiceController.State.UP) {
            throw ROOT_LOGGER.requiredServiceNotUp(serviceName, serviceState);
        }

        return (ModifiableKeyStoreService) serviceContainer.getService();
    }

    private static AcmeAccountService getAcmeAccountService(OperationContext context) throws OperationFailedException {
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
        PathAddress currentAddress = context.getCurrentAddress();
        RuntimeCapability<Void> runtimeCapability = CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY.fromBaseCapability(currentAddress.getLastElement().getValue());
        ServiceName serviceName = runtimeCapability.getCapabilityServiceName();

        ServiceController<AcmeAccount> serviceContainer = getRequiredService(serviceRegistry, serviceName, AcmeAccount.class);
        ServiceController.State serviceState = serviceContainer.getState();
        if (serviceState != ServiceController.State.UP) {
            throw ROOT_LOGGER.requiredServiceNotUp(serviceName, serviceState);
        }

        return (AcmeAccountService) serviceContainer.getService();
    }

    private static AcmeAccount getAcmeAccount(final OperationContext context, boolean staging) throws OperationFailedException {
        return getAcmeAccount(getAcmeAccountService(context), staging);
    }

    private static AcmeAccount getAcmeAccount(AcmeAccountService acmeAccountService, boolean staging) {
        AcmeAccount acmeAccount = acmeAccountService.getValue();
        return resetAcmeAccount(acmeAccount, staging);
    }
}
