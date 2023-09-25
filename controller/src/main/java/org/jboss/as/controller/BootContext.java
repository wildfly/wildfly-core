/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.msc.service.ServiceTarget;

/**
 * Context used to boot a controller service.
 *
 * @author John Bailey
 */
public interface BootContext {
    /**
     * Get the service target used for boot.
     *
     * @return the service target
     */
    ServiceTarget getServiceTarget();
}
