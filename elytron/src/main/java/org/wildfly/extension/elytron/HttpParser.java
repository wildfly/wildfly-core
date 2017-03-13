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
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AGGREGATE_HTTP_SERVER_MECHANISM_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CONFIGURABLE_HTTP_SERVER_MECHANISM_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ENABLING;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILTER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILTERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HTTP;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HTTP_AUTHENTICATION_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HTTP_SERVER_FACTORIES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HTTP_SERVER_MECHANISM_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MODULE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PATTERN_FILTER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROPERTIES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROPERTY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDER_HTTP_SERVER_MECHANISM_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SERVICE_LOADER_HTTP_SERVER_MECHANISM_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.VALUE;
import static org.wildfly.extension.elytron.ElytronSubsystemParser.verifyNamespace;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * XML handling of the HTTP element.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class HttpParser {

    private final AuthenticationFactoryParser authenticationFactoryParser = new AuthenticationFactoryParser();

    void readHttp(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            switch (localName) {
                case HTTP_AUTHENTICATION_FACTORY:
                    authenticationFactoryParser.readHttpAuthenticationFactoryElement(parentAddress, reader, operations);
                    break;
                case AGGREGATE_HTTP_SERVER_MECHANISM_FACTORY:
                    readAggregateHttpServerMechanismFactoryElement(parentAddress, reader, operations);
                    break;
                case CONFIGURABLE_HTTP_SERVER_MECHANISM_FACTORY:
                    readConfigurableHttpServerMechanismFactoryElement(parentAddress, reader, operations);
                    break;
                case PROVIDER_HTTP_SERVER_MECHANISM_FACTORY:
                    readProviderHttpServerMechanismFactoryElement(parentAddress, reader, operations);
                    break;
                case SERVICE_LOADER_HTTP_SERVER_MECHANISM_FACTORY:
                    readServiceLoaderHttpServerMechanismFactoryElement(parentAddress, reader, operations);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void readAggregateHttpServerMechanismFactoryElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addOperation = new ModelNode();
        addOperation.get(OP).set(ADD);

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

        addOperation.get(OP_ADDR).set(parentAddress).add(AGGREGATE_HTTP_SERVER_MECHANISM_FACTORY, name);

        operations.add(addOperation);

        ListAttributeDefinition httpServerFactories = HttpServerDefinitions.getRawAggregateHttpServerFactoryDefinition().getReferencesAttribute();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (HTTP_SERVER_MECHANISM_FACTORY.equals(localName) == false) {
                throw unexpectedElement(reader);
            }

            requireSingleAttribute(reader, NAME);
            String httpServerFactoryName = reader.getAttributeValue(0);

            httpServerFactories.parseAndAddParameterElement(httpServerFactoryName, addOperation, reader);

            requireNoContent(reader);
        }
    }

    private void readConfigurableHttpServerMechanismFactoryElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addOperation = new ModelNode();
        addOperation.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, HTTP_SERVER_MECHANISM_FACTORY }));

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
                    case HTTP_SERVER_MECHANISM_FACTORY:
                        HttpServerDefinitions.HTTP_SERVER_FACTORY.parseAndSetParameter(value, addOperation, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addOperation.get(OP_ADDR).set(parentAddress).add(CONFIGURABLE_HTTP_SERVER_MECHANISM_FACTORY, name);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();

            switch (localName) {
                case FILTERS:
                    ModelNode filters = addOperation.get(FILTERS);
                    parseFilters(filters, reader);
                    break;
                case PROPERTIES:
                    ModelNode properties = addOperation.get(PROPERTIES);
                    parseProperties(properties, reader);
                    break;
                default:
                    throw unexpectedElement(reader);
            }

        }

        operations.add(addOperation);
    }

    private void parseFilters(ModelNode filters, XMLExtendedStreamReader reader) throws XMLStreamException {
        requireNoAttributes(reader);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            switch (localName) {
                case FILTER:
                    parseFilter(filters, reader);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void parseFilter(ModelNode filters, XMLExtendedStreamReader reader) throws XMLStreamException {
        ModelNode filter = new ModelNode();

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String attributeValue = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case ENABLING:
                        HttpServerDefinitions.ENABLING.parseAndSetParameter(attributeValue, filter, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            String value;
            switch (localName) {
                case PATTERN_FILTER:
                    requireSingleAttribute(reader, VALUE);
                    value = reader.getAttributeValue(0);
                    HttpServerDefinitions.PATTERN_FILTER.parseAndSetParameter(value, filter, reader);
                    requireNoContent(reader);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }

        filters.add(filter);
    }

    private void parseProperties(ModelNode properties, XMLExtendedStreamReader reader) throws XMLStreamException {
        requireNoAttributes(reader);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            switch (localName) {
                case PROPERTY:
                    parseProperty(properties, reader);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void parseProperty(ModelNode properties, XMLExtendedStreamReader reader) throws XMLStreamException {
        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, VALUE }));
        String key = null;
        String value = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String attributeValue = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        key = attributeValue;
                        break;
                    case VALUE:
                        value = attributeValue;
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        requireNoContent(reader);

        properties.add(key, new ModelNode(value));
    }

    private void readProviderHttpServerMechanismFactoryElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addOperation = new ModelNode();
        addOperation.get(OP).set(ADD);

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
                    case PROVIDERS:
                        HttpServerDefinitions.PROVIDERS.parseAndSetParameter(value, addOperation, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (name == null) {
            throw missingRequired(reader, NAME);
        }

        addOperation.get(OP_ADDR).set(parentAddress).add(PROVIDER_HTTP_SERVER_MECHANISM_FACTORY, name);

        operations.add(addOperation);

        requireNoContent(reader);
    }

    private void readServiceLoaderHttpServerMechanismFactoryElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addOperation = new ModelNode();
        addOperation.get(OP).set(ADD);

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
                    case MODULE:
                        ClassLoadingAttributeDefinitions.MODULE.parseAndSetParameter(value, addOperation, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (name == null) {
            throw missingRequired(reader, NAME);
        }

        addOperation.get(OP_ADDR).set(parentAddress).add(SERVICE_LOADER_HTTP_SERVER_MECHANISM_FACTORY, name);

        operations.add(addOperation);

        requireNoContent(reader);
    }

    private void startHttp(boolean started, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (started == false) {
            writer.writeStartElement(HTTP);
        }
    }

    private boolean writeAggregateHttpServerMechanismFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(AGGREGATE_HTTP_SERVER_MECHANISM_FACTORY)) {
            startHttp(started, writer);
            ModelNode aggregateHttpServerFactory = subsystem.require(AGGREGATE_HTTP_SERVER_MECHANISM_FACTORY);
            for (String name : aggregateHttpServerFactory.keys()) {
                ModelNode serverFactory = aggregateHttpServerFactory.require(name);
                writer.writeStartElement(AGGREGATE_HTTP_SERVER_MECHANISM_FACTORY);
                writer.writeAttribute(NAME, name);

                List<ModelNode> serverFactoryReferences = serverFactory.get(HTTP_SERVER_FACTORIES).asList();
                for (ModelNode currentReference : serverFactoryReferences) {
                    writer.writeStartElement(HTTP_SERVER_MECHANISM_FACTORY);
                    writer.writeAttribute(NAME, currentReference.asString());
                    writer.writeEndElement();
                }

                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeConfigurableHttpServerMechanismFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CONFIGURABLE_HTTP_SERVER_MECHANISM_FACTORY)) {
            startHttp(started, writer);
            ModelNode httpServerFactories = subsystem.require(CONFIGURABLE_HTTP_SERVER_MECHANISM_FACTORY);
            for (String name : httpServerFactories.keys()) {
                ModelNode serverFactory = httpServerFactories.require(name);
                writer.writeStartElement(CONFIGURABLE_HTTP_SERVER_MECHANISM_FACTORY);
                writer.writeAttribute(NAME, name);
                HttpServerDefinitions.HTTP_SERVER_FACTORY.marshallAsAttribute(serverFactory, writer);
                CommonAttributes.PROPERTIES.marshallAsElement(serverFactory, writer);
                if (serverFactory.hasDefined(FILTERS)) {
                    writer.writeStartElement(FILTERS);
                    List<ModelNode> filters = serverFactory.require(FILTERS).asList();
                    for (ModelNode currentFilter : filters) {
                        writer.writeStartElement(FILTER);
                        HttpServerDefinitions.ENABLING.marshallAsAttribute(currentFilter, writer);
                        if (currentFilter.hasDefined(PATTERN_FILTER)) {
                            writer.writeStartElement(PATTERN_FILTER);
                            HttpServerDefinitions.PATTERN_FILTER.marshallAsAttribute(currentFilter, writer);
                            writer.writeEndElement();
                        }
                        writer.writeEndElement();
                    }
                    writer.writeEndElement();
                }

                writer.writeEndElement();
            }
            return true;
        }

        return false;
    }

    private boolean writeProviderHttpServerMechanismFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(PROVIDER_HTTP_SERVER_MECHANISM_FACTORY)) {
            startHttp(started, writer);
            ModelNode serverFactories = subsystem.require(PROVIDER_HTTP_SERVER_MECHANISM_FACTORY);
            for (String name : serverFactories.keys()) {
                ModelNode serverFactory = serverFactories.require(name);
                writer.writeStartElement(PROVIDER_HTTP_SERVER_MECHANISM_FACTORY);
                writer.writeAttribute(NAME, name);
                HttpServerDefinitions.PROVIDERS.marshallAsAttribute(serverFactory, writer);
                writer.writeEndElement();
            }
            return true;
        }

        return false;
    }

    private boolean writeServiceLoaderHttpServerMechanismFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(SERVICE_LOADER_HTTP_SERVER_MECHANISM_FACTORY)) {
            startHttp(started, writer);
            ModelNode serverFactories = subsystem.require(SERVICE_LOADER_HTTP_SERVER_MECHANISM_FACTORY);
            for (String name : serverFactories.keys()) {
                ModelNode serverFactory = serverFactories.require(name);
                writer.writeStartElement(SERVICE_LOADER_HTTP_SERVER_MECHANISM_FACTORY);
                writer.writeAttribute(NAME, name);
                ClassLoadingAttributeDefinitions.MODULE.marshallAsAttribute(serverFactory, writer);
                writer.writeEndElement();
            }
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
