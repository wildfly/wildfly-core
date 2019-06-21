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

import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;

/**
 * XML parser description for the discovery subsystem.
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:paul.ferraro@redhat.com">Paul Ferraro</a>
 */
final class DiscoverySubsystemParser extends PersistentResourceXMLParser {

    private final DiscoverySchema schema;

    DiscoverySubsystemParser(DiscoverySchema schema) {
        this.schema = schema;
    }

    @Override
    public PersistentResourceXMLDescription getParserDescription() {
        return builder(DiscoverySubsystemDefinition.PATH, this.schema.getNamespaceUri())
                .addChild(
                        builder(StaticDiscoveryProviderDefinition.PATH)
                                .addAttribute(StaticDiscoveryProviderDefinition.SERVICES, AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER, AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
                )
                .addChild(
                        builder(AggregateDiscoveryProviderDefinition.PATH)
                                .addAttribute(AggregateDiscoveryProviderDefinition.PROVIDER_NAMES)
                )
                .build();
    }
}
