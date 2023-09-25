/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;

/**
 * Extends the {@link AbstractRemoveStepHandler} overriding the {@link #requiresRuntime(OperationContext)}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class ElytronRemoveStepHandler extends AbstractRemoveStepHandler implements ElytronOperationStepHandler {

    protected ElytronRemoveStepHandler() {
        super();
    }

    @Override
    protected boolean requiresRuntime(final OperationContext context) {
        return isServerOrHostController(context);
    }
}
