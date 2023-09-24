/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.handlers;

import java.util.logging.Handler;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathInfoHandler;
import org.jboss.as.controller.services.path.ResolvePathHandler;
import org.jboss.as.logging.CommonAttributes;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractFileHandlerDefinition extends AbstractHandlerDefinition {

    private static final String CHANGE_FILE_OPERATION_NAME = "change-file";

    private final ResolvePathHandler resolvePathHandler;
    private final PathInfoHandler diskUsagePathHandler;
    private final boolean registerLegacyOps;

    AbstractFileHandlerDefinition(final PathElement path, final Class<? extends Handler> type,
                                  final ResolvePathHandler resolvePathHandler,
                                  final PathInfoHandler diskUsagePathHandler,
                                  final AttributeDefinition... attributes) {
        this(path, true, type, resolvePathHandler, diskUsagePathHandler, attributes);
    }

    AbstractFileHandlerDefinition(final PathElement path, final boolean registerLegacyOps,
                                  final Class<? extends Handler> type,
                                  final ResolvePathHandler resolvePathHandler,
                                  final PathInfoHandler diskUsagePathHandler,
                                  final AttributeDefinition... attributes) {
        super(createParameters(path, type, attributes, CommonAttributes.FILE, CommonAttributes.APPEND), registerLegacyOps, null, attributes);
        this.registerLegacyOps = registerLegacyOps;
        this.resolvePathHandler = resolvePathHandler;
        this.diskUsagePathHandler = diskUsagePathHandler;
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration registration) {
        super.registerOperations(registration);
        if (registerLegacyOps) {
            registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(CHANGE_FILE_OPERATION_NAME, getResourceDescriptionResolver())
                    .setDeprecated(ModelVersion.create(1, 2, 0))
                    .setParameters(CommonAttributes.FILE)
                    .build(), HandlerOperations.CHANGE_FILE);
        }
        if (resolvePathHandler != null)
            registration.registerOperationHandler(resolvePathHandler.getOperationDefinition(), resolvePathHandler);
        if (diskUsagePathHandler != null)
            PathInfoHandler.registerOperation(registration, diskUsagePathHandler);
    }
}
