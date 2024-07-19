/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.management;

import java.util.function.Consumer;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.registry.Resource;

/**
 * An extension of {@link ReloadRequiredWriteAttributeHandler} that takes into account that management interfaces run in all
 * modes.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ManagementWriteAttributeHandler extends ReloadRequiredWriteAttributeHandler {

    private final Consumer<OperationContext> contextConsumer;

    ManagementWriteAttributeHandler() {
        contextConsumer = null;
    }

    ManagementWriteAttributeHandler(Consumer<OperationContext> contextConsumer) {
        this.contextConsumer = contextConsumer;
    }

    @Override
    protected void validateUpdatedModel(OperationContext context, Resource model) throws OperationFailedException {
        if (contextConsumer != null) {
            contextConsumer.accept(context);
        }
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        // Management interfaces run in all modes including ADMIN_ONLY
        return !context.isBooting();
    }

}
