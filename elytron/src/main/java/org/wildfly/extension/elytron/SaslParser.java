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
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AGGREGATE_SASL_SERVER_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CONFIGURABLE_SASL_SERVER_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ENABLING;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILTER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILTERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MECHANISM_NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MECHANISM_PROVIDER_FILTERING_SASL_SERVER_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MODULE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PATTERN_FILTER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PREDEFINED_FILTER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROPERTIES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROPERTY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROTOCOL;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDER_NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDER_SASL_SERVER_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDER_VERSION;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SASL;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SASL_AUTHENTICATION_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SASL_SERVER_FACTORIES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SASL_SERVER_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SERVER_NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SERVICE_LOADER_SASL_SERVER_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.VALUE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.VERSION_COMPARISON;
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
 * XML handling for the SASL definitions.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SaslParser {

    private final AuthenticationFactoryParser authenticationFactoryParser = new AuthenticationFactoryParser();

    void readSasl(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            switch (localName) {
                case SASL_AUTHENTICATION_FACTORY:
                    authenticationFactoryParser.readSaslAuthenticationFactoryElement(parentAddress, reader, operations);
                    break;
                case AGGREGATE_SASL_SERVER_FACTORY:
                    readAggregateSaslServerFactoryElement(parentAddress, reader, operations);
                    break;
                case CONFIGURABLE_SASL_SERVER_FACTORY:
                    readConfigurableSaslServerFactoryElement(parentAddress, reader, operations);
                    break;
                case MECHANISM_PROVIDER_FILTERING_SASL_SERVER_FACTORY:
                    readMechanismProviderFilteringSaslServerFactoryElement(parentAddress, reader, operations);
                    break;
                case PROVIDER_SASL_SERVER_FACTORY:
                    readProviderSaslServerFactoryElement(parentAddress, reader, operations);
                    break;
                case SERVICE_LOADER_SASL_SERVER_FACTORY:
                    readServiceLoaderSaslServerFactoryElement(parentAddress, reader, operations);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void readAggregateSaslServerFactoryElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
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

        addOperation.get(OP_ADDR).set(parentAddress).add(AGGREGATE_SASL_SERVER_FACTORY, name);

        operations.add(addOperation);

        ListAttributeDefinition saslServerFactories = SaslServerDefinitions.getRawAggregateSaslServerFactoryDefinition().getReferencesAttribute();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (SASL_SERVER_FACTORY.equals(localName) == false) {
                throw unexpectedElement(reader);
            }

            requireSingleAttribute(reader, NAME);
            String saslServerFactoryName = reader.getAttributeValue(0);

            saslServerFactories.parseAndAddParameterElement(saslServerFactoryName, addOperation, reader);

            requireNoContent(reader);
        }
    }

    private void readConfigurableSaslServerFactoryElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addOperation = new ModelNode();
        addOperation.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, SASL_SERVER_FACTORY }));

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
                    case SASL_SERVER_FACTORY:
                        SaslServerDefinitions.SASL_SERVER_FACTORY.parseAndSetParameter(value, addOperation, reader);
                        break;
                    case PROTOCOL:
                        SaslServerDefinitions.PROTOCOL.parseAndSetParameter(value, addOperation, reader);
                        break;
                    case SERVER_NAME:
                        SaslServerDefinitions.SERVER_NAME.parseAndSetParameter(value, addOperation, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addOperation.get(OP_ADDR).set(parentAddress).add(CONFIGURABLE_SASL_SERVER_FACTORY, name);

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
                        SaslServerDefinitions.ENABLING.parseAndSetParameter(attributeValue, filter, reader);
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
                case PREDEFINED_FILTER:
                    requireSingleAttribute(reader, VALUE);
                    value = reader.getAttributeValue(0);
                    SaslServerDefinitions.PREDEFINED_FILTER.parseAndSetParameter(value, filter, reader);
                    requireNoContent(reader);
                    break;
                case PATTERN_FILTER:
                    requireSingleAttribute(reader, VALUE);
                    value = reader.getAttributeValue(0);
                    SaslServerDefinitions.PATTERN_FILTER.parseAndSetParameter(value, filter, reader);
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

    private void readMechanismProviderFilteringSaslServerFactoryElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addOperation = new ModelNode();
        addOperation.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, SASL_SERVER_FACTORY }));

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
                    case SASL_SERVER_FACTORY:
                        SaslServerDefinitions.SASL_SERVER_FACTORY.parseAndSetParameter(value, addOperation, reader);
                        break;
                    case ENABLING:
                        SaslServerDefinitions.ENABLING.parseAndSetParameter(value, addOperation, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addOperation.get(OP_ADDR).set(parentAddress).add(MECHANISM_PROVIDER_FILTERING_SASL_SERVER_FACTORY, name);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();

            switch (localName) {
                case FILTERS:
                    ModelNode filters = addOperation.get(FILTERS);
                    requireNoAttributes(reader);

                    while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                        verifyNamespace(reader);
                        if (FILTER.equals(reader.getLocalName()) == false) {
                            throw unexpectedElement(reader);
                        }
                        parseMechanismProviderFilter(filters, reader);
                    }
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }

        operations.add(addOperation);
    }

    private void parseMechanismProviderFilter(ModelNode filters, XMLExtendedStreamReader reader) throws XMLStreamException {
        ModelNode filter = new ModelNode();

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { PROVIDER_NAME }));

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case MECHANISM_NAME:
                        SaslServerDefinitions.MECHANISM_NAME.parseAndSetParameter(value, filter, reader);
                        break;
                    case PROVIDER_NAME:
                        SaslServerDefinitions.PROVIDER_NAME.parseAndSetParameter(value, filter, reader);
                        break;
                    case PROVIDER_VERSION:
                        SaslServerDefinitions.PROVIDER_VERSION.parseAndSetParameter(value, filter, reader);
                        break;
                    case VERSION_COMPARISON:
                        SaslServerDefinitions.VERSION_COMPARISON.parseAndSetParameter(value, filter, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        requireNoContent(reader);

        filters.add(filter);
    }

    private void readProviderSaslServerFactoryElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
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
                        SaslServerDefinitions.PROVIDERS.parseAndSetParameter(value, addOperation, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (name == null) {
            throw missingRequired(reader, NAME);
        }

        addOperation.get(OP_ADDR).set(parentAddress).add(PROVIDER_SASL_SERVER_FACTORY, name);

        operations.add(addOperation);

        requireNoContent(reader);
    }

    private void readServiceLoaderSaslServerFactoryElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
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

        addOperation.get(OP_ADDR).set(parentAddress).add(SERVICE_LOADER_SASL_SERVER_FACTORY, name);

        operations.add(addOperation);

        requireNoContent(reader);
    }

    private void startSasl(boolean started, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (started == false) {
            writer.writeStartElement(SASL);
        }
    }

    private boolean writeAggregateSaslServerFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(AGGREGATE_SASL_SERVER_FACTORY)) {
            startSasl(started, writer);
            ModelNode serverFactories = subsystem.require(AGGREGATE_SASL_SERVER_FACTORY);
            for (String name : serverFactories.keys()) {
                ModelNode serverFactory = serverFactories.require(name);
                writer.writeStartElement(AGGREGATE_SASL_SERVER_FACTORY);
                writer.writeAttribute(NAME, name);

                List<ModelNode> serverFactoryReferences = serverFactory.get(SASL_SERVER_FACTORIES).asList();
                for (ModelNode currentReference : serverFactoryReferences) {
                    writer.writeStartElement(SASL_SERVER_FACTORY);
                    writer.writeAttribute(NAME, currentReference.asString());
                    writer.writeEndElement();
                }

                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeConfigurableSaslServerFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CONFIGURABLE_SASL_SERVER_FACTORY)) {
            startSasl(started, writer);
            ModelNode saslServerFactories = subsystem.require(CONFIGURABLE_SASL_SERVER_FACTORY);
            for (String name : saslServerFactories.keys()) {
                ModelNode serverFactory = saslServerFactories.require(name);
                writer.writeStartElement(CONFIGURABLE_SASL_SERVER_FACTORY);
                writer.writeAttribute(NAME, name);
                SaslServerDefinitions.PROTOCOL.marshallAsAttribute(serverFactory, writer);
                SaslServerDefinitions.SASL_SERVER_FACTORY.marshallAsAttribute(serverFactory, writer);
                SaslServerDefinitions.SERVER_NAME.marshallAsAttribute(serverFactory, writer);
                CommonAttributes.PROPERTIES.marshallAsElement(serverFactory, writer);
                if (serverFactory.hasDefined(FILTERS)) {
                    writer.writeStartElement(FILTERS);
                    List<ModelNode> filters = serverFactory.require(FILTERS).asList();
                    for (ModelNode currentFilter : filters) {
                        writer.writeStartElement(FILTER);
                        SaslServerDefinitions.ENABLING.marshallAsAttribute(currentFilter, writer);
                        if (currentFilter.hasDefined(PREDEFINED_FILTER)) {
                            writer.writeStartElement(PREDEFINED_FILTER);
                            SaslServerDefinitions.PREDEFINED_FILTER.marshallAsAttribute(currentFilter, writer);
                            writer.writeEndElement();
                        } else if (currentFilter.hasDefined(PATTERN_FILTER)) {
                            writer.writeStartElement(PATTERN_FILTER);
                            SaslServerDefinitions.PATTERN_FILTER.marshallAsAttribute(currentFilter, writer);
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

    private boolean writeMechanismProviderFilteringSaslServerFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(MECHANISM_PROVIDER_FILTERING_SASL_SERVER_FACTORY)) {
            startSasl(started, writer);
            ModelNode saslServerFactories = subsystem.require(MECHANISM_PROVIDER_FILTERING_SASL_SERVER_FACTORY);
            for (String name : saslServerFactories.keys()) {
                ModelNode serverFactory = saslServerFactories.require(name);
                writer.writeStartElement(MECHANISM_PROVIDER_FILTERING_SASL_SERVER_FACTORY);
                writer.writeAttribute(NAME, name);
                SaslServerDefinitions.ENABLING.marshallAsAttribute(serverFactory, writer);
                SaslServerDefinitions.SASL_SERVER_FACTORY.marshallAsAttribute(serverFactory, writer);
                if (serverFactory.hasDefined(FILTERS)) {
                    writer.writeStartElement(FILTERS);
                    List<ModelNode> filters = serverFactory.require(FILTERS).asList();
                    for (ModelNode currentFilter : filters) {
                        writer.writeStartElement(FILTER);
                        SaslServerDefinitions.MECHANISM_NAME.marshallAsAttribute(currentFilter, writer);
                        SaslServerDefinitions.PROVIDER_NAME.marshallAsAttribute(currentFilter, writer);
                        SaslServerDefinitions.PROVIDER_VERSION.marshallAsAttribute(currentFilter, writer);
                        SaslServerDefinitions.VERSION_COMPARISON.marshallAsAttribute(currentFilter, writer);
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

    private boolean writeProviderSaslServerFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(PROVIDER_SASL_SERVER_FACTORY)) {
            startSasl(started, writer);
            ModelNode serverFactories = subsystem.require(PROVIDER_SASL_SERVER_FACTORY);
            for (String name : serverFactories.keys()) {
                ModelNode serverFactory = serverFactories.require(name);
                writer.writeStartElement(PROVIDER_SASL_SERVER_FACTORY);
                writer.writeAttribute(NAME, name);
                SaslServerDefinitions.PROVIDERS.marshallAsAttribute(serverFactory, writer);
                writer.writeEndElement();
            }
            return true;
        }

        return false;
    }

    private boolean writeServiceLoaderSaslServerFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(SERVICE_LOADER_SASL_SERVER_FACTORY)) {
            startSasl(started, writer);
            ModelNode serverFactories = subsystem.require(SERVICE_LOADER_SASL_SERVER_FACTORY);
            for (String name : serverFactories.keys()) {
                ModelNode serverFactory = serverFactories.require(name);
                writer.writeStartElement(SERVICE_LOADER_SASL_SERVER_FACTORY);
                writer.writeAttribute(NAME, name);
                ClassLoadingAttributeDefinitions.MODULE.marshallAsAttribute(serverFactory, writer);
                writer.writeEndElement();
            }
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
