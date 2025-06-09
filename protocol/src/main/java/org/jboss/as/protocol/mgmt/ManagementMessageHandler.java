/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol.mgmt;

import java.io.DataInput;

import org.jboss.remoting3.Channel;

/**
 * Interface implemented by classes able to handle a management protocol message
 * coming in from a JBoss Remoting {@link Channel}.
 *
 * @author Emanuel Muckenhuber
 */
public interface ManagementMessageHandler {

    /**
     * Handle a message on the channel.
     *
     * @param channel the channel
     * @param input the data input
     * @param header the header
     */
    void handleMessage(Channel channel, DataInput input, ManagementProtocolHeader header);

}
