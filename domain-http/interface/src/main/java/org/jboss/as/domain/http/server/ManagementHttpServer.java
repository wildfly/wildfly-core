/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.http.server;

import static org.jboss.as.domain.http.server.logging.HttpServerLogger.ROOT_LOGGER;
import static org.xnio.Options.SSL_CLIENT_AUTH_MODE;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.management.HttpInterfaceCommonPolicy.Header;
import org.jboss.as.domain.http.server.cors.CorsHttpHandler;
import org.jboss.as.domain.http.server.logging.HttpServerLogger;
import org.jboss.as.domain.http.server.security.DmrFailureReadinessHandler;
import org.jboss.as.domain.http.server.security.ElytronIdentityHandler;
import org.jboss.as.domain.http.server.security.LogoutHandler;
import org.jboss.as.domain.http.server.security.RedirectReadinessHandler;
import org.jboss.as.domain.http.server.security.ServerErrorReadinessHandler;
import org.jboss.modules.ModuleLoadException;
import org.wildfly.common.Assert;
import org.wildfly.elytron.web.undertow.server.ElytronContextAssociationHandler;
import org.wildfly.elytron.web.undertow.server.ElytronHttpExchange;
import org.wildfly.security.auth.server.HttpAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.http.HttpServerAuthenticationMechanism;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.SslClientAuthMode;
import org.xnio.StreamConnection;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.ssl.SslConnection;
import org.xnio.ssl.XnioSsl;

import io.undertow.UndertowOptions;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.SinglePortConfidentialityHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.CanonicalPathHandler;
import io.undertow.server.handlers.ChannelUpgradeHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.SetHeaderHandler;
import io.undertow.server.handlers.cache.CacheHandler;
import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.error.SimpleErrorPageHandler;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.server.protocol.http.HttpOpenListener;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

/**
 * The general HTTP server for handling management API requests.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ManagementHttpServer {


    public interface PathRemapper {
        String remapPath(String originalPath);
    }

    private static final String DEFAULT_SECURITY_REALM = "ManagementRealm";

    private static final Set<String> RESERVED_CONTEXTS;

    static {
        Set<String> set = new HashSet<>();
        set.add(DomainApiCheckHandler.PATH);
        set.add(DomainApiCheckHandler.GENERIC_CONTENT_REQUEST);
        set.add(LogoutHandler.PATH);
        set.add(ErrorContextHandler.ERROR_CONTEXT);
        RESERVED_CONTEXTS = Collections.unmodifiableSet(set);
    }

    private final HttpOpenListener openListener;
    private final InetSocketAddress httpAddress;
    private final InetSocketAddress secureAddress;
    private final XnioWorker worker;
    private volatile AcceptingChannel<StreamConnection> normalServer;
    private volatile AcceptingChannel<SslConnection> secureServer;
    private final SSLContext sslContext;
    private final SslClientAuthMode sslClientAuthMode;
    private final HttpAuthenticationFactory httpAuthenticationFactory;
    private final ExtensionHandlers extensionHandlers;
    private final Executor managementExecutor;
    private final Integer backlog;
    private final Integer connectionHighWater;
    private final Integer connectionLowWater;

    private ManagementHttpServer(HttpOpenListener openListener, Builder builder, SSLContext sslContext,
                                 SslClientAuthMode sslClientAuthMode, ExtensionHandlers extensionExtensionHandlers) {
        this.openListener = openListener;
        this.httpAddress = builder.bindAddress;
        this.secureAddress = builder.secureBindAddress;
        this.sslContext = sslContext;
        this.sslClientAuthMode = sslClientAuthMode;
        this.worker = builder.worker;
        this.httpAuthenticationFactory = builder.httpAuthenticationFactory;
        this.extensionHandlers = extensionExtensionHandlers;
        this.managementExecutor = builder.executor;
        this.backlog = builder.backlog;
        this.connectionHighWater = builder.connectionHighWater;
        this.connectionLowWater = builder.connectionLowWater;
    }

    public void start() {
        try {

            OptionMap.Builder serverOptionsBuilder = OptionMap.builder()
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.REUSE_ADDRESSES, true);
            if (backlog != null) {
                serverOptionsBuilder.set(Options.BACKLOG, backlog);
            }
            if (connectionHighWater != null) {
                serverOptionsBuilder.set(Options.CONNECTION_HIGH_WATER, connectionHighWater);
            }
            if (connectionLowWater != null) {
                serverOptionsBuilder.set(Options.CONNECTION_LOW_WATER, connectionLowWater);
            }

            ChannelListener acceptListener = ChannelListeners.openListenerAdapter(openListener);
            if (httpAddress != null) {
                normalServer = worker.createStreamConnectionServer(httpAddress, acceptListener, serverOptionsBuilder.getMap());
                normalServer.resumeAccepts();
            }
            if (secureAddress != null) {
                if (sslClientAuthMode != null) {
                    serverOptionsBuilder.set(SSL_CLIENT_AUTH_MODE, sslClientAuthMode);
                }
                OptionMap secureOptions = serverOptionsBuilder.getMap();
                XnioSsl xnioSsl = new UndertowXnioSsl(worker.getXnio(), secureOptions, sslContext);
                secureServer = xnioSsl.createSslConnectionServer(worker, secureAddress, acceptListener, secureOptions);
                secureServer.resumeAccepts();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        IoUtils.safeClose(normalServer);
        IoUtils.safeClose(secureServer);
    }

    public synchronized void addStaticContext(String contextName, ResourceManager resourceManager) {
        Assert.checkNotNullParam("contextName", contextName);
        Assert.checkNotNullParam("resourceManager", resourceManager);
        String context = fixPath(contextName);
        // Reject reserved contexts or duplicate extensions
        if (extensionHandlers.reservedContexts.contains(context) || !extensionHandlers.extensionContexts.add(context)) {
            throw new IllegalStateException();
        }
        ResourceHandlerDefinition def = DomainUtil.createStaticContentHandler(resourceManager, context);
        HttpHandler readinessHandler = new RedirectReadinessHandler(extensionHandlers.readyFunction, def.getHandler(),
                ErrorContextHandler.ERROR_CONTEXT);
        extensionHandlers.extensionPathHandler.addPrefixPath(context, readinessHandler);
    }

    public synchronized void addManagementHandler(String contextName, boolean requireSecurity, HttpHandler managementHandler) {
        Assert.checkNotNullParam("contextName", contextName);
        Assert.checkNotNullParam("managementHandler", managementHandler);
        String context = fixPath(contextName);
        // Reject reserved contexts or duplicate extensions
        if (extensionHandlers.reservedContexts.contains(context) || !extensionHandlers.extensionContexts.add(context)) {
            throw new IllegalStateException();
        }
        final Function<HttpServerExchange, Boolean> readyFunction;
        if (requireSecurity) {
            readyFunction = extensionHandlers.readyFunction;
            managementHandler = secureDomainAccess(associateIdentity(managementHandler), httpAuthenticationFactory);
        } else {
            readyFunction = ALWAYS_READY;
        }
        HttpHandler readinessHandler = new ServerErrorReadinessHandler(contextName, readyFunction, managementHandler);
        extensionHandlers.extensionPathHandler.addPrefixPath(context, readinessHandler);
    }

    public synchronized void addManagementGetRemapContext(String contextName, PathRemapper remapper) {
        Assert.checkNotNullParam("contextName", contextName);
        String context = fixPath(contextName);
        // Reject reserved contexts or duplicate extensions
        if (extensionHandlers.reservedContexts.contains(context) || !extensionHandlers.extensionContexts.add(context)) {
            throw new IllegalStateException();
        }
        HttpHandler remapHandler = new RemapHandler(remapper, extensionHandlers.managementHandler);
        extensionHandlers.extensionPathHandler.addPrefixPath(context, remapHandler);
    }

    public synchronized void removeContext(String contextName) {
        Assert.checkNotNullParam("contextName", contextName);
        String context = fixPath(contextName);
        // Reject reserved contexts or non-existent extensions
        if (extensionHandlers.reservedContexts.contains(context) || !extensionHandlers.extensionContexts.contains(context)) {
            throw new IllegalStateException();
        }
        extensionHandlers.extensionContexts.remove(context);
        extensionHandlers.extensionPathHandler.removePrefixPath(context);
    }

    private static String fixPath(String contextName) {
        Assert.checkNotEmptyParam("contextName", contextName);
        return '/' == contextName.charAt(0) ? contextName : "/" + contextName;
    }

    private static SSLContext getSSLContext(Builder builder) {
        if (builder.sslContext != null) {
            return builder.sslContext;
        } else {
            throw ROOT_LOGGER.noRealmOrSSLContext();
        }
    }

    private static final ByteBufferSlicePool bufferPool = new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 4096, 10 * 4096);

    private static ManagementHttpServer create(Builder builder) {
        SSLContext sslContext = null;
        SslClientAuthMode sslClientAuthMode = builder.sslClientAuthMode;
        if (builder.secureBindAddress != null) {
            sslContext = getSSLContext(builder);
            if (sslContext == null) {
                throw ROOT_LOGGER.sslRequestedNoSslContext();
            }
        }

        ROOT_LOGGER.debugf("Resource Constraints backlog=%d, noRequestTimeout=%d, connectionHighWater=%d, connectionLowWater=%d",
            builder.backlog, builder.noRequestTimeout, builder.connectionHighWater, builder.connectionLowWater);

        final OptionMap undertowOptions;
        if (builder.noRequestTimeout != null) {
            undertowOptions = OptionMap.create(UndertowOptions.NO_REQUEST_TIMEOUT, builder.noRequestTimeout);
        } else {
            undertowOptions = OptionMap.EMPTY;
        }

        HttpOpenListener openListener = new HttpOpenListener(bufferPool, undertowOptions);

        int secureRedirectPort = builder.secureBindAddress != null ? builder.secureBindAddress.getPort() : -1;
        // WFLY-2870 -- redirect not supported if bindAddress and secureBindAddress are using different InetAddress
        boolean redirectSupported = (builder.bindAddress == null || builder.secureBindAddress == null || builder.bindAddress.getAddress().equals(builder.secureBindAddress.getAddress()));
        if (!redirectSupported && secureRedirectPort > 0) {
            HttpServerLogger.ROOT_LOGGER.httpsRedirectNotSupported(builder.bindAddress.getAddress(), builder.secureBindAddress.getAddress());
            secureRedirectPort = -1;
        }

        final ExtensionHandlers extensionHandlers = setupOpenListener(openListener, secureRedirectPort, builder);
        return new ManagementHttpServer(openListener, builder, sslContext, sslClientAuthMode, extensionHandlers);
    }

    private static Function<HttpServerExchange, Boolean> createReadyFunction(Builder builder) {
        // TODO WFCORE-5532 We need an Elytron equivalent for realm readiness.
        return e -> Boolean.TRUE;
    }

    private static void addRedirectRedinessHandler(PathHandler pathHandler, ResourceHandlerDefinition consoleHandler, Function<HttpServerExchange, Boolean> readyFunction) {
        HttpHandler readinessHandler = new RedirectReadinessHandler(readyFunction, consoleHandler.getHandler(), ErrorContextHandler.ERROR_CONTEXT);
        pathHandler.addPrefixPath(consoleHandler.getContext(), readinessHandler);
    }

    private static HttpHandler addDmrRedinessHandler(PathHandler pathHandler, HttpHandler domainApiHandler, Function<HttpServerExchange, Boolean> readinessFunction) {
        HttpHandler readinessHandler = wrapHttpHeader(wrapHttpHeader(new DmrFailureReadinessHandler(readinessFunction, domainApiHandler, ErrorContextHandler.ERROR_CONTEXT), "X-Frame-Options", "SAMEORIGIN"), "X-Content-Type-Options", "nosniff");
        pathHandler.addPrefixPath(DomainApiCheckHandler.PATH, readinessHandler);
        pathHandler.addExactPath(DomainApiCheckHandler.GENERIC_CONTENT_REQUEST, readinessHandler);

        return readinessHandler;
    }

    private static void addLogoutHandler(PathHandler pathHandler, Builder builder) {
        pathHandler.addPrefixPath(LogoutHandler.PATH, wrapHttpHeader(wrapHttpHeader(
                new LogoutHandler(DEFAULT_SECURITY_REALM), "X-Frame-Options", "SAMEORIGIN"), "X-Content-Type-Options", "nosniff"));
    }

    private static void addErrorContextHandler(PathHandler pathHandler, Builder builder) throws ModuleLoadException {
        HttpHandler errorContextHandler = (wrapHttpHeader(wrapHttpHeader(ErrorContextHandler.createErrorContext(builder.consoleSlot), "X-Frame-Options", "SAMEORIGIN"), "X-Content-Type-Options", "nosniff"));
        pathHandler.addPrefixPath(ErrorContextHandler.ERROR_CONTEXT, errorContextHandler);
    }

    private static class ExtensionHandlers {
        private final PathHandler extensionPathHandler;
        private final HttpHandler managementHandler;
        private final Function<HttpServerExchange, Boolean> readyFunction;
        private final Set<String> reservedContexts;
        private final Set<String> extensionContexts = new HashSet<>();

        private ExtensionHandlers(PathHandler extensionPathHandler, HttpHandler managementHandler,
                Function<HttpServerExchange, Boolean> readyFunction, ResourceHandlerDefinition consoleHandler) {
            this.extensionPathHandler = extensionPathHandler;
            this.managementHandler = managementHandler;
            this.readyFunction = readyFunction;
            if (consoleHandler == null) {
                this.reservedContexts = RESERVED_CONTEXTS;
            } else {
                Set<String> set = new HashSet<>(RESERVED_CONTEXTS);
                set.add(consoleHandler.getContext());
                this.reservedContexts = Collections.unmodifiableSet(set);
            }
        }
    }

    private static ExtensionHandlers setupOpenListener(HttpOpenListener listener, int secureRedirectPort, Builder builder) {
        CanonicalPathHandler canonicalPathHandler = new CanonicalPathHandler();

        ManagementHttpRequestHandler managementHttpRequestHandler = new ManagementHttpRequestHandler(builder.managementHttpRequestProcessor, canonicalPathHandler);
        CorsHttpHandler corsHandler = new CorsHttpHandler(managementHttpRequestHandler, builder.allowedOrigins);
        listener.setRootHandler(new UpgradeFixHandler(corsHandler));

        PathHandler pathHandler = new PathHandler();
        HttpHandler current = pathHandler;

        Map<String, List<Header>> constantHeaders = builder.constantHeaders;
        if (constantHeaders != null) {
            StaticHeadersHandler headerHandler = new StaticHeadersHandler(current);
            for (Entry<String, List<Header>> entry : constantHeaders.entrySet()) {
                for (Header header : entry.getValue()) {
                    headerHandler.addHeader(entry.getKey(), header.getName(), header.getValue());
                }
            }

            current = headerHandler;
        }

        if (builder.upgradeHandler != null) {
            builder.upgradeHandler.setNonUpgradeHandler(current);
            current = builder.upgradeHandler;
        }

        if (secureRedirectPort > 0) {
            // Add handler for redirect from http to https if needed
            current = new SinglePortConfidentialityHandler(current, secureRedirectPort);
        }
        // caching handler, used for static resources
        current = new CacheHandler(new DirectBufferCache(1024, 1024 * 10, 1024 * 1000, BufferAllocator.BYTE_BUFFER_ALLOCATOR),
                current);
        current = new SimpleErrorPageHandler(current);

        canonicalPathHandler.setNext(current);

        ResourceHandlerDefinition consoleHandler = null;
        try {
            consoleHandler = builder.consoleMode.createConsoleHandler(builder.consoleSlot);
        } catch (ModuleLoadException e) {
            ROOT_LOGGER.consoleModuleNotFound(builder.consoleSlot == null ? "main" : builder.consoleSlot);
        }

        if (builder.consoleMode != ConsoleMode.NO_CONSOLE) {
            try {
                addErrorContextHandler(pathHandler, builder);
            } catch (ModuleLoadException e) {
                ROOT_LOGGER.errorContextModuleNotFound(builder.consoleSlot == null ? "main" : builder.consoleSlot);
            }
        }

        ManagementRootConsoleRedirectHandler rootConsoleRedirectHandler = new ManagementRootConsoleRedirectHandler(consoleHandler);
        HttpHandler domainApiHandler = StreamReadLimitHandler.wrap(CorrelationHandler.wrap(
                InExecutorHandler.wrap(
                    builder.executor,
                    associateIdentity(new DomainApiCheckHandler(builder.modelController,
                        builder.allowedOrigins, builder.consoleAvailability))
                )));

        final Function<HttpServerExchange, Boolean> readyFunction = createReadyFunction(builder);
        pathHandler.addPrefixPath("/", rootConsoleRedirectHandler);
        if (consoleHandler != null) {
            addRedirectRedinessHandler(pathHandler, consoleHandler, readyFunction);
        }

        domainApiHandler = secureDomainAccess(domainApiHandler, builder);
        HttpHandler readinessHandler = addDmrRedinessHandler(pathHandler, domainApiHandler, readyFunction);
        addLogoutHandler(pathHandler, builder);

        return new ExtensionHandlers(pathHandler, readinessHandler, readyFunction, consoleHandler);
    }

    private static HttpHandler associateIdentity(HttpHandler domainHandler) {
        domainHandler = new ElytronIdentityHandler(domainHandler);

        return new BlockingHandler(domainHandler);
    }

    private static HttpHandler secureDomainAccess(HttpHandler domainHandler, final Builder builder) {
        return secureDomainAccess(domainHandler, builder.httpAuthenticationFactory);
    }

    private static HttpHandler secureDomainAccess(HttpHandler domainHandler, final HttpAuthenticationFactory httpAuthenticationFactory) {
        if (httpAuthenticationFactory != null) {
            return secureDomainAccessElytron(domainHandler, httpAuthenticationFactory);
        }

        return domainHandler;
    }

    private static HttpHandler secureDomainAccessElytron(HttpHandler domainHandler, final HttpAuthenticationFactory httpAuthenticationFactory) {
        domainHandler = new AuthenticationCallHandler(domainHandler);
        domainHandler = new AuthenticationConstraintHandler(domainHandler);
        Supplier<List<HttpServerAuthenticationMechanism>> mechanismSupplier = () ->
            httpAuthenticationFactory.getMechanismNames().stream()
            .map(s -> {
                    try {
                        return httpAuthenticationFactory.createMechanism(s);
                    } catch (Exception e) {
                        return null;
                    }
                })
            .collect(Collectors.toList());
        domainHandler = ElytronContextAssociationHandler.builder()
                .setNext(domainHandler)
                .setMechanismSupplier(mechanismSupplier)
                .setHttpExchangeSupplier(h -> new ElytronHttpExchange(h) {

                    @Override
                    public void authenticationComplete(SecurityIdentity securityIdentity, String mechanismName) {
                        super.authenticationComplete(securityIdentity, mechanismName);
                        h.putAttachment(ElytronIdentityHandler.IDENTITY_KEY, securityIdentity);
                    }

                })
                .build();

        return domainHandler;
    }

    private static HttpHandler wrapHttpHeader(final HttpHandler toWrap, final String header, final String value) {
        return new SetHeaderHandler(toWrap, header, value);
    }

    private static Function<HttpServerExchange, Boolean> ALWAYS_READY = new Function<HttpServerExchange, Boolean>() {
        @Override
        public Boolean apply(HttpServerExchange httpServerExchange) {
            return true;
        }
    };

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean built = false;

        private InetSocketAddress bindAddress;
        private InetSocketAddress secureBindAddress;
        private ModelController modelController;
        private SSLContext sslContext;
        private SslClientAuthMode sslClientAuthMode;
        private HttpAuthenticationFactory httpAuthenticationFactory;
        private ConsoleMode consoleMode;
        private String consoleSlot;
        private ChannelUpgradeHandler upgradeHandler;
        private ManagementHttpRequestProcessor managementHttpRequestProcessor;
        private Collection<String> allowedOrigins;
        private XnioWorker worker;
        private Executor executor;
        private Map<String, List<Header>> constantHeaders;
        private ConsoleAvailability consoleAvailability;
        private Integer backlog;
        private Integer connectionHighWater;
        private Integer connectionLowWater;
        private Integer noRequestTimeout;

        private Builder() {
        }

        public Builder setBindAddress(InetSocketAddress bindAddress) {
            assertNotBuilt();
            this.bindAddress = bindAddress;

            return this;
        }

        public Builder setSecureBindAddress(InetSocketAddress secureBindAddress) {
            assertNotBuilt();
            this.secureBindAddress = secureBindAddress;

            return this;
        }

        public Builder setModelController(ModelController modelController) {
            assertNotBuilt();
            this.modelController = modelController;

            return this;
        }

        public Builder setSSLContext(SSLContext sslContext) {
            assertNotBuilt();
            this.sslContext = sslContext;

            return this;
        }

        public Builder setHttpAuthenticationFactory(HttpAuthenticationFactory httpAuthenticationFactory) {
            assertNotBuilt();
            this.httpAuthenticationFactory = httpAuthenticationFactory;

            return this;
        }

        public Builder setConsoleMode(ConsoleMode consoleMode) {
            assertNotBuilt();
            this.consoleMode = consoleMode;

            return this;
        }

        public Builder setConsoleSlot(String consoleSlot) {
            assertNotBuilt();
            this.consoleSlot = consoleSlot;

            return this;
        }

        public Builder setChannelUpgradeHandler(ChannelUpgradeHandler upgradeHandler) {
            assertNotBuilt();
            this.upgradeHandler = upgradeHandler;

            return this;
        }

        public Builder setManagementHttpRequestProcessor(ManagementHttpRequestProcessor managementHttpRequestProcessor) {
            assertNotBuilt();
            this.managementHttpRequestProcessor = managementHttpRequestProcessor;

            return this;
        }

        public Builder setAllowedOrigins(Collection<String> allowedOrigins) {
            assertNotBuilt();
            this.allowedOrigins = allowedOrigins;

            return this;
        }

        public Builder setWorker(XnioWorker worker) {
            assertNotBuilt();
            this.worker = worker;

            return this;
        }

        public Builder setExecutor(Executor executor) {
            assertNotBuilt();
            this.executor = executor;

            return this;
        }

        /**
         * Set a map of constant headers that should be set on each response by matching the path of the incoming request.
         *
         * The key is the path prefix that will be matched against the canonicalised path of the incoming request. The value is
         * a {@link List} or {@link Header} instances.
         *
         * The entry set and list interated so if the Map implementation supports ordering the ordering will be preserved.
         */
        public Builder setConstantHeaders(Map<String, List<Header>> constantHeaders) {
            assertNotBuilt();
            this.constantHeaders = constantHeaders;

            return this;
        }

        /**
         * Set the TCP backlog for each server socket.
         */
        public Builder setBacklog(Integer backlog) {
            assertNotBuilt();
            this.backlog = backlog;

            return this;
        }

        /**
         * Set the high water mark for the number of connections that can be accepted before the server will stop accepting new connections.
         *
         * This is set on each server socket.
         */
        public Builder setConnectionHighWater(Integer connectionHighWater) {
            assertNotBuilt();
            this.connectionHighWater = connectionHighWater;

            return this;
        }

        /**
         * Set the low water mark for the number of connections that can be accepted before the server will start accepting new connections.
         *
         * This is set on each server socket.
         */
        public Builder setConnectionLowWater(Integer connectionLowWater) {
            assertNotBuilt();
            this.connectionLowWater = connectionLowWater;

            return this;
        }

        /**
         * Set the no request timeout for open connections.
         */
        public Builder setNoRequestTimeout(Integer noRequestTimeout) {
            assertNotBuilt();
            this.noRequestTimeout = noRequestTimeout;

            return this;
        }

        public ManagementHttpServer build() {
            assertNotBuilt();

            ManagementHttpServer managementHttpServer = create(this);
            built = true;

            return managementHttpServer;
        }

        private void assertNotBuilt() {
            if (built) {
                throw ROOT_LOGGER.managementHttpServerAlreadyBuild();
            }
        }

        public Builder setConsoleAvailability(ConsoleAvailability consoleAvailability) {
            assertNotBuilt();
            this.consoleAvailability = consoleAvailability;

            return this;
        }
    }

    /**
     * Handler to work around a bug with old XNIO versions that did not handle
     * content-length for HTTP upgrade. This should be removed when it is no longer
     * nessesary to support WF 8.x clients.
     */
    private static class UpgradeFixHandler implements HttpHandler {

        final HttpHandler next;

        private UpgradeFixHandler(HttpHandler next) {
            this.next = next;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            if(exchange.getRequestHeaders().contains(Headers.UPGRADE)) {
                exchange.addResponseWrapper((factory, ex) -> {
                    StreamSinkConduit ret = factory.create();
                    if(exchange.getResponseHeaders().contains(Headers.UPGRADE)) {
                        exchange.getResponseHeaders().add(Headers.CONTENT_LENGTH, "0");
                    }
                    return ret;
                });
            }
            next.handleRequest(exchange);
        }
    }

    private static class RemapHandler implements HttpHandler {

        private final PathRemapper remapper;
        private final HttpHandler next;

        private RemapHandler(PathRemapper remapper, HttpHandler next) {
            this.remapper = remapper;
            this.next = next;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            if (Methods.POST.equals(exchange.getRequestMethod()))  {
                ResponseCodeHandler.HANDLE_405.handleRequest(exchange);
                return;
            }
            String origReqPath = exchange.getRelativePath();
            String remapped = remapper.remapPath(origReqPath);
            if (remapped == null) {
                ResponseCodeHandler.HANDLE_404.handleRequest(exchange);
                return;
            }
            exchange.setRelativePath(remapped);

            // Note: we only change the relative path, not other exchange data that
            // incorporates it (like getRequestPath(), getRequestURL()) and not the
            // resolved path. If this request gets to DomainApiHandler, it should
            // work off the relative path. Other handlers in between may need the
            // original data.

            next.handleRequest(exchange);
        }
    }

    public SocketAddress getLocalAddress() {
        return normalServer.getLocalAddress();
    }

    public <A extends SocketAddress> A getLocalAddress(Class<A> type) {
        return normalServer.getLocalAddress(type);
    }

    public SocketAddress getSecureLocalAddress() {
        return secureServer.getLocalAddress();
    }

    public <A extends SocketAddress> A getSecureLocalAddress(Class<A> type) {
        return secureServer.getLocalAddress(type);
    }

}
