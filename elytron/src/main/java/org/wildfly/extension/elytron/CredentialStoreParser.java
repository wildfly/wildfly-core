/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016 Red Hat, Inc., and individual contributors
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
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CREDENTIAL_STORE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CREDENTIAL_STORES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.OTHER_PROVIDERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDER_NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.RELATIVE_TO;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TYPE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.URI;
import static org.wildfly.extension.elytron.ElytronSubsystemParser.verifyNamespace;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeParser;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A parser for Credential Store definition.
 *
 * @author <a href="mailto:pskopek@redhat.com">Peter Skopek</a>
 */
class CredentialStoreParser {

    void readCredentialStores(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            switch (localName) {
                case CREDENTIAL_STORE:
                    readCredentialStore(parentAddress, reader, operations);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }


    private void readCredentialStore(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addCredentialStore = new ModelNode();
        addCredentialStore.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<>(Arrays.asList(new String[] {NAME}));
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
                    case TYPE:
                        CredentialStoreResourceDefinition.TYPE.parseAndSetParameter(value, addCredentialStore, reader);
                        break;
                    case PROVIDER_NAME:
                        CredentialStoreResourceDefinition.PROVIDER_NAME.parseAndSetParameter(value, addCredentialStore, reader);
                        break;
                    case PROVIDERS:
                        CredentialStoreResourceDefinition.PROVIDERS.parseAndSetParameter(value, addCredentialStore, reader);
                        break;
                    case OTHER_PROVIDERS:
                        CredentialStoreResourceDefinition.OTHER_PROVIDERS.parseAndSetParameter(value, addCredentialStore, reader);
                        break;
                    case RELATIVE_TO:
                        CredentialStoreResourceDefinition.RELATIVE_TO.parseAndSetParameter(value, addCredentialStore, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addCredentialStore.get(OP_ADDR).set(parentAddress).add(CREDENTIAL_STORE, name);

        while(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (URI.equals(localName)) {
                String value = reader.getElementText();
                CredentialStoreResourceDefinition.URI.parseAndSetParameter(value, addCredentialStore, reader);
            } else if (CredentialStoreResourceDefinition.CREDENTIAL_REFERENCE.getXmlName().equals(localName)) {
                AttributeParser ap = CredentialStoreResourceDefinition.CREDENTIAL_REFERENCE.getParser();
                ap.parseElement(CredentialStoreResourceDefinition.CREDENTIAL_REFERENCE, reader, addCredentialStore);
            } else {
                throw unexpectedElement(reader);
            }
        }

        operations.add(addCredentialStore);
    }

    void writeCredentialStores(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {

        if (subsystem.hasDefined(CREDENTIAL_STORE)) {
            writer.writeStartElement(CREDENTIAL_STORES);
            ModelNode credentialStores = subsystem.require(CREDENTIAL_STORE);
            for (String name : credentialStores.keys()) {
                ModelNode credentialStoreModelNode = credentialStores.require(name);
                writer.writeStartElement(CREDENTIAL_STORE);
                writer.writeAttribute(NAME, name);
                CredentialStoreResourceDefinition.TYPE.marshallAsAttribute(credentialStoreModelNode, writer);
                CredentialStoreResourceDefinition.PROVIDER_NAME.marshallAsAttribute(credentialStoreModelNode, writer);
                CredentialStoreResourceDefinition.PROVIDERS.marshallAsAttribute(credentialStoreModelNode, writer);
                CredentialStoreResourceDefinition.OTHER_PROVIDERS.marshallAsAttribute(credentialStoreModelNode, writer);
                CredentialStoreResourceDefinition.RELATIVE_TO.marshallAsAttribute(credentialStoreModelNode, writer);
                CredentialStoreResourceDefinition.URI.marshallAsElement(credentialStoreModelNode, writer);
                CredentialStoreResourceDefinition.CREDENTIAL_REFERENCE.marshallAsElement(credentialStoreModelNode, writer);
                writer.writeEndElement();
            }

            writer.writeEndElement();
        }

    }

}
