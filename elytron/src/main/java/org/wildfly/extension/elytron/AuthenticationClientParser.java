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

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.jboss.as.controller.PersistentResourceXMLDescription.decorator;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AUTHENTICATION_CONTEXT;

import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;

/**
 * A parser for the authentication client configuration.
 * <p>
 * <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AuthenticationClientParser {

    private final PersistentResourceXMLDescription authenticationConfigurationParser = builder(PathElement.pathElement(AUTHENTICATION_CONFIGURATION))
            .addAttributes(AuthenticationClientDefinitions.AUTHENTICATION_CONFIGURATION_SIMPLE_ATTRIBUTES)
            .addAttribute(AuthenticationClientDefinitions.MECHANISM_PROPERTIES, AttributeParser.PROPERTIES_PARSER, AttributeMarshaller.PROPERTIES_MARSHALLER)
            .addAttribute(AuthenticationClientDefinitions.CREDENTIAL_REFERENCE, AuthenticationClientDefinitions.CREDENTIAL_REFERENCE.getParser(), AuthenticationClientDefinitions.CREDENTIAL_REFERENCE.getMarshaller())
            .build();

    private final PersistentResourceXMLDescription authenticationConfigurationParser_9_0 = builder(PathElement.pathElement(AUTHENTICATION_CONFIGURATION))
            .addAttributes(AuthenticationClientDefinitions.AUTHENTICATION_CONFIGURATION_SIMPLE_ATTRIBUTES)
            .addAttribute(AuthenticationClientDefinitions.MECHANISM_PROPERTIES, AttributeParser.PROPERTIES_PARSER, AttributeMarshaller.PROPERTIES_MARSHALLER)
            .addAttribute(AuthenticationClientDefinitions.CREDENTIAL_REFERENCE, AuthenticationClientDefinitions.CREDENTIAL_REFERENCE.getParser(), AuthenticationClientDefinitions.CREDENTIAL_REFERENCE.getMarshaller())
            .addAttribute(AuthenticationClientDefinitions.WEBSERVICES)
            .build();

    private final PersistentResourceXMLDescription authenticationContextParser = builder(PathElement.pathElement(AUTHENTICATION_CONTEXT))
            .addAttribute(AuthenticationClientDefinitions.CONTEXT_EXTENDS)
            .addAttribute(AuthenticationClientDefinitions.MATCH_RULES, AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER, AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .build();

    final PersistentResourceXMLDescription parser = decorator(ElytronDescriptionConstants.AUTHENTICATION_CLIENT)
            .addChild(authenticationConfigurationParser)
            .addChild(authenticationContextParser)
            .build();

    final PersistentResourceXMLDescription parser_9_0 = decorator(ElytronDescriptionConstants.AUTHENTICATION_CLIENT)
            .addChild(authenticationConfigurationParser_9_0)
            .addChild(authenticationContextParser)
            .build();

}
