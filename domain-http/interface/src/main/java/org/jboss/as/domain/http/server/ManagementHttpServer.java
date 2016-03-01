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
package org.jboss.as.domain.http.server;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.jboss.as.domain.http.server.cors.CorsHttpHandler;

import static org.jboss.as.domain.http.server.logging.HttpServerLogger.ROOT_LOGGER;
import static org.xnio.Options.SSL_CLIENT_AUTH_MODE;
import static org.xnio.SslClientAuthMode.REQUESTED;
import static org.xnio.SslClientAuthMode.REQUIRED;

import javax.net.ssl.SSLContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.handlers.SinglePortConfidentialityHandler;
import io.undertow.security.idm.DigestAlgorithm;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.security.impl.CachedAuthenticatedSessionMechanism;
import io.undertow.security.impl.ClientCertAuthenticationMechanism;
import io.undertow.security.impl.DigestAuthenticationMechanism;
import io.undertow.security.impl.DigestQop;
import io.undertow.security.impl.GSSAPIAuthenticationMechanism;
import io.undertow.security.impl.SimpleNonceManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.CanonicalPathHandler;
import io.undertow.server.handlers.ChannelUpgradeHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.cache.CacheHandler;
import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.error.SimpleErrorPageHandler;
import io.undertow.server.protocol.http.HttpOpenListener;
import java.util.Collection;
import java.util.concurrent.Executor;

import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ModelController;
import org.jboss.as.domain.http.server.logging.HttpServerLogger;
import org.jboss.as.domain.http.server.security.AnonymousMechanism;
import org.jboss.as.domain.http.server.security.AuthenticationMechanismWrapper;
import org.jboss.as.domain.http.server.security.DmrFailureReadinessHandler;
import org.jboss.as.domain.http.server.security.LogoutHandler;
import org.jboss.as.domain.http.server.security.RealmIdentityManager;
import org.jboss.as.domain.http.server.security.RedirectReadinessHandler;
import org.jboss.as.domain.http.server.security.ServerSubjectFactory;
import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.modules.ModuleLoadException;
import org.jboss.msc.service.StartException;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.OptionMap.Builder;
import org.xnio.Options;
import org.xnio.SslClientAuthMode;
import org.xnio.StreamConnection;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.ssl.SslConnection;
import org.xnio.ssl.XnioSsl;

/**
 * The general HTTP server for handling management API requests.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ManagementHttpServer {

    private final HttpOpenListener openListener;
    private final InetSocketAddress httpAddress;
    private final InetSocketAddress secureAddress;
    private final XnioWorker worker;
    private volatile AcceptingChannel<StreamConnection> normalServer;
    private volatile AcceptingChannel<SslConnection> secureServer;
    private final SSLContext sslContext;
    private final SslClientAuthMode sslClientAuthMode;


    private ManagementHttpServer(HttpOpenListener openListener, InetSocketAddress httpAddress, InetSocketAddress secureAddress, SSLContext sslContext,
                                 SslClientAuthMode sslClientAuthMode, XnioWorker worker) {
        this.openListener = openListener;
        this.httpAddress = httpAddress;
        this.secureAddress = secureAddress;
        this.sslContext = sslContext;
        this.sslClientAuthMode = sslClientAuthMode;
        this.worker = worker;
    }


    public void start() {
        try {

            Builder serverOptionsBuilder = OptionMap.builder()
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.REUSE_ADDRESSES, true);
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

    public static ManagementHttpServer create(InetSocketAddress bindAddress, InetSocketAddress secureBindAddress, int backlog,
                                              ModelController modelController, SecurityRealm securityRealm, ControlledProcessStateService controlledProcessStateService,
                                              ConsoleMode consoleMode, String consoleSlot, final ChannelUpgradeHandler upgradeHandler,
                                              ManagementHttpRequestProcessor managementHttpRequestProcessor, Collection<String> allowedOrigins, XnioWorker worker, Executor managementExecutor) throws IOException, StartException {

        SSLContext sslContext = null;
        SslClientAuthMode sslClientAuthMode = null;
        if (secureBindAddress != null) {
            sslContext = securityRealm.getSSLContext();
            if (sslContext == null) {
                throw ROOT_LOGGER.sslRequestedNoSslContext();
            }
            Set<AuthMechanism> supportedMechanisms = securityRealm.getSupportedAuthenticationMechanisms();
            if (supportedMechanisms.contains(AuthMechanism.CLIENT_CERT)) {
                if (supportedMechanisms.contains(AuthMechanism.DIGEST)
                        || supportedMechanisms.contains(AuthMechanism.PLAIN)) {
                    // Username / Password auth is possible so don't mandate a client certificate.
                    sslClientAuthMode = REQUESTED;
                } else {
                    sslClientAuthMode = REQUIRED;
                }
            }
        }

        HttpOpenListener openListener = new HttpOpenListener(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 4096, 10 * 4096));

        int secureRedirectPort = secureBindAddress != null ? secureBindAddress.getPort() : -1;
        // WFLY-2870 -- redirect not supported if bindAddress and secureBindAddress are using different InetAddress
        boolean redirectSupported = (bindAddress == null || secureBindAddress == null || bindAddress.getAddress().equals(secureBindAddress.getAddress()));
        if (!redirectSupported && secureRedirectPort > 0) {
            HttpServerLogger.ROOT_LOGGER.httpsRedirectNotSupported(bindAddress.getAddress(), secureBindAddress.getAddress());
            secureRedirectPort = -1;
        }

        setupOpenListener(openListener, modelController, consoleMode, consoleSlot, controlledProcessStateService,
                secureRedirectPort, securityRealm, upgradeHandler, managementHttpRequestProcessor, allowedOrigins, managementExecutor);
        return new ManagementHttpServer(openListener, bindAddress, secureBindAddress, sslContext, sslClientAuthMode, worker);
    }


    private static void setupOpenListener(HttpOpenListener listener, ModelController modelController, ConsoleMode consoleMode,
                                          String consoleSlot, ControlledProcessStateService controlledProcessStateService,
                                          int secureRedirectPort, SecurityRealm securityRealm,
                                          final ChannelUpgradeHandler upgradeHandler, final ManagementHttpRequestProcessor managementHttpRequestProcessor,
                                          final Collection<String> allowedOrigins, Executor managementExecutor) {

        CanonicalPathHandler canonicalPathHandler = new CanonicalPathHandler();
        ManagementHttpRequestHandler managementHttpRequestHandler = new ManagementHttpRequestHandler(managementHttpRequestProcessor, canonicalPathHandler);
        CorsHttpHandler corsHandler = new CorsHttpHandler(managementHttpRequestHandler, allowedOrigins);
        listener.setRootHandler(new UpgradeFixHandler(corsHandler));

        PathHandler pathHandler = new PathHandler();
        HttpHandler current = pathHandler;
        if(upgradeHandler != null) {
            upgradeHandler.setNonUpgradeHandler(current);
            current = upgradeHandler;
        }

        if (secureRedirectPort > 0) {
            // Add handler for redirect from http to https if needed
            current = new SinglePortConfidentialityHandler(current, secureRedirectPort);
        }
        //caching handler, used for static resources
        current = new CacheHandler(new DirectBufferCache(1024,1024 * 10, 1024 * 1000, BufferAllocator.BYTE_BUFFER_ALLOCATOR), current);
        current = new SimpleErrorPageHandler(current);

        canonicalPathHandler.setNext(current);

        ResourceHandlerDefinition consoleHandler = null;
        try {
            consoleHandler = consoleMode.createConsoleHandler(consoleSlot);
        } catch (ModuleLoadException e) {
            ROOT_LOGGER.consoleModuleNotFound(consoleSlot == null ? "main" : consoleSlot);
        }

        try {
            pathHandler.addPrefixPath(ErrorContextHandler.ERROR_CONTEXT, ErrorContextHandler.createErrorContext(consoleSlot));
        } catch (ModuleLoadException e) {
            ROOT_LOGGER.errorContextModuleNotFound(consoleSlot == null ? "main" : consoleSlot);
        }

        ManagementRootConsoleRedirectHandler rootConsoleRedirectHandler = new ManagementRootConsoleRedirectHandler(consoleHandler);
        HttpHandler domainApiHandler = InExecutorHandler.wrap(
                managementExecutor,
                new DomainApiCheckHandler(modelController, controlledProcessStateService, allowedOrigins)
        );
        pathHandler.addPrefixPath("/", rootConsoleRedirectHandler);
        if (consoleHandler != null) {
            HttpHandler readinessHandler = new RedirectReadinessHandler(securityRealm, consoleHandler.getHandler(),
                    ErrorContextHandler.ERROR_CONTEXT);
            pathHandler.addPrefixPath(consoleHandler.getContext(), readinessHandler);
        }

        HttpHandler readinessHandler = new DmrFailureReadinessHandler(securityRealm, secureDomainAccess(domainApiHandler, securityRealm), ErrorContextHandler.ERROR_CONTEXT);
        pathHandler.addPrefixPath(DomainApiCheckHandler.PATH, readinessHandler);
        pathHandler.addExactPath("management-upload", readinessHandler);

        if (securityRealm != null) {
            pathHandler.addPrefixPath(LogoutHandler.PATH, new LogoutHandler(securityRealm.getName()));
        }
    }

    private static HttpHandler secureDomainAccess(final HttpHandler domainHandler, final SecurityRealm securityRealm) {
        RealmIdentityManager rim = new RealmIdentityManager(securityRealm);
        List<AuthenticationMechanism> undertowMechanisms;
        if (securityRealm != null) {
            Set<AuthMechanism> mechanisms = securityRealm.getSupportedAuthenticationMechanisms();
            undertowMechanisms = new ArrayList<AuthenticationMechanism>(mechanisms.size());
            undertowMechanisms.add(wrap(new CachedAuthenticatedSessionMechanism(), null));
            for (AuthMechanism current : mechanisms) {
                switch (current) {
                    case KERBEROS:
                        undertowMechanisms.add(wrap(new GSSAPIAuthenticationMechanism(new ServerSubjectFactory(securityRealm,
                                rim)), current));
                        break;
                    case CLIENT_CERT:
                        undertowMechanisms.add(wrap(new ClientCertAuthenticationMechanism(), current));
                        break;
                    case DIGEST:
                        List<DigestAlgorithm> digestAlgorithms = Collections.singletonList(DigestAlgorithm.MD5);
                        List<DigestQop> digestQops = Collections.singletonList(DigestQop.AUTH);
                        undertowMechanisms.add(wrap(new DigestAuthenticationMechanism(digestAlgorithms, digestQops,
                                securityRealm.getName(), "/management", new SimpleNonceManager()), current));
                        break;
                    case PLAIN:
                        undertowMechanisms.add(wrap(new BasicAuthenticationMechanism(securityRealm.getName()), current));
                        break;
                    case LOCAL:
                        break;
                }
            }
        } else {
            undertowMechanisms = Collections.singletonList(wrap(new AnonymousMechanism(), null));
        }

        // If the only mechanism is the cached mechanism then no need to add these.
        HttpHandler current = domainHandler;
        current = new AuthenticationCallHandler(current);
        // Currently the security handlers are being added after a PATH handler so we know authentication is required by
        // this point.
        current = new AuthenticationConstraintHandler(current);
        current = new AuthenticationMechanismsHandler(current, undertowMechanisms);

        return new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, rim, current);
    }

    private static AuthenticationMechanism wrap(final AuthenticationMechanism toWrap, final AuthMechanism mechanism) {
        return new AuthenticationMechanismWrapper(toWrap, mechanism);
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
}
