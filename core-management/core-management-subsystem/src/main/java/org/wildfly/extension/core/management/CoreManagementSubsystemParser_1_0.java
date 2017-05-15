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

package org.wildfly.extension.core.management;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;

/**
 * Parser and Marshaller for core-management's {@link #NAMESPACE}.
 *
 * <em>All resources and attributes must be listed explicitly and not through any collections.</em>
 * This ensures that if the resource definitions change in later version (e.g. a new attribute is added),
 * this will have no impact on parsing this specific version of the subsystem.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 */
class CoreManagementSubsystemParser_1_0 extends PersistentResourceXMLParser {

    static final String NAMESPACE = "urn:jboss:domain:core-management:1.0";

    @Override
    public PersistentResourceXMLDescription getParserDescription() {
        return builder(CoreManagementExtension.SUBSYSTEM_PATH, NAMESPACE)
                .addChild(builder(ConfigurationChangeResourceDefinition.PATH).addAttribute(ConfigurationChangeResourceDefinition.MAX_HISTORY))
                .addChild(builder(CoreManagementExtension.PROCESS_STATE_LISTENER_PATH)
                        .addAttribute(ProcessStateListenerResourceDefinition.LISTENER_CLASS)
                        .addAttribute(ProcessStateListenerResourceDefinition.LISTENER_MODULE)
                        .addAttribute(ProcessStateListenerResourceDefinition.PROPERTIES)
                        .addAttribute(ProcessStateListenerResourceDefinition.TIMEOUT))
                .build();
    }
}
