/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.http.server;

import org.jboss.as.controller.capability.RuntimeCapability;

/**
 * Allows callers to check the availability of the web console.
 * <p>
 * By default, the web console is not available until the process controller transitions to RUNNING.
 * The callers can try to make the console available even before by using the {@link #isAvailable()} method.
 *
 * @author <a href="mailto:yborgessf@redhat.com">Yeray Borges</a>
 */
public interface ConsoleAvailability {

    /**
     * Capability users of the controller use to read the web console availability and force it to make it available
     * before the process controller is in RUNNING state.
     *
     * This capability isn't necessarily directly related to this class but we declare it
     * here as it's as good a place as any at this time.
     */
    RuntimeCapability<Void> CONSOLE_AVAILABILITY_CAPABILITY =
            RuntimeCapability.Builder.of("org.wildfly.management.console-availability", ConsoleAvailability.class)
                    .build();

    /**
     * Gets the availability of the web console.
     *
     * @return Whether the console is available at this moment.
     */
    boolean isAvailable();

    /**
     * Tries to make the console available. The console will be available only if the process controller is not
     * stopping.
     */
    void setAvailable();
}
