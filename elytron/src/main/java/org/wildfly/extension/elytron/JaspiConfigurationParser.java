/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;

import static org.wildfly.extension.elytron.ElytronDescriptionConstants.JASPI;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.JASPI_CONFIGURATION;

class JaspiConfigurationParser {

    final PersistentResourceXMLDescription jaspiConfigurationParser_5_0 = PersistentResourceXMLDescription.builder(PathElement.pathElement(JASPI_CONFIGURATION))
            .setXmlWrapperElement(JASPI)
            .addAttributes(JaspiDefinition.ATTRIBUTES)
            .build();
}
