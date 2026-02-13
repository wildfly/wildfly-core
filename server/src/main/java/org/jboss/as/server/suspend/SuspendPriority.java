/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.suspend;

/**
 * Enumerates the permissible suspend priorities.
 * This priority determines the order in which suspendable activity will be invoked during suspend/resume.
 * @author Paul Ferraro
 */
public enum SuspendPriority {
    ZERO,
    ONE,
    TWO,
    THREE,
    FOUR,
    FIVE,
    SIX,
    SEVEN,
    EIGHT,
    NINE,
    TEN;

    /** First to suspend, last to resume **/
    public static final SuspendPriority FIRST = ZERO;
    public static final SuspendPriority SECOND = ONE;
    public static final SuspendPriority DEFAULT = FIVE;
    public static final SuspendPriority PENULTIMATE = NINE;
    /** Last to suspend, first to resume **/
    public static final SuspendPriority LAST = TEN;
}
