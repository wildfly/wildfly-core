/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AGGREGATE_SASL_SERVER_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CONFIGURABLE_SASL_SERVER_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MECHANISM_PROVIDER_FILTERING_SASL_SERVER_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDER_SASL_SERVER_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SASL;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SASL_AUTHENTICATION_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SASL_SERVER_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SERVICE_LOADER_SASL_SERVER_FACTORY;

import java.util.List;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * XML handling for the SASL definitions.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SaslParser {

    private final AuthenticationFactoryParser authenticationFactoryParser = new AuthenticationFactoryParser();
    private final ElytronSubsystemParser elytronSubsystemParser;

    private PersistentResourceXMLDescription aggregateSaslServerMechanismFactory = PersistentResourceXMLDescription.builder(PathElement.pathElement(ElytronDescriptionConstants.AGGREGATE_SASL_SERVER_FACTORY))
            .addAttribute(SaslServerDefinitions.getRawAggregateSaslServerFactoryDefinition().getReferencesAttribute(),
                    new CommonAttributes.AggregateAttributeParser(SASL_SERVER_FACTORY),
                    new CommonAttributes.AggregateAttributeMarshaller(SASL_SERVER_FACTORY))
            .build();

    private PersistentResourceXMLDescription configurableSaslServerMechanismFactoryParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(CONFIGURABLE_SASL_SERVER_FACTORY))
            .addAttribute(SaslServerDefinitions.SASL_SERVER_FACTORY)
            .addAttribute(SaslServerDefinitions.PROTOCOL)
            .addAttribute(SaslServerDefinitions.SERVER_NAME)
            .addAttribute(CommonAttributes.PROPERTIES)
            .addAttribute(SaslServerDefinitions.CONFIGURED_FILTERS)
            .build();

    private PersistentResourceXMLDescription mechanismProviderFilteringSaslServerFactoryElement = PersistentResourceXMLDescription.builder(PathElement.pathElement(ElytronDescriptionConstants.MECHANISM_PROVIDER_FILTERING_SASL_SERVER_FACTORY))
            .addAttribute(SaslServerDefinitions.SASL_SERVER_FACTORY)
            .addAttribute(SaslServerDefinitions.ENABLING)
            .addAttribute(SaslServerDefinitions.MECH_PROVIDER_FILTERS)
            .build();

    private PersistentResourceXMLDescription providerSaslServerMechanismFactoryParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(ElytronDescriptionConstants.PROVIDER_SASL_SERVER_FACTORY))
            .addAttribute(SaslServerDefinitions.PROVIDERS)
            .build();

    private PersistentResourceXMLDescription serviceLoaderSaslServerMechanismFactoryParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(ElytronDescriptionConstants.SERVICE_LOADER_SASL_SERVER_FACTORY))
            .setUseElementsForGroups(false)
            .addAttribute(ClassLoadingAttributeDefinitions.MODULE)
            .build();

    SaslParser(ElytronSubsystemParser elytronSubsystemParser) {
        this.elytronSubsystemParser = elytronSubsystemParser;
    }

    private void verifyNamespace(XMLExtendedStreamReader reader) throws XMLStreamException {
        elytronSubsystemParser.verifyNamespace(reader);
    }

    void readSasl(PathAddress parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            switch (localName) {
                case SASL_AUTHENTICATION_FACTORY:
                    authenticationFactoryParser.readSaslAuthenticationFactoryElement(parentAddress, reader, operations);
                    break;
                case AGGREGATE_SASL_SERVER_FACTORY:
                    aggregateSaslServerMechanismFactory.parse(reader, parentAddress, operations);
                    break;
                case CONFIGURABLE_SASL_SERVER_FACTORY:
                    configurableSaslServerMechanismFactoryParser.parse(reader, parentAddress, operations);
                    break;
                case MECHANISM_PROVIDER_FILTERING_SASL_SERVER_FACTORY:
                    mechanismProviderFilteringSaslServerFactoryElement.parse(reader, parentAddress, operations);
                    break;
                case PROVIDER_SASL_SERVER_FACTORY:
                    providerSaslServerMechanismFactoryParser.parse(reader, parentAddress, operations);
                    break;
                case SERVICE_LOADER_SASL_SERVER_FACTORY:
                    serviceLoaderSaslServerMechanismFactoryParser.parse(reader, parentAddress, operations);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }


    private void startSasl(boolean started, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (started == false) {
            writer.writeStartElement(SASL);
        }
    }

    private boolean writeAggregateSaslServerFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(AGGREGATE_SASL_SERVER_FACTORY)) {
            startSasl(started, writer);
            aggregateSaslServerMechanismFactory.persist(writer, subsystem);
            return true;
        }

        return false;
    }

    private boolean writeConfigurableSaslServerFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CONFIGURABLE_SASL_SERVER_FACTORY)) {
            startSasl(started, writer);
            configurableSaslServerMechanismFactoryParser.persist(writer, subsystem);
            return true;
        }
        return false;
    }

    private boolean writeMechanismProviderFilteringSaslServerFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(MECHANISM_PROVIDER_FILTERING_SASL_SERVER_FACTORY)) {
            startSasl(started, writer);
            mechanismProviderFilteringSaslServerFactoryElement.persist(writer, subsystem);
            return true;
        }
        return false;
    }

    private boolean writeProviderSaslServerFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(PROVIDER_SASL_SERVER_FACTORY)) {
            startSasl(started, writer);
            providerSaslServerMechanismFactoryParser.persist(writer, subsystem);
            return true;
        }

        return false;
    }

    private boolean writeServiceLoaderSaslServerFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(SERVICE_LOADER_SASL_SERVER_FACTORY)) {
            startSasl(started, writer);
            serviceLoaderSaslServerMechanismFactoryParser.persist(writer, subsystem);
            return true;
        }

        return false;
    }

    void writeSasl(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        boolean saslStarted = false;

        saslStarted = saslStarted | authenticationFactoryParser.writeSaslAuthenticationFactory(saslStarted, subsystem, writer, b -> startSasl(b, writer));
        saslStarted = saslStarted | writeAggregateSaslServerFactory(saslStarted, subsystem, writer);
        saslStarted = saslStarted | writeConfigurableSaslServerFactory(saslStarted, subsystem, writer);
        saslStarted = saslStarted | writeMechanismProviderFilteringSaslServerFactory(saslStarted, subsystem, writer);
        saslStarted = saslStarted | writeProviderSaslServerFactory(saslStarted, subsystem, writer);
        saslStarted = saslStarted | writeServiceLoaderSaslServerFactory(saslStarted, subsystem, writer);

        if (saslStarted) {
            writer.writeEndElement();
        }
    }
}
