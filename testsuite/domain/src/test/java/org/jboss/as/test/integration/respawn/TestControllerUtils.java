package org.jboss.as.test.integration.respawn;

import org.jboss.as.protocol.ProtocolConnectionConfiguration;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.threads.JBossThreadFactory;
import org.xnio.OptionMap;

import javax.security.auth.callback.CallbackHandler;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.remoting3.remote.HttpUpgradeConnectionProviderFactory;
import org.xnio.Options;

import static org.jboss.as.network.NetworkUtils.formatPossibleIpv6Address;

/**
 * @author Emanuel Muckenhuber
 */
class TestControllerUtils implements Closeable {

    private static final String ENDPOINT_NAME = "respawn-client-mgmt-endpoint";

    private static final AtomicInteger executorCount = new AtomicInteger();
    static ExecutorService createDefaultExecutor() {
        final ThreadGroup group = new ThreadGroup("mgmt-client-thread");
        final ThreadFactory threadFactory = new JBossThreadFactory(group, Boolean.FALSE, null, "%G " + executorCount.incrementAndGet() + "-%t", null, null);
        return new ThreadPoolExecutor(4, 4, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(256), threadFactory);
    }

    static TestControllerUtils create(String host, int port, CallbackHandler callbackHandler) throws IOException, URISyntaxException {
        return create("remote",host, port, callbackHandler);
    }

    static TestControllerUtils create(String scheme, String host, int port, CallbackHandler callbackHandler) throws IOException, URISyntaxException {
        return create(new URI(scheme, null, formatPossibleIpv6Address(host), port, null, null, null), callbackHandler);
    }
    static TestControllerUtils create(URI uri, CallbackHandler callbackHandler) throws IOException {
        final Endpoint endpoint = Remoting.createEndpoint(ENDPOINT_NAME, OptionMap.EMPTY);
        endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.EMPTY);
        endpoint.addConnectionProvider("http-remoting", new HttpUpgradeConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
        endpoint.addConnectionProvider("remote+http", new HttpUpgradeConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
        endpoint.addConnectionProvider("https-remoting", new HttpUpgradeConnectionProviderFactory(),  OptionMap.create(Options.SSL_ENABLED, Boolean.TRUE));
        endpoint.addConnectionProvider("remote+https", new HttpUpgradeConnectionProviderFactory(),  OptionMap.create(Options.SSL_ENABLED, Boolean.TRUE));
        final ProtocolConnectionConfiguration configuration = ProtocolConnectionConfiguration.create(endpoint, uri);
        configuration.setCallbackHandler(callbackHandler);
        return new TestControllerUtils(endpoint, configuration, createDefaultExecutor());
    }

    private final Endpoint endpoint;
    private final ExecutorService executor;
    private final ProtocolConnectionConfiguration configuration;

    TestControllerUtils(Endpoint endpoint, final ProtocolConnectionConfiguration configuration, final ExecutorService executor) {
        this.endpoint = endpoint;
        this.executor = executor;
        this.configuration = configuration;
    }

    public ProtocolConnectionConfiguration getConfiguration() {
        return configuration;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    @Override
    public void close() throws IOException {
        executor.shutdown();
        try {
            endpoint.close();
        } finally {
            executor.shutdownNow();
        }
    }


}
