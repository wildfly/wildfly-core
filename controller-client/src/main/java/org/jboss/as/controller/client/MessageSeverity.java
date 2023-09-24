/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client;

/**
 * The severity of the message to send to the client.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public enum MessageSeverity {
    /**
     * Informational severity.
     */
    INFO,
    /**
     * Warning severity.
     */
    WARN,
    /**
     * Error severity.
     */
    ERROR,
}
