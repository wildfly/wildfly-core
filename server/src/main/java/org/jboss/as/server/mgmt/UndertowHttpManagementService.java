/*
* JBoss, Home of Professional Open Source.
* Copyright 2012, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.server.mgmt;

import io.undertow.server.HttpHandler;
import io.undertow.server.ListenerRegistry;
import io.undertow.server.handlers.ChannelUpgradeHandler;

import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import io.undertow.server.handlers.resource.ResourceManager;
import org.jboss.as.controller.ProcessStateNotifier;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.management.HttpInterfaceCommonPolicy.Header;
import org.jboss.as.domain.http.server.ConsoleMode;
import org.jboss.as.domain.http.server.ManagementHttpRequestProcessor;
import org.jboss.as.domain.http.server.ManagementHttpServer;
import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.network.ManagedBinding;
import org.jboss.as.network.ManagedBindingRegistry;
import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.mgmt.domain.ExtensibleHttpManagement;
import org.jboss.as.server.mgmt.domain.HttpManagement;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.security.auth.server.HttpAuthenticationFactory;
import org.wildfly.common.Assert;
import org.xnio.SslClientAuthMode;
import org.xnio.XnioWorker;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class UndertowHttpManagementService implements Service<HttpManagement> {

    public static final RuntimeCapability<Void> EXTENSIBLE_HTTP_MANAGEMENT_CAPABILITY =
            RuntimeCapability.Builder.of("org.wildfly.management.http.extensible", ExtensibleHttpManagement.class)
                    .addAdditionalRequiredPackages("org.jboss.as.domain-http-error-context")
                    .build();
    public static final ServiceName SERVICE_NAME = EXTENSIBLE_HTTP_MANAGEMENT_CAPABILITY.getCapabilityServiceName();

    public static final String SERVER_NAME = "wildfly-managment";
    public static final String HTTP_MANAGEMENT = "http-management";
    public static final String HTTPS_MANAGEMENT = "https-management";

    public static final ServiceName HTTP_UPGRADE_SERVICE_NAME = ServiceName.JBOSS.append("http-upgrade-registry", HTTP_MANAGEMENT);
    public static final ServiceName HTTPS_UPGRADE_SERVICE_NAME = ServiceName.JBOSS.append("http-upgrade-registry", HTTPS_MANAGEMENT);
    public static final String JBOSS_REMOTING = "jboss-remoting";
    public static final String MANAGEMENT_ENDPOINT = "management-endpoint";

    private final Consumer<HttpManagement> httpManagementConsumer;
    private final Supplier<ListenerRegistry> listenerRegistrySupplier;
    private final Supplier<ModelController> modelControllerSupplier;
    private final Supplier<SocketBinding> socketBindingSupplier;
    private final Supplier<SocketBinding> secureSocketBindingSupplier;
    private final Supplier<NetworkInterfaceBinding> interfaceBindingSupplier;
    private final Supplier<NetworkInterfaceBinding> secureInterfaceBindingSupplier;
    private final Supplier<SocketBindingManager> socketBindingManagerSupplier;
    private final Supplier<ProcessStateNotifier> processStateNotifierSupplier;
    private final Supplier<ManagementHttpRequestProcessor> requestProcessorSupplier;
    private final Supplier<XnioWorker> workerSupplier;
    private final Supplier<Executor> executorSupplier;
    private final Integer port;
    private final Integer securePort;
    private final Collection<String> allowedOrigins;
    private final Supplier<HttpAuthenticationFactory> httpAuthFactorySupplier;
    private final Supplier<SecurityRealm> securityRealmSupplier;
    private final Supplier<SSLContext> sslContextSupplier;
    private final ConsoleMode consoleMode;
    private final String consoleSlot;
    private final Map<String, List<Header>> constantHeaders;
    private ManagementHttpServer serverManagement;
    private SocketBindingManager socketBindingManager;
    private boolean useUnmanagedBindings = false;
    private ManagedBinding basicManagedBinding;
    private ManagedBinding secureManagedBinding;

    private ExtensibleHttpManagement httpManagement = new ExtensibleHttpManagement() {

        @Override
        public void addStaticContext(String contextName, ResourceManager resourceManager) {
            Assert.assertNotNull(serverManagement);
            serverManagement.addStaticContext(contextName, resourceManager);
        }

        @Override
        public void addManagementGetRemapContext(String contextName, final PathRemapper remapper) {
            Assert.assertNotNull(serverManagement);
            serverManagement.addManagementGetRemapContext(contextName, new ManagementHttpServer.PathRemapper() {
                @Override
                public String remapPath(String originalPath) {
                    return remapper.remapPath(originalPath);
                }
            });
        }

        @Override
        public void addManagementHandler(String contextName, boolean requiresSecurity, HttpHandler managementHandler) {
            Assert.assertNotNull(serverManagement);
            serverManagement.addManagementHandler(contextName, requiresSecurity, managementHandler);
        }

        @Override
        public void removeContext(String contextName) {
            Assert.assertNotNull(serverManagement);
            serverManagement.removeContext(contextName);
        }

        public InetSocketAddress getHttpSocketAddress(){
            return basicManagedBinding == null ? null : basicManagedBinding.getBindAddress();
        }

        public InetSocketAddress getHttpsSocketAddress() {
            return secureManagedBinding == null ? null : secureManagedBinding.getBindAddress();
        }

        @Override
        public int getHttpPort() {
            if (basicManagedBinding != null) {
                return basicManagedBinding.getBindAddress().getPort();
            }
            return port != null ? port : -1;
        }

        @Override
        public NetworkInterfaceBinding getHttpNetworkInterfaceBinding() {
            NetworkInterfaceBinding binding = interfaceBindingSupplier != null ? interfaceBindingSupplier.get() : null;
            if (binding == null) {
                SocketBinding socketBinding = socketBindingSupplier != null ? socketBindingSupplier.get() : null;
                if (socketBinding != null) {
                    binding = socketBinding.getNetworkInterfaceBinding();
                }
            }
            return binding;
        }

        @Override
        public int getHttpsPort() {
            if (secureManagedBinding != null) {
                return secureManagedBinding.getBindAddress().getPort();
            }
            return securePort != null ? securePort : -1;
        }

        @Override
        public NetworkInterfaceBinding getHttpsNetworkInterfaceBinding() {
            NetworkInterfaceBinding binding = interfaceBindingSupplier != null ? interfaceBindingSupplier.get() : null;
            if (binding == null) {
                SocketBinding socketBinding = secureSocketBindingSupplier != null ? secureSocketBindingSupplier.get() : null;
                if (socketBinding != null) {
                    binding = socketBinding.getNetworkInterfaceBinding();
                }
            }
            return binding;
        }

        @Override
        public boolean hasConsole() {
            return consoleMode.hasConsole();
        }
    };

    public UndertowHttpManagementService(final Consumer<HttpManagement> httpManagementConsumer,
                                         final Supplier<ListenerRegistry> listenerRegistrySupplier,
                                         final Supplier<ModelController> modelControllerSupplier,
                                         final Supplier<SocketBinding> socketBindingSupplier,
                                         final Supplier<SocketBinding> secureSocketBindingSupplier,
                                         final Supplier<SocketBindingManager> socketBindingManagerSupplier,
                                         final Supplier<NetworkInterfaceBinding> interfaceBindingSupplier,
                                         final Supplier<NetworkInterfaceBinding> secureInterfaceBindingSupplier,
                                         final Supplier<ProcessStateNotifier> processStateNotifierSupplier,
                                         final Supplier<ManagementHttpRequestProcessor> requestProcessorSupplier,
                                         final Supplier<XnioWorker> workerSupplier,
                                         final Supplier<Executor> executorSupplier,
                                         final Supplier<HttpAuthenticationFactory> httpAuthFactorySupplier,
                                         final Supplier<SecurityRealm> securityRealmSupplier,
                                         final Supplier<SSLContext> sslContextSupplier,
                                         final Integer port,
                                         final Integer securePort,
                                         final Collection<String> allowedOrigins,
                                         final ConsoleMode consoleMode,
                                         final String consoleSlot,
                                         final Map<String, List<Header>> constantHeaders) {
        this.httpManagementConsumer = httpManagementConsumer;
        this.listenerRegistrySupplier = listenerRegistrySupplier;
        this.modelControllerSupplier = modelControllerSupplier;
        this.socketBindingSupplier = socketBindingSupplier;
        this.secureSocketBindingSupplier = secureSocketBindingSupplier;
        this.socketBindingManagerSupplier = socketBindingManagerSupplier;
        this.interfaceBindingSupplier = interfaceBindingSupplier;
        this.secureInterfaceBindingSupplier = secureInterfaceBindingSupplier;
        this.processStateNotifierSupplier = processStateNotifierSupplier;
        this.requestProcessorSupplier = requestProcessorSupplier;
        this.workerSupplier = workerSupplier;
        this.executorSupplier = executorSupplier;
        this.httpAuthFactorySupplier = httpAuthFactorySupplier;
        this.securityRealmSupplier = securityRealmSupplier;
        this.sslContextSupplier = sslContextSupplier;
        this.port = port;
        this.securePort = securePort;
        this.allowedOrigins = allowedOrigins;
        this.consoleMode = consoleMode;
        this.consoleSlot = consoleSlot;
        this.constantHeaders = constantHeaders;
    }

    /**
     * Starts the service.
     *
     * @param context The start context
     * @throws StartException If any errors occur
     */
    @Override
    public synchronized void start(final StartContext context) throws StartException {
        final ModelController modelController = modelControllerSupplier.get();
        final ProcessStateNotifier processStateNotifier = processStateNotifierSupplier.get();
        socketBindingManager = socketBindingManagerSupplier != null ? socketBindingManagerSupplier.get() : null;

        final SecurityRealm securityRealm = securityRealmSupplier != null ? securityRealmSupplier.get() : null;
        final HttpAuthenticationFactory httpAuthenticationFactory = httpAuthFactorySupplier != null ? httpAuthFactorySupplier.get() : null;
        SSLContext sslContext = sslContextSupplier != null ? sslContextSupplier.get() : null;
        final SslClientAuthMode sslClientAuthMode;
        if (sslContext == null && securityRealm != null) {
            sslContext = securityRealm.getSSLContext();
            sslClientAuthMode = getSslClientAuthMode(securityRealm);
        } else {
            sslClientAuthMode = null;
        }

        InetSocketAddress bindAddress = null;
        InetSocketAddress secureBindAddress = null;

        final SocketBinding basicBinding = socketBindingSupplier != null ? socketBindingSupplier.get() : null;
        final SocketBinding secureBinding = secureSocketBindingSupplier != null ? secureSocketBindingSupplier.get() : null;
        final NetworkInterfaceBinding interfaceBinding = interfaceBindingSupplier != null ? interfaceBindingSupplier.get() : null;
        final NetworkInterfaceBinding secureInterfaceBinding = secureInterfaceBindingSupplier != null ? secureInterfaceBindingSupplier.get() : null;
        if (interfaceBinding != null) {
            useUnmanagedBindings = true;
            assert this.port != null;
            final int port = this.port;
            if (port > 0) {
                bindAddress = new InetSocketAddress(interfaceBinding.getAddress(), port);
            }
            assert this.securePort != null;
            final int securePort = this.securePort;
            if (securePort > 0) {
                InetAddress secureAddress = secureInterfaceBinding == null ? interfaceBinding.getAddress() : secureInterfaceBinding.getAddress();
                secureBindAddress = new InetSocketAddress(secureAddress, securePort);
            }
        } else {
            if (basicBinding != null) {
                bindAddress = basicBinding.getSocketAddress();
            }
            if (secureBinding != null) {
                secureBindAddress = secureBinding.getSocketAddress();
            }
        }
        List<ListenerRegistry.Listener> listeners = new ArrayList<>();
        //TODO: rethink this whole ListenerRegistry business
        if(bindAddress != null) {
            ListenerRegistry.Listener http = new ListenerRegistry.Listener("http", HTTP_MANAGEMENT, SERVER_NAME, bindAddress);
            http.setContextInformation("socket-binding", basicBinding);
            listeners.add(http);
        }
        if(secureBindAddress != null) {
            ListenerRegistry.Listener https = new ListenerRegistry.Listener("https", HTTPS_MANAGEMENT, SERVER_NAME, secureBindAddress);
            https.setContextInformation("socket-binding", secureBinding);
            listeners.add(https);
        }

        final ChannelUpgradeHandler upgradeHandler = new ChannelUpgradeHandler();
        final ServiceBuilder<?> builder = context.getChildTarget().addService(HTTP_UPGRADE_SERVICE_NAME);
        final Consumer<Object> upgradeHandlerConsumer = builder.provides(HTTP_UPGRADE_SERVICE_NAME, HTTPS_UPGRADE_SERVICE_NAME);
        // TODO: An "alias" shouldn't actually be needed since we already do a
        // builder.provides(...) with this same ServiceName. However, without this explicit aliasing
        // the call to (service)registry.getService(...) returns null if it's queried by the "provided"
        // ServiceName. It works fine if it's instead queried by the "alias".
        // See WFCORE-4560 for more details.
        builder.addAliases(HTTPS_UPGRADE_SERVICE_NAME);
        builder.setInstance(org.jboss.msc.Service.newInstance(upgradeHandlerConsumer, upgradeHandler));
        builder.install();
        for (ListenerRegistry.Listener listener : listeners) {
            listener.addHttpUpgradeMetadata(new ListenerRegistry.HttpUpgradeMetadata(JBOSS_REMOTING, MANAGEMENT_ENDPOINT));
        }

        if (listenerRegistrySupplier.get() != null) {
            for(ListenerRegistry.Listener listener : listeners) {
                listenerRegistrySupplier.get().addListener(listener);
            }
        }

        try {
            serverManagement = ManagementHttpServer.builder()
                    .setBindAddress(bindAddress)
                    .setSecureBindAddress(secureBindAddress)
                    .setModelController(modelController)
                    .setSecurityRealm(securityRealm)
                    .setSSLContext(sslContext)
                    .setSSLClientAuthMode(sslClientAuthMode)
                    .setHttpAuthenticationFactory(httpAuthenticationFactory)
                    .setControlledProcessStateNotifier(processStateNotifier)
                    .setConsoleMode(consoleMode)
                    .setConsoleSlot(consoleSlot)
                    .setChannelUpgradeHandler(upgradeHandler)
                    .setManagementHttpRequestProcessor(requestProcessorSupplier.get())
                    .setAllowedOrigins(allowedOrigins)
                    .setWorker(workerSupplier.get())
                    .setExecutor(executorSupplier.get())
                    .setConstantHeaders(constantHeaders)
                    .build();

            serverManagement.start();

            // Register the now-created sockets with the SBM
            if (socketBindingManager != null) {
                if (useUnmanagedBindings) {
                    SocketBindingManager.UnnamedBindingRegistry registry = socketBindingManager.getUnnamedRegistry();
                    if (bindAddress != null) {
                        final InetSocketAddress boundAddress = serverManagement.getLocalAddress(InetSocketAddress.class);
                        basicManagedBinding = ManagedBinding.Factory.createSimpleManagedBinding("management-http", boundAddress, null);
                        registry.registerBinding(basicManagedBinding);
                    }
                    if (secureBindAddress != null) {
                        final InetSocketAddress boundAddress = serverManagement.getSecureLocalAddress(InetSocketAddress.class);
                        secureManagedBinding = ManagedBinding.Factory.createSimpleManagedBinding("management-https", boundAddress, null);
                        registry.registerBinding(secureManagedBinding);
                    }
                } else {
                    SocketBindingManager.NamedManagedBindingRegistry registry = socketBindingManager.getNamedRegistry();
                    if (basicBinding != null) {
                        final InetSocketAddress boundAddress = serverManagement.getLocalAddress(InetSocketAddress.class);
                        basicManagedBinding = ManagedBinding.Factory.createSimpleManagedBinding(basicBinding.getName(), boundAddress, null);
                        registry.registerBinding(basicManagedBinding);
                    }
                    if (secureBinding != null) {
                        final InetSocketAddress boundAddress = serverManagement.getSecureLocalAddress(InetSocketAddress.class);
                        secureManagedBinding = ManagedBinding.Factory.createSimpleManagedBinding(secureBinding.getName(), boundAddress, null);
                        registry.registerBinding(secureManagedBinding);
                    }
                }
            }
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (e instanceof BindException || cause instanceof BindException) {
                final StringBuilder sb = new StringBuilder().append(e.getLocalizedMessage());
                if (bindAddress != null)
                    sb.append(" ").append(bindAddress);
                if (secureBindAddress != null)
                    sb.append(" ").append(secureBindAddress);
                throw new StartException(sb.toString());
            } else {
                throw ServerLogger.ROOT_LOGGER.failedToStartHttpManagementService(e);
            }
        }
        httpManagementConsumer.accept(httpManagement);
    }

    /**
     * Stops the service.
     *
     * @param context The stop context
     */
    @Override
    public synchronized void stop(final StopContext context) {
        httpManagementConsumer.accept(null);
        ListenerRegistry lr = listenerRegistrySupplier.get();
        if(lr != null) {
            lr.removeListener(HTTP_MANAGEMENT);
            lr.removeListener(HTTPS_MANAGEMENT);
        }
        if (serverManagement != null) {
            try {
                serverManagement.stop();
            } finally {
                serverManagement = null;

                // Unregister sockets from the SBM
                if (socketBindingManager != null) {
                    ManagedBindingRegistry registry = useUnmanagedBindings ? socketBindingManager.getUnnamedRegistry() : socketBindingManager.getNamedRegistry();
                    if (basicManagedBinding != null) {
                        registry.unregisterBinding(basicManagedBinding);
                        basicManagedBinding = null;
                    }
                    if (secureManagedBinding != null) {
                        registry.unregisterBinding(secureManagedBinding);
                        secureManagedBinding = null;
                    }
                    socketBindingManager = null;
                    useUnmanagedBindings = false;
                }
            }
        }
    }

    @Override
    public HttpManagement getValue() throws IllegalStateException, IllegalArgumentException {
        return httpManagement;
    }

    private static SslClientAuthMode getSslClientAuthMode(final SecurityRealm securityRealm) {
        Set<AuthMechanism> supportedMechanisms = securityRealm.getSupportedAuthenticationMechanisms();
        if (supportedMechanisms.contains(AuthMechanism.CLIENT_CERT)) {
            if (supportedMechanisms.contains(AuthMechanism.DIGEST)
                    || supportedMechanisms.contains(AuthMechanism.PLAIN)) {
                // Username / Password auth is possible so don't mandate a client certificate.
                return SslClientAuthMode.REQUESTED;
            } else {
                return SslClientAuthMode.REQUIRED;
            }
        }

        return null;
    }

}
