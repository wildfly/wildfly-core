/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
