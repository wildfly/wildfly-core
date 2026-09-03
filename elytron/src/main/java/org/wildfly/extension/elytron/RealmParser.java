/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.jboss.as.controller.PersistentResourceXMLDescription.decorator;

import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLDescription.PersistentResourceXMLBuilder;
import org.wildfly.extension.elytron.JdbcRealmDefinition.PrincipalQueryAttributes;


/**
 * A parser for the security realm definition.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Tomaz Cerar
 */
class RealmParser {

    @Deprecated
    private final PersistentResourceXMLDescription aggregateRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.AGGREGATE_REALM))
            .addAttributes(AggregateRealmDefinition.ATTRIBUTES)
            .build();
    private final PersistentResourceXMLDescription aggregateRealmParser_8_0 = builder(PathElement.pathElement(ElytronDescriptionConstants.AGGREGATE_REALM))
            .addAttributes(AggregateRealmDefinition.ATTRIBUTES_8_0)
            .build();
    private final PersistentResourceXMLDescription customRealmParser =
            customRealmAttributes(builder(PathElement.pathElement(ElytronDescriptionConstants.CUSTOM_REALM)))
                    .build();
    private final PersistentResourceXMLDescription customModifiableRealmParser =
            customModifiableRealmAttributes(builder(PathElement.pathElement(ElytronDescriptionConstants.CUSTOM_MODIFIABLE_REALM)))
                    .build();
    private final PersistentResourceXMLDescription customRealmParser_19_0_community =
            customRealmAttributes_19_0_community(builder(PathElement.pathElement(ElytronDescriptionConstants.CUSTOM_REALM)))
                    .build();
    private final PersistentResourceXMLDescription customModifiableRealmParser_19_0_community =
            customModifiableRealmAttributes_19_0_community(builder(PathElement.pathElement(ElytronDescriptionConstants.CUSTOM_MODIFIABLE_REALM)))
                    .build();
    private final PersistentResourceXMLDescription identityRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.IDENTITY_REALM))
            .addAttributes(RealmDefinitions.IDENTITY_REALM_ATTRIBUTES)
            .setUseElementsForGroups(false)
            .build();
    @Deprecated
    private final PersistentResourceXMLDescription jdbcRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM))
            .addAttribute(PrincipalQueryAttributes.PRINCIPAL_QUERIES, AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER, AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .build();
    private final PersistentResourceXMLDescription jdbcRealmParser_7_0 =
            jdbcRealmAttributes_7_0(builder(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM)))
                    .build();
    private final PersistentResourceXMLDescription jdbcRealmParser_14_0 =
            jdbcRealmAttributes_14_0(builder(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM)))
                    .build();
    private final PersistentResourceXMLDescription jdbcRealmParser_19_0_community =
            jdbcRealmAttributes_19_0_community(builder(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM)))
                    .build();
    private final PersistentResourceXMLDescription keyStoreRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.KEY_STORE_REALM))
            .addAttribute(KeyStoreRealmDefinition.KEYSTORE)
            .build();
    private final PersistentResourceXMLDescription propertiesRealmParser =
            propertiesRealmAttributes(builder(PathElement.pathElement(ElytronDescriptionConstants.PROPERTIES_REALM)))
                    .build();
    private final PersistentResourceXMLDescription propertiesRealmParser_14_0 =
            propertiesRealmAttributes_14_0(builder(PathElement.pathElement(ElytronDescriptionConstants.PROPERTIES_REALM)))
                    .build();
    private final PersistentResourceXMLDescription propertiesRealmParser_19_0_community =
            propertiesRealmAttributes_19_0_community(builder(PathElement.pathElement(ElytronDescriptionConstants.PROPERTIES_REALM)))
                    .build();
    private final PersistentResourceXMLDescription ldapRealmParser =
            ldapRealmAttributes(builder(PathElement.pathElement(ElytronDescriptionConstants.LDAP_REALM)))
                    .build();
    private final PersistentResourceXMLDescription ldapRealmParser_19_0_community =
            ldapRealmAttributes_19_0_community(builder(PathElement.pathElement(ElytronDescriptionConstants.LDAP_REALM)))
                    .build();
    private final PersistentResourceXMLDescription fileSystemRealmDescription =
            filesystemRealmAttributes(builder(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM)))
                    .build();
    private final PersistentResourceXMLDescription filesystemRealmParser_14_0 =
            filesystemRealmAttributes_14_0(builder(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM)))
                    .build();
    private final PersistentResourceXMLDescription filesystemRealmParser_15_1 =
            filesystemRealmAttributes_15_1(builder(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM)))
                    .build();
    private final PersistentResourceXMLDescription filesystemRealmParser_16 =
            filesystemRealmAttributes_16(builder(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM)))
                    .build();
    private final PersistentResourceXMLDescription filesystemRealmParser_19_0_community =
            filesystemRealmAttributes_19_0_community(builder(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM)))
                    .build();

    private final PersistentResourceXMLDescription tokenRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.TOKEN_REALM))
            .addAttributes(TokenRealmDefinition.ATTRIBUTES)
            .build();
    private final PersistentResourceXMLDescription cachingRealmParser =
            cachingRealmAttributes(builder(PathElement.pathElement(ElytronDescriptionConstants.CACHING_REALM)))
                    .build();
    private final PersistentResourceXMLDescription cachingRealmParser_19_0_community =
            cachingRealmAttributes_19_0_community(builder(PathElement.pathElement(ElytronDescriptionConstants.CACHING_REALM)))
                    .build();
    private final PersistentResourceXMLDescription distributedRealmParser =
            distributedRealmAttributes(builder(PathElement.pathElement(ElytronDescriptionConstants.DISTRIBUTED_REALM)))
                    .build();
    private final PersistentResourceXMLDescription distributedRealmParser_18 =
            distributedRealmAttributes_18(builder(PathElement.pathElement(ElytronDescriptionConstants.DISTRIBUTED_REALM)))
                    .build();
    private final PersistentResourceXMLDescription distributedRealmParser_19_0_community =
            distributedRealmAttributes_19_0_community(builder(PathElement.pathElement(ElytronDescriptionConstants.DISTRIBUTED_REALM)))
                    .build();
    private final PersistentResourceXMLDescription failoverRealmParser =
            failoverRealmAttributes(builder(PathElement.pathElement(ElytronDescriptionConstants.FAILOVER_REALM)))
                    .build();
    private final PersistentResourceXMLDescription failoverRealmParser_19_0_community =
            failoverRealmAttributes_19_0_community(builder(PathElement.pathElement(ElytronDescriptionConstants.FAILOVER_REALM)))
                    .build();
    private final PersistentResourceXMLDescription jaasRealmParser =
            jaasRealmAttributes(builder(PathElement.pathElement(ElytronDescriptionConstants.JAAS_REALM)))
                    .build();
    private final PersistentResourceXMLDescription jaasRealmParser_19_0_community =
            jaasRealmAttributes_19_0_community(builder(PathElement.pathElement(ElytronDescriptionConstants.JAAS_REALM)))
                    .build();

    final PersistentResourceXMLDescription realmParser = decorator(ElytronDescriptionConstants.SECURITY_REALMS)
            .addChild(aggregateRealmParser)
            .addChild(customRealmParser)
            .addChild(customModifiableRealmParser)
            .addChild(identityRealmParser)
            .addChild(jdbcRealmParser)
            .addChild(keyStoreRealmParser)
            .addChild(propertiesRealmParser)
            .addChild(ldapRealmParser)
            .addChild(fileSystemRealmDescription)
            .addChild(tokenRealmParser)
            .addChild(cachingRealmParser)
            .build();

    final PersistentResourceXMLDescription realmParser_7_0 = decorator(ElytronDescriptionConstants.SECURITY_REALMS)
            .addChild(aggregateRealmParser)
            .addChild(customRealmParser)
            .addChild(customModifiableRealmParser)
            .addChild(identityRealmParser)
            .addChild(jdbcRealmParser_7_0)
            .addChild(keyStoreRealmParser)
            .addChild(propertiesRealmParser)
            .addChild(ldapRealmParser)
            .addChild(fileSystemRealmDescription)
            .addChild(tokenRealmParser)
            .addChild(cachingRealmParser)
            .build();

    final PersistentResourceXMLDescription realmParser_8_0 = decorator(ElytronDescriptionConstants.SECURITY_REALMS)
            .addChild(aggregateRealmParser_8_0)
            .addChild(customRealmParser)
            .addChild(customModifiableRealmParser)
            .addChild(identityRealmParser)
            .addChild(jdbcRealmParser_7_0)
            .addChild(keyStoreRealmParser)
            .addChild(propertiesRealmParser)
            .addChild(ldapRealmParser)
            .addChild(fileSystemRealmDescription)
            .addChild(tokenRealmParser)
            .addChild(cachingRealmParser)
            .build();

    final PersistentResourceXMLDescription realmParser_11_0 = decorator(ElytronDescriptionConstants.SECURITY_REALMS)
            .addChild(aggregateRealmParser_8_0)
            .addChild(customRealmParser)
            .addChild(customModifiableRealmParser)
            .addChild(identityRealmParser)
            .addChild(jdbcRealmParser_7_0)
            .addChild(keyStoreRealmParser)
            .addChild(propertiesRealmParser)
            .addChild(ldapRealmParser)
            .addChild(fileSystemRealmDescription)
            .addChild(tokenRealmParser)
            .addChild(cachingRealmParser)
            .addChild(distributedRealmParser)
            .addChild(failoverRealmParser)
            .build();

    final PersistentResourceXMLDescription realmParser_14_0 = decorator(ElytronDescriptionConstants.SECURITY_REALMS)
            .addChild(aggregateRealmParser_8_0)
            .addChild(customRealmParser)
            .addChild(customModifiableRealmParser)
            .addChild(identityRealmParser)
            .addChild(jdbcRealmParser_14_0)
            .addChild(keyStoreRealmParser)
            .addChild(propertiesRealmParser_14_0)
            .addChild(ldapRealmParser)
            .addChild(filesystemRealmParser_14_0)
            .addChild(tokenRealmParser)
            .addChild(cachingRealmParser)
            .addChild(distributedRealmParser)
            .addChild(failoverRealmParser)
            .build();

    final PersistentResourceXMLDescription realmParser_15_0 = decorator(ElytronDescriptionConstants.SECURITY_REALMS)
            .addChild(aggregateRealmParser_8_0)
            .addChild(customRealmParser)
            .addChild(customModifiableRealmParser)
            .addChild(identityRealmParser)
            .addChild(jdbcRealmParser_14_0)
            .addChild(keyStoreRealmParser)
            .addChild(propertiesRealmParser_14_0)
            .addChild(ldapRealmParser)
            .addChild(filesystemRealmParser_14_0)
            .addChild(tokenRealmParser)
            .addChild(cachingRealmParser)
            .addChild(distributedRealmParser)
            .addChild(failoverRealmParser)
            .addChild(jaasRealmParser)
            .build();

    final PersistentResourceXMLDescription realmParser_15_1 = decorator(ElytronDescriptionConstants.SECURITY_REALMS)
            .addChild(aggregateRealmParser_8_0)
            .addChild(customRealmParser)
            .addChild(customModifiableRealmParser)
            .addChild(identityRealmParser)
            .addChild(jdbcRealmParser_14_0)
            .addChild(keyStoreRealmParser)
            .addChild(propertiesRealmParser_14_0)
            .addChild(ldapRealmParser)
            .addChild(filesystemRealmParser_15_1)
            .addChild(tokenRealmParser)
            .addChild(cachingRealmParser)
            .addChild(distributedRealmParser)
            .addChild(failoverRealmParser)
            .addChild(jaasRealmParser)
            .build();

    final PersistentResourceXMLDescription realmParser_16 = decorator(ElytronDescriptionConstants.SECURITY_REALMS)
            .addChild(aggregateRealmParser_8_0)
            .addChild(customRealmParser)
            .addChild(customModifiableRealmParser)
            .addChild(identityRealmParser)
            .addChild(jdbcRealmParser_14_0)
            .addChild(keyStoreRealmParser)
            .addChild(propertiesRealmParser_14_0)
            .addChild(ldapRealmParser)
            .addChild(filesystemRealmParser_16)
            .addChild(tokenRealmParser)
            .addChild(cachingRealmParser)
            .addChild(distributedRealmParser)
            .addChild(failoverRealmParser)
            .addChild(jaasRealmParser)
            .build();

    final PersistentResourceXMLDescription realmParser_18 = decorator(ElytronDescriptionConstants.SECURITY_REALMS)
            .addChild(aggregateRealmParser_8_0)
            .addChild(customRealmParser)
            .addChild(customModifiableRealmParser)
            .addChild(identityRealmParser)
            .addChild(jdbcRealmParser_14_0)
            .addChild(keyStoreRealmParser)
            .addChild(propertiesRealmParser_14_0)
            .addChild(ldapRealmParser)
            .addChild(filesystemRealmParser_16)
            .addChild(tokenRealmParser)
            .addChild(cachingRealmParser)
            .addChild(distributedRealmParser_18)
            .addChild(failoverRealmParser)
            .addChild(jaasRealmParser)
            .build();

    final PersistentResourceXMLDescription realmParser_19_0_community = decorator(ElytronDescriptionConstants.SECURITY_REALMS)
            .addChild(aggregateRealmParser_8_0)
            .addChild(customRealmParser_19_0_community)
            .addChild(customModifiableRealmParser_19_0_community)
            .addChild(identityRealmParser)
            .addChild(jdbcRealmParser_19_0_community)
            .addChild(keyStoreRealmParser)
            .addChild(propertiesRealmParser_19_0_community)
            .addChild(ldapRealmParser_19_0_community)
            .addChild(filesystemRealmParser_19_0_community)
            .addChild(tokenRealmParser)
            .addChild(cachingRealmParser_19_0_community)
            .addChild(distributedRealmParser_19_0_community)
            .addChild(failoverRealmParser_19_0_community)
            .addChild(jaasRealmParser_19_0_community)
            .build();

    RealmParser() {

    }

    // Static helper methods for realm attribute configuration

    // Properties Realm
    static PersistentResourceXMLBuilder propertiesRealmAttributes(PersistentResourceXMLBuilder builder) {
        return builder
                .addAttributes(PropertiesRealmDefinition.GROUPS_ATTRIBUTE)
                .addAttribute(PropertiesRealmDefinition.USERS_PROPERTIES, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT)
                .addAttribute(PropertiesRealmDefinition.GROUPS_PROPERTIES, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT);
    }

    static PersistentResourceXMLBuilder propertiesRealmAttributes_14_0(PersistentResourceXMLBuilder builder) {
        return propertiesRealmAttributes(builder)
                .addAttribute(PropertiesRealmDefinition.HASH_CHARSET)
                .addAttribute(PropertiesRealmDefinition.HASH_ENCODING);
    }

    static PersistentResourceXMLBuilder propertiesRealmAttributes_19_0_community(PersistentResourceXMLBuilder builder) {
        return propertiesRealmAttributes_14_0(builder)
                .addAttribute(RealmDefinitions.BRUTE_FORCE_PROTECTION, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT);
    }

    // Filesystem Realm
    static PersistentResourceXMLBuilder filesystemRealmAttributes(PersistentResourceXMLBuilder builder) {
        return builder
                .addAttribute(FileSystemRealmDefinition.ENCODED)
                .addAttribute(FileSystemRealmDefinition.LEVELS)
                .addAttribute(FileSystemRealmDefinition.PATH)
                .addAttribute(FileSystemRealmDefinition.RELATIVE_TO)
                .setMarshallDefaultValues(true);
    }

    static PersistentResourceXMLBuilder filesystemRealmAttributes_14_0(PersistentResourceXMLBuilder builder) {
        return builder
                .addAttributes(FileSystemRealmDefinition.PATH)
                .addAttribute(FileSystemRealmDefinition.RELATIVE_TO)
                .addAttribute(FileSystemRealmDefinition.LEVELS)
                .addAttribute(FileSystemRealmDefinition.ENCODED)
                .addAttribute(FileSystemRealmDefinition.HASH_ENCODING)
                .addAttribute(FileSystemRealmDefinition.HASH_CHARSET);
    }

    static PersistentResourceXMLBuilder filesystemRealmAttributes_15_1(PersistentResourceXMLBuilder builder) {
        return filesystemRealmAttributes_14_0(builder)
                .addAttributes(FileSystemRealmDefinition.CREDENTIAL_STORE)
                .addAttributes(FileSystemRealmDefinition.SECRET_KEY);
    }

    static PersistentResourceXMLBuilder filesystemRealmAttributes_16(PersistentResourceXMLBuilder builder) {
        return filesystemRealmAttributes_15_1(builder)
                .addAttribute(FileSystemRealmDefinition.KEY_STORE)
                .addAttribute(FileSystemRealmDefinition.KEY_STORE_ALIAS);
    }

    static PersistentResourceXMLBuilder filesystemRealmAttributes_19_0_community(PersistentResourceXMLBuilder builder) {
        return filesystemRealmAttributes_16(builder)
                .addAttribute(RealmDefinitions.BRUTE_FORCE_PROTECTION, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT);
    }

    // LDAP Realm
    static PersistentResourceXMLBuilder ldapRealmAttributes(PersistentResourceXMLBuilder builder) {
        return builder
                .addAttributes(LdapRealmDefinition.ATTRIBUTES);
    }

    static PersistentResourceXMLBuilder ldapRealmAttributes_19_0_community(PersistentResourceXMLBuilder builder) {
        return builder
                .addAttribute(LdapRealmDefinition.IdentityMappingObjectDefinition.OBJECT_DEFINITION, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT)
                .addAttribute(LdapRealmDefinition.DIR_CONTEXT)
                .addAttribute(LdapRealmDefinition.DIRECT_VERIFICATION)
                .addAttribute(LdapRealmDefinition.ALLOW_BLANK_PASSWORD)
                .addAttribute(LdapRealmDefinition.HASH_ENCODING)
                .addAttribute(LdapRealmDefinition.HASH_CHARSET)
                .addAttribute(RealmDefinitions.BRUTE_FORCE_PROTECTION, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT);
    }

    // JDBC Realm
    static PersistentResourceXMLBuilder jdbcRealmAttributes_7_0(PersistentResourceXMLBuilder builder) {
        return builder
                .addAttribute(PrincipalQueryAttributes.PRINCIPAL_QUERIES_7_0, AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER, AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER);
    }

    static PersistentResourceXMLBuilder jdbcRealmAttributes_14_0(PersistentResourceXMLBuilder builder) {
        return jdbcRealmAttributes_7_0(builder)
                .addAttribute(JdbcRealmDefinition.HASH_CHARSET);
    }

    static PersistentResourceXMLBuilder jdbcRealmAttributes_19_0_community(PersistentResourceXMLBuilder builder) {
        return jdbcRealmAttributes_14_0(builder)
                .addAttribute(RealmDefinitions.BRUTE_FORCE_PROTECTION, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT);
    }

    // JAAS Realm
    static PersistentResourceXMLBuilder jaasRealmAttributes(PersistentResourceXMLBuilder builder) {
        return builder
                .addAttributes(JaasRealmDefinition.ATTRIBUTES);
    }

    static PersistentResourceXMLBuilder jaasRealmAttributes_19_0_community(PersistentResourceXMLBuilder builder) {
        return builder
                .addAttribute(JaasRealmDefinition.ENTRY)
                .addAttribute(JaasRealmDefinition.PATH)
                .addAttribute(JaasRealmDefinition.RELATIVE_TO)
                .addAttribute(JaasRealmDefinition.MODULE)
                .addAttribute(JaasRealmDefinition.CALLBACK_HANDLER)
                .addAttribute(RealmDefinitions.BRUTE_FORCE_PROTECTION, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT);
    }

    // Caching Realm
    static PersistentResourceXMLBuilder cachingRealmAttributes(PersistentResourceXMLBuilder builder) {
        return builder
                .addAttributes(CachingRealmDefinition.ATTRIBUTES);
    }

    static PersistentResourceXMLBuilder cachingRealmAttributes_19_0_community(PersistentResourceXMLBuilder builder) {
        return builder
                .addAttribute(CachingRealmDefinition.REALM_NAME)
                .addAttribute(CachingRealmDefinition.MAXIMUM_ENTRIES)
                .addAttribute(CachingRealmDefinition.MAXIMUM_AGE)
                .addAttribute(RealmDefinitions.BRUTE_FORCE_PROTECTION, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT);
    }

    // Distributed Realm
    static PersistentResourceXMLBuilder distributedRealmAttributes(PersistentResourceXMLBuilder builder) {
        return builder
                .addAttribute(DistributedRealmDefinition.REALMS, AttributeParser.STRING_LIST, AttributeMarshaller.STRING_LIST);
    }

    static PersistentResourceXMLBuilder distributedRealmAttributes_18(PersistentResourceXMLBuilder builder) {
        return distributedRealmAttributes(builder)
                .addAttribute(DistributedRealmDefinition.IGNORE_UNAVAILABLE_REALMS)
                .addAttribute(DistributedRealmDefinition.EMIT_EVENTS);
    }

    static PersistentResourceXMLBuilder distributedRealmAttributes_19_0_community(PersistentResourceXMLBuilder builder) {
        return distributedRealmAttributes_18(builder)
                .addAttribute(RealmDefinitions.BRUTE_FORCE_PROTECTION, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT);
    }

    // Failover Realm
    static PersistentResourceXMLBuilder failoverRealmAttributes(PersistentResourceXMLBuilder builder) {
        return builder
                .addAttributes(FailoverRealmDefinition.ATTRIBUTES);
    }

    static PersistentResourceXMLBuilder failoverRealmAttributes_19_0_community(PersistentResourceXMLBuilder builder) {
        return builder
                .addAttribute(FailoverRealmDefinition.DELEGATE_REALM)
                .addAttribute(FailoverRealmDefinition.FAILOVER_REALM)
                .addAttribute(FailoverRealmDefinition.EMIT_EVENTS)
                .addAttribute(RealmDefinitions.BRUTE_FORCE_PROTECTION, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT);
    }

    // Custom Realm
    static PersistentResourceXMLBuilder customRealmAttributes(PersistentResourceXMLBuilder builder) {
        return builder
                .addAttributes(CustomComponentDefinition.ATTRIBUTES)
                .setUseElementsForGroups(false);
    }

    static PersistentResourceXMLBuilder customRealmAttributes_19_0_community(PersistentResourceXMLBuilder builder) {
        return customRealmAttributes(builder)
                .addAttribute(RealmDefinitions.BRUTE_FORCE_PROTECTION, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT);
    }

    // Custom Modifiable Realm
    static PersistentResourceXMLBuilder customModifiableRealmAttributes(PersistentResourceXMLBuilder builder) {
        return builder
                .addAttributes(CustomComponentDefinition.ATTRIBUTES)
                .setUseElementsForGroups(false);
    }

    static PersistentResourceXMLBuilder customModifiableRealmAttributes_19_0_community(PersistentResourceXMLBuilder builder) {
        return customModifiableRealmAttributes(builder)
                .addAttribute(RealmDefinitions.BRUTE_FORCE_PROTECTION, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT);
    }

}
