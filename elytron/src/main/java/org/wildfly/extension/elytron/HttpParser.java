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

import static org.jboss.as.controller.PersistentResourceXMLDescription.decorator;
import static org.wildfly.extension.elytron.ElytronCommonCapabilities.HTTP_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronCommonConstants.CONFIGURABLE_HTTP_SERVER_MECHANISM_FACTORY;
import static org.wildfly.extension.elytron.ElytronCommonConstants.HTTP_AUTHENTICATION_FACTORY;
import static org.wildfly.extension.elytron.ElytronCommonConstants.HTTP_SERVER_MECHANISM_FACTORY;
import static org.wildfly.extension.elytron.HttpServerDefinitions.CONFIGURED_FILTERS;

import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;

/**
 * XML handling of the HTTP element.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Tomaz Cerar
 */
class HttpParser {

    private PersistentResourceXMLDescription httpServerMechanismFactoryParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(HTTP_AUTHENTICATION_FACTORY))
            .addAttribute(AuthenticationFactoryDefinitions.BASE_SECURITY_DOMAIN_REF)
            .addAttribute(AuthenticationFactoryDefinitions.HTTP_SERVER_MECHANISM_FACTORY)
            .addAttribute(AuthenticationFactoryDefinitions.getMechanismConfiguration(HTTP_AUTHENTICATION_FACTORY_CAPABILITY))
            .build();

    private PersistentResourceXMLDescription aggregateHttpServerMechanismFactory = PersistentResourceXMLDescription.builder(PathElement.pathElement(ElytronCommonConstants.AGGREGATE_HTTP_SERVER_MECHANISM_FACTORY))
            .addAttribute(HttpServerDefinitions.getRawAggregateHttpServerFactoryDefinition().getReferencesAttribute(),
                    new AttributeParsers.NamedStringListParser(HTTP_SERVER_MECHANISM_FACTORY),
                    new AttributeMarshallers.NamedStringListMarshaller(HTTP_SERVER_MECHANISM_FACTORY))
            .build();

    private PersistentResourceXMLDescription configurableHttpServerMechanismFactoryParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(CONFIGURABLE_HTTP_SERVER_MECHANISM_FACTORY))
            .addAttribute(HttpServerDefinitions.HTTP_SERVER_FACTORY)
            .addAttribute(CommonAttributes.PROPERTIES)
            .addAttribute(CONFIGURED_FILTERS)
            .build();

    private PersistentResourceXMLDescription providerHttpServerMechanismFactoryParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(ElytronCommonConstants.PROVIDER_HTTP_SERVER_MECHANISM_FACTORY))
            .addAttribute(HttpServerDefinitions.PROVIDERS)
            .build();

    private PersistentResourceXMLDescription serviceLoaderHttpServerMechanismFactoryParser = PersistentResourceXMLDescription.builder(PathElement.pathElement(ElytronCommonConstants.SERVICE_LOADER_HTTP_SERVER_MECHANISM_FACTORY))
            .setUseElementsForGroups(false)
            .addAttribute(ClassLoadingAttributeDefinitions.MODULE)
            .build();

    final PersistentResourceXMLDescription parser = decorator(ElytronCommonConstants.HTTP)
            .addChild(httpServerMechanismFactoryParser)
            .addChild(aggregateHttpServerMechanismFactory)
            .addChild(configurableHttpServerMechanismFactoryParser)
            .addChild(providerHttpServerMechanismFactoryParser)
            .addChild(serviceLoaderHttpServerMechanismFactoryParser)
            .build();

    HttpParser() {

    }


}
