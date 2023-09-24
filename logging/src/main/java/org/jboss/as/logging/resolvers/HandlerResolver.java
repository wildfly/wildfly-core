/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.resolvers;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Date: 15.12.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class HandlerResolver implements ModelNodeResolver<Set<String>> {

    public static final HandlerResolver INSTANCE = new HandlerResolver();

    private HandlerResolver(){}

    @Override
    public Set<String> resolveValue(final OperationContext context, final ModelNode value) throws OperationFailedException {
        if (value.isDefined()) {
            final List<ModelNode> handlers = value.asList();
            final Set<String> names = new HashSet<String>(handlers.size());
            for (ModelNode handler : handlers) {
                names.add(handler.asString());
            }
            return names;
        }
        return Collections.emptySet();
    }
}
