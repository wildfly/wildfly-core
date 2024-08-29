/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.parsing;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;

import java.util.concurrent.ExecutorService;

import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.parsing.ManagementSchemas;
import org.jboss.as.version.Stability;
import org.jboss.modules.ModuleLoader;

/**
 * Representation of the schemas for the host configuration.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class HostXmlSchemas extends ManagementSchemas {

    public HostXmlSchemas(Stability stability, String defaultHostControllerName, RunningMode runningMode, boolean isCachedDC, final ModuleLoader loader,
                   final ExecutorService executorService, final ExtensionRegistry extensionRegistry) {
        super(stability, new HostXml(defaultHostControllerName, runningMode, isCachedDC, loader,
                            executorService, extensionRegistry), HOST);
    }

}
