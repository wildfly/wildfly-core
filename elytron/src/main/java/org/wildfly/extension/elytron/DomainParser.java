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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.DEFAULT_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.OUTFLOW_ANONYMOUS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.OUTFLOW_SECURITY_DOMAINS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PERMISSION_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.POST_REALM_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PRE_REALM_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PRINCIPAL_DECODER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REALMS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REALM_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ROLE_DECODER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ROLE_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_DOMAIN;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_EVENT_LISTENER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TRUSTED_SECURITY_DOMAINS;
import static org.wildfly.extension.elytron.ElytronSubsystemParser.verifyNamespace;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A parser for the security realm definition.
 *
 * <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class DomainParser {

    void readDomain(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addDomain = new ModelNode();
        addDomain.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, DEFAULT_REALM }));

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case DEFAULT_REALM:
                        DomainDefinition.DEFAULT_REALM.parseAndSetParameter(value, addDomain, reader);
                        break;
                    case PERMISSION_MAPPER:
                        DomainDefinition.PERMISSION_MAPPER.parseAndSetParameter(value, addDomain, reader);
                        break;
                    case PRE_REALM_PRINCIPAL_TRANSFORMER:
                        DomainDefinition.PRE_REALM_PRINCIPAL_TRANSFORMER.parseAndSetParameter(value, addDomain, reader);
                        break;
                    case POST_REALM_PRINCIPAL_TRANSFORMER:
                        DomainDefinition.POST_REALM_PRINCIPAL_TRANSFORMER.parseAndSetParameter(value, addDomain, reader);
                        break;
                    case PRINCIPAL_DECODER:
                        DomainDefinition.PRINCIPAL_DECODER.parseAndSetParameter(value, addDomain, reader);
                        break;
                    case REALM_MAPPER:
                        DomainDefinition.REALM_MAPPER.parseAndSetParameter(value, addDomain, reader);
                        break;
                    case ROLE_MAPPER:
                        DomainDefinition.ROLE_MAPPER.parseAndSetParameter(value, addDomain, reader);
                        break;
                    case TRUSTED_SECURITY_DOMAINS:
                        for (String trustedSecurityDomain : reader.getListAttributeValue(i)) {
                            DomainDefinition.TRUSTED_SECURITY_DOMAINS.parseAndAddParameterElement(trustedSecurityDomain, addDomain, reader);
                        }
                        break;
                    case OUTFLOW_ANONYMOUS:
                        DomainDefinition.OUTFLOW_ANONYMOUS.parseAndSetParameter(value, addDomain, reader);
                        break;
                    case OUTFLOW_SECURITY_DOMAINS:
                        for (String outflowSecurityDomain : reader.getListAttributeValue(i)) {
                            DomainDefinition.OUTFLOW_SECURITY_DOMAINS.parseAndAddParameterElement(outflowSecurityDomain, addDomain, reader);
                        }
                        break;
                    case SECURITY_EVENT_LISTENER:
                        DomainDefinition.SECURITY_EVENT_LISTENER.parseAndSetParameter(value, addDomain, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addDomain.get(OP_ADDR).set(parentAddress).add(SECURITY_DOMAIN, name);

        boolean realmFound = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (REALM.equals(localName) == false) {
                throw unexpectedElement(reader);
            }

            parseRealmElement(addDomain, reader);
            realmFound = true;
        }

        if (realmFound == false) {
            throw missingRequired(reader, REALM);
        }
        operations.add(addDomain);
    }

    private String parseRealmElement(ModelNode addOperation, XMLExtendedStreamReader reader) throws XMLStreamException {

        String realmName = null;

        ModelNode realm = new ModelNode();

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String attributeValue = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case NAME:
                        realmName = attributeValue;
                        DomainDefinition.REALM_NAME.parseAndSetParameter(attributeValue, realm, reader);
                        break;
                    case PRINCIPAL_TRANSFORMER:
                        DomainDefinition.REALM_PRINCIPAL_TRANSFORMER.parseAndSetParameter(attributeValue, realm, reader);
                        break;
                    case ROLE_DECODER:
                        DomainDefinition.REALM_ROLE_DECODER.parseAndSetParameter(attributeValue, realm, reader);
                        break;
                    case ROLE_MAPPER:
                        DomainDefinition.ROLE_MAPPER.parseAndSetParameter(attributeValue, realm, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (realmName == null) {
            throw missingRequired(reader, NAME);
        }

        requireNoContent(reader);

        addOperation.get(REALMS).add(realm);
        return realmName;
    }

    void writeDomain(String name, ModelNode domain, XMLExtendedStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement(SECURITY_DOMAIN);
        writer.writeAttribute(NAME, name);
        DomainDefinition.PRE_REALM_PRINCIPAL_TRANSFORMER.marshallAsAttribute(domain, writer);
        DomainDefinition.DEFAULT_REALM.marshallAsAttribute(domain, writer);
        DomainDefinition.POST_REALM_PRINCIPAL_TRANSFORMER.marshallAsAttribute(domain, writer);
        DomainDefinition.PERMISSION_MAPPER.marshallAsAttribute(domain, writer);
        DomainDefinition.PRINCIPAL_DECODER.marshallAsAttribute(domain, writer);
        DomainDefinition.REALM_MAPPER.marshallAsAttribute(domain, writer);
        DomainDefinition.ROLE_MAPPER.marshallAsAttribute(domain, writer);
        DomainDefinition.TRUSTED_SECURITY_DOMAINS.getAttributeMarshaller().marshallAsAttribute(DomainDefinition.TRUSTED_SECURITY_DOMAINS, domain, false, writer);
        DomainDefinition.OUTFLOW_ANONYMOUS.marshallAsAttribute(domain, writer);
        DomainDefinition.OUTFLOW_SECURITY_DOMAINS.getAttributeMarshaller().marshallAsAttribute(DomainDefinition.OUTFLOW_SECURITY_DOMAINS, domain, false, writer);
        DomainDefinition.SECURITY_EVENT_LISTENER.marshallAsAttribute(domain, writer);

        List<ModelNode> realms = domain.get(REALMS).asList();

        for (ModelNode current : realms) {
            writeRealm(current, writer);
        }

        writer.writeEndElement();
    }

    private void writeRealm(ModelNode realm, XMLExtendedStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement(REALM);
        DomainDefinition.REALM_NAME.marshallAsAttribute(realm, writer);
        DomainDefinition.REALM_PRINCIPAL_TRANSFORMER.marshallAsAttribute(realm, writer);
        DomainDefinition.REALM_ROLE_DECODER.marshallAsAttribute(realm, writer);
        DomainDefinition.ROLE_MAPPER.marshallAsAttribute(realm, writer);
        writer.writeEndElement();
    }

}

