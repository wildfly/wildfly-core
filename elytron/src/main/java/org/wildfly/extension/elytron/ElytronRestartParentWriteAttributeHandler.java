/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import java.util.Collection;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.RestartParentWriteAttributeHandler;

/**
 * Extends the {@link RestartParentWriteAttributeHandler} overriding the {@link #requiresRuntime(OperationContext)}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class ElytronRestartParentWriteAttributeHandler extends RestartParentWriteAttributeHandler implements ElytronOperationStepHandler {
    ElytronRestartParentWriteAttributeHandler(final String parentKeyName, final AttributeDefinition... definitions) {
        super(parentKeyName, definitions);
    }

    ElytronRestartParentWriteAttributeHandler(final String parentKeyName, final Collection<AttributeDefinition> definitions) {
        super(parentKeyName, definitions);
    }

    @Override
    protected boolean requiresRuntime(final OperationContext context) {
        return isServerOrHostController(context);
    }
}
