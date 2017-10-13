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
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AUDIT_LOGGING;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AUTHENTICATION_CLIENT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CREDENTIAL_SECURITY_FACTORIES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CREDENTIAL_STORES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.DEFAULT_AUTHENTICATION_CONTEXT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.DIR_CONTEXTS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.DISALLOWED_PROVIDERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FINAL_PROVIDERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HTTP;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.INITIAL_PROVIDERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MAPPERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.POLICY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SASL;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_DOMAIN;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_DOMAINS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_PROPERTIES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_REALMS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TLS;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.operations.common.Util;
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
 * @author Tomaz Cerar
 */
class ElytronSubsystemParser implements XMLElementReader<List<ModelNode>>, XMLElementWriter<SubsystemMarshallingContext> {

    private final AuthenticationClientParser clientParser = new AuthenticationClientParser(this);
    private final AuditLoggingParser auditLoggingParser = new AuditLoggingParser(this);

    private final PersistentResourceXMLDescription domainParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(SECURITY_DOMAIN))
                .setXmlWrapperElement(SECURITY_DOMAINS)
                .addAttribute(DomainDefinition.DEFAULT_REALM)
                .addAttribute(DomainDefinition.PERMISSION_MAPPER)
                .addAttribute(DomainDefinition.PRE_REALM_PRINCIPAL_TRANSFORMER)
                .addAttribute(DomainDefinition.POST_REALM_PRINCIPAL_TRANSFORMER)
                .addAttribute(DomainDefinition.PRINCIPAL_DECODER)
                .addAttribute(DomainDefinition.REALM_MAPPER)
                .addAttribute(DomainDefinition.ROLE_MAPPER)
                .addAttribute(DomainDefinition.TRUSTED_SECURITY_DOMAINS)
                .addAttribute(DomainDefinition.OUTFLOW_ANONYMOUS)
                .addAttribute(DomainDefinition.OUTFLOW_SECURITY_DOMAINS)
                .addAttribute(DomainDefinition.SECURITY_EVENT_LISTENER)
                .addAttribute(DomainDefinition.REALMS)
                .build();

    private final RealmParser realmParser = new RealmParser(this);
    private final TlsParser tlsParser = new TlsParser(this);
    private final ProviderParser providerParser = new ProviderParser(this);
    private final CredentialSecurityFactoryParser credentialSecurityFactoryParser = new CredentialSecurityFactoryParser(this);
    private final SaslParser saslParser = new SaslParser(this);
    private final HttpParser httpParser = new HttpParser(this);
    private final CredentialStoreParser credentialStoreParser = new CredentialStoreParser(this);
    private final DirContextParser dirContextParser = new DirContextParser(this);
    private final PolicyParserTemp policyParser = new PolicyParserTemp(this);
    private final String namespace;
    private final MapperParser mapperParser;

    ElytronSubsystemParser(String namespace) {
        this.namespace = namespace;
        this.mapperParser = new MapperParser(this, ElytronExtension.NAMESPACE_1_0.equals(namespace));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readElement(XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        PathAddress parentAddress = PathAddress.pathAddress(ElytronExtension.SUBSYSTEM_PATH);
        ModelNode subsystemAdd = Util.createAddOperation(parentAddress);
        operations.add(subsystemAdd);

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
                        ElytronDefinition.DISALLOWED_PROVIDERS.getParser().parseAndSetParameter(ElytronDefinition.DISALLOWED_PROVIDERS,value, subsystemAdd, reader);
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
                    ElytronDefinition.SECURITY_PROPERTIES.getParser().parseElement(ElytronDefinition.SECURITY_PROPERTIES, reader, subsystemAdd);
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
                    realmParser.readRealms(parentAddress, reader, operations);
                    break;
                case CREDENTIAL_SECURITY_FACTORIES:
                    credentialSecurityFactoryParser.readCredentialSecurityFactories(parentAddress, reader, operations);
                    break;
                case MAPPERS:
                    mapperParser.readMappers(parentAddress, reader, operations);
                    break;
                case HTTP:
                    httpParser.readHttp(PathAddress.pathAddress(parentAddress), reader, operations);
                    break;
                case SASL:
                    saslParser.readSasl(parentAddress, reader, operations);
                    break;
                case TLS:
                    tlsParser.readTls(parentAddress, reader, operations);
                    break;
                case CREDENTIAL_STORES:
                    credentialStoreParser.readCredentialStores(parentAddress.toModelNode(), reader, operations);
                    break;
                case DIR_CONTEXTS:
                    dirContextParser.readDirContexts(parentAddress.toModelNode(), reader, operations);
                    break;
                case POLICY:
                    policyParser.readPolicy(parentAddress.toModelNode(), reader, operations);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void readDomains(PathAddress parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (SECURITY_DOMAIN.equals(localName)) {
                domainParser.parse(reader, parentAddress, operations);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    private static PersistentResourceXMLDescription getCustomComponentParser(String componentType){
        return PersistentResourceXMLDescription.builder(PathElement.pathElement(componentType))
                .setUseElementsForGroups(false)
                .addAttribute(ClassLoadingAttributeDefinitions.MODULE)
                .addAttribute(ClassLoadingAttributeDefinitions.CLASS_NAME)
                .addAttribute(CustomComponentDefinition.CONFIGURATION)
                .build();
    }
    /*
     * Utility Parsing Methods
     */

    static void readCustomComponent(String componentType, PathAddress parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        getCustomComponentParser(componentType).parse(reader, parentAddress, operations);
    }

    static void writeCustomComponent(String elementName, String componentType, ModelNode component, XMLExtendedStreamWriter writer) throws XMLStreamException {
        //todo simplify this
        writer.writeStartElement(elementName);
        writer.writeAttribute(NAME, componentType);
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
        context.startSubsystemElement(namespace, false);

        ModelNode model = context.getModelNode();

        ElytronDefinition.DEFAULT_AUTHENTICATION_CONTEXT.marshallAsAttribute(model, writer);
        ElytronDefinition.INITIAL_PROVIDERS.marshallAsAttribute(model, writer);
        ElytronDefinition.FINAL_PROVIDERS.marshallAsAttribute(model, writer);
        ElytronDefinition.DISALLOWED_PROVIDERS.getMarshaller().marshallAsAttribute(ElytronDefinition.DISALLOWED_PROVIDERS, model, true, writer);
        ElytronDefinition.SECURITY_PROPERTIES.getMarshaller().marshall(ElytronDefinition.SECURITY_PROPERTIES, model, true, writer);
        clientParser.writeAuthenticationClient(model, writer);
        providerParser.writeProviders(model, writer);
        auditLoggingParser.writeAuditLogging(model, writer);

        domainParser.persist(writer, model);

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

    String getNamespace() {
        return namespace;
    }

    void verifyNamespace(final XMLExtendedStreamReader reader) throws XMLStreamException {
        if (!(namespace.equals(reader.getNamespaceURI()))) {
            throw unexpectedElement(reader);
        }
    }
}
