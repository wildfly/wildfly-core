/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;

import org.jboss.as.controller.client.helpers.ContextualModelControllerClient;
import org.jboss.as.controller.client.impl.RemotingModelControllerClient;
import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.threads.AsyncFuture;
import org.wildfly.client.config.ConfigXMLParseException;
import org.wildfly.common.context.Contextual;
import org.wildfly.security.auth.client.ElytronXmlParser;

/**
 * A client for an application server management model controller.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public interface ModelControllerClient extends Closeable {

    /**
     * Execute an operation synchronously.
     *
     * @param operation the operation to execute
     * @return the result of the operation
     * @throws IOException if an I/O error occurs while executing the operation
     */
    default ModelNode execute(ModelNode operation) throws IOException{
        return execute(Operation.Factory.create(operation), OperationMessageHandler.DISCARD);
    }

    /**
     * Execute an operation synchronously.
     *
     * Note that associated input-streams have to be closed by the caller, after the
     * operation completed {@link OperationAttachments#isAutoCloseStreams()}.
     *
     * @param operation the operation to execute
     * @return the result of the operation
     * @throws IOException if an I/O error occurs while executing the operation
     */
    default ModelNode execute(Operation operation) throws IOException{
        return execute(operation, OperationMessageHandler.DISCARD);
    }

    /**
     * Execute an operation synchronously, optionally receiving progress reports.
     *
     * @param operation the operation to execute
     * @param messageHandler the message handler to use for operation progress reporting, or {@code null} for none
     * @return the result of the operation
     * @throws IOException if an I/O error occurs while executing the operation
     */
    default ModelNode execute(ModelNode operation, OperationMessageHandler messageHandler) throws IOException {
        return execute(Operation.Factory.create(operation), messageHandler);
    }

    /**
     * Execute an operation synchronously, optionally receiving progress reports.
     * <p>
     * Note that associated input-streams have to be closed by the caller, after the
     * operation completed {@link OperationAttachments#isAutoCloseStreams()}.
     *
     * @param operation the operation to execute
     * @param messageHandler the message handler to use for operation progress reporting, or {@code null} for none
     * @return the result of the operation
     * @throws IOException if an I/O error occurs while executing the operation
     */
    default ModelNode execute(Operation operation, OperationMessageHandler messageHandler) throws IOException {
        try (OperationResponse or = executeOperation(operation, messageHandler)) {
            return or.getResponseNode();
        }
    }

    /**
     * Execute an operation synchronously, optionally receiving progress reports, with the response
     * to the operation making available any input streams that the server may associate with the response.
     * <p>
     * Note that associated input-streams have to be closed by the caller, after the
     * operation completed {@link OperationAttachments#isAutoCloseStreams()}.
     *
     * @param operation the operation to execute
     * @param messageHandler the message handler to use for operation progress reporting, or {@code null} for none
     * @return the result of the operation
     * @throws IOException if an I/O error occurs while executing the operation
     */
    OperationResponse executeOperation(Operation operation, OperationMessageHandler messageHandler) throws IOException;

    /**
     * Execute an operation in another thread.
     *
     * @param operation the operation to execute
     * @return the future result of the operation
     */
    default AsyncFuture<ModelNode> executeAsync(ModelNode operation) {
        return executeAsync(Operation.Factory.create(operation), OperationMessageHandler.DISCARD);
    }

    /**
     * Execute an operation in another thread, optionally receiving progress reports.
     *
     * @param operation the operation to execute
     * @param messageHandler the message handler to use for operation progress reporting, or {@code null} for none
     * @return the future result of the operation
     */
    default AsyncFuture<ModelNode> executeAsync(ModelNode operation, OperationMessageHandler messageHandler) {
        return executeAsync(Operation.Factory.create(operation), messageHandler);
    }

    /**
     * Execute an operation in another thread.
     * <p>
     * Note that associated input-streams have to be closed by the caller, after the
     * operation completed {@link OperationAttachments#isAutoCloseStreams()}.
     *
     * @param operation the operation to execute
     * @return the future result of the operation
     */
    default AsyncFuture<ModelNode> executeAsync(Operation operation) {
        return executeAsync(operation, OperationMessageHandler.DISCARD);
    }

    /**
     * Execute an operation in another thread, optionally receiving progress reports.
     * <p>
     * Note that associated input-streams have to be closed by the caller, after the
     * operation completed {@link OperationAttachments#isAutoCloseStreams()}.
     *
     * @param operation the operation to execute
     * @param messageHandler the message handler to use for operation progress reporting, or {@code null} for none
     * @return the future result of the operation
     */
    AsyncFuture<ModelNode> executeAsync(Operation operation, OperationMessageHandler messageHandler);

    /**
     * Execute an operation in another thread, optionally receiving progress reports, with the response
     * to the operation making available any input streams that the server may associate with the response.
     * <p>
     * Note that associated input-streams have to be closed by the caller, after the
     * operation completed {@link OperationAttachments#isAutoCloseStreams()}.
     *
     * @param operation the operation to execute
     * @param messageHandler the message handler to use for operation progress reporting, or {@code null} for none
     * @return the future result of the operation
     */
    AsyncFuture<OperationResponse> executeOperationAsync(Operation operation, OperationMessageHandler messageHandler);

    /** Factory methods for creating a {@code ModelControllerClient}. */
    class Factory {

        /**
         * Create a client instance for a remote address and port.
         *
         * @param address the address of the remote host
         * @param port    the port
         * @return A model controller client
         */
        public static ModelControllerClient create(final InetAddress address, final int port) {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setHostName(address.getHostAddress())
                    .setPort(port)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port.
         *
         * @param protocol The prototcol to use. If this is remote+http or remote+https http upgrade will be used rather than the native remote protocol
         * @param address  the address of the remote host
         * @param port     the port
         * @return A model controller client
         */
        public static ModelControllerClient create(final String protocol, final InetAddress address, final int port) {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setHostName(address.getHostAddress())
                    .setPort(port)
                    .setProtocol(protocol)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port.
         *
         * @param address the address of the remote host
         * @param port    the port
         * @param handler CallbackHandler to obtain authentication information for the call.
         * @return A model controller client
         */
        public static ModelControllerClient create(final InetAddress address, final int port, final CallbackHandler handler) {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setHandler(handler)
                    .setHostName(address.getHostAddress())
                    .setPort(port)
                    .build());
        }


        /**
         * Create a client instance for a remote address and port.
         *
         * @param protocol The prototcol to use. If this is remote+http or remote+https http upgrade will be used rather than the native remote protocol
         * @param address  the address of the remote host
         * @param port     the port
         * @param handler  CallbackHandler to obtain authentication information for the call.
         * @return A model controller client
         */
        public static ModelControllerClient create(final String protocol, final InetAddress address, final int port, final CallbackHandler handler) {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setHandler(handler)
                    .setHostName(address.getHostAddress())
                    .setPort(port)
                    .setProtocol(protocol)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port.
         *
         * @param address     the address of the remote host
         * @param port        the port
         * @param handler     CallbackHandler to obtain authentication information for the call.
         * @param saslOptions Additional options to be passed to the SASL mechanism.
         * @return A model controller client
         * @deprecated use {@link org.jboss.as.controller.client.ModelControllerClientConfiguration.Builder} and {@link #create(ModelControllerClientConfiguration)}
         */
        @Deprecated
        public static ModelControllerClient create(final InetAddress address, final int port, final CallbackHandler handler, final Map<String, String> saslOptions) {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setHandler(handler)
                    .setHostName(address.getHostAddress())
                    .setPort(port)
                    .setSaslOptions(saslOptions)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port.
         *
         * @param protocol    The prototcol to use. If this is remote+http or remote+https http upgrade will be used
         * @param address     the address of the remote host
         * @param port        the port
         * @param handler     CallbackHandler to obtain authentication information for the call.
         * @param saslOptions Additional options to be passed to the SASL mechanism.
         * @return A model controller client
         * @deprecated use {@link org.jboss.as.controller.client.ModelControllerClientConfiguration.Builder} and {@link #create(ModelControllerClientConfiguration)}
         */
        @Deprecated
        public static ModelControllerClient create(final String protocol, final InetAddress address, final int port, final CallbackHandler handler, final Map<String, String> saslOptions) {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setHandler(handler)
                    .setHostName(address.getHostAddress())
                    .setPort(port)
                    .setProtocol(protocol)
                    .setSaslOptions(saslOptions)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port.
         *
         * @param hostName the remote host
         * @param port     the port
         * @return A model controller client
         * @throws UnknownHostException if the host cannot be found
         */
        public static ModelControllerClient create(final String hostName, final int port) throws UnknownHostException {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setHostName(hostName)
                    .setPort(port)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port.
         *
         * @param protocol The prototcol to use. If this is remote+http or remote+https http upgrade will be used rather than the native remote protocol
         * @param hostName the remote host
         * @param port     the port
         * @return A model controller client
         * @throws UnknownHostException if the host cannot be found
         */
        public static ModelControllerClient create(final String protocol, final String hostName, final int port) throws UnknownHostException {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setHostName(hostName)
                    .setPort(port)
                    .setProtocol(protocol)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port and CallbackHandler.
         *
         * @param hostName the remote host
         * @param port     the port
         * @param handler  CallbackHandler to obtain authentication information for the call.
         * @return A model controller client
         * @throws UnknownHostException if the host cannot be found
         */
        public static ModelControllerClient create(final String hostName, final int port, final CallbackHandler handler) throws UnknownHostException {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setHandler(handler)
                    .setHostName(hostName)
                    .setPort(port)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port and CallbackHandler.
         *
         * @param protocol The prototcol to use. If this is remote+http or remote+https http upgrade will be used rather than the native remote protocol
         * @param hostName the remote host
         * @param port     the port
         * @param handler  CallbackHandler to obtain authentication information for the call.
         * @return A model controller client
         * @throws UnknownHostException if the host cannot be found
         */
        public static ModelControllerClient create(final String protocol, final String hostName, final int port, final CallbackHandler handler) throws UnknownHostException {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setHandler(handler)
                    .setHostName(hostName)
                    .setPort(port)
                    .setProtocol(protocol)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port and CallbackHandler.
         *
         * @param hostName   the remote host
         * @param port       the port
         * @param handler    CallbackHandler to obtain authentication information for the call.
         * @param sslContext a pre-initialised SSLContext
         * @return A model controller client
         * @throws UnknownHostException if the host cannot be found
         * @deprecated use {@link org.jboss.as.controller.client.ModelControllerClientConfiguration.Builder} and {@link #create(ModelControllerClientConfiguration)}
         */
        @Deprecated
        public static ModelControllerClient create(final String hostName, final int port, final CallbackHandler handler, final SSLContext sslContext) throws UnknownHostException {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setHandler(handler)
                    .setHostName(hostName)
                    .setPort(port)
                    .setSslContext(sslContext)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port and CallbackHandler.
         *
         * @param protocol    The prototcol to use. If this is remote+http or remote+https http upgrade will be used rather than the native remote protocol
         * @param hostName   the remote host
         * @param port       the port
         * @param handler    CallbackHandler to obtain authentication information for the call.
         * @param sslContext a pre-initialised SSLContext
         * @return A model controller client
         * @throws UnknownHostException if the host cannot be found
         * @deprecated use {@link org.jboss.as.controller.client.ModelControllerClientConfiguration.Builder} and {@link #create(ModelControllerClientConfiguration)}
         */
        @Deprecated
        public static ModelControllerClient create(final String protocol, final String hostName, final int port, final CallbackHandler handler, final SSLContext sslContext) throws UnknownHostException {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setHandler(handler)
                    .setHostName(hostName)
                    .setPort(port)
                    .setProtocol(protocol)
                    .setSslContext(sslContext)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port and CallbackHandler.
         *
         * @param hostName   the remote host
         * @param port       the port
         * @param handler    CallbackHandler to obtain authentication information for the call.
         * @param sslContext a pre-initialised SSLContext
         * @param connectionTimeout maximum time, in milliseconds, to wait for the connection to be established
         * @return A model controller client
         * @throws UnknownHostException if the host cannot be found
         * @deprecated use {@link org.jboss.as.controller.client.ModelControllerClientConfiguration.Builder} and {@link #create(ModelControllerClientConfiguration)}
         */
        @Deprecated
        public static ModelControllerClient create(final String hostName, final int port, final CallbackHandler handler, final SSLContext sslContext, final int connectionTimeout) throws UnknownHostException {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setConnectionTimeout(connectionTimeout)
                    .setHandler(handler)
                    .setHostName(hostName)
                    .setPort(port)
                    .setSslContext(sslContext)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port and CallbackHandler.
         *
         * @param protocol    The prototcol to use. If this is remote+http or remote+https http upgrade will be used rather than the native remote protocol
         * @param hostName   the remote host
         * @param port       the port
         * @param handler    CallbackHandler to obtain authentication information for the call.
         * @param sslContext a pre-initialised SSLContext
         * @return A model controller client
         * @throws UnknownHostException if the host cannot be found
         * @deprecated use {@link org.jboss.as.controller.client.ModelControllerClientConfiguration.Builder} and {@link #create(ModelControllerClientConfiguration)}
         */
        @Deprecated
        public static ModelControllerClient create(final String protocol, final String hostName, final int port, final CallbackHandler handler, final SSLContext sslContext, final int connectionTimeout) throws UnknownHostException {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setConnectionTimeout(connectionTimeout)
                    .setHandler(handler)
                    .setHostName(hostName)
                    .setPort(port)
                    .setProtocol(protocol)
                    .setSslContext(sslContext)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port and CallbackHandler.
         *
         * @param hostName   the remote host
         * @param port       the port
         * @param handler    CallbackHandler to obtain authentication information for the call.
         * @param sslContext a pre-initialised SSLContext
         * @param connectionTimeout maximum time, in milliseconds, to wait for the connection to be established
         * @param saslOptions Additional options to be passed to the SASL mechanism.
         * @return A model controller client
         * @throws UnknownHostException if the host cannot be found
         * @deprecated use {@link org.jboss.as.controller.client.ModelControllerClientConfiguration.Builder} and {@link #create(ModelControllerClientConfiguration)}
         */
        @Deprecated
        public static ModelControllerClient create(final String hostName, final int port, final CallbackHandler handler, final SSLContext sslContext, final int connectionTimeout, final Map<String, String> saslOptions) throws UnknownHostException {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setConnectionTimeout(connectionTimeout)
                    .setHandler(handler)
                    .setHostName(hostName)
                    .setPort(port)
                    .setSaslOptions(saslOptions)
                    .setSslContext(sslContext)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port and CallbackHandler.
         *
         * @param protocol    The prototcol to use. If this is remote+http or remote+https http upgrade will be used rather than the native remote protocol
         * @param hostName   the remote host
         * @param port       the port
         * @param handler    CallbackHandler to obtain authentication information for the call.
         * @param sslContext a pre-initialised SSLContext
         * @param connectionTimeout maximum time, in milliseconds, to wait for the connection to be established
         * @param saslOptions Additional options to be passed to the SASL mechanism.
         * @return A model controller client
         * @throws UnknownHostException if the host cannot be found
         * @deprecated use {@link org.jboss.as.controller.client.ModelControllerClientConfiguration.Builder} and {@link #create(ModelControllerClientConfiguration)}
         */
        @Deprecated
        public static ModelControllerClient create(final String protocol, final String hostName, final int port, final CallbackHandler handler, final SSLContext sslContext, final int connectionTimeout, final Map<String, String> saslOptions) throws UnknownHostException {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setConnectionTimeout(connectionTimeout)
                    .setHandler(handler)
                    .setHostName(hostName)
                    .setPort(port)
                    .setProtocol(protocol)
                    .setSaslOptions(saslOptions)
                    .setSslContext(sslContext)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port and CallbackHandler.
         *
         * @param hostName   the remote host
         * @param port       the port
         * @param handler    CallbackHandler to obtain authentication information for the call.
         * @param sslContext a pre-initialised SSLContext
         * @param connectionTimeout maximum time, in milliseconds, to wait for the connection to be established
         * @param saslOptions Additional options to be passed to the SASL mechanism.
         * @param clientBindAddress the address to which the client will bind.
         * @return A model controller client
         * @throws UnknownHostException if the host cannot be found
         * @deprecated use {@link org.jboss.as.controller.client.ModelControllerClientConfiguration.Builder} and {@link #create(ModelControllerClientConfiguration)}
         */
        @Deprecated
        public static ModelControllerClient create(final String hostName, final int port, final CallbackHandler handler, final SSLContext sslContext, final int connectionTimeout, final Map<String, String> saslOptions, final String clientBindAddress) throws UnknownHostException {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setClientBindAddress(clientBindAddress)
                    .setConnectionTimeout(connectionTimeout)
                    .setHandler(handler)
                    .setHostName(hostName)
                    .setPort(port)
                    .setSaslOptions(saslOptions)
                    .setSslContext(sslContext)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port and CallbackHandler.
         *
         * @param protocol    The prototcol to use. If this is remote+http or remote+https http upgrade will be used rather than the native remote protocol
         * @param hostName   the remote host
         * @param port       the port
         * @param handler    CallbackHandler to obtain authentication information for the call.
         * @param sslContext a pre-initialised SSLContext
         * @param connectionTimeout maximum time, in milliseconds, to wait for the connection to be established
         * @param saslOptions Additional options to be passed to the SASL mechanism.
         * @param clientBindAddress the address to which the client will bind.
         * @return A model controller client
         * @throws UnknownHostException if the host cannot be found
         * @deprecated use {@link org.jboss.as.controller.client.ModelControllerClientConfiguration.Builder} and {@link #create(ModelControllerClientConfiguration)}
         */
        @Deprecated
        public static ModelControllerClient create(final String protocol, final String hostName, final int port, final CallbackHandler handler, final SSLContext sslContext, final int connectionTimeout, final Map<String, String> saslOptions, final String clientBindAddress) throws UnknownHostException {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setClientBindAddress(clientBindAddress)
                    .setConnectionTimeout(connectionTimeout)
                    .setHandler(handler)
                    .setHostName(hostName)
                    .setPort(port)
                    .setProtocol(protocol)
                    .setSaslOptions(saslOptions)
                    .setSslContext(sslContext)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port and CallbackHandler.
         *
         * @param hostName    the remote host
         * @param port        the port
         * @param handler     CallbackHandler to obtain authentication information for the call.
         * @param saslOptions Additional options to be passed to the SASL mechanism.
         * @return A model controller client
         * @throws UnknownHostException if the host cannot be found
         * @deprecated use {@link org.jboss.as.controller.client.ModelControllerClientConfiguration.Builder} and {@link #create(ModelControllerClientConfiguration)}
         */
        @Deprecated
        public static ModelControllerClient create(final String hostName, final int port, final CallbackHandler handler, final Map<String, String> saslOptions) throws UnknownHostException {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setHandler(handler)
                    .setHostName(hostName)
                    .setPort(port)
                    .setSaslOptions(saslOptions)
                    .build());
        }

        /**
         * Create a client instance for a remote address and port and CallbackHandler.
         *
         * @param protocol    The prototcol to use. If this is remote+http or remote+https http upgrade will be used rather than the native remote protocol
         * @param hostName    the remote host
         * @param port        the port
         * @param handler     CallbackHandler to obtain authentication information for the call.
         * @param saslOptions Additional options to be passed to the SASL mechanism.
         * @return A model controller client
         * @throws UnknownHostException if the host cannot be found
         * @deprecated use {@link org.jboss.as.controller.client.ModelControllerClientConfiguration.Builder} and {@link #create(ModelControllerClientConfiguration)}
         */
        @Deprecated
        public static ModelControllerClient create(final String protocol, final String hostName, final int port, final CallbackHandler handler, final Map<String, String> saslOptions) throws UnknownHostException {
            return create(new ModelControllerClientConfiguration.Builder()
                    .setHandler(handler)
                    .setHostName(hostName)
                    .setPort(port)
                    .setProtocol(protocol)
                    .setSaslOptions(saslOptions)
                    .build());
        }

        /**
         * Create a client instance based on the client configuration.
         *
         * @param configuration the controller client configuration
         * @return the client
         */
        public static ModelControllerClient create(final ModelControllerClientConfiguration configuration) {
            final ModelControllerClient result =  RemotingModelControllerClient.create(configuration);
            Contextual<?> contextual = null;
            final URI authenticationConfig = configuration.getAuthenticationConfigUri();
            if (authenticationConfig != null) {
                try {
                    contextual = ElytronXmlParser.parseAuthenticationClientConfiguration(authenticationConfig).create();
                } catch (GeneralSecurityException | ConfigXMLParseException e) {
                    throw ControllerClientLogger.ROOT_LOGGER.failedToParseAuthenticationConfig(e, authenticationConfig);
                }
            }
            if (contextual == null) {
                return result;
            }
            return new ContextualModelControllerClient(result, contextual);
        }

    }

}
