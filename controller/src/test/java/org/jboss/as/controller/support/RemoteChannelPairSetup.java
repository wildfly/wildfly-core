/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jboss.as.protocol.ProtocolConnectionConfiguration;
import org.jboss.as.protocol.mgmt.support.ManagementChannelInitialization;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.OpenListener;
import org.jboss.threads.EnhancedQueueExecutor;
import org.jboss.threads.JBossThreadFactory;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public class RemoteChannelPairSetup {

    static final String ENDPOINT_NAME = "endpoint";
    static final String URI_SCHEME = "remote";
    static final String TEST_CHANNEL = "Test-Channel";
    static final int PORT = 32123;
    static final int EXECUTOR_MAX_THREADS = 20;
    private static final long EXECUTOR_KEEP_ALIVE_TIME = 60000;

    ChannelServer channelServer;

    protected ExecutorService executorService;
    protected Channel serverChannel;
    protected Channel clientChannel;
    private Connection connection;

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

    public void setupRemoting(final ManagementChannelInitialization initialization) throws IOException {
        //executorService = Executors.newCachedThreadPool();
        final ThreadFactory threadFactory = new JBossThreadFactory(new ThreadGroup("Remoting"), Boolean.FALSE, null, "Remoting %f thread %t", null, null);
        executorService = new EnhancedQueueExecutor.Builder()
                .setCorePoolSize(EXECUTOR_MAX_THREADS / 4 + 1)
                .setMaximumPoolSize(EXECUTOR_MAX_THREADS)
                .setKeepAliveTime(EXECUTOR_KEEP_ALIVE_TIME, TimeUnit.MILLISECONDS)
                .setMaximumQueueSize(500)
                .setThreadFactory(threadFactory)
                .build();

        final ChannelServer.Configuration configuration = new ChannelServer.Configuration();
        configuration.setEndpointName(ENDPOINT_NAME);
        configuration.setUriScheme(URI_SCHEME);
        configuration.setBindAddress(new InetSocketAddress("127.0.0.1", PORT));
        configuration.setExecutor(executorService);
        channelServer = ChannelServer.create(configuration);

        channelServer.addChannelOpenListener(TEST_CHANNEL, new OpenListener() {

            @Override
            public void registrationTerminated() {
            }

            @Override
            public void channelOpened(Channel channel) {
                serverChannel = channel;
                initialization.startReceiving(channel);
                clientConnectedLatch.countDown();
            }
        });
    }

    public void startClientConnetion() throws IOException, URISyntaxException {
        ProtocolConnectionConfiguration configuration = ProtocolConnectionConfiguration.create(channelServer.getEndpoint(),
                new URI("" + URI_SCHEME + "://127.0.0.1:" + PORT + ""));

        connection = configuration.getEndpoint().getConnection(configuration.getUri()).get();

        clientChannel = connection.openChannel(TEST_CHANNEL, OptionMap.EMPTY).get();
        try {
            clientConnectedLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void stopChannels() throws InterruptedException {
        IoUtils.safeClose(clientChannel);
        IoUtils.safeClose(serverChannel);
//        IoUtils.safeClose(connection);
    }

    public void shutdownRemoting() throws IOException, InterruptedException {
        IoUtils.safeClose(channelServer);
        executorService.shutdown();
        executorService.awaitTermination(10L, TimeUnit.SECONDS);
        executorService.shutdownNow();
    }

}
