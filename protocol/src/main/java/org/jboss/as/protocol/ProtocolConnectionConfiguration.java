/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

import org.jboss.as.protocol.logging.ProtocolLogger;
import org.jboss.remoting3.Endpoint;
import org.wildfly.common.Assert;
import org.xnio.OptionMap;

/**
 * @author Emanuel Muckenhuber
 */
public class ProtocolConnectionConfiguration {

    private static final long DEFAULT_CONNECT_TIMEOUT = 5000;
    private static final String JBOSS_CLIENT_SOCKET_BIND_ADDRESS = "jboss.management.client_socket_bind_address";

    private URI uri;
    private Endpoint endpoint;
    private OptionMap optionMap = OptionMap.EMPTY;
    private long connectionTimeout = DEFAULT_CONNECT_TIMEOUT;
    private CallbackHandler callbackHandler;
    private Map<String, String> saslOptions = Collections.emptyMap();
    private SSLContext sslContext;
    private String clientBindAddress;
    private ProtocolTimeoutHandler timeoutHandler;
    private boolean sslEnabled = true;
    private boolean useStartTLS = true;
    private boolean callbackHandlerPreferred = true;

    protected ProtocolConnectionConfiguration() {
        // TODO AS7-6223 propagate clientBindAddress configuration up to end user level and get rid of this system property
        this.clientBindAddress = SecurityActions.getSystemProperty(JBOSS_CLIENT_SOCKET_BIND_ADDRESS);
        if(this.clientBindAddress != null) {
             ProtocolLogger.ROOT_LOGGER.deprecatedCLIConfiguration(JBOSS_CLIENT_SOCKET_BIND_ADDRESS);
        }
    }

    /**
     * Checks that this object is in a usable state, with the minimal
     * required properties (endpoint, optionMap, uri) set
     *
     * @throws IllegalArgumentException if any required properties are not set
     */
    protected void validate() {
        Assert.checkNotNullParam("endpoint", endpoint);
        Assert.checkNotNullParam("optionMap", optionMap);
        Assert.checkNotNullParam("uri", uri);
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
        if (uri != null) {
            switch(uri.getScheme()) {
                case "http-remoting":
                case "remote+http":
                    this.sslEnabled = false;
                    this.useStartTLS = false;
                    break;
                case "https-remoting":
                case "remote+https":
                    this.sslEnabled = true;
                    this.useStartTLS = false;
                    break;
            }
        }
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public OptionMap getOptionMap() {
        return optionMap;
    }

    public void setOptionMap(OptionMap optionMap) {
        this.optionMap = optionMap;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public CallbackHandler getCallbackHandler() {
        return callbackHandler;
    }

    public void setCallbackHandler(CallbackHandler callbackHandler) {
        this.callbackHandler = callbackHandler;
    }

    public Map<String, String> getSaslOptions() {
        return saslOptions;
    }

    public void setSaslOptions(Map<String, String> saslOptions) {
        this.saslOptions = saslOptions;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public String getClientBindAddress() {
        return clientBindAddress;
    }

    public void setClientBindAddress(String clientBindAddress) {
        if(clientBindAddress != null || this.clientBindAddress == null) {
            this.clientBindAddress = clientBindAddress;
        }
    }

    public ProtocolTimeoutHandler getTimeoutHandler() {
        return timeoutHandler;
    }

    public void setTimeoutHandler(ProtocolTimeoutHandler timeoutHandler) {
        this.timeoutHandler = timeoutHandler;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public boolean isUseStartTLS() {
        return useStartTLS;
    }

    /**
     * Where a {@code CallbackHandler} is provided should this be preferred over any resolved
     * {@code AuthenticationConfiguration}, defaults to {@code true}.
     *
     * @return {@code true} if the referenced {@code CallbackHandler} should be preferred.
     */
    public boolean isCallbackHandlerPreferred() {
        return callbackHandlerPreferred;
    }

    public void setCallbackHandlerPreferred(boolean callbackHandlerPreferred) {
        this.callbackHandlerPreferred = callbackHandlerPreferred;
    }

    public ProtocolConnectionConfiguration copy() {
        return copy(this);
    }

    public static ProtocolConnectionConfiguration create(final Endpoint endpoint, final URI uri) {
        return create(endpoint, uri, OptionMap.EMPTY);
    }

    public static ProtocolConnectionConfiguration create(final Endpoint endpoint, final OptionMap options) {
        return create(endpoint, null, options);
    }

    public static ProtocolConnectionConfiguration create(final Endpoint endpoint, final URI uri, final OptionMap options) {
        final ProtocolConnectionConfiguration configuration = new ProtocolConnectionConfiguration();
        configuration.setEndpoint(endpoint);
        configuration.setUri(uri);
        configuration.setOptionMap(options);
        return configuration;
    }

    public static ProtocolConnectionConfiguration copy(final ProtocolConnectionConfiguration old) {
        return copy(old, new ProtocolConnectionConfiguration());
    }

    static ProtocolConnectionConfiguration copy(final ProtocolConnectionConfiguration old,
                                                final ProtocolConnectionConfiguration target) {
        target.uri = old.uri;
        target.endpoint = old.endpoint;
        target.optionMap = old.optionMap;
        target.connectionTimeout = old.connectionTimeout;
        target.callbackHandler = old.callbackHandler;
        target.saslOptions = old.saslOptions;
        target.sslContext = old.sslContext;
        target.clientBindAddress = old.clientBindAddress;
        target.timeoutHandler = old.timeoutHandler;
        target.sslEnabled = old.sslEnabled;
        target.useStartTLS = old.useStartTLS;
        target.callbackHandlerPreferred = old.callbackHandlerPreferred;
        return target;
    }

}
