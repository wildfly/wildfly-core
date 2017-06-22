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
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AGGREGATE_PROVIDERS;
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

    private final PersistentResourceXMLDescription providerLoaderParser = builder(PathElement.pathElement(ElytronDescriptionConstants.PROVIDER_LOADER))
            .setUseElementsForGroups(false)
            .addAttributes(ClassLoadingAttributeDefinitions.MODULE, ClassLoadingAttributeDefinitions.CLASS_NAMES, ProviderDefinitions.PATH, ProviderDefinitions.RELATIVE_TO,
                    ProviderDefinitions.ARGUMENT, ProviderDefinitions.CONFIGURATION)
            .build();
    private final PersistentResourceXMLDescription aggregateProviders = builder(PathElement.pathElement(ElytronDescriptionConstants.AGGREGATE_PROVIDERS))
            .addAttribute(ProviderDefinitions.REFERENCES,
                    new CommonAttributes.AggregateAttributeParser(PROVIDERS), //TODO "providers" is a odd name for singular reference
                    new CommonAttributes.AggregateAttributeMarshaller(PROVIDERS))
            .build();

    void readProviders(PathAddress parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            switch (localName) {
                case AGGREGATE_PROVIDERS:
                    aggregateProviders.parse(reader, parentAddress, operations);
                    break;
                case PROVIDER_LOADER:
                    providerLoaderParser.parse(reader, parentAddress, operations);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }


    void writeProviders(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(AGGREGATE_PROVIDERS) || subsystem.hasDefined(PROVIDER_LOADER)) {
            writer.writeStartElement(PROVIDERS);
            aggregateProviders.persist(writer, subsystem);
            providerLoaderParser.persist(writer, subsystem);
            writer.writeEndElement();
        }
    }
}
