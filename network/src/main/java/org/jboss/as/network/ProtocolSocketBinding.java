/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.network;

/**
 * Simple data object that associates a {@link SocketBinding} with the name of
 * a protocol.
 */
public final class ProtocolSocketBinding {

    private final String protocol;
    private final SocketBinding socketBinding;

    public ProtocolSocketBinding(String protocol, SocketBinding socketBinding) {
        this.protocol = protocol;
        this.socketBinding = socketBinding;
    }

    public String getProtocol() {
        return protocol;
    }

    public SocketBinding getSocketBinding() {
        return socketBinding;
    }
}
