/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
    private final String userName = null; // TODO This likely needs to be further visited.
    private final String serverProcessName;
    private final String initialServerAuthenticationToken;
    private final int connectOperationID;
    private final boolean managementSubsystemEndpoint;
    private volatile ResponseAttachmentInputStreamSupport responseAttachmentSupport;
    private final Supplier<SSLContext> sslContextSupplier;

    private HostControllerClient client;

    public HostControllerConnectionService(final URI connectionURI, final String serverName, final String serverProcessName,
                                           final String serverAuthenticationToken, final int connectOperationID,
                                           final boolean managementSubsystemEndpoint, final Supplier<SSLContext> sslContextSupplier,
                                           final Supplier<ExecutorService> executorSupplier,
                                           final Supplier<ScheduledExecutorService> scheduledExecutorSupplier,
                                           final Supplier<Endpoint> endpointSupplier,
                                           final Supplier<ProcessStateNotifier> processStateNotifierSupplier) {
        this.connectionURI= connectionURI;
        this.serverName = serverName;
        this.serverProcessName = serverProcessName;
        this.initialServerAuthenticationToken = serverAuthenticationToken;
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
            // we leave local auth enabled as an option for domain servers to use if available. elytron only configuration
            // will require this to be enabled and available on the server side for servers to connect successfully.
            // final OptionMap options = OptionMap.create(Options.SASL_DISALLOWED_MECHANISMS, Sequence.of(JBOSS_LOCAL_USER));
            // Create the connection configuration
            final ProtocolConnectionConfiguration configuration = ProtocolConnectionConfiguration.create(endpoint, connectionURI, OptionMap.EMPTY);
            final String userName = this.userName != null ? this.userName : serverName;
            configuration.setCallbackHandler(HostControllerConnection.createClientCallbackHandler(userName, initialServerAuthenticationToken));
            configuration.setConnectionTimeout(SERVER_CONNECTION_TIMEOUT);
            configuration.setSslContext(sslContextSupplier.get());
            this.responseAttachmentSupport = new ResponseAttachmentInputStreamSupport(scheduledExecutorSupplier.get());
            // Create the connection
            final HostControllerConnection connection = new HostControllerConnection(serverProcessName, userName, connectOperationID,
                    configuration, responseAttachmentSupport, executorSupplier.get());
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
