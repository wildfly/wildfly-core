/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.ResourceDefinition;

/**
 * Attributes used by {@link ResourceDefinition} instances that need a common home.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class CommonAttributes {

    // TODO - Check we really want this and not other suitable common location is available.

    static final PropertiesAttributeDefinition PROPERTIES = new PropertiesAttributeDefinition.Builder(ElytronDescriptionConstants.PROPERTIES, true)
            .setAllowExpression(true)
            .setRestartAllServices().build();

}
