/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.security.manager;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;

/**
 * This class implements a parser for version 1.0 of the security manager subsystem.
 *
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 */
class SecurityManagerSubsystemParser_1_0 extends PersistentResourceXMLParser {

    SecurityManagerSubsystemParser_1_0() {
    }

    @Override
    public PersistentResourceXMLDescription getParserDescription() {
        return builder(SecurityManagerRootDefinition.INSTANCE.getPathElement(), Namespace.SECURITY_MANAGER_1_0.getUriString())
                .addChild(builder(DeploymentPermissionsResourceDefinition.INSTANCE.getPathElement())
                        .setXmlElementName(Constants.DEPLOYMENT_PERMISSIONS)
                        .addAttribute(DeploymentPermissionsResourceDefinition.MINIMUM_PERMISSIONS)
                        .addAttribute(DeploymentPermissionsResourceDefinition.MAXIMUM_PERMISSIONS)
                )
                .build();
    }
}
