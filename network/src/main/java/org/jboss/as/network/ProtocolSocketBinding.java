/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
