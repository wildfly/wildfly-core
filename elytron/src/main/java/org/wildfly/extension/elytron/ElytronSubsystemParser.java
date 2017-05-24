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
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AUDIT_LOGGING;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AUTHENTICATION_CLIENT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CLASS_NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CONFIGURATION;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CREDENTIAL_SECURITY_FACTORIES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CREDENTIAL_STORES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.DEFAULT_AUTHENTICATION_CONTEXT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.DIR_CONTEXTS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.DISALLOWED_PROVIDERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FINAL_PROVIDERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HTTP;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.INITIAL_PROVIDERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MAPPERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MODULE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.POLICY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROPERTY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SASL;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_DOMAIN;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_DOMAINS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_PROPERTIES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_PROPERTY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_REALMS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TLS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.VALUE;
import static org.wildfly.extension.elytron.ElytronExtension.NAMESPACE;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * The subsystem parser, which uses stax to read and write to and from xml
 *
 * <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ElytronSubsystemParser implements XMLElementReader<List<ModelNode>>, XMLElementWriter<SubsystemMarshallingContext> {

    private final AuthenticationClientParser clientParser = new AuthenticationClientParser();
    private final AuditLoggingParser auditLoggingParser = new AuditLoggingParser();
    private final DomainParser domainParser = new DomainParser();
    private final RealmParser realmParser = new RealmParser();
    private final TlsParser tlsParser = new TlsParser();
    private final ProviderParser providerParser = new ProviderParser();
    private final CredentialSecurityFactoryParser credentialSecurityFactoryParser = new CredentialSecurityFactoryParser();
    private final MapperParser mapperParser = new MapperParser();
    private final SaslParser saslParser = new SaslParser();
    private final HttpParser httpParser = new HttpParser();
    private final CredentialStoreParser credentialStoreParser = new CredentialStoreParser();
    private final DirContextParser dirContextParser = new DirContextParser();
    private final PolicyParser policyParser = new PolicyParser();

    /**
     * {@inheritDoc}
     */
    @Override
    public void readElement(XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode subsystemAdd = ElytronExtension.createAddSubsystemOperation();
        operations.add(subsystemAdd);
        ModelNode parentAddress = subsystemAdd.get(OP_ADDR);

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case DEFAULT_AUTHENTICATION_CONTEXT:
                        ElytronDefinition.DEFAULT_AUTHENTICATION_CONTEXT.parseAndSetParameter(value, subsystemAdd, reader);
                        break;
                    case INITIAL_PROVIDERS:
                        ElytronDefinition.INITIAL_PROVIDERS.parseAndSetParameter(value, subsystemAdd, reader);
                        break;
                    case FINAL_PROVIDERS:
                        ElytronDefinition.FINAL_PROVIDERS.parseAndSetParameter(value, subsystemAdd, reader);
                        break;
                    case DISALLOWED_PROVIDERS:
                        ElytronDefinition.DISALLOWED_PROVIDERS.parseAndSetParameter(value, subsystemAdd, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        Set<String> foundElements = new HashSet<>();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (foundElements.add(localName) == false) {
                throw unexpectedElement(reader);
            }

            switch (reader.getLocalName()) {
                case SECURITY_PROPERTIES:
                    readSecurityProperties(parentAddress, reader, operations);
                    break;
                case AUTHENTICATION_CLIENT:
                    clientParser.readAuthenticationClient(parentAddress, reader, operations);
                    break;
                case PROVIDERS:
                    providerParser.readProviders(parentAddress, reader, operations);
                    break;
                case AUDIT_LOGGING:
                    auditLoggingParser.readAuditLogging(parentAddress, reader, operations);
                    break;
                case SECURITY_DOMAINS:
                    readDomains(parentAddress, reader, operations);
                    break;
                case SECURITY_REALMS:
                    //realmParser.realmParser.parse(reader, PathAddress.pathAddress(parentAddress), operations);
                    realmParser.readRealms(parentAddress, reader, operations);
                    break;
                case CREDENTIAL_SECURITY_FACTORIES:
                    credentialSecurityFactoryParser.readCredentialSecurityFactories(parentAddress, reader, operations);
                    break;
                case MAPPERS:
                    mapperParser.readMappers(parentAddress, reader, operations);
                    break;
                case HTTP:
                    httpParser.readHttp(parentAddress, reader, operations);
                    break;
                case SASL:
                    saslParser.readSasl(parentAddress, reader, operations);
                    break;
                case TLS:
                    tlsParser.readTls(parentAddress, reader, operations);
                    break;
                case CREDENTIAL_STORES:
                    credentialStoreParser.readCredentialStores(parentAddress, reader, operations);
                    break;
                case DIR_CONTEXTS:
                    dirContextParser.readDirContexts(parentAddress, reader, operations);
                    break;
                case POLICY:
                    policyParser.readPolicy(parentAddress, reader, operations);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    public void readSecurityProperties(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        requireNoAttributes(reader);
        while(reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (SECURITY_PROPERTY.equals(localName)) {
                ModelNode operation = new ModelNode();
                operation.get(OP).set(ADD);
                Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, VALUE }));
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
                            case VALUE:
                                SecurityPropertyResourceDefinition.VALUE.parseAndSetParameter(value, operation, reader);
                                break;
                            default:
                                throw unexpectedAttribute(reader, i);
                        }
                    }
                }

                if (requiredAttributes.isEmpty() == false) {
                    throw missingRequired(reader, requiredAttributes);
                }
                operation.get(OP_ADDR).set(parentAddress).add(SECURITY_PROPERTY, name);
                operations.add(operation);

                requireNoContent(reader);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private void readDomains(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (SECURITY_DOMAIN.equals(localName)) {
                domainParser.readDomain(parentAddress, reader, operations);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    /*
     * Utility Parsing Methods
     */

    static void readCustomComponent(String componentType, ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addComponent = new ModelNode();
        addComponent.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, CLASS_NAME }));
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
                    case MODULE:
                        ClassLoadingAttributeDefinitions.MODULE.parseAndSetParameter(value, addComponent, reader);
                        break;
                    case CLASS_NAME:
                        ClassLoadingAttributeDefinitions.CLASS_NAME.parseAndSetParameter(value, addComponent, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addComponent.get(OP_ADDR).set(parentAddress).add(componentType, name);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            switch (localName) {
                case CONFIGURATION:
                    requireNoAttributes(reader);
                    parseConfiguration(addComponent, reader);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }

        operations.add(addComponent);
    }

    private static void parseConfiguration(ModelNode addOperation, XMLExtendedStreamReader reader) throws XMLStreamException {
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            switch (localName) {
                case PROPERTY:
                    parsePropertyElement(addOperation, reader);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private static void parsePropertyElement(ModelNode addOperation, XMLExtendedStreamReader reader) throws XMLStreamException {
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

        addOperation.get(CONFIGURATION).add(key, new ModelNode(value));
    }

    static void writeCustomComponent(String elementName, String componentName, ModelNode component, XMLExtendedStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement(elementName);
        writer.writeAttribute(NAME, componentName);
        ClassLoadingAttributeDefinitions.MODULE.marshallAsAttribute(component, writer);
        ClassLoadingAttributeDefinitions.CLASS_NAME.marshallAsAttribute(component, writer);
        CustomComponentDefinition.CONFIGURATION.marshallAsElement(component, writer);
        writer.writeEndElement();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeContent(XMLExtendedStreamWriter writer, SubsystemMarshallingContext context) throws XMLStreamException {
        context.startSubsystemElement(ElytronExtension.NAMESPACE, false);

        ModelNode model = context.getModelNode();

        ElytronDefinition.DEFAULT_AUTHENTICATION_CONTEXT.marshallAsAttribute(model, writer);
        ElytronDefinition.INITIAL_PROVIDERS.marshallAsAttribute(model, writer);
        ElytronDefinition.FINAL_PROVIDERS.marshallAsAttribute(model, writer);
        ElytronDefinition.DISALLOWED_PROVIDERS.getAttributeMarshaller().marshallAsAttribute(ElytronDefinition.DISALLOWED_PROVIDERS, model, true, writer);

        if (model.hasDefined(SECURITY_PROPERTY)) {
            writer.writeStartElement(SECURITY_PROPERTIES);
            ModelNode securityProperties = model.require(SECURITY_PROPERTY);
            for (String name : securityProperties.keys()) {
                writer.writeEmptyElement(SECURITY_PROPERTY);
                writer.writeAttribute(NAME, name);
                SecurityPropertyResourceDefinition.VALUE.marshallAsAttribute(securityProperties.require(name), writer);
            }

            writer.writeEndElement();
        }

        clientParser.writeAuthenticationClient(model, writer);
        providerParser.writeProviders(model, writer);
        auditLoggingParser.writeAuditLogging(model, writer);

        if (model.hasDefined(SECURITY_DOMAIN)) {
            writer.writeStartElement(SECURITY_DOMAINS);
            ModelNode securityDomains = model.require(SECURITY_DOMAIN);
            for (String name : securityDomains.keys()) {
                ModelNode domain = securityDomains.require(name);
                domainParser.writeDomain(name, domain, writer);
            }
            writer.writeEndElement();
        }

        realmParser.writeRealms(model, writer);
        credentialSecurityFactoryParser.writeCredentialSecurityFactories(model, writer);
        mapperParser.writeMappers(model, writer);
        httpParser.writeHttp(model, writer);
        saslParser.writeSasl(model, writer);
        tlsParser.writeTLS(model, writer);
        credentialStoreParser.writeCredentialStores(model, writer);
        dirContextParser.writeDirContexts(model, writer);
        policyParser.writePolicy(model, writer);

        writer.writeEndElement();
    }

    static void verifyNamespace(final XMLExtendedStreamReader reader) throws XMLStreamException {
        if ((NAMESPACE.equals(reader.getNamespaceURI())) == false) {
            throw unexpectedElement(reader);
        }
    }
}
