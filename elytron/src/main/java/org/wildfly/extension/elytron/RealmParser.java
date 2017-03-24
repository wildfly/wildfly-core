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

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AGGREGATE_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CACHING_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CUSTOM_MODIFIABLE_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CUSTOM_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILESYSTEM_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.IDENTITY_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.JDBC_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.KEY_STORE_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.LDAP_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROPERTIES_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_REALMS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TOKEN_REALM;
import static org.wildfly.extension.elytron.ElytronSubsystemParser.verifyNamespace;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.extension.elytron.JdbcRealmDefinition.PrincipalQueryAttributes;

/**
 * A parser for the security realm definition.
 * <p>
 * <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class RealmParser {

    private final PersistentResourceXMLDescription aggregateRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.AGGREGATE_REALM), null)
            .addAttributes(AggregateRealmDefinition.ATTRIBUTES)
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
    private final PersistentResourceXMLDescription jdbcRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM), null)
            .addAttribute(PrincipalQueryAttributes.PRINCIPAL_QUERIES, AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER, AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .build();
    private final PersistentResourceXMLDescription keyStoreRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.KEY_STORE_REALM), null)
            .addAttribute(KeyStoreRealmDefinition.KEYSTORE)
            .build();
    private final PersistentResourceXMLDescription propertiesRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.PROPERTIES_REALM), null)
            .addAttributes(PropertiesRealmDefinition.GROUPS_ATTRIBUTE)
            .addAttribute(RealmDefinitions.CASE_SENSITIVE)
            .addAttribute(PropertiesRealmDefinition.USERS_PROPERTIES, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT)
            .addAttribute(PropertiesRealmDefinition.GROUPS_PROPERTIES, AttributeParser.OBJECT_PARSER, AttributeMarshaller.ATTRIBUTE_OBJECT)
            .build();
    private final PersistentResourceXMLDescription ldapRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.LDAP_REALM), null)
            .addAttributes(LdapRealmDefinition.ATTRIBUTES)
            .build();
    private final PersistentResourceXMLDescription fileSystemRealmDescription = builder(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM), null)
            .addAttributes(FileSystemRealmDefinition.ATTRIBUTES)
            .setMarshallDefaultValues(true)
            .build();
    private final PersistentResourceXMLDescription tokenRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.TOKEN_REALM), null)
            .addAttributes(TokenRealmDefinition.ATTRIBUTES)
            .build();
    private final PersistentResourceXMLDescription cachingRealmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.CACHING_REALM), null)
            .addAttributes(CachingRealmDefinition.ATTRIBUTES)
            .build();

    /*final PersistentResourceXMLDescription realmParser = builder(PathElement.pathElement(ElytronDescriptionConstants.SECURITY_REALMS, "ignored"), null)
            .setNoAddOperation(true)
            .addChild(builder(PathElement.pathElement(ElytronDescriptionConstants.AGGREGATE_REALM), null)
                    .addAttributes(AggregateRealmDefinition.ATTRIBUTES))
            .addChild(builder(PathElement.pathElement(ElytronDescriptionConstants.CUSTOM_REALM), null)
                    .addAttributes(CustomComponentDefinition.ATTRIBUTES))
            .addChild(builder(PathElement.pathElement(ElytronDescriptionConstants.CUSTOM_MODIFIABLE_REALM), null)
                    .addAttributes(CustomComponentDefinition.ATTRIBUTES))
            .addChild(builder(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM), null)
                    .addAttributes(JdbcRealmDefinition.ATTRIBUTES))
            .addChild(builder(PathElement.pathElement(ElytronDescriptionConstants.KEY_STORE_REALM), null)
                    .addAttributes(JdbcRealmDefinition.ATTRIBUTES))
            .addChild(builder(PathElement.pathElement(ElytronDescriptionConstants.PROPERTIES_REALM), null)
                    .addAttributes(PropertiesRealmDefinition.ATTRIBUTES))
            .addChild(builder(PathElement.pathElement(ElytronDescriptionConstants.LDAP_REALM), null)
                    .addAttributes(LdapRealmDefinition.ATTRIBUTES))
            .addChild(builder(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM), null)
                    .addAttributes(FileSystemRealmDefinition.ATTRIBUTES))
            .addChild(builder(PathElement.pathElement(ElytronDescriptionConstants.TOKEN_REALM), null)
                    .addAttributes(TokenRealmDefinition.ATTRIBUTES))
            .build();*/


    void readRealms(ModelNode parentAddressNode, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            PathAddress parentAddress = PathAddress.pathAddress(parentAddressNode);
            switch (localName) {
                case AGGREGATE_REALM:
                    aggregateRealmParser.parse(reader, parentAddress, operations);
                    break;
                case CUSTOM_REALM:
                    customRealmParser.parse(reader, parentAddress, operations);
                    break;
                case CUSTOM_MODIFIABLE_REALM:
                    customModifiableRealmParser.parse(reader, parentAddress, operations);
                    break;
                case JDBC_REALM:
                    jdbcRealmParser.parse(reader, parentAddress, operations);
                    break;
                case IDENTITY_REALM:
                    identityRealmParser.parse(reader, parentAddress, operations);
                    break;
                case KEY_STORE_REALM:
                    keyStoreRealmParser.parse(reader, parentAddress, operations);
                    break;
                case PROPERTIES_REALM:
                    propertiesRealmParser.parse(reader, parentAddress, operations);
                    break;
                case LDAP_REALM:
                    ldapRealmParser.parse(reader, parentAddress, operations);
                    break;
                case FILESYSTEM_REALM:
                    fileSystemRealmDescription.parse(reader, parentAddress, operations);
                    break;
                case TOKEN_REALM:
                    tokenRealmParser.parse(reader, parentAddress, operations);
                    break;
                case CACHING_REALM:
                    cachingRealmParser.parse(reader, parentAddress, operations);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }


    void writeRealms(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (shouldWrite(subsystem) == false) {
            return;
        }

        writer.writeStartElement(SECURITY_REALMS);

        aggregateRealmParser.persist(writer, subsystem);
        customRealmParser.persist(writer, subsystem);
        customModifiableRealmParser.persist(writer, subsystem);
        identityRealmParser.persist(writer, subsystem);
        jdbcRealmParser.persist(writer, subsystem);
        keyStoreRealmParser.persist(writer, subsystem);
        propertiesRealmParser.persist(writer, subsystem);
        ldapRealmParser.persist(writer, subsystem);
        fileSystemRealmDescription.persist(writer, subsystem);
        tokenRealmParser.persist(writer, subsystem);
        cachingRealmParser.persist(writer, subsystem);

        writer.writeEndElement();
    }

    private boolean shouldWrite(ModelNode subsystem) {
        return subsystem.hasDefined(AGGREGATE_REALM) || subsystem.hasDefined(CUSTOM_REALM)
                || subsystem.hasDefined(CUSTOM_MODIFIABLE_REALM) || subsystem.hasDefined(JDBC_REALM)
                || subsystem.hasDefined(IDENTITY_REALM) || subsystem.hasDefined(KEY_STORE_REALM)
                || subsystem.hasDefined(PROPERTIES_REALM) || subsystem.hasDefined(LDAP_REALM)
                || subsystem.hasDefined(FILESYSTEM_REALM) || subsystem.hasDefined(TOKEN_REALM);

    }
}
