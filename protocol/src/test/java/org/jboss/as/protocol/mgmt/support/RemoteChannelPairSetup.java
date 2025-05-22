/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.protocol.mgmt.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jboss.as.protocol.ProtocolConnectionConfiguration;
import org.jboss.as.protocol.ProtocolConnectionUtils;
import org.jboss.as.protocol.mgmt.ManagementChannelReceiver;
import org.jboss.as.protocol.mgmt.ManagementMessageHandler;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.threads.EnhancedQueueExecutor;
import org.jboss.threads.JBossThreadFactory;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class RemoteChannelPairSetup implements RemotingChannelPairSetup {

    static final String ENDPOINT_NAME = "endpoint";
    static final String URI_SCHEME = "remoting";
    static final String TEST_CHANNEL = "Test-Channel";
    static final int PORT = 32123;
    static final int EXECUTOR_MAX_THREADS = 20;
    private static final long EXECUTOR_KEEP_ALIVE_TIME = 60000;

    ChannelServer channelServer;

    protected ExecutorService executorService;
    protected Channel serverChannel;
    protected Channel clientChannel;
    protected Connection connection;
    private Registration registration;

    final CountDownLatch clientConnectedLatch = new CountDownLatch(1);

    public Channel getServerChannel() {
        return serverChannel;
    }

    public Channel getClientChannel() {
        return clientChannel;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setupRemoting(final ManagementMessageHandler handler) throws IOException {
        //executorService = new ThreadPoolExecutor(16, 16, 1L, TimeUnit.DAYS, new LinkedBlockingQueue<Runnable>());
        ThreadFactory threadFactory = new JBossThreadFactory(new ThreadGroup("Remoting"), Boolean.FALSE, null, "Remoting %f thread %t", null, null);
        executorService = new EnhancedQueueExecutor.Builder()
                .setCorePoolSize(EXECUTOR_MAX_THREADS / 4 + 1)
                .setMaximumPoolSize(EXECUTOR_MAX_THREADS)
                .setKeepAliveTime(EXECUTOR_KEEP_ALIVE_TIME, TimeUnit.MILLISECONDS)
                .setMaximumQueueSize(500)
                .setThreadFactory(threadFactory)
                .build();
        ChannelServer.Configuration configuration = new ChannelServer.Configuration();
        configuration.setEndpointName(ENDPOINT_NAME);
        configuration.setUriScheme(URI_SCHEME);
        configuration.setBindAddress(new InetSocketAddress("127.0.0.1", PORT));
        configuration.setExecutor(executorService);
        channelServer = ChannelServer.create(configuration);

        final Channel.Receiver receiver = ManagementChannelReceiver.createDelegating(handler);

        this.registration = channelServer.addChannelOpenListener(TEST_CHANNEL, new OpenListener() {

            @Override
            public void registrationTerminated() {
            }

            @Override
            public void channelOpened(Channel channel) {
                serverChannel = channel;
                serverChannel.receiveMessage(receiver);
                clientConnectedLatch.countDown();
            }
        });
    }

    public void startChannels() throws IOException, URISyntaxException {

        ProtocolConnectionConfiguration configuration = ProtocolConnectionConfiguration.create(channelServer.getEndpoint(),
                new URI("" + URI_SCHEME + "://127.0.0.1:" + PORT + ""),
                OptionMap.create(Options.SASL_POLICY_NOANONYMOUS, Boolean.FALSE, Options.SSL_ENABLED, Boolean.FALSE));
        configuration.setClientBindAddress("127.0.0.1"); // we set this to exercise this code path in ProtocolConnectionUtils.connectSync
                                                         // The path with no client bind address gets used all the time

        connection = ProtocolConnectionUtils.connectSync(configuration);
        clientChannel = connection.openChannel(TEST_CHANNEL, OptionMap.EMPTY).get();
        try {
            clientConnectedLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void stopChannels() {
        IoUtils.safeClose(registration);
        IoUtils.safeClose(clientChannel);
        IoUtils.safeClose(serverChannel);
    }

    public void shutdownRemoting() throws IOException, InterruptedException {
        IoUtils.safeClose(channelServer);
        executorService.shutdown();
        executorService.awaitTermination(1L, TimeUnit.DAYS);
        executorService.shutdownNow();
    }

}
