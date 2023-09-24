/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;

/**
 * An {@link OperationStepHandler} which adds the ability to check whether or not the operation is running on a server
 * or host controller.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
interface ElytronOperationStepHandler extends OperationStepHandler {

    /**
     * Checks if the context is running on a {@linkplain ProcessType#isServer() server} or on a host controller. This
     * will return {@code true} even if the server is running in {@link org.jboss.as.controller.RunningMode#ADMIN_ONLY}.
     *
     * @param context the current operation context
     *
     * @return {@code true} if the current context is a server or a host controller
     */
    default boolean isServerOrHostController(final OperationContext context) {
        return context.getProcessType().isServer() || !ModelDescriptionConstants.PROFILE.equals(context.getCurrentAddress().getElement(0).getKey());
    }
}
