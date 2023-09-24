/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol.mgmt.support;

import org.jboss.remoting3.Channel;

/**
 * Initializes a {@link org.jboss.remoting3.Channel.Receiver} for receiving
 * messages over management channels.
 *
 * @author Emanuel Muckenhuber
 */
public interface ManagementChannelInitialization {

    /**
     * Initialize the channel receiver and start receiving requests.
     *
     * @param channel an opened channel
     * @return a handle to the receiver that can be used to coordinate a controlled shutdown
     */
    ManagementChannelShutdownHandle startReceiving(Channel channel);

}
