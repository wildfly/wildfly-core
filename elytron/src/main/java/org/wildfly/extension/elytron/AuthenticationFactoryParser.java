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
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CREDENTIAL_SECURITY_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FINAL_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HOST_NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HTTP_AUTHENTICATION_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HTTP_SERVER_MECHANISM_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MECHANISM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MECHANISM_CONFIGURATION;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MECHANISM_CONFIGURATIONS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MECHANISM_NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MECHANISM_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MECHANISM_REALM_CONFIGURATIONS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.POST_REALM_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PRE_REALM_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROTOCOL;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REALM_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REALM_NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SASL_AUTHENTICATION_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SASL_SERVER_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_DOMAIN;
import static org.wildfly.extension.elytron.ElytronSubsystemParser.verifyNamespace;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 *
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AuthenticationFactoryParser {

    private void readMechanismRealmElement(ModelNode mechanismRealmConfiguration, XMLExtendedStreamReader reader) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case REALM_NAME:
                        AuthenticationFactoryDefinitions.REALM_NAME.parseAndSetParameter(value, mechanismRealmConfiguration, reader);
                        break;
                    case PRE_REALM_PRINCIPAL_TRANSFORMER:
                        AuthenticationFactoryDefinitions.BASE_PRE_REALM_PRINCIPAL_TRANSFORMER.parseAndSetParameter(value, mechanismRealmConfiguration, reader);
                        break;
                    case POST_REALM_PRINCIPAL_TRANSFORMER:
                        AuthenticationFactoryDefinitions.BASE_POST_REALM_PRINCIPAL_TRANSFORMER.parseAndSetParameter(value, mechanismRealmConfiguration, reader);
                        break;
                    case FINAL_PRINCIPAL_TRANSFORMER:
                        AuthenticationFactoryDefinitions.BASE_FINAL_PRINCIPAL_TRANSFORMER.parseAndSetParameter(value, mechanismRealmConfiguration, reader);
                        break;
                    case REALM_MAPPER:
                        AuthenticationFactoryDefinitions.BASE_REALM_MAPPER.parseAndSetParameter(value, mechanismRealmConfiguration, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (mechanismRealmConfiguration.hasDefined(REALM_NAME) == false) {
            throw missingRequired(reader, Collections.singleton(REALM_NAME));
        }

        requireNoContent(reader);
    }

    private void readMechanismElement(ModelNode mechanismConfiguration, XMLExtendedStreamReader reader) throws XMLStreamException {

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case MECHANISM_NAME:
                        AuthenticationFactoryDefinitions.MECHANISM_NAME.parseAndSetParameter(value, mechanismConfiguration, reader);
                        break;
                    case HOST_NAME:
                        AuthenticationFactoryDefinitions.HOST_NAME.parseAndSetParameter(value, mechanismConfiguration, reader);
                        break;
                    case PROTOCOL:
                        AuthenticationFactoryDefinitions.PROTOCOL.parseAndSetParameter(value, mechanismConfiguration, reader);
                        break;
                    case PRE_REALM_PRINCIPAL_TRANSFORMER:
                        AuthenticationFactoryDefinitions.BASE_PRE_REALM_PRINCIPAL_TRANSFORMER.parseAndSetParameter(value, mechanismConfiguration, reader);
                        break;
                    case POST_REALM_PRINCIPAL_TRANSFORMER:
                        AuthenticationFactoryDefinitions.BASE_POST_REALM_PRINCIPAL_TRANSFORMER.parseAndSetParameter(value, mechanismConfiguration, reader);
                        break;
                    case FINAL_PRINCIPAL_TRANSFORMER:
                        AuthenticationFactoryDefinitions.BASE_FINAL_PRINCIPAL_TRANSFORMER.parseAndSetParameter(value, mechanismConfiguration, reader);
                        break;
                    case REALM_MAPPER:
                        AuthenticationFactoryDefinitions.BASE_REALM_MAPPER.parseAndSetParameter(value, mechanismConfiguration, reader);
                        break;
                    case CREDENTIAL_SECURITY_FACTORY:
                        AuthenticationFactoryDefinitions.BASE_CREDENTIAL_SECURITY_FACTORY.parseAndSetParameter(value, mechanismConfiguration, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (MECHANISM_REALM.equals(localName)) {
                ModelNode mechanismRealmConfiguration = new ModelNode();
                readMechanismRealmElement(mechanismRealmConfiguration, reader);
                mechanismConfiguration.get(MECHANISM_REALM_CONFIGURATIONS).add(mechanismRealmConfiguration);
            }
        }
    }


    private void readMechanismConfigurationElement(ModelNode addOperation, XMLExtendedStreamReader reader) throws XMLStreamException {
        requireNoAttributes(reader);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (MECHANISM.equals(localName)) {
               ModelNode mechanismConfiguration = new ModelNode();
               readMechanismElement(mechanismConfiguration, reader);
               addOperation.get(MECHANISM_CONFIGURATIONS).add(mechanismConfiguration);
            }
        }

    }

    private void attemptReadMechanismConfigurationElement(ModelNode addOperation, XMLExtendedStreamReader reader) throws XMLStreamException {
        boolean mechanismConfigurationAdded = false;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (MECHANISM_CONFIGURATION.equals(localName) && mechanismConfigurationAdded == false) {
                mechanismConfigurationAdded = true;
               readMechanismConfigurationElement(addOperation, reader);
            } else {
                throw unexpectedElement(reader);
            }

        }
    }

    void readHttpAuthenticationFactoryElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addOperation = new ModelNode();
        addOperation.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, SECURITY_DOMAIN, HTTP_SERVER_MECHANISM_FACTORY }));

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
                        AuthenticationFactoryDefinitions.HTTP_SERVER_MECHANISM_FACTORY.parseAndSetParameter(value, addOperation, reader);
                        break;
                    case SECURITY_DOMAIN:
                        AuthenticationFactoryDefinitions.BASE_SECURITY_DOMAIN_REF.parseAndSetParameter(value, addOperation, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addOperation.get(OP_ADDR).set(parentAddress).add(HTTP_AUTHENTICATION_FACTORY, name);

        attemptReadMechanismConfigurationElement(addOperation, reader);

        operations.add(addOperation);

    }

    void readSaslAuthenticationFactoryElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addOperation = new ModelNode();
        addOperation.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, SECURITY_DOMAIN, SASL_SERVER_FACTORY }));

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
                    case SECURITY_DOMAIN:
                        SaslServerDefinitions.SECURITY_DOMAIN.parseAndSetParameter(value, addOperation, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addOperation.get(OP_ADDR).set(parentAddress).add(SASL_AUTHENTICATION_FACTORY, name);

        attemptReadMechanismConfigurationElement(addOperation, reader);

        operations.add(addOperation);

    }

    private void writeMechanismConfiguration(ModelNode configuration, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (configuration.hasDefined(MECHANISM_CONFIGURATIONS)) {
            writer.writeStartElement(MECHANISM_CONFIGURATION);
            for (ModelNode currentMechConfig : configuration.require(MECHANISM_CONFIGURATIONS).asList()) {
                writer.writeStartElement(MECHANISM);
                AuthenticationFactoryDefinitions.MECHANISM_NAME.marshallAsAttribute(currentMechConfig, writer);
                AuthenticationFactoryDefinitions.HOST_NAME.marshallAsAttribute(currentMechConfig, writer);
                AuthenticationFactoryDefinitions.PROTOCOL.marshallAsAttribute(currentMechConfig, writer);
                AuthenticationFactoryDefinitions.BASE_PRE_REALM_PRINCIPAL_TRANSFORMER.marshallAsAttribute(currentMechConfig, writer);
                AuthenticationFactoryDefinitions.BASE_POST_REALM_PRINCIPAL_TRANSFORMER.marshallAsAttribute(currentMechConfig, writer);
                AuthenticationFactoryDefinitions.BASE_FINAL_PRINCIPAL_TRANSFORMER.marshallAsAttribute(currentMechConfig, writer);
                AuthenticationFactoryDefinitions.BASE_REALM_MAPPER.marshallAsAttribute(currentMechConfig, writer);
                AuthenticationFactoryDefinitions.BASE_CREDENTIAL_SECURITY_FACTORY.marshallAsAttribute(currentMechConfig, writer);
                if (currentMechConfig.hasDefined(MECHANISM_REALM_CONFIGURATIONS)) {
                    for (ModelNode currentMechRealmConfig : currentMechConfig.require(MECHANISM_REALM_CONFIGURATIONS).asList()) {
                        writer.writeStartElement(MECHANISM_REALM);
                        AuthenticationFactoryDefinitions.REALM_NAME.marshallAsAttribute(currentMechRealmConfig, writer);
                        AuthenticationFactoryDefinitions.BASE_PRE_REALM_PRINCIPAL_TRANSFORMER.marshallAsAttribute(currentMechRealmConfig, writer);
                        AuthenticationFactoryDefinitions.BASE_POST_REALM_PRINCIPAL_TRANSFORMER.marshallAsAttribute(currentMechRealmConfig, writer);
                        AuthenticationFactoryDefinitions.BASE_FINAL_PRINCIPAL_TRANSFORMER.marshallAsAttribute(currentMechRealmConfig, writer);
                        AuthenticationFactoryDefinitions.BASE_REALM_MAPPER.marshallAsAttribute(currentMechRealmConfig, writer);
                        writer.writeEndElement();
                    }
                }
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
    }

    boolean writeHttpAuthenticationFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer, WrapperWriter wrapperWriter) throws XMLStreamException {
        if (subsystem.hasDefined(HTTP_AUTHENTICATION_FACTORY)) {
            wrapperWriter.start(started);
            ModelNode httpAuthenticationFactory = subsystem.require(HTTP_AUTHENTICATION_FACTORY);
            for (String name : httpAuthenticationFactory.keys()) {
                ModelNode configuration = httpAuthenticationFactory.require(name);
                writer.writeStartElement(HTTP_AUTHENTICATION_FACTORY);
                writer.writeAttribute(NAME, name);
                AuthenticationFactoryDefinitions.HTTP_SERVER_MECHANISM_FACTORY.marshallAsAttribute(configuration, writer);
                AuthenticationFactoryDefinitions.BASE_SECURITY_DOMAIN_REF.marshallAsAttribute(configuration, writer);
                writeMechanismConfiguration(configuration, writer);
                writer.writeEndElement();
            }
            return true;
        }

        return false;
    }

    boolean writeSaslAuthenticationFactory(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer, WrapperWriter wrapperWriter) throws XMLStreamException {
        if (subsystem.hasDefined(SASL_AUTHENTICATION_FACTORY)) {
            wrapperWriter.start(started);
            ModelNode securityDomainSaslConfigurationInstances = subsystem.require(SASL_AUTHENTICATION_FACTORY);
            for (String name : securityDomainSaslConfigurationInstances.keys()) {
                ModelNode configuration = securityDomainSaslConfigurationInstances.require(name);
                writer.writeStartElement(SASL_AUTHENTICATION_FACTORY);
                writer.writeAttribute(NAME, name);
                SaslServerDefinitions.SASL_SERVER_FACTORY.marshallAsAttribute(configuration, writer);
                SaslServerDefinitions.SECURITY_DOMAIN.marshallAsAttribute(configuration, writer);
                writeMechanismConfiguration(configuration, writer);
                writer.writeEndElement();
            }
            return true;
        }

        return false;
    }

    @FunctionalInterface
    interface WrapperWriter {

        void start(boolean started) throws XMLStreamException;

    }

}
