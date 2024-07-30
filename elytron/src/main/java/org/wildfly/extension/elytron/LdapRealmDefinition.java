/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.DIR_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_REALM_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.BASE64;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HEX;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.UTF_8;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.ldap.LdapName;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.validation.CharsetValidator;
import org.jboss.as.controller.operations.validation.StringAllowedValuesValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron.capabilities._private.DirContextSupplier;
import org.wildfly.security.auth.realm.ldap.AttributeMapping;
import org.wildfly.security.auth.realm.ldap.LdapSecurityRealmBuilder;
import org.wildfly.security.auth.realm.ldap.LdapSecurityRealmBuilder.IdentityMappingBuilder;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.password.spec.Encoding;

/**
 * A {@link ResourceDefinition} for a {@link SecurityRealm} backed by LDAP.
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
class LdapRealmDefinition extends SimpleResourceDefinition {

    static class AttributeMappingObjectDefinition {
        static final SimpleAttributeDefinition FROM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FROM, ModelType.STRING, true)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition TO = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.TO, ModelType.STRING, true)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition REFERENCE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REFERENCE, ModelType.STRING, true)
                .setAlternatives(ElytronDescriptionConstants.FILTER)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition FILTER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FILTER, ModelType.STRING, true)
                .setRequires(ElytronDescriptionConstants.TO)
                .setAlternatives(ElytronDescriptionConstants.REFERENCE)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition FILTER_BASE_DN = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FILTER_BASE_DN, ModelType.STRING, true)
                .setRequires(ElytronDescriptionConstants.FILTER)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition RECURSIVE_SEARCH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SEARCH_RECURSIVE, ModelType.BOOLEAN, true)
                .setRequires(ElytronDescriptionConstants.FILTER)
                .setDefaultValue(ModelNode.TRUE)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition ROLE_RECURSION = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ROLE_RECURSION, ModelType.INT, true)
                .setDefaultValue(ModelNode.ZERO)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition ROLE_RECURSION_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ROLE_RECURSION_NAME, ModelType.STRING, true)
                .setDefaultValue(new ModelNode("cn"))
                .setRequires(ElytronDescriptionConstants.ROLE_RECURSION)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition EXTRACT_RDN = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.EXTRACT_RDN, ModelType.STRING, true)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition[] ATTRIBUTES = new SimpleAttributeDefinition[] {FROM, TO, REFERENCE, FILTER, FILTER_BASE_DN, RECURSIVE_SEARCH, ROLE_RECURSION, ROLE_RECURSION_NAME, EXTRACT_RDN};

        static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.ATTRIBUTE, ATTRIBUTES)
                .build();
    }

    interface CredentialMappingObjectDefinition {
        void configure(LdapSecurityRealmBuilder builder, OperationContext context, ModelNode identityMapping) throws OperationFailedException;
    }

    static class UserPasswordCredentialMappingObjectDefinition implements CredentialMappingObjectDefinition {

        static final SimpleAttributeDefinition FROM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FROM, ModelType.STRING, false)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition WRITABLE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.WRITABLE, ModelType.BOOLEAN, true)
                .setDefaultValue(ModelNode.FALSE)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition VERIFIABLE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VERIFIABLE, ModelType.BOOLEAN, true)
                .setDefaultValue(ModelNode.TRUE)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {FROM, WRITABLE, VERIFIABLE};

        static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.USER_PASSWORD_MAPPER, ATTRIBUTES)
                .build();

        @Override
        public void configure(LdapSecurityRealmBuilder builder, OperationContext context, ModelNode identityMapping) throws OperationFailedException {
            ModelNode model = OBJECT_DEFINITION.resolveModelAttribute(context, identityMapping);
            if (!model.isDefined()) return;

            String from = FROM.resolveModelAttribute(context, model).asString();
            boolean writable = WRITABLE.resolveModelAttribute(context, model).asBoolean();
            boolean verifiable = VERIFIABLE.resolveModelAttribute(context, model).asBoolean();

            LdapSecurityRealmBuilder.UserPasswordCredentialLoaderBuilder b = builder.userPasswordCredentialLoader();
            if (from != null) b.setUserPasswordAttribute(from);
            if (writable) b.enablePersistence();
            if (!verifiable) b.disableVerification();
            b.build();
        }
    }

    static List<CredentialMappingObjectDefinition> CREDENTIAL_MAPPERS = Arrays.asList(
            new UserPasswordCredentialMappingObjectDefinition(),
            new OtpCredentialMappingObjectDefinition(),
            new X509CredentialMappingObjectDefinition()
    );

    static class OtpCredentialMappingObjectDefinition implements CredentialMappingObjectDefinition {

        static final SimpleAttributeDefinition ALGORITHM_FROM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM_FROM, ModelType.STRING, false)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition HASH_FROM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.HASH_FROM, ModelType.STRING, false)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition SEED_FROM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SEED_FROM, ModelType.STRING, false)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition SEQUENCE_FROM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SEQUENCE_FROM, ModelType.STRING, false)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {ALGORITHM_FROM, HASH_FROM, SEED_FROM, SEQUENCE_FROM};

        static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.OTP_CREDENTIAL_MAPPER, ATTRIBUTES)
                .build();

        @Override
        public void configure(LdapSecurityRealmBuilder builder, OperationContext context, ModelNode identityMapping) throws OperationFailedException {
            ModelNode model = OBJECT_DEFINITION.resolveModelAttribute(context, identityMapping);
            if (!model.isDefined()) return;

            String algorithmFrom = ALGORITHM_FROM.resolveModelAttribute(context, model).asString();
            String hashFrom = HASH_FROM.resolveModelAttribute(context, model).asString();
            String seedFrom = SEED_FROM.resolveModelAttribute(context, model).asString();
            String sequenceFrom = SEQUENCE_FROM.resolveModelAttribute(context, model).asString();

            LdapSecurityRealmBuilder.OtpCredentialLoaderBuilder b = builder.otpCredentialLoader();
            if (algorithmFrom != null) b.setOtpAlgorithmAttribute(algorithmFrom);
            if (hashFrom != null) b.setOtpHashAttribute(hashFrom);
            if (seedFrom != null) b.setOtpSeedAttribute(seedFrom);
            if (sequenceFrom != null) b.setOtpSequenceAttribute(sequenceFrom);
            b.build();
        }
    }

    static class X509CredentialMappingObjectDefinition implements CredentialMappingObjectDefinition {

        static final SimpleAttributeDefinition DIGEST_FROM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DIGEST_FROM, ModelType.STRING, true)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition DIGEST_ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DIGEST_ALGORITHM, ModelType.STRING, true)
                .setAllowExpression(true)
                .setDefaultValue(new ModelNode("SHA-1"))
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition CERTIFICATE_FROM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CERTIFICATE_FROM, ModelType.STRING, true)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition SERIAL_NUMBER_FROM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SERIAL_NUMBER_FROM, ModelType.STRING, true)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition SUBJECT_DN_FROM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SUBJECT_DN_FROM, ModelType.STRING, true)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {DIGEST_FROM, DIGEST_ALGORITHM, CERTIFICATE_FROM, SERIAL_NUMBER_FROM, SUBJECT_DN_FROM};

        static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.X509_CREDENTIAL_MAPPER, ATTRIBUTES)
                .build();

        @Override
        public void configure(LdapSecurityRealmBuilder builder, OperationContext context, ModelNode identityMapping) throws OperationFailedException {
            ModelNode model = OBJECT_DEFINITION.resolveModelAttribute(context, identityMapping);
            if (!model.isDefined()) return;
            LdapSecurityRealmBuilder.X509EvidenceVerifierBuilder b = builder.x509EvidenceVerifier();

            ModelNode digestFrom = DIGEST_FROM.resolveModelAttribute(context, model);
            ModelNode digestAlgorithmFrom = DIGEST_ALGORITHM.resolveModelAttribute(context, model);
            if (digestFrom.isDefined()) b.addDigestCertificateVerifier(digestFrom.asString(), digestAlgorithmFrom.asString());

            ModelNode certificateFrom = CERTIFICATE_FROM.resolveModelAttribute(context, model);
            if (certificateFrom.isDefined()) b.addEncodedCertificateVerifier(certificateFrom.asString());

            ModelNode serialNumberFrom = SERIAL_NUMBER_FROM.resolveModelAttribute(context, model);
            if (serialNumberFrom.isDefined()) b.addSerialNumberCertificateVerifier(serialNumberFrom.asString());

            ModelNode subjectDnFrom = SUBJECT_DN_FROM.resolveModelAttribute(context, model);
            if (subjectDnFrom.isDefined()) {
                b.addSubjectDnCertificateVerifier(subjectDnFrom.asString());
            }

            b.build();
        }
    }

    static class NewIdentityAttributeObjectDefinition {
        static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NAME, ModelType.STRING, false)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final StringListAttributeDefinition VALUE = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.VALUE)
                .setAllowExpression(true)
                .setMinSize(1)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {NAME, VALUE};

        static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.ATTRIBUTE, ATTRIBUTES)
                .build();
    }

    static class IdentityMappingObjectDefinition {

        static final SimpleAttributeDefinition RDN_IDENTIFIER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RDN_IDENTIFIER, ModelType.STRING, false)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition USE_RECURSIVE_SEARCH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.USE_RECURSIVE_SEARCH, ModelType.BOOLEAN, true)
                .setRequires(ElytronDescriptionConstants.SEARCH_BASE_DN)
                .setDefaultValue(ModelNode.FALSE)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition SEARCH_BASE_DN = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SEARCH_BASE_DN, ModelType.STRING, true)
                .setRequires(ElytronDescriptionConstants.RDN_IDENTIFIER)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final ObjectListAttributeDefinition ATTRIBUTE_MAPPINGS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.ATTRIBUTE_MAPPING, AttributeMappingObjectDefinition.OBJECT_DEFINITION)
                .setRequired(false)
                .setAttributeGroup(ElytronDescriptionConstants.ATTRIBUTE)
                .setAllowDuplicates(true)
                .build();

        static final ObjectListAttributeDefinition NEW_IDENTITY_ATTRIBUTES = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.NEW_IDENTITY_ATTRIBUTES, NewIdentityAttributeObjectDefinition.OBJECT_DEFINITION)
                .setRequired(false)
                .setAllowDuplicates(true)
                .build();

        static final SimpleAttributeDefinition FILTER_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FILTER_NAME, ModelType.STRING, true)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition ITERATOR_FILTER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ITERATOR_FILTER, ModelType.STRING, true)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final SimpleAttributeDefinition NEW_IDENTITY_PARENT_DN = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NEW_IDENTITY_PARENT_DN, ModelType.STRING, true)
                .setAllowExpression(true)
                .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                .build();

        static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {
                RDN_IDENTIFIER, USE_RECURSIVE_SEARCH, SEARCH_BASE_DN,
                ATTRIBUTE_MAPPINGS,
                FILTER_NAME, ITERATOR_FILTER, NEW_IDENTITY_PARENT_DN, NEW_IDENTITY_ATTRIBUTES
        };

        static final ObjectTypeAttributeDefinition OBJECT_DEFINITION = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.IDENTITY_MAPPING,
                    RDN_IDENTIFIER, USE_RECURSIVE_SEARCH, SEARCH_BASE_DN,
                    ATTRIBUTE_MAPPINGS,
                    FILTER_NAME, ITERATOR_FILTER,
                    NEW_IDENTITY_PARENT_DN, NEW_IDENTITY_ATTRIBUTES,
                    UserPasswordCredentialMappingObjectDefinition.OBJECT_DEFINITION,
                    OtpCredentialMappingObjectDefinition.OBJECT_DEFINITION,
                    X509CredentialMappingObjectDefinition.OBJECT_DEFINITION
                )
                .setRequired(true)
                .setRestartAllServices()
                .build();
    }

    static final SimpleAttributeDefinition DIR_CONTEXT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DIR_CONTEXT, ModelType.STRING, false)
            .setAllowExpression(false)
            .setRestartAllServices()
            .setCapabilityReference(DIR_CONTEXT_CAPABILITY, SECURITY_REALM_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition DIRECT_VERIFICATION = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DIRECT_VERIFICATION, ModelType.BOOLEAN, true)
        .setDefaultValue(ModelNode.FALSE)
        .setAllowExpression(true)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition ALLOW_BLANK_PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALLOW_BLANK_PASSWORD, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.FALSE)
            .setRequires(ElytronDescriptionConstants.DIRECT_VERIFICATION)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition HASH_ENCODING = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.HASH_ENCODING, ModelType.STRING, true)
            .setDefaultValue(new ModelNode(BASE64))
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

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {IdentityMappingObjectDefinition.OBJECT_DEFINITION, DIR_CONTEXT, DIRECT_VERIFICATION, ALLOW_BLANK_PASSWORD,
                                                                                HASH_ENCODING, HASH_CHARSET};

    private static final AbstractAddStepHandler ADD = new RealmAddHandler();
    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY, SECURITY_REALM_RUNTIME_CAPABILITY);

    LdapRealmDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.LDAP_REALM), ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.LDAP_REALM))
            .setAddHandler(ADD)
            .setRemoveHandler(REMOVE)
            .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilities(MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY, SECURITY_REALM_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, ElytronReloadRequiredWriteAttributeHandler.INSTANCE);
        }
    }

    private static class RealmAddHandler extends BaseAddHandler {

        private RealmAddHandler() {
            super(Set.of(MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY, SECURITY_REALM_RUNTIME_CAPABILITY));
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();

            String address = context.getCurrentAddressValue();
            ServiceName mainServiceName = MODIFIABLE_SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(address).getCapabilityServiceName();
            ServiceName aliasServiceName = SECURITY_REALM_RUNTIME_CAPABILITY.fromBaseCapability(address).getCapabilityServiceName();

            final LdapSecurityRealmBuilder builder = LdapSecurityRealmBuilder.builder();

            if (DIRECT_VERIFICATION.resolveModelAttribute(context, model).asBoolean()) {
                ModelNode identityMappingNode = IdentityMappingObjectDefinition.OBJECT_DEFINITION.resolveModelAttribute(context, model);
                if (UserPasswordCredentialMappingObjectDefinition.OBJECT_DEFINITION.resolveModelAttribute(context, identityMappingNode).isDefined()) {
                    ROOT_LOGGER.ldapRealmDirectVerificationAndUserPasswordMapper();
                }
                boolean allowBlankPassword = ALLOW_BLANK_PASSWORD.resolveModelAttribute(context, model).asBoolean();
                builder.addDirectEvidenceVerification(allowBlankPassword);
            }

            String hashEncoding = HASH_ENCODING.resolveModelAttribute(context, model).asString();
            String hashCharset = HASH_CHARSET.resolveModelAttribute(context, model).asString();
            Charset charset = Charset.forName(hashCharset);
            builder.setHashEncoding(HEX.equals(hashEncoding) ? Encoding.HEX : Encoding.BASE64);
            builder.setHashCharset(charset);

            TrivialService<SecurityRealm> ldapRealmService = new TrivialService<>(builder::build);
            ServiceBuilder<SecurityRealm> serviceBuilder = serviceTarget.addService(mainServiceName, ldapRealmService)
                    .addAliases(aliasServiceName);

            commonDependencies(serviceBuilder);

            configureIdentityMapping(context, model, builder);
            configureDirContext(context, model, builder, serviceBuilder);

            serviceBuilder.setInitialMode(ServiceController.Mode.ACTIVE).install();
        }

        private void configureDirContext(OperationContext context, ModelNode model, LdapSecurityRealmBuilder realmBuilder, ServiceBuilder<SecurityRealm> serviceBuilder) throws OperationFailedException {
            String dirContextName = DIR_CONTEXT.resolveModelAttribute(context, model).asStringOrNull();

            String runtimeCapability = RuntimeCapability.buildDynamicCapabilityName(DIR_CONTEXT_CAPABILITY, dirContextName);
            ServiceName dirContextServiceName = context.getCapabilityServiceName(runtimeCapability, DirContextSupplier.class);

            final InjectedValue<DirContextSupplier> dirContextInjector = new InjectedValue<>();
            serviceBuilder.addDependency(dirContextServiceName, DirContextSupplier.class, dirContextInjector);

            realmBuilder.setDirContextSupplier(() -> {
                ExceptionSupplier<DirContext, NamingException> supplier = dirContextInjector.getValue();
                return supplier.get();
            });
        }

        private void configureIdentityMapping(OperationContext context, ModelNode model, LdapSecurityRealmBuilder builder) throws OperationFailedException {
            ModelNode identityMappingNode = IdentityMappingObjectDefinition.OBJECT_DEFINITION.resolveModelAttribute(context, model);

            IdentityMappingBuilder identityMappingBuilder = builder.identityMapping();

            ModelNode nameAttributeNode = IdentityMappingObjectDefinition.RDN_IDENTIFIER.resolveModelAttribute(context, identityMappingNode);
            identityMappingBuilder.setRdnIdentifier(nameAttributeNode.asString());

            ModelNode searchDnNode = IdentityMappingObjectDefinition.SEARCH_BASE_DN.resolveModelAttribute(context, identityMappingNode);
            if (searchDnNode.isDefined()) {
                identityMappingBuilder.setSearchDn(searchDnNode.asString());
            }

            ModelNode useRecursiveSearchNode = IdentityMappingObjectDefinition.USE_RECURSIVE_SEARCH.resolveModelAttribute(context, identityMappingNode);
            if (useRecursiveSearchNode.asBoolean()) {
                identityMappingBuilder.searchRecursive();
            }

            for(CredentialMappingObjectDefinition credentialMapper : CREDENTIAL_MAPPERS) {
                credentialMapper.configure(builder, context, identityMappingNode);
            }

            ModelNode attributeMappingNode = IdentityMappingObjectDefinition.ATTRIBUTE_MAPPINGS.resolveModelAttribute(context, identityMappingNode);
            if (attributeMappingNode.isDefined()) {
                for (ModelNode attributeNode : attributeMappingNode.asList()) {
                    ModelNode filter = AttributeMappingObjectDefinition.FILTER.resolveModelAttribute(context, attributeNode);
                    ModelNode reference = AttributeMappingObjectDefinition.REFERENCE.resolveModelAttribute(context, attributeNode);

                    AttributeMapping.Builder b;
                    if (filter.isDefined()) {
                        b = AttributeMapping.fromFilter(filter.asString());
                    } else if (reference.isDefined()) {
                        b = AttributeMapping.fromReference(reference.asString());
                    } else {
                        b = AttributeMapping.fromIdentity();
                    }

                    ModelNode from = AttributeMappingObjectDefinition.FROM.resolveModelAttribute(context, attributeNode);
                    if (from.isDefined()) {
                        b.from(from.asString());
                    }

                    ModelNode to = AttributeMappingObjectDefinition.TO.resolveModelAttribute(context, attributeNode);
                    if (to.isDefined()) {
                        b.to(to.asString());
                    }

                    ModelNode rdn = AttributeMappingObjectDefinition.EXTRACT_RDN.resolveModelAttribute(context, attributeNode);
                    if (rdn.isDefined()) {
                        b.extractRdn(rdn.asString());
                    }

                    ModelNode searchDn = AttributeMappingObjectDefinition.FILTER_BASE_DN.resolveModelAttribute(context, attributeNode);
                    if (searchDn.isDefined() && filter.isDefined()) {
                        b.searchDn(searchDn.asString());
                    }

                    ModelNode recursiveSearch = AttributeMappingObjectDefinition.RECURSIVE_SEARCH.resolveModelAttribute(context, attributeNode);
                    if (recursiveSearch.isDefined() && filter.isDefined()) {
                        b.searchRecursively(recursiveSearch.asBoolean());
                    }

                    ModelNode roleRecursion = AttributeMappingObjectDefinition.ROLE_RECURSION.resolveModelAttribute(context, attributeNode);
                    ModelNode roleRecursionName = AttributeMappingObjectDefinition.ROLE_RECURSION_NAME.resolveModelAttribute(context, attributeNode);
                    if (roleRecursion.isDefined() && (filter.isDefined() || reference.isDefined())) {
                        b.roleRecursion(roleRecursion.asInt());
                        if (roleRecursionName.isDefined()) {
                            b.roleRecursionName(roleRecursionName.asString());
                        }
                    }

                    identityMappingBuilder.map(b.build());
                }
            }

            ModelNode filterNameNode = IdentityMappingObjectDefinition.FILTER_NAME.resolveModelAttribute(context, identityMappingNode);
            if (filterNameNode.isDefined()) {
                identityMappingBuilder.setFilterName(filterNameNode.asString());
            }

            ModelNode iteratorFilterNode = IdentityMappingObjectDefinition.ITERATOR_FILTER.resolveModelAttribute(context, identityMappingNode);
            if (iteratorFilterNode.isDefined()) {
                identityMappingBuilder.setIteratorFilter(iteratorFilterNode.asString());
            }

            ModelNode newIdentityParentDnNode = IdentityMappingObjectDefinition.NEW_IDENTITY_PARENT_DN.resolveModelAttribute(context, identityMappingNode);
            if (newIdentityParentDnNode.isDefined()) {
                try {
                    identityMappingBuilder.setNewIdentityParent(new LdapName(newIdentityParentDnNode.asString()));
                } catch (InvalidNameException e) {
                    throw new OperationFailedException(e);
                }
            }

            ModelNode newIdentityAttributesNode = IdentityMappingObjectDefinition.NEW_IDENTITY_ATTRIBUTES.resolveModelAttribute(context, identityMappingNode);

            if (newIdentityAttributesNode.isDefined()) {
                Attributes attributes = new BasicAttributes(true);
                for (ModelNode attributeNode : newIdentityAttributesNode.asList()) {
                    ModelNode nameNode = NewIdentityAttributeObjectDefinition.NAME.resolveModelAttribute(context, attributeNode);
                    ModelNode valuesNode = NewIdentityAttributeObjectDefinition.VALUE.resolveModelAttribute(context, attributeNode);

                    if (valuesNode.getType() == ModelType.LIST) {
                        BasicAttribute listAttribute = new BasicAttribute(nameNode.asString());
                        for (ModelNode valueNode : valuesNode.asList()) {
                            listAttribute.add(valueNode.asString());
                        }
                        attributes.put(listAttribute);
                    } else {
                        attributes.put(new BasicAttribute(nameNode.asString(), valuesNode.asString()));
                    }
                }
                identityMappingBuilder.setNewIdentityAttributes(attributes);
            }

            identityMappingBuilder.build();
        }

    }

}
