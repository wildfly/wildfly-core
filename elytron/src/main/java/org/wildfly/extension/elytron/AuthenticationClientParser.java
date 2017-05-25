/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
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
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AUTHENTICATION_CLIENT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AUTHENTICATION_CONTEXT;
import static org.wildfly.extension.elytron.ElytronSubsystemParser.verifyNamespace;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A parser for the authentication client configuration.
 * <p>
 * <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AuthenticationClientParser {

    private final PersistentResourceXMLDescription authenticationConfigurationParser = builder(PathElement.pathElement(AUTHENTICATION_CONFIGURATION), null)
            .addAttributes(AuthenticationClientDefinitions.AUTHENTICATION_CONFIGURATION_SIMPLE_ATTRIBUTES)
            .addAttribute(AuthenticationClientDefinitions.MECHANISM_PROPERTIES, AttributeParser.PROPERTIES_PARSER, AttributeMarshaller.PROPERTIES_MARSHALLER)
            .addAttribute(AuthenticationClientDefinitions.CREDENTIAL_REFERENCE, AuthenticationClientDefinitions.CREDENTIAL_REFERENCE.getParser(), AuthenticationClientDefinitions.CREDENTIAL_REFERENCE.getAttributeMarshaller())
            .build();

    private final PersistentResourceXMLDescription authenticationContextParser = builder(PathElement.pathElement(AUTHENTICATION_CONTEXT), null)
            .addAttribute(AuthenticationClientDefinitions.CONTEXT_EXTENDS)
            .addAttribute(AuthenticationClientDefinitions.MATCH_RULES, AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER, AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .build();

    void readAuthenticationClient(ModelNode parentAddressNode, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            PathAddress parentAddress = PathAddress.pathAddress(parentAddressNode);
            switch (localName) {
                case AUTHENTICATION_CONFIGURATION:
                    authenticationConfigurationParser.parse(reader, parentAddress, operations);
                    break;
                case AUTHENTICATION_CONTEXT:
                    authenticationContextParser.parse(reader, parentAddress, operations);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    void writeAuthenticationClient(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (shouldWrite(subsystem)==false) {
            return;
        }

        writer.writeStartElement(AUTHENTICATION_CLIENT);

        authenticationConfigurationParser.persist(writer, subsystem);
        authenticationContextParser.persist(writer, subsystem);

        writer.writeEndElement();
    }

    private boolean shouldWrite(ModelNode subsystem) {
        return subsystem.hasDefined(AUTHENTICATION_CONFIGURATION) || subsystem.hasDefined(AUTHENTICATION_CONTEXT);
    }

}
