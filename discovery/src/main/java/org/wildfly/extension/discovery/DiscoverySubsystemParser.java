/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.extension.discovery;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.wildfly.extension.discovery.DiscoveryExtension.AGGREGATE_PROVIDER;
import static org.wildfly.extension.discovery.DiscoveryExtension.DISCOVERY;
import static org.wildfly.extension.discovery.DiscoveryExtension.STATIC_PROVIDER;

import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class DiscoverySubsystemParser extends PersistentResourceXMLParser {

    private static final PersistentResourceXMLDescription xmlDescription;

    static {
        xmlDescription = builder(PathElement.pathElement(SUBSYSTEM, DISCOVERY), DiscoveryExtension.NAMESPACE)
            .addChild(
                builder(PathElement.pathElement(STATIC_PROVIDER))
                    .addAttribute(StaticProviderDefinition.SERVICES, AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER, AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            )
            .addChild(
                builder(PathElement.pathElement(AGGREGATE_PROVIDER))
                    .addAttribute(AggregateProviderDefinition.PROVIDER_NAMES)
            )
            .build();
    }

    public PersistentResourceXMLDescription getParserDescription() {
        return xmlDescription;
    }
}
