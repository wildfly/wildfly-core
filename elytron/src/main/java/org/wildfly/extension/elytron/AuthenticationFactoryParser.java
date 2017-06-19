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

import static org.wildfly.extension.elytron.Capabilities.HTTP_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.HTTP_AUTHENTICATION_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MECHANISM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MECHANISM_CONFIGURATION;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MECHANISM_CONFIGURATIONS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MECHANISM_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MECHANISM_REALM_CONFIGURATIONS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SASL_AUTHENTICATION_FACTORY;

import java.util.List;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AuthenticationFactoryParser {

    private PersistentResourceXMLDescription httpServerMechanismFactoryParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(HTTP_AUTHENTICATION_FACTORY))
            .addAttribute(AuthenticationFactoryDefinitions.BASE_SECURITY_DOMAIN_REF)
            .addAttribute(AuthenticationFactoryDefinitions.HTTP_SERVER_MECHANISM_FACTORY)
            .addAttribute(AuthenticationFactoryDefinitions.getMechanismConfiguration(HTTP_AUTHENTICATION_FACTORY_CAPABILITY))
            .build();

    private PersistentResourceXMLDescription saslAuthenticationFactoryyParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(SASL_AUTHENTICATION_FACTORY))
            .addAttribute(SaslServerDefinitions.SASL_SERVER_FACTORY)
            .addAttribute(SaslServerDefinitions.SECURITY_DOMAIN)
            .addAttribute(AuthenticationFactoryDefinitions.getMechanismConfiguration(SASL_AUTHENTICATION_FACTORY_CAPABILITY))
            .build();

    void readHttpAuthenticationFactoryElement(PathAddress parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        httpServerMechanismFactoryParser.parse(reader, parentAddress, operations);
    }

    void readSaslAuthenticationFactoryElement(PathAddress parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        saslAuthenticationFactoryyParser.parse(reader, parentAddress, operations);

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
        //httpServerMechanismFactoryParser.persist(writer,subsystem);
        //return false;
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
