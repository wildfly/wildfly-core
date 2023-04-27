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

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.jboss.as.controller.PersistentResourceXMLDescription.decorator;
import static org.wildfly.extension.elytron.ElytronCommonCapabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronCommonConstants.CONFIGURABLE_SASL_SERVER_FACTORY;
import static org.wildfly.extension.elytron.ElytronCommonConstants.SASL_AUTHENTICATION_FACTORY;
import static org.wildfly.extension.elytron.ElytronCommonConstants.SASL_SERVER_FACTORY;

import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;

/**
 * XML handling for the SASL definitions.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Tomaz Cerar
 */
class SaslParser {

    final PersistentResourceXMLDescription parser = decorator(ElytronCommonConstants.SASL)
            .addChild(builder(PathElement.pathElement(SASL_AUTHENTICATION_FACTORY))
                    .addAttribute(SaslServerDefinitions.SASL_SERVER_FACTORY)
                    .addAttribute(SaslServerDefinitions.SECURITY_DOMAIN)
                    .addAttribute(AuthenticationFactoryDefinitions.getMechanismConfiguration(SASL_AUTHENTICATION_FACTORY_CAPABILITY)))
            .addChild(builder(PathElement.pathElement(ElytronCommonConstants.AGGREGATE_SASL_SERVER_FACTORY))
                    .addAttribute(SaslServerDefinitions.getRawAggregateSaslServerFactoryDefinition().getReferencesAttribute(),
                            new AttributeParsers.NamedStringListParser(SASL_SERVER_FACTORY),
                            new AttributeMarshallers.NamedStringListMarshaller(SASL_SERVER_FACTORY)))
            .addChild(builder(PathElement.pathElement(CONFIGURABLE_SASL_SERVER_FACTORY))
                    .addAttribute(SaslServerDefinitions.SASL_SERVER_FACTORY)
                    .addAttribute(SaslServerDefinitions.PROTOCOL)
                    .addAttribute(SaslServerDefinitions.SERVER_NAME)
                    .addAttribute(CommonAttributes.PROPERTIES)
                    .addAttribute(SaslServerDefinitions.CONFIGURED_FILTERS))
            .addChild(builder(PathElement.pathElement(ElytronCommonConstants.MECHANISM_PROVIDER_FILTERING_SASL_SERVER_FACTORY))
                    .addAttribute(SaslServerDefinitions.SASL_SERVER_FACTORY)
                    .addAttribute(SaslServerDefinitions.ENABLING)
                    .addAttribute(SaslServerDefinitions.MECH_PROVIDER_FILTERS))
            .addChild(builder(PathElement.pathElement(ElytronCommonConstants.PROVIDER_SASL_SERVER_FACTORY))
                    .addAttribute(SaslServerDefinitions.PROVIDERS))
            .addChild(builder(PathElement.pathElement(ElytronCommonConstants.SERVICE_LOADER_SASL_SERVER_FACTORY))
                    .setUseElementsForGroups(false)
                    .addAttribute(ClassLoadingAttributeDefinitions.MODULE))
            .build();

}
