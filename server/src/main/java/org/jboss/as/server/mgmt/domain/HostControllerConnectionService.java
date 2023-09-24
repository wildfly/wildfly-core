/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.mgmt.domain;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ProcessStateNotifier;
import org.jboss.as.controller.remote.ResponseAttachmentInputStreamSupport;
import org.jboss.as.protocol.ProtocolConnectionConfiguration;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.remoting3.Endpoint;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

/**
 * Service setting up the connection to the local host controller.
 *
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class HostControllerConnectionService implements Service<HostControllerClient> {

    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("host", "controller", "client");

    private static final long SERVER_CONNECTION_TIMEOUT = 60000;

    private final Supplier<ExecutorService> executorSupplier;
    private final Supplier<ScheduledExecutorService> scheduledExecutorSupplier;
    private final Supplier<Endpoint> endpointSupplier;
    private final Supplier<ProcessStateNotifier> processStateNotifierSupplier;

    private final URI connectionURI;
    private final String serverName;
    private final String serverProcessName;
    private final int connectOperationID;
    private final boolean managementSubsystemEndpoint;
    private volatile ResponseAttachmentInputStreamSupport responseAttachmentSupport;
    private final Supplier<SSLContext> sslContextSupplier;
    private volatile AuthenticationContext authenticationContext;

    private HostControllerClient client;

    public HostControllerConnectionService(final URI connectionURI, final String serverName, final String serverProcessName,
                                           final AuthenticationContext authenticationContext, final int connectOperationID,
                                           final boolean managementSubsystemEndpoint, final Supplier<SSLContext> sslContextSupplier,
                                           final Supplier<ExecutorService> executorSupplier,
                                           final Supplier<ScheduledExecutorService> scheduledExecutorSupplier,
                                           final Supplier<Endpoint> endpointSupplier,
                                           final Supplier<ProcessStateNotifier> processStateNotifierSupplier) {
        this.connectionURI= connectionURI;
        this.serverName = serverName;
        this.serverProcessName = serverProcessName;
        this.authenticationContext = authenticationContext;
        this.connectOperationID = connectOperationID;
        this.managementSubsystemEndpoint = managementSubsystemEndpoint;
        if (sslContextSupplier != null) {
            this.sslContextSupplier = sslContextSupplier;
        } else {
            this.sslContextSupplier = HostControllerConnectionService::getAcceptingSSLContext;
        }
        this.executorSupplier = executorSupplier;
        this.scheduledExecutorSupplier = scheduledExecutorSupplier;
        this.endpointSupplier = endpointSupplier;
        this.processStateNotifierSupplier = processStateNotifierSupplier;
    }

    @Override
    @SuppressWarnings("deprecation")
    public synchronized void start(final StartContext context) throws StartException {
        final Endpoint endpoint = endpointSupplier.get();
        try {
            // Create the connection configuration
            final ProtocolConnectionConfiguration configuration = ProtocolConnectionConfiguration.create(endpoint, connectionURI, OptionMap.EMPTY);
            configuration.setConnectionTimeout(SERVER_CONNECTION_TIMEOUT);
            configuration.setSslContext(sslContextSupplier.get());
            this.responseAttachmentSupport = new ResponseAttachmentInputStreamSupport(scheduledExecutorSupplier.get());
            // Create the connection
            final HostControllerConnection connection = new HostControllerConnection(serverProcessName, connectOperationID,
                    configuration, authenticationContext, responseAttachmentSupport, executorSupplier.get());
            // Trigger the started notification based on the process state listener
            final ProcessStateNotifier processService = processStateNotifierSupplier.get();
            processService.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(final PropertyChangeEvent evt) {
                    final ControlledProcessState.State old = (ControlledProcessState.State) evt.getOldValue();
                    final ControlledProcessState.State current = (ControlledProcessState.State) evt.getNewValue();
                    if (old == ControlledProcessState.State.STARTING) {
                        // After starting reload has to be cleared, may still require a restart
                        if(current == ControlledProcessState.State.RUNNING
                                || current == ControlledProcessState.State.RESTART_REQUIRED) {
                            connection.started();
                        } else {
                            IoUtils.safeClose(connection);
                        }
                    }
                }
            });
            this.client = new HostControllerClient(serverName, connection.getChannelHandler(), connection,
                    managementSubsystemEndpoint, executorSupplier.get());
        } catch (Exception e) {
            throw new StartException(e);
        }
    }

    @Override
    public synchronized void stop(final StopContext stopContext) {
        final ExecutorService executorService = executorSupplier.get();
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    responseAttachmentSupport.shutdown();
                } finally {
                    StreamUtils.safeClose(client);
                    client = null;
                    stopContext.complete();
                }
            }
        };
        try {
            executorService.execute(task);
        } catch (RejectedExecutionException e) {
            task.run();
        } finally {
            stopContext.asynchronous();
        }
    }

    @Override
    public synchronized HostControllerClient getValue() throws IllegalStateException, IllegalArgumentException {
        final HostControllerClient client = this.client;
        if(client == null) {
            throw new IllegalStateException();
        }
        return client;
    }

    private static SSLContext getAcceptingSSLContext() {
        /*
         * This connection is only a connection back to the local host controller.
         *
         * The HostController that started this process will have already provided the
         * required information regarding the connection so quietly allow the SSL connection
         * to be established.
         */
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManager[] trustManagers = new TrustManager[] { new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            } };

            sslContext.init(null, trustManagers, null);

            return sslContext;
        } catch (GeneralSecurityException e) {
            throw ServerLogger.ROOT_LOGGER.unableToInitialiseSSLContext(e.getMessage());
        }
    }

    public static class SSLContextSupplier implements Supplier<SSLContext>, Serializable {

        private final String sslProtocol;
        private final String trustManagerAlgorithm;
        private final String trustStoreType;
        private final String trustStorePath;
        private final char[] trustStorePassword;

        public SSLContextSupplier(String sslProtocol, String trustManagerAlgorithm, String trustStoreType, String trustStorePath, char[] trustStorePassword) {
            this.sslProtocol = sslProtocol;
            this.trustManagerAlgorithm = trustManagerAlgorithm;
            this.trustStoreType = trustStoreType;
            this.trustStorePath = trustStorePath;
            this.trustStorePassword = trustStorePassword;
        }

        @Override
        public SSLContext get() {
            try {
                if ("Default".equals(sslProtocol)) {
                    return SSLContext.getDefault();
                }

                KeyStore keyStore = KeyStore.getInstance(trustStoreType != null ? trustStoreType : KeyStore.getDefaultType());
                if (trustStorePath != null) {
                    try (FileInputStream fis = new FileInputStream(trustStorePath)) {
                        keyStore.load(fis, trustStorePassword);
                    }
                } else {
                    keyStore.load(null, trustStorePassword);
                }

                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(trustManagerAlgorithm != null ? trustManagerAlgorithm : TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(keyStore);

                SSLContext sslContext = SSLContext.getInstance(sslProtocol);
                sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

                return sslContext;
            } catch (NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException | KeyManagementException e) {
                throw ServerLogger.ROOT_LOGGER.unableToInitialiseSSLContext(e.getMessage());
            }
        }
    }
}
