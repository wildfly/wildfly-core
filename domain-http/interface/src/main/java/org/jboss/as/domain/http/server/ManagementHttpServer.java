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

import static org.jboss.as.domain.http.server.logging.HttpServerLogger.ROOT_LOGGER;
import static org.xnio.Options.SSL_CLIENT_AUTH_MODE;
import static org.xnio.SslClientAuthMode.REQUESTED;
import static org.xnio.SslClientAuthMode.REQUIRED;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;

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
import io.undertow.server.HttpServerExchange;
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
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ModelController;
import org.jboss.as.domain.http.server.cors.CorsHttpHandler;
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
import org.wildfly.common.Assert;
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


    public interface PathRemapper {
        String remapPath(String originalPath);
    }

    private static final Map<Pattern, Charset> USER_AGENT_CHARSET_MAP = generateCharsetMap();

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
    private final ExtensionHandlers extensionHandlers;

    private ManagementHttpServer(HttpOpenListener openListener, InetSocketAddress httpAddress, InetSocketAddress secureAddress, SSLContext sslContext,
                                 SslClientAuthMode sslClientAuthMode, XnioWorker worker, ExtensionHandlers extensionExtensionHandlers) {
        this.openListener = openListener;
        this.httpAddress = httpAddress;
        this.secureAddress = secureAddress;
        this.sslContext = sslContext;
        this.sslClientAuthMode = sslClientAuthMode;
        this.worker = worker;
        this.extensionHandlers = extensionExtensionHandlers;
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

    public void addStaticContext(String contextName, ResourceManager resourceManager) {
        Assert.checkNotNullParam("contextName", contextName);
        Assert.checkNotNullParam("resourceManager", resourceManager);
        String context = fixPath(contextName);
        // Reject reserved contexts or duplicate extensions
        if (extensionHandlers.reservedContexts.contains(context) || !extensionHandlers.extensionContexts.add(context)) {
            throw new IllegalStateException();
        }
        ResourceHandlerDefinition def = DomainUtil.createStaticContentHandler(resourceManager, context);
        HttpHandler readinessHandler = new RedirectReadinessHandler(extensionHandlers.securityRealm, def.getHandler(),
                ErrorContextHandler.ERROR_CONTEXT);
        extensionHandlers.extensionPathHandler.addPrefixPath(context, readinessHandler);
    }

    public void addManagementGetRemapContext(String contextName, PathRemapper remapper) {
        Assert.checkNotNullParam("contextName", contextName);
        String context = fixPath(contextName);
        // Reject reserved contexts or duplicate extensions
        if (extensionHandlers.reservedContexts.contains(context) || !extensionHandlers.extensionContexts.add(context)) {
            throw new IllegalStateException();
        }
        HttpHandler remapHandler = new RemapHandler(remapper, extensionHandlers.managementHandler);
        extensionHandlers.extensionPathHandler.addPrefixPath(context, remapHandler);
    }

    public void removeContext(String contextName) {
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

        final ExtensionHandlers extensionHandlers = setupOpenListener(openListener, modelController, consoleMode, consoleSlot, controlledProcessStateService,
                secureRedirectPort, securityRealm, upgradeHandler, managementHttpRequestProcessor, allowedOrigins, managementExecutor);
        return new ManagementHttpServer(openListener, bindAddress, secureBindAddress, sslContext, sslClientAuthMode, worker, extensionHandlers);
    }

    private static class ExtensionHandlers {
        private final PathHandler extensionPathHandler;
        private final HttpHandler managementHandler;
        private final SecurityRealm securityRealm;
        private final Set<String> reservedContexts;
        private final Set<String> extensionContexts = new HashSet<>();

        private ExtensionHandlers(PathHandler extensionPathHandler, HttpHandler managementHandler,
                                  SecurityRealm securityRealm, ResourceHandlerDefinition consoleHandler) {
            this.extensionPathHandler = extensionPathHandler;
            this.managementHandler = managementHandler;
            this.securityRealm = securityRealm;
            if (consoleHandler == null) {
                this.reservedContexts = RESERVED_CONTEXTS;
            } else {
                Set<String> set = new HashSet<>(RESERVED_CONTEXTS);
                set.add(consoleHandler.getContext());
                this.reservedContexts = Collections.unmodifiableSet(set);
            }
        }
    }

    private static ExtensionHandlers setupOpenListener(HttpOpenListener listener, ModelController modelController, ConsoleMode consoleMode,
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
        pathHandler.addPrefixPath("/", rootConsoleRedirectHandler);
        if (consoleHandler != null) {
            HttpHandler readinessHandler = new RedirectReadinessHandler(securityRealm, consoleHandler.getHandler(),
                    ErrorContextHandler.ERROR_CONTEXT);
            pathHandler.addPrefixPath(consoleHandler.getContext(), readinessHandler);
        }

        HttpHandler domainApiHandler = InExecutorHandler.wrap(
                managementExecutor,
                new DomainApiCheckHandler(modelController, controlledProcessStateService, allowedOrigins)
        );
        HttpHandler readinessHandler = wrapXFrameOptions(new DmrFailureReadinessHandler(securityRealm,
                secureDomainAccess(domainApiHandler, securityRealm), ErrorContextHandler.ERROR_CONTEXT));
        pathHandler.addPrefixPath(DomainApiCheckHandler.PATH, readinessHandler);
        pathHandler.addExactPath(DomainApiCheckHandler.GENERIC_CONTENT_REQUEST, readinessHandler);

        if (securityRealm != null) {
            pathHandler.addPrefixPath(LogoutHandler.PATH, wrapXFrameOptions(new LogoutHandler(securityRealm.getName())));
        }

        return new ExtensionHandlers(pathHandler, readinessHandler, securityRealm, consoleHandler);
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
                        undertowMechanisms.add(wrap(new BasicAuthenticationMechanism(securityRealm.getName(), "BASIC", false, null, StandardCharsets.UTF_8, USER_AGENT_CHARSET_MAP), current));
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

    private static Map<Pattern, Charset> generateCharsetMap() {
        final Map<Pattern, Charset> charsetMap = new HashMap<>();
        charsetMap.put(Pattern.compile("Mozilla/5\\.0 \\(.*\\) Gecko/.* Firefox/.*"), StandardCharsets.ISO_8859_1);
        charsetMap.put(Pattern.compile("(?!.*OPR)(?!.*Chrome)Mozilla/5\\.0 \\(.*\\).* Safari/.*"), StandardCharsets.ISO_8859_1);
        charsetMap.put(Pattern.compile("Mozilla/5\\.0 \\(.*; Trident/.*; rv:.*\\).*"), StandardCharsets.ISO_8859_1);
        charsetMap.put(Pattern.compile("Mozilla/5\\.0 \\(.* MSIE.* Trident/.*\\)"), StandardCharsets.ISO_8859_1);
        return Collections.unmodifiableMap(charsetMap);
    }

    private static AuthenticationMechanism wrap(final AuthenticationMechanism toWrap, final AuthMechanism mechanism) {
        return new AuthenticationMechanismWrapper(toWrap, mechanism);
    }

    private static HttpHandler wrapXFrameOptions(final HttpHandler toWrap) {
        return new SetHeaderHandler(toWrap, "X-Frame-Options", "SAMEORIGIN");
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
}
