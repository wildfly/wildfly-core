/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
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
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireSingleAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AGGREGATE_PROVIDERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDER_LOADER;
import static org.wildfly.extension.elytron.ElytronSubsystemParser.verifyNamespace;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * XML Parser and Marshaller for Provider configuration.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ProviderParser {

    private final PersistentResourceXMLDescription providerLoaderParser = builder(PathElement.pathElement(ElytronDescriptionConstants.PROVIDER_LOADER), null)
            .setUseElementsForGroups(false)
            .addAttributes(ClassLoadingAttributeDefinitions.MODULE, ClassLoadingAttributeDefinitions.CLASS_NAMES, ProviderDefinitions.PATH, ProviderDefinitions.RELATIVE_TO,
                    ProviderDefinitions.ARGUMENT, ProviderDefinitions.CONFIGURATION)
            .build();

    void readProviders(ModelNode parentAddressNode, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            PathAddress parentAddress = PathAddress.pathAddress(parentAddressNode);
            switch (localName) {
                case AGGREGATE_PROVIDERS:
                    readAggregateProviders(parentAddressNode, reader, operations);
                    break;
                case PROVIDER_LOADER:
                    providerLoaderParser.parse(reader, parentAddress, operations);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void readAggregateProviders(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addProviders = new ModelNode();
        addProviders.get(OP).set(ADD);

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (name == null) {
            throw missingRequired(reader, NAME);
        }

        addProviders.get(OP_ADDR).set(parentAddress).add(AGGREGATE_PROVIDERS, name);

        operations.add(addProviders);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (PROVIDERS.equals(localName) == false) {
                throw unexpectedElement(reader);
            }

            requireSingleAttribute(reader, NAME);
            String providersName = reader.getAttributeValue(0);


            ProviderDefinitions.REFERENCES.parseAndAddParameterElement(providersName, addProviders, reader);

            requireNoContent(reader);
        }
    }

    void writeProviders(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (shouldWrite(subsystem) == false) {
            return;
        }

        writer.writeStartElement(PROVIDERS);

        writeAggregateProviders(subsystem, writer);
        providerLoaderParser.persist(writer, subsystem);

        writer.writeEndElement();
    }

    private void writeAggregateProviders(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(AGGREGATE_PROVIDERS)) {
            ModelNode aggregateProviders = subsystem.require(AGGREGATE_PROVIDERS);
            for (String name : aggregateProviders.keys()) {
                ModelNode aggregateProvider = aggregateProviders.require(name);
                writer.writeStartElement(AGGREGATE_PROVIDERS);
                writer.writeAttribute(NAME, name);

                List<ModelNode> providersReferences = aggregateProvider.get(PROVIDERS).asList();
                for (ModelNode currentReference : providersReferences) {
                    writer.writeStartElement(PROVIDERS);
                    writer.writeAttribute(NAME, currentReference.asString());
                    writer.writeEndElement();
                }

                writer.writeEndElement();
            }
        }
    }

    private boolean shouldWrite(ModelNode subsystem) {
        return subsystem.hasDefined(AGGREGATE_PROVIDERS) || subsystem.hasDefined(PROVIDER_LOADER);
    }

}
