/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.management.util;

import static java.security.AccessController.doPrivileged;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.security.auth.callback.CallbackHandler;

import org.jboss.as.protocol.ProtocolConnectionConfiguration;
import org.jboss.remoting3.Endpoint;
import org.jboss.threads.JBossThreadFactory;


/**
 * Shared test configuration where all {@linkplain org.jboss.as.controller.client.ModelControllerClient}s share a common {@linkplain Endpoint} and
 * {@linkplain java.util.concurrent.Executor}.
 *
 * @author Emanuel Muckenhuber
 */
public class DomainControllerClientConfig implements Closeable {

    private static final String ENDPOINT_NAME = "domain-client-mgmt-endpoint";

    private static final AtomicInteger executorCount = new AtomicInteger();
    static ExecutorService createDefaultExecutor() {
        final ThreadFactory threadFactory = doPrivileged(new PrivilegedAction<ThreadFactory>() {
            public ThreadFactory run() {
                return new JBossThreadFactory(ThreadGroupHolder.THREAD_GROUP, Boolean.FALSE, null, "%G " + executorCount.incrementAndGet() + "-%t", null, null);
            }
        });
        return new ThreadPoolExecutor(4, 4, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(256), threadFactory);
    }

    private final Endpoint endpoint;
    private final ExecutorService executorService;
    private final boolean destroyExecutor;

    DomainControllerClientConfig(final Endpoint endpoint, final ExecutorService executorService, final boolean destroyExecutor) {
        this.endpoint = endpoint;
        this.executorService = executorService;
        this.destroyExecutor = destroyExecutor;
    }

    /**
     * Create a connection wrapper.
     *
     * @param connectionURI the connection URI
     * @param callbackHandler the callback handler
     * @return the connection wrapper
     * @throws IOException
     */
    DomainTestConnection createConnection(final URI connectionURI, final CallbackHandler callbackHandler) throws IOException {
        final ProtocolConnectionConfiguration configuration = ProtocolConnectionConfiguration.create(endpoint, connectionURI);
        return new DomainTestConnection(configuration, callbackHandler, executorService);
    }

    @Override
    public void close() throws IOException {
        if(destroyExecutor) {
            executorService.shutdown();
        }
        if(endpoint != null) try {
            endpoint.close();
        } catch (IOException | UnsupportedOperationException e) {
            // ignore
        }
        if(destroyExecutor) {
            executorService.shutdownNow();
        }
    }

    public static DomainControllerClientConfig create() throws IOException {
        return create(createDefaultExecutor(), true);
    }

    public static DomainControllerClientConfig create(final ExecutorService executorService) throws IOException {
        return create(executorService, false);
    }

    static DomainControllerClientConfig create(final ExecutorService executorService, boolean destroyExecutor) throws IOException {
        final Endpoint endpoint = Endpoint.builder().setEndpointName(ENDPOINT_NAME).build();

        return new DomainControllerClientConfig(endpoint, executorService, destroyExecutor);
    }

    // Wrapper class to delay thread group creation until when it's needed.
    private static class ThreadGroupHolder {
        private static final ThreadGroup THREAD_GROUP = new ThreadGroup("domain-mgmt-client-thread");
    }
}
