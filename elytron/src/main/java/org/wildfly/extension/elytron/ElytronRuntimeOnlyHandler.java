/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import org.jboss.as.controller.AbstractRuntimeOnlyHandler;
import org.jboss.as.controller.OperationContext;

/**
 * Extends the {@link AbstractRuntimeOnlyHandler} only {@linkplain #requiresRuntime(OperationContext) requiring the runtime step }
 * if {@link #isServerOrHostController(OperationContext)} returns {@code true}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class ElytronRuntimeOnlyHandler extends AbstractRuntimeOnlyHandler implements ElytronOperationStepHandler {

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return isServerOrHostController(context);
    }
}
