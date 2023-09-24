/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.process;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Protocol {

    private Protocol() {
    }

    // inbound messages
    public static final int AUTH = 0xEE;

    // inbound messages (SM only)
    public static final int ADD_PROCESS = 0x10;
    public static final int START_PROCESS = 0x11;
    public static final int STOP_PROCESS = 0x12;
    public static final int REMOVE_PROCESS = 0x13;
    public static final int SEND_STDIN = 0x14;
    public static final int REQUEST_PROCESS_INVENTORY = 0x15;
    public static final int RECONNECT_PROCESS = 0x16;
    public static final int SHUTDOWN = 0x17;
    public static final int DESTROY_PROECESS = 0x18;
    public static final int KILL_PROCESS = 0x19;

    // outbound messages

    // outbound messages (SM only)
    public static final int PROCESS_ADDED = 0x10;
    public static final int PROCESS_STARTED = 0x11;
    public static final int PROCESS_STOPPED = 0x12;
    public static final int PROCESS_REMOVED = 0x13;
    public static final int PROCESS_INVENTORY = 0x14;
    public static final int PROCESS_RECONNECTED = 0x15;
    public static final int OPERATION_FAILED = 0x16;
}
