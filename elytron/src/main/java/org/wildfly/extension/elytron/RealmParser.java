/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.jboss.as.controller.PersistentResourceXMLDescription.decorator;

import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.wildfly.extension.elytron.JdbcRealmDefinition.PrincipalQueryAttributes;


/**
 * A parser for the security realm definition.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Tomaz Cerar
 */
class RealmParser {

    @Deprecated
    private final PersistentResourceXMLDescription aggregateRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.AGGREGATE_REALM), null)
            .addAttributes(AggregateRealmDefinition.ATTRIBUTES)
            .build();
    private final PersistentResourceXMLDescription aggregateRealmParser_8_0 = builder(PathElement.pathElement(ElytronDescriptionConstants.AGGREGATE_REALM), null)
            .addAttributes(AggregateRealmDefinition.ATTRIBUTES_8_0)
            .build();
    private final PersistentResourceXMLDescription customRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.CUSTOM_REALM), null)
            .addAttributes(CustomComponentDefinition.ATTRIBUTES)
            .setUseElementsForGroups(false)
            .build();
    private final PersistentResourceXMLDescription customModifiableRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.CUSTOM_MODIFIABLE_REALM), null)
            .addAttributes(CustomComponentDefinition.ATTRIBUTES)
            .setUseElementsForGroups(false)
            .build();
    private final PersistentResourceXMLDescription identityRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.IDENTITY_REALM), null)
            .addAttributes(RealmDefinitions.IDENTITY_REALM_ATTRIBUTES)
            .setUseElementsForGroups(false)
            .build();
    @Deprecated
    private final PersistentResourceXMLDescription jdbcRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM), null)
            .addAttribute(PrincipalQueryAttributes.PRINCIPAL_QUERIES, AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER, AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .build();
    private final PersistentResourceXMLDescription jdbcRealmParser_7_0 = builder(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM), null)
            .addAttribute(PrincipalQueryAttributes.PRINCIPAL_QUERIES_7_0, AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER, AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .build();
    private final PersistentResourceXMLDescription jdbcRealmParser_14_0 = builder(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM), null)
            .addAttribute(PrincipalQueryAttributes.PRINCIPAL_QUERIES_7_0, AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER, AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .addAttribute(JdbcRealmDefinition.HASH_CHARSET)
            .build();
    private final PersistentResourceXMLDescription keyStoreRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.KEY_STORE_REALM), null)
            .addAttribute(KeyStoreRealmDefinition.KEYSTORE)
            .build();
    private final PersistentResourceXMLDescription propertiesRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.PROPERTIES_REALM), null)
            .addAttributes(PropertiesRealmDefinition.GROUPS_ATTRIBUTE)
            .addAttribute(PropertiesRealmDefinition.USERS_PROPERTIES, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT)
            .addAttribute(PropertiesRealmDefinition.GROUPS_PROPERTIES, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT)
            .build();
    private final PersistentResourceXMLDescription propertiesRealmParser_14_0 = builder(PathElement.pathElement(ElytronDescriptionConstants.PROPERTIES_REALM), null)
            .addAttributes(PropertiesRealmDefinition.GROUPS_ATTRIBUTE)
            .addAttribute(PropertiesRealmDefinition.USERS_PROPERTIES, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT)
            .addAttribute(PropertiesRealmDefinition.GROUPS_PROPERTIES, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT)
            .addAttribute(PropertiesRealmDefinition.HASH_CHARSET)
            .addAttribute(PropertiesRealmDefinition.HASH_ENCODING)
            .build();
    private final PersistentResourceXMLDescription ldapRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.LDAP_REALM), null)
            .addAttributes(LdapRealmDefinition.ATTRIBUTES)
            .build();
    private final PersistentResourceXMLDescription fileSystemRealmDescription = builder(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM), null)
            .addAttribute(FileSystemRealmDefinition.ENCODED)
            .addAttribute(FileSystemRealmDefinition.LEVELS)
            .addAttribute(FileSystemRealmDefinition.PATH)
            .addAttribute(FileSystemRealmDefinition.RELATIVE_TO)
            .setMarshallDefaultValues(true)
            .build();
    private final PersistentResourceXMLDescription filesystemRealmParser_14_0 = builder(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM), null)
            .addAttributes(FileSystemRealmDefinition.PATH)
            .addAttribute(FileSystemRealmDefinition.RELATIVE_TO)
            .addAttribute(FileSystemRealmDefinition.LEVELS)
            .addAttribute(FileSystemRealmDefinition.ENCODED)
            .addAttribute(FileSystemRealmDefinition.HASH_ENCODING)
            .addAttribute(FileSystemRealmDefinition.HASH_CHARSET)
            .build();
    private final PersistentResourceXMLDescription filesystemRealmParser_15_1 = builder(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM), null)
            .addAttributes(FileSystemRealmDefinition.PATH)
            .addAttributes(FileSystemRealmDefinition.RELATIVE_TO)
            .addAttributes(FileSystemRealmDefinition.LEVELS)
            .addAttributes(FileSystemRealmDefinition.ENCODED)
            .addAttributes(FileSystemRealmDefinition.HASH_ENCODING)
            .addAttributes(FileSystemRealmDefinition.HASH_CHARSET)
            .addAttributes(FileSystemRealmDefinition.CREDENTIAL_STORE) // new
            .addAttributes(FileSystemRealmDefinition.SECRET_KEY) // new
            .build();
    private final PersistentResourceXMLDescription tokenRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.TOKEN_REALM), null)
            .addAttributes(TokenRealmDefinition.ATTRIBUTES)
            .build();
    private final PersistentResourceXMLDescription cachingRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.CACHING_REALM), null)
            .addAttributes(CachingRealmDefinition.ATTRIBUTES)
            .build();
    private final PersistentResourceXMLDescription distributedRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.DISTRIBUTED_REALM), null)
            .addAttribute(DistributedRealmDefinition.REALMS, AttributeParser.STRING_LIST, AttributeMarshaller.STRING_LIST)
            .build();
    private final PersistentResourceXMLDescription failoverRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.FAILOVER_REALM), null)
            .addAttributes(FailoverRealmDefinition.ATTRIBUTES)
            .build();
    private final PersistentResourceXMLDescription jaasRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.JAAS_REALM), null)
            .addAttributes(JaasRealmDefinition.ATTRIBUTES)
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

    RealmParser() {

    }

}
