/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.security.CredentialReference.CREDENTIAL_REFERENCE;
import static org.jboss.as.controller.security.CredentialReference.REJECT_CREDENTIAL_REFERENCE_WITH_BOTH_STORE_AND_CLEAR_TEXT;
import static org.wildfly.extension.elytron.DistributedRealmDefinition.EMIT_EVENTS;
import static org.wildfly.extension.elytron.DistributedRealmDefinition.IGNORE_UNAVAILABLE_REALMS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AGGREGATE_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ALGORITHM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AUTHORIZATION_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AUTHORIZATION_REALMS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AUTOFLUSH;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.BCRYPT_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CERTIFICATE_AUTHORITY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LISTS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CREDENTIAL_STORE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.DISTRIBUTED_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILESYSTEM_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILE_AUDIT_LOG;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FROM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HASH_CHARSET;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HASH_ENCODING;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.JDBC_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.KEY_STORE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.KEY_STORE_ALIAS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.LDAP_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MAPPED_ROLE_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MODULAR_CRYPT_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PERIODIC_ROTATING_FILE_AUDIT_LOG;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROPERTIES_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ROLE_MAP;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SALTED_SIMPLE_DIGEST_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SALT_ENCODING;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SCRAM_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECRET_KEY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_DOMAIN;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SIMPLE_DIGEST_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SIZE_ROTATING_FILE_AUDIT_LOG;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SYNCHRONIZED;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TO;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TRUSTED_VIRTUAL_SECURITY_DOMAINS;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_10_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_11_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_12_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_13_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_14_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_15_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_15_1_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_16_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_17_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_18_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_1_2_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_2_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_3_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_4_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_5_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_6_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_7_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_8_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_9_0_0;
import static org.wildfly.extension.elytron.JdbcRealmDefinition.PrincipalQueryAttributes.PRINCIPAL_QUERIES;
import static org.wildfly.extension.elytron.JdbcRealmDefinition.PrincipalQueryAttributes.PRINCIPAL_QUERY;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.transform.ExtensionTransformerRegistration;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.description.AttributeConverter;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.security.password.interfaces.ScramDigestPassword;
import org.wildfly.security.x500.cert.acme.CertificateAuthority;

/**
 * Registers transformers for the elytron subsystem.
 *
 * @author Brian Stansberry
 */
public final class ElytronSubsystemTransformers implements ExtensionTransformerRegistration {

    @Override
    public String getSubsystemName() {
        return ElytronExtension.SUBSYSTEM_NAME;
    }

    @Override
    public void registerTransformers(SubsystemTransformerRegistration registration) {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedSubystemInstance(registration.getCurrentSubsystemVersion());

        // 18.0.0 (WildFly 29) to 17.0.0 (WildFly 28)
        from18(chainedBuilder);
        // 17.0.0 (WildFly 28) to 16.0.0 (WildFly 27)
        from17(chainedBuilder);
        // 16.0.0 (WildFly 27) to 15.1.0 (WildFly 26.1)
        from16(chainedBuilder);
        // 15.1.0 (WildFly 26.1) to 15.0.0 (WildFly 26)
        from15_1(chainedBuilder);
        // 15.0.0 (WildFly 26) to 14.0.0 (WildFly 25)
        from15(chainedBuilder);
        // 14.0.0 (WildFly 24) to 13.0.0 (WildFly 23)
        from14(chainedBuilder);
        // 13.0.0 (WildFly 23) to 12.0.0 (WildFly 22)
        from13(chainedBuilder);
        // 12.0.0 (WildFly 22) to 11.0.0 (WildFly 21)
        from12(chainedBuilder);
        // 11.0.0 (WildFly 21) to 10.0.0 (WildFly 20)
        from11(chainedBuilder);
        // 10.0.0 (WildFly 20) to 9.0.0 (WildFly 19)
        from10(chainedBuilder);
        // 9.0.0 (WildFly 19) to 8.0.0 (WildFly 18)
        from9(chainedBuilder);
        // 8.0.0 (WildFly 18) to 7.0.0 (WildFly 17)
        from8(chainedBuilder);
        // 7.0.0 (WildFly 17) to 6.0.0 (WildFly 16)
        from7(chainedBuilder);
        // 6.0.0 (WildFly 16) to 5.0.0 (WildFly 15)
        from6(chainedBuilder);
        // 5.0.0 (WildFly 15) to 4.0.0 (WildFly 14)
        from5(chainedBuilder);
        // 4.0.0 (WildFly 14) to 3.0.0 (WildFly 13)
        from4(chainedBuilder);
        // 3.0.0 (WildFly 13) to 2.0.0 (WildFly 12)
        from3(chainedBuilder);
        // 2.0.0 (WildFly 12) to 1.2.0, (WildFly 11 and EAP 7.1.0)
        from2(chainedBuilder);

        chainedBuilder.buildAndRegister(registration, new ModelVersion[] { ELYTRON_17_0_0, ELYTRON_16_0_0, ELYTRON_15_1_0, ELYTRON_15_0_0, ELYTRON_14_0_0, ELYTRON_13_0_0, ELYTRON_12_0_0, ELYTRON_11_0_0, ELYTRON_10_0_0, ELYTRON_9_0_0,
                ELYTRON_8_0_0, ELYTRON_7_0_0, ELYTRON_6_0_0, ELYTRON_5_0_0, ELYTRON_4_0_0, ELYTRON_3_0_0, ELYTRON_2_0_0, ELYTRON_1_2_0 });
    }

    private static void from18(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_18_0_0, ELYTRON_17_0_0);

        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.FILE_AUDIT_LOG))
        .getAttributeBuilder()
        .addRejectCheck(RejectAttributeChecker.DEFINED, ElytronDescriptionConstants.ENCODING)
        .setDiscard(DiscardAttributeChecker.UNDEFINED, AuditResourceDefinitions.ENCODING)
        .end();
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.PERIODIC_ROTATING_FILE_AUDIT_LOG))
        .getAttributeBuilder()
        .addRejectCheck(RejectAttributeChecker.DEFINED, ElytronDescriptionConstants.ENCODING)
        .setDiscard(DiscardAttributeChecker.UNDEFINED, AuditResourceDefinitions.ENCODING)
        .end();
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.SIZE_ROTATING_FILE_AUDIT_LOG))
        .getAttributeBuilder()
        .addRejectCheck(RejectAttributeChecker.DEFINED, ElytronDescriptionConstants.ENCODING)
        .setDiscard(DiscardAttributeChecker.UNDEFINED, AuditResourceDefinitions.ENCODING)
        .end();
        builder.addChildResource(PathElement.pathElement(DISTRIBUTED_REALM))
        .getAttributeBuilder()
        .setDiscard(DiscardAttributeChecker.UNDEFINED, IGNORE_UNAVAILABLE_REALMS)
        .setDiscard(DiscardAttributeChecker.UNDEFINED, EMIT_EVENTS)
        .addRejectCheck(new RejectAttributeChecker.SimpleRejectAttributeChecker(ModelNode.TRUE), IGNORE_UNAVAILABLE_REALMS)
        .addRejectCheck(RejectAttributeChecker.DEFINED, EMIT_EVENTS);
    }

    private static void from17(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_17_0_0, ELYTRON_16_0_0);
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.VIRTUAL_SECURITY_DOMAIN));
        builder.addChildResource(PathElement.pathElement(SECURITY_DOMAIN))
                .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.DEFINED, TRUSTED_VIRTUAL_SECURITY_DOMAINS)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, TRUSTED_VIRTUAL_SECURITY_DOMAINS);

        builder.addChildResource(PathElement.pathElement(MAPPED_ROLE_MAPPER))
                .getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, FROM)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, TO)
                .setValueConverter(MAPPED_ROLE_MAPPER_CONVERTER, ROLE_MAP);
    }

    private static void from16(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_16_0_0, ELYTRON_15_1_0);
        builder.addChildResource(PathElement.pathElement(FILESYSTEM_REALM))
                .getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, KEY_STORE)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, KEY_STORE_ALIAS)
                .addRejectCheck(RejectAttributeChecker.DEFINED, KEY_STORE)
                .addRejectCheck(RejectAttributeChecker.DEFINED, KEY_STORE_ALIAS);
    }

    private static void from15_1(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_15_1_0, ELYTRON_15_0_0);

        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.KEY_STORE))
        .addOperationTransformationOverride(ElytronDescriptionConstants.READ_ALIAS)
        .setDiscard(DiscardAttributeChecker.UNDEFINED, ModifiableKeyStoreDecorator.ReadAliasHandler.VERBOSE)
        .addRejectCheck(new RejectAttributeChecker.SimpleRejectAttributeChecker(ModelNode.FALSE), ElytronDescriptionConstants.VERBOSE)
        .end();

        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.KEY_STORE))
        .addOperationTransformationOverride(ElytronDescriptionConstants.READ_ALIASES)
        .setDiscard(DiscardAttributeChecker.UNDEFINED, ModifiableKeyStoreDecorator.ReadAliasesHandler.VERBOSE)
        .setDiscard(DiscardAttributeChecker.UNDEFINED, ModifiableKeyStoreDecorator.ReadAliasesHandler.RECURSIVE)
        .addRejectCheck(new RejectAttributeChecker.SimpleRejectAttributeChecker(ModelNode.TRUE), ElytronDescriptionConstants.VERBOSE)
        .addRejectCheck(new RejectAttributeChecker.SimpleRejectAttributeChecker(ModelNode.TRUE), ElytronDescriptionConstants.RECURSIVE)
        .end();

        builder.addChildResource(PathElement.pathElement(FILESYSTEM_REALM))
                .getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, CREDENTIAL_STORE)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, SECRET_KEY)
                .addRejectCheck(RejectAttributeChecker.DEFINED, CREDENTIAL_STORE)
                .addRejectCheck(RejectAttributeChecker.DEFINED, SECRET_KEY);
    }

    private static void from15(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_15_0_0, ELYTRON_14_0_0);
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.JAAS_REALM));

    }

    private static void from14(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_14_0_0, ELYTRON_13_0_0);
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.SERVER_SSL_SNI_CONTEXT))
                .getAttributeBuilder()
                .addRejectCheck(new RejectAttributeChecker.DefaultRejectAttributeChecker() {
                    @Override
                    protected boolean rejectAttribute(PathAddress address, String attributeName, ModelNode value, TransformationContext context) {
                        if (value.isDefined()) {
                            for (String hostname : value.keys()) {
                                // character '^' was not allowed in older versions
                                if (hostname.contains("^")) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    }

                    @Override
                    public String getRejectionLogMessage(Map<String, ModelNode> attributes) {
                        return ROOT_LOGGER.hostContextMapHostnameContainsCaret().getMessage();
                    }
                }, ElytronDescriptionConstants.HOST_CONTEXT_MAP);
        builder.addChildResource(PathElement.pathElement(PROPERTIES_REALM))
                .getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, HASH_ENCODING)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, HASH_CHARSET)
                .addRejectCheck(RejectAttributeChecker.DEFINED, HASH_ENCODING)
                .addRejectCheck(RejectAttributeChecker.DEFINED, HASH_CHARSET);
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.SERVER_SSL_CONTEXT))
                .getAttributeBuilder()
                .addRejectCheck(new RejectAttributeChecker.ListRejectAttributeChecker(new RejectAttributeChecker.SimpleRejectAttributeChecker( new ModelNode(ElytronDescriptionConstants.SSL_V2_HELLO))), ElytronDescriptionConstants.PROTOCOLS)
                .end();
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.CLIENT_SSL_CONTEXT))
                .getAttributeBuilder()
                .addRejectCheck(new RejectAttributeChecker.ListRejectAttributeChecker(new RejectAttributeChecker.SimpleRejectAttributeChecker( new ModelNode(ElytronDescriptionConstants.SSL_V2_HELLO))), ElytronDescriptionConstants.PROTOCOLS)
                .end();
        builder.addChildResource(PathElement.pathElement(FILESYSTEM_REALM))
                .getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, HASH_ENCODING)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, HASH_CHARSET)
                .addRejectCheck(RejectAttributeChecker.DEFINED, HASH_ENCODING)
                .addRejectCheck(RejectAttributeChecker.DEFINED, HASH_CHARSET);

        builder.addChildResource(PathElement.pathElement(JDBC_REALM))
                .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.DEFINED, HASH_CHARSET)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, HASH_CHARSET);

        builder.addChildResource(PathElement.pathElement(LDAP_REALM))
                .getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, HASH_CHARSET)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, HASH_ENCODING)
                .addRejectCheck(RejectAttributeChecker.DEFINED, HASH_CHARSET)
                .addRejectCheck(RejectAttributeChecker.DEFINED, HASH_ENCODING);
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.TRUST_MANAGER))
                .getAttributeBuilder()
                .addRejectCheck(REJECT_IF_MULTIPLE_CERTIFICATE_REVOCATION_LISTS, ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LISTS)
                .addRename(ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LISTS, ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LIST)
                .setValueConverter(CERTIFICATE_REVOCATION_LIST_CONVERTER, ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LISTS)
                .end();
    }

    private static void from13(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_13_0_0, ELYTRON_12_0_0);
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.EXPRESSION, ElytronDescriptionConstants.ENCRYPTION));
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.SECRET_KEY_CREDENTIAL_STORE));
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.CREDENTIAL_STORE))
            .getAttributeBuilder()
            .addRename(ElytronDescriptionConstants.PATH, ElytronDescriptionConstants.LOCATION)
            .end();
    }

    private static void from12(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_12_0_0, ELYTRON_11_0_0);
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.KEY_MANAGER))
                .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.DEFINED, ElytronDescriptionConstants.GENERATE_SELF_SIGNED_CERTIFICATE_HOST)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, SSLDefinitions.GENERATE_SELF_SIGNED_CERTIFICATE_HOST)
                .end();


        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.CASE_PRINCIPAL_TRANSFORMER));
    }

    private static void from11(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_11_0_0, ELYTRON_10_0_0);
        builder.rejectChildResource(PathElement.pathElement(DISTRIBUTED_REALM));
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.FAILOVER_REALM));
    }

    private static void from10(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_10_0_0, ELYTRON_9_0_0);
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION))
                .getAttributeBuilder()
                .addRejectCheck(REJECT_CREDENTIAL_REFERENCE_WITH_BOTH_STORE_AND_CLEAR_TEXT, CREDENTIAL_REFERENCE)
                .end();
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.KEY_STORE))
                .getAttributeBuilder()
                .addRejectCheck(REJECT_CREDENTIAL_REFERENCE_WITH_BOTH_STORE_AND_CLEAR_TEXT, CREDENTIAL_REFERENCE)
                .end();
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.KEY_MANAGER))
                .getAttributeBuilder()
                .addRejectCheck(REJECT_CREDENTIAL_REFERENCE_WITH_BOTH_STORE_AND_CLEAR_TEXT, CREDENTIAL_REFERENCE)
                .end();
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.CREDENTIAL_STORE))
                .getAttributeBuilder()
                .addRejectCheck(REJECT_CREDENTIAL_REFERENCE_WITH_BOTH_STORE_AND_CLEAR_TEXT, CREDENTIAL_REFERENCE)
                .end();
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.DIR_CONTEXT))
                .getAttributeBuilder()
                .addRejectCheck(REJECT_CREDENTIAL_REFERENCE_WITH_BOTH_STORE_AND_CLEAR_TEXT, CREDENTIAL_REFERENCE)
                .end();
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT))
                .getAttributeBuilder()
                .addRejectCheck(REJECT_CREDENTIAL_REFERENCE_WITH_BOTH_STORE_AND_CLEAR_TEXT, CREDENTIAL_REFERENCE)
                .end();
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.SECURITY_DOMAIN))
                .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.DEFINED, ElytronDescriptionConstants.ROLE_DECODER)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, DomainDefinition.ROLE_DECODER)
                .end();
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.SOURCE_ADDRESS_ROLE_DECODER));
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.AGGREGATE_ROLE_DECODER));
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.REGEX_ROLE_MAPPER));
    }

    private static void from9(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_9_0_0, ELYTRON_8_0_0);
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION))
                .getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.ALWAYS, AuthenticationClientDefinitions.WEBSERVICES);
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.SERVER_SSL_CONTEXT))
                .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.DEFINED, ElytronDescriptionConstants.CIPHER_SUITE_NAMES)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, SSLDefinitions.CIPHER_SUITE_NAMES)
                .end();
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.CLIENT_SSL_CONTEXT))
                .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.DEFINED, ElytronDescriptionConstants.CIPHER_SUITE_NAMES)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, SSLDefinitions.CIPHER_SUITE_NAMES)
                .end();
    }

    private static void from8(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_8_0_0, ELYTRON_7_0_0);
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.SECURITY_DOMAIN))
                .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.DEFINED, ElytronDescriptionConstants.EVIDENCE_DECODER)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, DomainDefinition.EVIDENCE_DECODER)
                .end();
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.X500_SUBJECT_EVIDENCE_DECODER));
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.X509_SUBJECT_ALT_NAME_EVIDENCE_DECODER));
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.CUSTOM_EVIDENCE_DECODER));
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.AGGREGATE_EVIDENCE_DECODER));
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY));
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.SYSLOG_AUDIT_LOG))
                .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.DEFINED, ElytronDescriptionConstants.SYSLOG_FORMAT)
                .addRejectCheck(RejectAttributeChecker.DEFINED, ElytronDescriptionConstants.RECONNECT_ATTEMPTS)
                .setDiscard(DiscardAttributeChecker.DEFAULT_VALUE, ElytronDescriptionConstants.SYSLOG_FORMAT, ElytronDescriptionConstants.RECONNECT_ATTEMPTS)
                .end();
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT))
                .getAttributeBuilder()
                .addRejectCheck(new RejectAttributeChecker.DefaultRejectAttributeChecker() {

                    @Override
                    protected boolean rejectAttribute(PathAddress address, String attributeName, ModelNode value, TransformationContext context) {
                        // only 'LetsEncrypt' was allowed in older versions
                        return value.isDefined() && !value.asString().equalsIgnoreCase(CertificateAuthority.LETS_ENCRYPT.getName());
                    }
                    @Override
                    public String getRejectionLogMessage(Map<String, ModelNode> attributes) {
                        return ROOT_LOGGER.invalidAttributeValue(CERTIFICATE_AUTHORITY).getMessage();
                    }
                }, ElytronDescriptionConstants.CERTIFICATE_AUTHORITY);
        builder.addChildResource(PathElement.pathElement(AGGREGATE_REALM))
                .getAttributeBuilder()
                .addRejectCheck(REJECT_IF_MULTIPLE_AUTHORIZATION_REALMS, AUTHORIZATION_REALMS)
                .setValueConverter(ONE_AUTHORIZATION_REALMS, AUTHORIZATION_REALMS)
                .addRename(AUTHORIZATION_REALMS, AUTHORIZATION_REALM)
                .addRejectCheck(RejectAttributeChecker.DEFINED, PRINCIPAL_TRANSFORMER)
                .end();
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.TRUST_MANAGER))
                .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.DEFINED, ElytronDescriptionConstants.OCSP)
                .setValueConverter(MAXIMUM_CERT_PATH_CONVERTER, ElytronDescriptionConstants.MAXIMUM_CERT_PATH, ElytronDescriptionConstants.CERTIFICATE_REVOCATION_LIST)
                .addRejectCheck(new RejectAttributeChecker.SimpleRejectAttributeChecker(ModelNode.TRUE), ElytronDescriptionConstants.ONLY_LEAF_CERT)
                .addRejectCheck(new RejectAttributeChecker.SimpleRejectAttributeChecker(ModelNode.TRUE), ElytronDescriptionConstants.SOFT_FAIL)
                .setDiscard(DiscardAttributeChecker.ALWAYS, ElytronDescriptionConstants.ONLY_LEAF_CERT)
                .setDiscard(DiscardAttributeChecker.ALWAYS, ElytronDescriptionConstants.SOFT_FAIL)
                .end();
    }

    private static void from7(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_7_0_0, ELYTRON_6_0_0);
        Map<String, RejectAttributeChecker> keyMapperChecker = new HashMap<>();
        keyMapperChecker.put(HASH_ENCODING, RejectAttributeChecker.DEFINED);
        keyMapperChecker.put(SALT_ENCODING, RejectAttributeChecker.DEFINED);
        Map<String, RejectAttributeChecker> principalQueryCheckers = new HashMap<>();
        principalQueryCheckers.put(BCRYPT_MAPPER, new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(keyMapperChecker));
        principalQueryCheckers.put(SALTED_SIMPLE_DIGEST_MAPPER, new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(keyMapperChecker));
        principalQueryCheckers.put(SIMPLE_DIGEST_MAPPER, new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(keyMapperChecker));
        principalQueryCheckers.put(SCRAM_MAPPER, new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(keyMapperChecker));
        principalQueryCheckers.put(MODULAR_CRYPT_MAPPER, RejectAttributeChecker.DEFINED);
        builder.addChildResource(PathElement.pathElement(JDBC_REALM))
                .getAttributeBuilder()
                .addRejectCheck(new RejectAttributeChecker.ListRejectAttributeChecker(
                        new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(principalQueryCheckers)
                ), PRINCIPAL_QUERY);
    }

    private static void from6(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_6_0_0, ELYTRON_5_0_0);
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.KEY_STORE))
            .getAttributeBuilder()
            .addRejectCheck(RejectAttributeChecker.UNDEFINED, ElytronDescriptionConstants.TYPE)
            .end();
    }

    private static void from5(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_5_0_0, ELYTRON_4_0_0);
        builder.getAttributeBuilder()
            .setDiscard(DiscardAttributeChecker.ALWAYS, ElytronDefinition.REGISTER_JASPI_FACTORY)
            .addRejectCheck(RejectAttributeChecker.DEFINED, ElytronDescriptionConstants.DEFAULT_SSL_CONTEXT)
            .setDiscard(DiscardAttributeChecker.UNDEFINED, ElytronDefinition.DEFAULT_SSL_CONTEXT)
            .end();
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.JASPI_CONFIGURATION));
        transformAutoFlush(builder, FILE_AUDIT_LOG);
        transformAutoFlush(builder, PERIODIC_ROTATING_FILE_AUDIT_LOG);
        transformAutoFlush(builder, SIZE_ROTATING_FILE_AUDIT_LOG);
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.SERVER_SSL_SNI_CONTEXT));
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.TOKEN_REALM))
            .getAttributeBuilder()
            .addRejectCheck(new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(Collections.singletonMap(ElytronDescriptionConstants.HOST_NAME_VERIFICATION_POLICY,
                RejectAttributeChecker.DEFINED)), ElytronDescriptionConstants.JWT)
            .addRejectCheck(new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(Collections.singletonMap(ElytronDescriptionConstants.SSL_CONTEXT,
                RejectAttributeChecker.DEFINED)), ElytronDescriptionConstants.JWT)
            .addRejectCheck(new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(Collections.singletonMap(ElytronDescriptionConstants.KEY_MAP,
                RejectAttributeChecker.DEFINED)), ElytronDescriptionConstants.JWT)
            .end();
    }

    private static void transformAutoFlush(ResourceTransformationDescriptionBuilder builder, final String resourceName) {
        builder
            .addChildResource(PathElement.pathElement(resourceName))
            .getAttributeBuilder()
            .setDiscard(DISCARD_IF_EQUALS_SYNCHRONIZED, AuditResourceDefinitions.AUTOFLUSH)
            .addRejectCheck(REJECT_IF_DIFFERENT_FROM_SYNCHRONIZED, AuditResourceDefinitions.AUTOFLUSH)
            .end();
    }

    private static void from4(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_4_0_0, ELYTRON_3_0_0);
        builder
                .addChildResource(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM))
                .getAttributeBuilder()
                .addRejectCheck(new RejectAttributeChecker.ListRejectAttributeChecker(
                        new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(Collections.singletonMap(SCRAM_MAPPER,
                                new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(Collections.singletonMap(ALGORITHM,
                                        new RejectAttributeChecker.SimpleRejectAttributeChecker(new ModelNode(ScramDigestPassword.ALGORITHM_SCRAM_SHA_384))
                                ))
                        ))
                ), PRINCIPAL_QUERIES)
                .addRejectCheck(new RejectAttributeChecker.ListRejectAttributeChecker(
                        new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(Collections.singletonMap(SCRAM_MAPPER,
                                new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(Collections.singletonMap(ALGORITHM,
                                        new RejectAttributeChecker.SimpleRejectAttributeChecker(new ModelNode(ScramDigestPassword.ALGORITHM_SCRAM_SHA_512))
                                ))
                        ))
                ), PRINCIPAL_QUERIES)
                .end();
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT));
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.MAPPED_ROLE_MAPPER));
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.CUSTOM_SECURITY_EVENT_LISTENER));
    }

    private static void from3(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_3_0_0, ELYTRON_2_0_0);
        builder.discardChildResource(PathElement.pathElement(ElytronDescriptionConstants.PERMISSION_SET));
        builder
                .addChildResource(PathElement.pathElement(ElytronDescriptionConstants.SIMPLE_PERMISSION_MAPPER))
                .getAttributeBuilder()
                .setValueConverter(MAPPING_PERMISSION_SET_CONVERTER, ElytronDescriptionConstants.PERMISSION_MAPPINGS)
                .end();
        builder
                .addChildResource(PathElement.pathElement(ElytronDescriptionConstants.CONSTANT_PERMISSION_MAPPER))
                .getAttributeBuilder()
                .addRename(ElytronDescriptionConstants.PERMISSION_SETS, ElytronDescriptionConstants.PERMISSIONS)
                .setValueConverter(CONSTANT_PERMISSION_SET_CONVERTER, ElytronDescriptionConstants.PERMISSION_SETS)
                .end();
    }

    private static void from2(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_2_0_0, ELYTRON_1_2_0);

        // Discard new "fail-cache" if it's undefined or has a value same as old unconfigurable behavior; reject otherwise
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY))
            .getAttributeBuilder()
            .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(ModelNode.ZERO), KerberosSecurityFactoryDefinition.FAIL_CACHE)
            .addRejectCheck(RejectAttributeChecker.DEFINED, KerberosSecurityFactoryDefinition.FAIL_CACHE);
    }

    // converting permission-set reference back to inline permissions
    private static final AttributeConverter MAPPING_PERMISSION_SET_CONVERTER = new AttributeConverter.DefaultAttributeConverter() {
        @Override
        protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            if (attributeValue.isDefined()) {
                for (ModelNode permissionMapping : attributeValue.asList()) {
                    if (permissionMapping.hasDefined(ElytronDescriptionConstants.PERMISSION_SETS)) {
                        ModelNode permissionSets = permissionMapping.get(ElytronDescriptionConstants.PERMISSION_SETS);
                        for (ModelNode permissionSet : permissionSets.asList()) {
                            ModelNode permissionSetName = permissionSet.get(ElytronDescriptionConstants.PERMISSION_SET);
                            PathAddress permissionSetAddress = address.getParent().append(ElytronDescriptionConstants.PERMISSION_SET, permissionSetName.asString());
                            ModelNode permissions = context.readResourceFromRoot(permissionSetAddress).getModel().get(ElytronDescriptionConstants.PERMISSIONS);
                            for (ModelNode permission: permissions.asList()) {
                                permissionMapping.get(ElytronDescriptionConstants.PERMISSIONS).add(permission);
                            }
                        }
                        permissionMapping.remove(ElytronDescriptionConstants.PERMISSION_SETS);
                    }
                }
            }
        }
    };

    // converting permission-set reference back to inline permissions
    private static final AttributeConverter CONSTANT_PERMISSION_SET_CONVERTER = new AttributeConverter.DefaultAttributeConverter() {
        @Override
        protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            if (attributeValue.isDefined()) {
                ModelNode allPermissions = new ModelNode();
                for (ModelNode permissionSet : attributeValue.asList()) {
                    ModelNode permissionSetName = permissionSet.get(ElytronDescriptionConstants.PERMISSION_SET);
                    PathAddress permissionSetAddress = address.getParent().append(ElytronDescriptionConstants.PERMISSION_SET, permissionSetName.asString());
                    ModelNode permissions = context.readResourceFromRoot(permissionSetAddress).getModel().get(ElytronDescriptionConstants.PERMISSIONS);
                    for (ModelNode permission: permissions.asList()) {
                        allPermissions.add(permission);
                    }
                }
                attributeValue.set(allPermissions);
            }
        }
    };

    // Moves maximum-cert-path from trust-manager back to certificate-revocation-list
    private static final AttributeConverter MAXIMUM_CERT_PATH_CONVERTER = new AttributeConverter.DefaultAttributeConverter() {
        Integer maxCertPath;

        @Override
        protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            if (attributeName.equals(ElytronDescriptionConstants.MAXIMUM_CERT_PATH)) {
                maxCertPath = attributeValue.asIntOrNull();
                attributeValue.clear();
            } else {
                if (attributeValue.isDefined()) {
                    if (maxCertPath != null) {
                        attributeValue.set(ElytronDescriptionConstants.MAXIMUM_CERT_PATH, new ModelNode(maxCertPath));
                    } else {
                        attributeValue.set(ElytronDescriptionConstants.MAXIMUM_CERT_PATH, new ModelNode(5));
                    }
                }
            }
        }
    };

    private static final AttributeConverter CERTIFICATE_REVOCATION_LIST_CONVERTER = new AttributeConverter.DefaultAttributeConverter() {
        @Override
        protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            // If we reached this point, we know the attribute was not rejected so CERTIFICATE_REVOCATION_LISTS can have at most one CRL.
            if (attributeValue.isDefined()) {
                List<ModelNode> crls = attributeValue.asListOrEmpty();
                if (crls.size() == 1) {
                    ModelNode singleCrl = crls.get(0);
                    attributeValue.clear();
                    attributeValue.get(ElytronDescriptionConstants.PATH).set(singleCrl.get(ElytronDescriptionConstants.PATH));
                    attributeValue.get(ElytronDescriptionConstants.RELATIVE_TO).set(singleCrl.get(ElytronDescriptionConstants.RELATIVE_TO));
                } else if (crls.isEmpty()) {
                    attributeValue.clear();
                }
            }
        }

    };

    /* [WFCORE-6244] converts list of objects:              [{"from"=STRING, "to"=[STRING,STRING,...]}...]
     * back to a direct string to string-list mapping:      {STRING=[STRING,STRING,...],...}
     *
     * XML schema remains unchanged. */
    private static final AttributeConverter MAPPED_ROLE_MAPPER_CONVERTER = new AttributeConverter.DefaultAttributeConverter() {
        @Override
        protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            if (attributeValue.isDefined() && attributeValue.getType() == ModelType.LIST) {
                ModelNode oldRoleMappingMap = new ModelNode();
                for (ModelNode mapping : attributeValue.asList()) {
                    oldRoleMappingMap.add(mapping.get(FROM).asString(), mapping.get(TO));
                }

                attributeValue.set(oldRoleMappingMap.asObject());
            }
        }
    };

    private static final RejectAttributeChecker REJECT_IF_DIFFERENT_FROM_SYNCHRONIZED = new RejectAttributeChecker.DefaultRejectAttributeChecker() {
        @Override
        public String getRejectionLogMessage(Map<String, ModelNode> attributes) {
            return ROOT_LOGGER.unableToTransformTornAttribute(AUTOFLUSH, SYNCHRONIZED);
        }

        @Override
        protected boolean rejectAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            if (attributeValue.isDefined()) {
                boolean synced = context.readResourceFromRoot(address).getModel().get(SYNCHRONIZED).asBoolean();
                return synced != attributeValue.asBoolean();
            }
            return false;
        }
    };

    private static final DiscardAttributeChecker DISCARD_IF_EQUALS_SYNCHRONIZED = new DiscardAttributeChecker.DefaultDiscardAttributeChecker() {
        @Override
        protected boolean isValueDiscardable(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            boolean synced = context.readResourceFromRoot(address).getModel().get(SYNCHRONIZED).asBoolean();
            return synced == attributeValue.asBoolean();
        }
    };

    private static final RejectAttributeChecker REJECT_IF_MULTIPLE_AUTHORIZATION_REALMS = new RejectAttributeChecker.DefaultRejectAttributeChecker() {

        @Override
        protected boolean rejectAttribute(PathAddress address, String attributeName, ModelNode value, TransformationContext context) {
            // reject if there is more than one authorization realm specified
            if (value.isDefined()) {
                List<ModelNode> values = value.asList();
                return (values.size() > 1);
            }
            return false;
        }
        @Override
        public String getRejectionLogMessage(Map<String, ModelNode> attributes) {
            return ROOT_LOGGER.invalidAttributeValue(AUTHORIZATION_REALMS).getMessage();
        }
    };

    private static final RejectAttributeChecker REJECT_IF_MULTIPLE_CERTIFICATE_REVOCATION_LISTS = new RejectAttributeChecker.DefaultRejectAttributeChecker() {

        @Override
        protected boolean rejectAttribute(PathAddress address, String attributeName, ModelNode value, TransformationContext context) {
            // reject if there is more than one certificate revocation list specified
            if (value.isDefined()) {
                List<ModelNode> values = value.asList();
                return (values.size() > 1);
            }
            return false;
        }
        @Override
        public String getRejectionLogMessage(Map<String, ModelNode> attributes) {
            return ROOT_LOGGER.invalidAttributeValue(CERTIFICATE_REVOCATION_LISTS).getMessage();
        }
    };

    private static final AttributeConverter ONE_AUTHORIZATION_REALMS = new AttributeConverter.DefaultAttributeConverter() {
        @Override
        protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            if (attributeValue.isDefined()) {
                /*
                 * If we reach this point we know the attribute was not rejected so AUTHORIZATION_REALMS can only have one value.
                 */
                String authorizationRealm = context.readResourceFromRoot(address).getModel().get(AUTHORIZATION_REALMS).asList().get(0).asString();
                attributeValue.set(authorizationRealm);
            }
        }
    };

}
