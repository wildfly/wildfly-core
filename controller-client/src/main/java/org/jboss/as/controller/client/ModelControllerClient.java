/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;

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
     *
     * @deprecated the type returned by this method will change to {@link java.util.concurrent.CompletableFuture}.
     *             Callers can prepare for this by not assigning this method's returned value to a variable of type
     *             {@link AsyncFuture} but instead use {@link java.util.concurrent.Future} as the variable type.
     */
    @Deprecated
    default AsyncFuture<ModelNode> executeAsync(ModelNode operation) {
        return executeAsync(Operation.Factory.create(operation), OperationMessageHandler.DISCARD);
    }

    /**
     * Execute an operation in another thread, optionally receiving progress reports.
     *
     * @param operation the operation to execute
     * @param messageHandler the message handler to use for operation progress reporting, or {@code null} for none
     * @return the future result of the operation
     *
     * @deprecated the type returned by this method will change to {@link java.util.concurrent.CompletableFuture}.
     *             Callers can prepare for this by not assigning this method's returned value to a variable of type
     *             {@link AsyncFuture} but instead use {@link java.util.concurrent.Future} as the variable type.
     */
    @Deprecated
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
     *
     * @deprecated the type returned by this method will change to {@link java.util.concurrent.CompletableFuture}.
     *             Callers can prepare for this by not assigning this method's returned value to a variable of type
     *             {@link AsyncFuture} but instead use {@link java.util.concurrent.Future} as the variable type.
     */
    @Deprecated
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
     *
     * @deprecated the type returned by this method will change to {@link java.util.concurrent.CompletableFuture}.
     *             Callers can prepare for this by not assigning this method's returned value to a variable of type
     *             {@link AsyncFuture} but instead use {@link java.util.concurrent.Future} as the variable type.
     */
    @Deprecated
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
     *
     * @deprecated the type returned by this method will change to {@link java.util.concurrent.CompletableFuture}.
     *             Callers can prepare for this by not assigning this method's returned value to a variable of type
     *             {@link AsyncFuture} but instead use {@link java.util.concurrent.Future} as the variable type.
     */
    @Deprecated
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
