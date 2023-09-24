/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.jboss.as.controller.PersistentResourceXMLDescription.decorator;
import static org.wildfly.extension.elytron.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CONFIGURABLE_SASL_SERVER_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SASL_AUTHENTICATION_FACTORY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SASL_SERVER_FACTORY;

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

    final PersistentResourceXMLDescription parser = decorator(ElytronDescriptionConstants.SASL)
            .addChild(builder(PathElement.pathElement(SASL_AUTHENTICATION_FACTORY))
                    .addAttribute(SaslServerDefinitions.SASL_SERVER_FACTORY)
                    .addAttribute(SaslServerDefinitions.SECURITY_DOMAIN)
                    .addAttribute(AuthenticationFactoryDefinitions.getMechanismConfiguration(SASL_AUTHENTICATION_FACTORY_CAPABILITY)))
            .addChild(builder(PathElement.pathElement(ElytronDescriptionConstants.AGGREGATE_SASL_SERVER_FACTORY))
                    .addAttribute(SaslServerDefinitions.getRawAggregateSaslServerFactoryDefinition().getReferencesAttribute(),
                            new AttributeParsers.NamedStringListParser(SASL_SERVER_FACTORY),
                            new AttributeMarshallers.NamedStringListMarshaller(SASL_SERVER_FACTORY)))
            .addChild(builder(PathElement.pathElement(CONFIGURABLE_SASL_SERVER_FACTORY))
                    .addAttribute(SaslServerDefinitions.SASL_SERVER_FACTORY)
                    .addAttribute(SaslServerDefinitions.PROTOCOL)
                    .addAttribute(SaslServerDefinitions.SERVER_NAME)
                    .addAttribute(CommonAttributes.PROPERTIES)
                    .addAttribute(SaslServerDefinitions.CONFIGURED_FILTERS))
            .addChild(builder(PathElement.pathElement(ElytronDescriptionConstants.MECHANISM_PROVIDER_FILTERING_SASL_SERVER_FACTORY))
                    .addAttribute(SaslServerDefinitions.SASL_SERVER_FACTORY)
                    .addAttribute(SaslServerDefinitions.ENABLING)
                    .addAttribute(SaslServerDefinitions.MECH_PROVIDER_FILTERS))
            .addChild(builder(PathElement.pathElement(ElytronDescriptionConstants.PROVIDER_SASL_SERVER_FACTORY))
                    .addAttribute(SaslServerDefinitions.PROVIDERS))
            .addChild(builder(PathElement.pathElement(ElytronDescriptionConstants.SERVICE_LOADER_SASL_SERVER_FACTORY))
                    .setUseElementsForGroups(false)
                    .addAttribute(ClassLoadingAttributeDefinitions.MODULE))
            .build();

}
