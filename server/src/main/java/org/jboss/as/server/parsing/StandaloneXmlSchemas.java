/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.parsing;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;

import java.util.concurrent.ExecutorService;

import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.parsing.ManagementSchemas;
import org.jboss.as.version.Stability;
import org.jboss.modules.ModuleLoader;

/**
 * Representation of the schemas for the standalone server configuration.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class StandaloneXmlSchemas extends ManagementSchemas {

    public StandaloneXmlSchemas(final Stability stability, final ModuleLoader loader, final ExecutorService executorService,
            final ExtensionRegistry extensionRegistry) {
        super(stability, new StandaloneXml(loader, executorService, extensionRegistry), SERVER);
    }

}
