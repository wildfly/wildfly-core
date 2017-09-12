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
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AGGREGATE_HTTP_SERVER_MECHANISM_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CONFIGURABLE_HTTP_SERVER_MECHANISM_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HTTP;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HTTP_AUTHENTICATION_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HTTP_SERVER_MECHANISM_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDER_HTTP_SERVER_MECHANISM_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SERVICE_LOADER_HTTP_SERVER_MECHANISM_FACTORY;
import static org.wildfly.extension.elytron.HttpServerDefinitions.CONFIGURED_FILTERS;

import java.util.List;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * XML handling of the HTTP element.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Tomaz Cerar
 */
class HttpParser {

    private final AuthenticationFactoryParser authenticationFactoryParser = new AuthenticationFactoryParser();
    private final ElytronSubsystemParser elytronSubsystemParser;

    private PersistentResourceXMLDescription aggregateHttpServerMechanismFactory = PersistentResourceXMLDescription.builder(PathElement.pathElement(ElytronDescriptionConstants.AGGREGATE_HTTP_SERVER_MECHANISM_FACTORY))
            .addAttribute(HttpServerDefinitions.getRawAggregateHttpServerFactoryDefinition().getReferencesAttribute(),
                    new CommonAttributes.AggregateAttributeParser(HTTP_SERVER_MECHANISM_FACTORY),
                    new CommonAttributes.AggregateAttributeMarshaller(HTTP_SERVER_MECHANISM_FACTORY))
            .build();

    private PersistentResourceXMLDescription configurableHttpServerMechanismFactoryParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(CONFIGURABLE_HTTP_SERVER_MECHANISM_FACTORY))
            .addAttribute(HttpServerDefinitions.HTTP_SERVER_FACTORY)
            .addAttribute(CommonAttributes.PROPERTIES)
            .addAttribute(CONFIGURED_FILTERS)
            .build();

    private PersistentResourceXMLDescription providerHttpServerMechanismFactoryParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(ElytronDescriptionConstants.PROVIDER_HTTP_SERVER_MECHANISM_FACTORY))
            .addAttribute(HttpServerDefinitions.PROVIDERS)
            .build();

    private PersistentResourceXMLDescription serviceLoaderHttpServerMechanismFactoryParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(ElytronDescriptionConstants.SERVICE_LOADER_HTTP_SERVER_MECHANISM_FACTORY))
            .setUseElementsForGroups(false)
            .addAttribute(ClassLoadingAttributeDefinitions.MODULE)
            .build();

    HttpParser(ElytronSubsystemParser elytronSubsystemParser) {
        this.elytronSubsystemParser = elytronSubsystemParser;
    }

    private void verifyNamespace(XMLExtendedStreamReader reader) throws XMLStreamException {
        elytronSubsystemParser.verifyNamespace(reader);
    }

    void readHttp(PathAddress parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            switch (localName) {
                case HTTP_AUTHENTICATION_FACTORY:
                    authenticationFactoryParser.readHttpAuthenticationFactoryElement(parentAddress, reader, operations);
                    break;
                case AGGREGATE_HTTP_SERVER_MECHANISM_FACTORY:
                    aggregateHttpServerMechanismFactory.parse(reader, PathAddress.pathAddress(parentAddress), operations);
                    break;
                case CONFIGURABLE_HTTP_SERVER_MECHANISM_FACTORY:
                    configurableHttpServerMechanismFactoryParser.parse(reader, parentAddress, operations);
                    break;
                case PROVIDER_HTTP_SERVER_MECHANISM_FACTORY:
                    providerHttpServerMechanismFactoryParser.parse(reader, parentAddress, operations);
                    break;
                case SERVICE_LOADER_HTTP_SERVER_MECHANISM_FACTORY:
                    serviceLoaderHttpServerMechanismFactoryParser.parse(reader, parentAddress, operations);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void startHttp(boolean started, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (started == false) {
            writer.writeStartElement(HTTP);
        }
    }

    private boolean writeAggregateHttpServerMechanismFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(AGGREGATE_HTTP_SERVER_MECHANISM_FACTORY)) {
            startHttp(started, writer);
            aggregateHttpServerMechanismFactory.persist(writer, subsystem);

            return true;
        }

        return false;
    }

    private boolean writeConfigurableHttpServerMechanismFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CONFIGURABLE_HTTP_SERVER_MECHANISM_FACTORY)) {
            startHttp(started, writer);
            configurableHttpServerMechanismFactoryParser.persist(writer, subsystem);
            return true;
        }

        return false;
    }

    private boolean writeProviderHttpServerMechanismFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(PROVIDER_HTTP_SERVER_MECHANISM_FACTORY)) {
            startHttp(started, writer);
            providerHttpServerMechanismFactoryParser.persist(writer, subsystem);
            return true;
        }

        return false;
    }

    private boolean writeServiceLoaderHttpServerMechanismFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(SERVICE_LOADER_HTTP_SERVER_MECHANISM_FACTORY)) {
            startHttp(started, writer);
            serviceLoaderHttpServerMechanismFactoryParser.persist(writer, subsystem);
            return true;
        }

        return false;
    }

    void writeHttp(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        boolean started = false;

        started = started | authenticationFactoryParser.writeHttpAuthenticationFactory(started, subsystem, writer, b -> startHttp(b, writer));
        started = started | writeAggregateHttpServerMechanismFactory(started, subsystem, writer);
        started = started | writeConfigurableHttpServerMechanismFactory(started, subsystem, writer);
        started = started | writeProviderHttpServerMechanismFactory(started, subsystem, writer);
        started = started | writeServiceLoaderHttpServerMechanismFactory(started, subsystem, writer);

        if (started) {
            writer.writeEndElement();
        }
    }
}
