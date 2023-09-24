/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.process.protocol;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ProtocolConstants {
    public static final int CHUNK_START = 0x98;
    public static final int CHUNK_END = 0x99;

    private ProtocolConstants() {
    }
}
