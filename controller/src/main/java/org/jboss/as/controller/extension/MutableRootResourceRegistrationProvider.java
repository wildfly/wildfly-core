/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.extension;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * Provides access to the mutable ManagementResourceRegistration for the root resource.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public interface MutableRootResourceRegistrationProvider {

    ManagementResourceRegistration getRootResourceRegistrationForUpdate(OperationContext context);
}
