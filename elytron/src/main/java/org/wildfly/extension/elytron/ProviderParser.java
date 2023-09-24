/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.jboss.as.controller.PersistentResourceXMLDescription.decorator;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PROVIDERS;

import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;

/**
 * XML Parser and Marshaller for Provider configuration.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Tomaz Cerar
 */
class ProviderParser {

    private final PersistentResourceXMLDescription providerLoaderParser = builder(PathElement.pathElement(ElytronDescriptionConstants.PROVIDER_LOADER))
            .setUseElementsForGroups(false)
            .addAttributes(ClassLoadingAttributeDefinitions.MODULE, ClassLoadingAttributeDefinitions.CLASS_NAMES, ProviderDefinitions.PATH, ProviderDefinitions.RELATIVE_TO,
                    ProviderDefinitions.ARGUMENT, ProviderDefinitions.CONFIGURATION)
            .build();
    private final PersistentResourceXMLDescription aggregateProviders = builder(PathElement.pathElement(ElytronDescriptionConstants.AGGREGATE_PROVIDERS))
            .addAttribute(ProviderDefinitions.REFERENCES,
                    new AttributeParsers.NamedStringListParser(PROVIDERS),
                    new AttributeMarshallers.NamedStringListMarshaller(PROVIDERS))
            .build();
    final PersistentResourceXMLDescription parser = decorator(ElytronDescriptionConstants.PROVIDERS)
            .addChild(aggregateProviders)
            .addChild(providerLoaderParser)
            .build();

    ProviderParser() {

    }

}
