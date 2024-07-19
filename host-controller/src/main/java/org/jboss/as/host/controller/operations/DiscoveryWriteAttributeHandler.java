/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;

/**
 * Handles changes to the options attribute containing the list
 * of custom and static descovery options.
 *
 * @author Alexey Loubyansky
 */
public class DiscoveryWriteAttributeHandler extends ReloadRequiredWriteAttributeHandler {

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        // HCs may connect to the in either RunningMode.NORMAL or ADMIN_ONLY,
        // so the running mode doesn't figure in whether reload is required
        return !context.isBooting();
    }
}
