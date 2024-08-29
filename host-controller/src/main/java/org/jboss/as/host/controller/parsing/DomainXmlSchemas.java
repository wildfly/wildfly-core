/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.parsing;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN;

import java.util.concurrent.ExecutorService;

import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.parsing.ManagementSchemas;
import org.jboss.as.version.Stability;
import org.jboss.modules.ModuleLoader;

/**
 * Representation of the schemas for the host configuration.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class DomainXmlSchemas extends ManagementSchemas {

    public DomainXmlSchemas(final Stability stability, final ModuleLoader loader, ExecutorService executorService, ExtensionRegistry extensionRegistry) {
        super(stability, new DomainXml(loader, executorService, extensionRegistry), DOMAIN);
    }

}
