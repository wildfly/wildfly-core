/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.transform.TransformationContext;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
@FunctionalInterface
public interface DynamicDiscardPolicy {
    /**
     * Checks whether the child should be added
     *
     * @param context the transformation context
     * @param address the address of the child
     * @return the discard policy for how the resource should be handled
     */
    DiscardPolicy checkResource(TransformationContext context, PathAddress address);

}
