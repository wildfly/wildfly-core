/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import java.util.Collection;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;

/**
 * Extends the {@link AbstractWriteAttributeHandler} overriding the {@link #requiresRuntime(OperationContext)}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class ElytronWriteAttributeHandler<V> extends AbstractWriteAttributeHandler<V> implements ElytronOperationStepHandler {
    protected ElytronWriteAttributeHandler(final AttributeDefinition... definitions) {
        super(definitions);
    }

    protected ElytronWriteAttributeHandler(final Collection<AttributeDefinition> definitions) {
        super(definitions);
    }

    @Override
    protected boolean requiresRuntime(final OperationContext context) {
        return isServerOrHostController(context);
    }
}
