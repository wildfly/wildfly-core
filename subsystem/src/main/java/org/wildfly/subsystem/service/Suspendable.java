/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.service;

import org.jboss.as.server.suspend.SuspendPriority;

/**
 * Configures a suspendable object.
 * @author Paul Ferraro
 */
public interface Suspendable<C extends Suspendable<C>> {
    /**
     * Configures the suspend priority of this object.
     * @param priority a suspend priority
     * @return a reference to this object
     */
    C withSuspendPriority(SuspendPriority priority);
}
