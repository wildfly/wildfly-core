/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;

import static org.jboss.as.remoting.RemotingHttpUpgradeService.HTTP_UPGRADE_REGISTRY;
import static org.jboss.as.server.mgmt.HttpManagementResourceDefinition.SECURE_SOCKET_BINDING;
import static org.jboss.as.server.mgmt.HttpManagementResourceDefinition.SOCKET_BINDING;
import static org.jboss.as.server.mgmt.UndertowHttpManagementService.EXTENSIBLE_HTTP_MANAGEMENT_CAPABILITY;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.CapabilityServiceTarget;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.management.BaseHttpInterfaceAddStepHandler;
import org.jboss.as.controller.management.HttpInterfaceCommonPolicy;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.http.server.ConsoleAvailability;
import org.jboss.as.domain.http.server.ConsoleMode;
import org.jboss.as.domain.http.server.ManagementHttpRequestProcessor;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.as.remoting.RemotingHttpUpgradeService;
import org.jboss.as.remoting.RemotingServices;
import org.jboss.as.remoting.management.ManagementChannelRegistryService;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.as.server.ExternalManagementRequestExecutor;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.Services;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.mgmt.HttpManagementRequestsService;
import org.jboss.as.server.mgmt.HttpManagementResourceDefinition;
import org.jboss.as.server.mgmt.HttpShutdownService;
import org.jboss.as.server.mgmt.ManagementWorkerService;
import org.jboss.as.server.mgmt.UndertowHttpManagementService;
import org.jboss.as.server.mgmt.domain.HttpManagement;
import org.jboss.as.server.security.AdvancedSecurityMetaData;
import org.jboss.as.server.security.SecurityMetaData;
import org.jboss.as.server.security.VirtualDomainMarkerUtility;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.wildfly.security.auth.server.HttpAuthenticationFactory;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.http.HttpServerAuthenticationMechanismFactory;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.xnio.XnioWorker;

import io.undertow.server.ListenerRegistry;


/**
 * A handler that activates the HTTP management API on a Server.
 *
 * @author Jason T. Greene
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class HttpManagementAddHandler extends BaseHttpInterfaceAddStepHandler {

    public static final HttpManagementAddHandler INSTANCE = new HttpManagementAddHandler();

    private HttpManagementAddHandler() {
    }

    @Override
    protected void populateModel(OperationContext context, ModelNode operation, Resource resource)
            throws OperationFailedException {
        super.populateModel(context, operation, resource);
        HttpManagementResourceDefinition.addAttributeValidator(context);
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return super.requiresRuntime(context)
                && (context.getProcessType() != ProcessType.EMBEDDED_SERVER || context.getRunningMode() != RunningMode.ADMIN_ONLY);
    }

    @Override
    protected List<ServiceName> installServices(OperationContext context, HttpInterfaceCommonPolicy commonPolicy, ModelNode model) throws OperationFailedException {
        final CapabilityServiceTarget serviceTarget = context.getCapabilityServiceTarget();

        // Socket-binding reference based config
        final String socketBindingName = SOCKET_BINDING.resolveModelAttribute(context, model).asStringOrNull();
        final String secureSocketBindingName = SECURE_SOCKET_BINDING.resolveModelAttribute(context, model).asStringOrNull();

        // Log the config
        if (socketBindingName != null) {
            if (secureSocketBindingName != null) {
                ServerLogger.ROOT_LOGGER.creatingHttpManagementServiceOnSocketAndSecureSocket(socketBindingName,
                        secureSocketBindingName);
            } else {
                ServerLogger.ROOT_LOGGER.creatingHttpManagementServiceOnSocket(socketBindingName);
            }
        } else if (secureSocketBindingName != null) {
            ServerLogger.ROOT_LOGGER.creatingHttpManagementServiceOnSecureSocket(secureSocketBindingName);
        }

        ConsoleMode consoleMode = consoleMode(commonPolicy.isConsoleEnabled(), context.getRunningMode() == RunningMode.ADMIN_ONLY);

        // Track active requests
        final ServiceName requestProcessorName = UndertowHttpManagementService.SERVICE_NAME.append("requests");
        HttpManagementRequestsService.installService(requestProcessorName, serviceTarget);

        NativeManagementServices.installManagementWorkerService(serviceTarget, context.getServiceRegistry(false));

        final String httpAuthenticationFactory = commonPolicy.getHttpAuthenticationFactory();
        final String sslContext = commonPolicy.getSSLContext();
        if (httpAuthenticationFactory == null) {
            ServerLogger.ROOT_LOGGER.httpManagementInterfaceIsUnsecured();
        }

        final CapabilityServiceBuilder<?> builder = serviceTarget.addCapability(EXTENSIBLE_HTTP_MANAGEMENT_CAPABILITY);
        final Consumer<HttpManagement> hmConsumer = builder.provides(EXTENSIBLE_HTTP_MANAGEMENT_CAPABILITY);
        final Supplier<ListenerRegistry> lrSupplier = builder.requires(RemotingServices.HTTP_LISTENER_REGISTRY);
        final Supplier<ModelController> mcSupplier = builder.requires(Services.JBOSS_SERVER_CONTROLLER);
        final Supplier<SocketBinding> sbSupplier = socketBindingName != null ? builder.requires(SocketBinding.SERVICE_DESCRIPTOR, socketBindingName) : null;
        final Supplier<SocketBinding> ssbSupplier = secureSocketBindingName != null ? builder.requires(SocketBinding.SERVICE_DESCRIPTOR, secureSocketBindingName) : null;
        final Supplier<SocketBindingManager> sbmSupplier = builder.requires(SocketBindingManager.SERVICE_DESCRIPTOR);
        final Supplier<ConsoleAvailability> caSupplier = builder.requiresCapability("org.wildfly.management.console-availability", ConsoleAvailability.class);
        final Supplier<ManagementHttpRequestProcessor> rpSupplier = builder.requires(requestProcessorName);
        final Supplier<XnioWorker> xwSupplier = builder.requires(ManagementWorkerService.SERVICE_NAME);
        final Supplier<Executor> eSupplier = builder.requires(ExternalManagementRequestExecutor.SERVICE_NAME);
        final Supplier<HttpAuthenticationFactory> hafSupplier = httpAuthenticationFactory != null ? builder.requiresCapability(HTTP_AUTHENTICATION_FACTORY_CAPABILITY, HttpAuthenticationFactory.class, httpAuthenticationFactory) : null;
        Supplier<ServerEnvironment> environment = builder.requires(ServerEnvironment.SERVICE_DESCRIPTOR);
        Supplier<String> consoleSlot = new Supplier<>() {
            @Override
            public String get() {
                return environment.get().getProductConfig().getConsoleSlot();
            }
        };
        Supplier<SecurityDomain> virtualSecurityDomainSupplier = null;
        Supplier<HttpServerAuthenticationMechanismFactory> virtualMechanismFactorySupplier = null;
        if (VirtualDomainMarkerUtility.isVirtualDomainRequired(context)) {
            SecurityMetaData securityMetaData = context.getAttachment(SecurityMetaData.OPERATION_CONTEXT_ATTACHMENT_KEY);
            if (securityMetaData instanceof AdvancedSecurityMetaData) {
                virtualSecurityDomainSupplier = builder.requires(securityMetaData.getSecurityDomain());
                virtualMechanismFactorySupplier = builder.requires(((AdvancedSecurityMetaData) securityMetaData).getHttpServerAuthenticationMechanismFactory());
            }
        }
        final Supplier<SSLContext> scSupplier = sslContext != null ? builder.requiresCapability(SSL_CONTEXT_CAPABILITY, SSLContext.class, sslContext) : null;
        final UndertowHttpManagementService undertowService = new UndertowHttpManagementService(hmConsumer, lrSupplier, mcSupplier, sbSupplier, ssbSupplier, sbmSupplier,
                null, null, rpSupplier, xwSupplier, eSupplier, hafSupplier, scSupplier, null, null, commonPolicy.getAllowedOrigins(), consoleMode,
                consoleSlot, commonPolicy.getConstantHeaders(), caSupplier, virtualSecurityDomainSupplier, virtualMechanismFactorySupplier,
                commonPolicy.getBacklog(), commonPolicy.getNoRequestTimeoutMs(), commonPolicy.getConnectionHighWater(), commonPolicy.getConnectionLowWater());
        builder.setInstance(undertowService);
        builder.install();

        // Add service preventing the server from shutting down
        final ServiceName shutdownName = UndertowHttpManagementService.SERVICE_NAME.append("shutdown");
        final ServiceBuilder<?> sb = serviceTarget.addService(shutdownName);
        final Supplier<Executor> executorSupplier = sb.requires(Services.JBOSS_SERVER_EXECUTOR);
        final Supplier<ManagementHttpRequestProcessor> processorSupplier = sb.requires(requestProcessorName);
        final Supplier<ManagementChannelRegistryService> registrySupplier = sb.requires(ManagementChannelRegistryService.SERVICE_NAME);
        sb.requires(UndertowHttpManagementService.SERVICE_NAME);
        sb.setInstance(new HttpShutdownService(executorSupplier, processorSupplier, registrySupplier));
        sb.install();

        if(commonPolicy.isHttpUpgradeEnabled()) {
            final String hostName = WildFlySecurityManager.getPropertyPrivileged(ServerEnvironment.NODE_NAME, null);

            NativeManagementServices.installRemotingServicesIfNotInstalled(serviceTarget, hostName, context.getServiceRegistry(false));
            final String httpConnectorName;
            if (socketBindingName != null || (secureSocketBindingName == null)) {
                httpConnectorName = ManagementRemotingServices.HTTP_CONNECTOR;
            } else {
                httpConnectorName = ManagementRemotingServices.HTTPS_CONNECTOR;
            }

            String saslAuthFactoryName = commonPolicy.getSaslAuthenticationFactory();
            ServiceName saslAuthenticationFactory = saslAuthFactoryName != null ? context.getCapabilityServiceName(
                    SASL_AUTHENTICATION_FACTORY_CAPABILITY, saslAuthFactoryName, SaslAuthenticationFactory.class) : null;

            RemotingHttpUpgradeService.installServices(context, ManagementRemotingServices.HTTP_CONNECTOR, httpConnectorName,
                    ManagementRemotingServices.MANAGEMENT_ENDPOINT, commonPolicy.getConnectorOptions(), saslAuthenticationFactory);

            return Arrays.asList(UndertowHttpManagementService.SERVICE_NAME, HTTP_UPGRADE_REGISTRY.append(httpConnectorName));
        }
        return Collections.singletonList(UndertowHttpManagementService.SERVICE_NAME);
    }

    private ConsoleMode consoleMode(boolean consoleEnabled, boolean adminOnly) {
        return consoleEnabled ? adminOnly ?  ConsoleMode.ADMIN_ONLY : ConsoleMode.CONSOLE : ConsoleMode.NO_CONSOLE;
    }

}
