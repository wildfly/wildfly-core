/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.protocol.mgmt.support;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;

import org.jboss.as.protocol.mgmt.ManagementMessageHandler;
import org.jboss.remoting3.Channel;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public interface RemotingChannelPairSetup {
    Channel getServerChannel();
    Channel getClientChannel();
    ExecutorService getExecutorService();
    void setupRemoting(ManagementMessageHandler serverChannelHandler) throws IOException;
    void startChannels() throws IOException, URISyntaxException;
    void stopChannels();
    void shutdownRemoting() throws IOException, InterruptedException;
}
