/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.requestcontroller;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;

/**
 * @author Stuart Douglas
 */
class RequestControllerSubsystemParser_1_0 extends PersistentResourceXMLParser {


    @Override
    public PersistentResourceXMLDescription getParserDescription() {
        return builder(RequestControllerRootDefinition.INSTANCE.getPathElement(), Namespace.CURRENT.getUriString())
                .addAttributes(RequestControllerRootDefinition.MAX_REQUESTS, RequestControllerRootDefinition.TRACK_INDIVIDUAL_ENDPOINTS)
                .build();
    }
}

