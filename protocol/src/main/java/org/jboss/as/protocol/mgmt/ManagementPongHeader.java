/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.protocol.mgmt;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ManagementPongHeader extends ManagementProtocolHeader {

    protected ManagementPongHeader(int version) {
        super(version);
    }

    @Override
    public byte getType() {
        return ManagementProtocol.TYPE_PONG;
    }

}
