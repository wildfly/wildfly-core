/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.jboss.as.subsystem.test;

import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * For tests to call package protected methods in this package
 */
public class InternalPackageProtectedAccess {
    public static Resource modelToResource(final ImmutableManagementResourceRegistration reg, final ModelNode model, boolean includeUndefined) {
        return TransformationUtils.modelToResource(reg, model, includeUndefined);
    }
}
