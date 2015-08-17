/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.operations;

import static org.jboss.as.server.mgmt.HttpManagementResourceDefinition.ALLOWED_ORIGINS;
import static org.jboss.as.server.mgmt.HttpManagementResourceDefinition.HTTP_MANAGEMENT_CAPABILITY;
import static org.jboss.as.server.mgmt.HttpManagementResourceDefinition.SECURE_SOCKET_BINDING;
import static org.jboss.as.server.mgmt.HttpManagementResourceDefinition.SECURITY_REALM;
import static org.jboss.as.server.mgmt.HttpManagementResourceDefinition.SOCKET_BINDING;
import static org.jboss.as.server.mgmt.HttpManagementResourceDefinition.SOCKET_BINDING_CAPABILITY_NAME;

import io.undertow.server.ListenerRegistry;

import java.util.List;
import java.util.concurrent.Executor;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.domain.http.server.ConsoleMode;
import org.jboss.as.domain.http.server.ManagementHttpRequestProcessor;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.as.network.SocketBindingManagerImpl;
import org.jboss.as.remoting.HttpListenerRegistryService;
import org.jboss.as.remoting.RemotingHttpUpgradeService;
import org.jboss.as.remoting.RemotingServices;
import org.jboss.as.remoting.management.ManagementChannelRegistryService;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerEnvironmentService;
import org.jboss.as.server.Services;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.mgmt.HttpManagementRequestsService;
import org.jboss.as.server.mgmt.HttpManagementResourceDefinition;
import org.jboss.as.server.mgmt.HttpShutdownService;
import org.jboss.as.server.mgmt.UndertowHttpManagementService;
import org.jboss.as.server.mgmt.domain.HttpManagement;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.remoting3.RemotingOptions;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.xnio.OptionMap;
import org.xnio.OptionMap.Builder;

/**
 * A handler that activates the HTTP management API on a Server.
 *
 * @author Jason T. Greene
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class HttpManagementAddHandler extends AbstractAddStepHandler {

    public static final HttpManagementAddHandler INSTANCE = new HttpManagementAddHandler();
    public static final String OPERATION_NAME = ModelDescriptionConstants.ADD;

    public HttpManagementAddHandler() {
        super(HTTP_MANAGEMENT_CAPABILITY, HttpManagementResourceDefinition.ATTRIBUTE_DEFINITIONS);
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        //TODO Gigantic HACK to disable the runtime part of this for the core model testing.
        //The core model testing currently uses RunningMode.ADMIN_ONLY, but in the real world
        //the http interface needs to be enabled even when that happens.
        //I don't want to wire up all the services unless I can avoid it, so for now the tests set this system property
        return WildFlySecurityManager.getPropertyPrivileged("jboss.as.test.disable.runtime", null) == null
                && (context.getProcessType() != ProcessType.EMBEDDED_SERVER || context.getRunningMode() != RunningMode.ADMIN_ONLY);
    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model)
            throws OperationFailedException {

        boolean httpUpgrade = HttpManagementResourceDefinition.HTTP_UPGRADE_ENABLED.resolveModelAttribute(context, model).asBoolean();
        installHttpManagementConnector(context, model, context.getServiceTarget(), httpUpgrade);
    }

    static void installHttpManagementConnector(final OperationContext context, final ModelNode model, final ServiceTarget serviceTarget,
                                               final boolean httpUpgrade) throws OperationFailedException {

        ServiceName socketBindingServiceName = null;
        ServiceName secureSocketBindingServiceName = null;

        final List<String> allowedOrigins = ALLOWED_ORIGINS.unwrap(context, model);

        // Socket-binding reference based config
        final ModelNode socketBindingNode = SOCKET_BINDING.resolveModelAttribute(context, model);
        if (socketBindingNode.isDefined()) {
            final String bindingName = socketBindingNode.asString();
            socketBindingServiceName = context.getCapabilityServiceName(SOCKET_BINDING_CAPABILITY_NAME, bindingName, SocketBinding.class);
        }
        final ModelNode secureSocketBindingNode = SECURE_SOCKET_BINDING.resolveModelAttribute(context, model);
        if (secureSocketBindingNode.isDefined()) {
            final String bindingName = secureSocketBindingNode.asString();
            secureSocketBindingServiceName = context.getCapabilityServiceName(SOCKET_BINDING_CAPABILITY_NAME, bindingName, SocketBinding.class);
        }

        // Log the config
        if (socketBindingServiceName != null) {
            if (secureSocketBindingServiceName != null) {
                ServerLogger.ROOT_LOGGER.creatingHttpManagementServiceOnSocketAndSecureSocket(socketBindingServiceName.getSimpleName(),
                        secureSocketBindingServiceName.getSimpleName());
            } else {
                ServerLogger.ROOT_LOGGER.creatingHttpManagementServiceOnSocket(socketBindingServiceName.getSimpleName());
            }
        } else if (secureSocketBindingServiceName != null) {
            ServerLogger.ROOT_LOGGER.creatingHttpManagementServiceOnSecureSocket(secureSocketBindingServiceName.getSimpleName());
        }

        String securityRealm = null;
        final ModelNode realmNode = SECURITY_REALM.resolveModelAttribute(context, model);
        if (realmNode.isDefined()) {
            securityRealm = realmNode.asString();
        } else {
            ServerLogger.ROOT_LOGGER.httpManagementInterfaceIsUnsecured();
        }
        boolean consoleEnabled = HttpManagementResourceDefinition.CONSOLE_ENABLED.resolveModelAttribute(context, model).asBoolean();
        ConsoleMode consoleMode;
        if (consoleEnabled){
            consoleMode = context.getRunningMode() == RunningMode.ADMIN_ONLY ? ConsoleMode.ADMIN_ONLY : ConsoleMode.CONSOLE;
        }else{
            consoleMode = ConsoleMode.NO_CONSOLE;
        }

        // Track active requests
        final ServiceName requestProcessorName = UndertowHttpManagementService.SERVICE_NAME.append("requests");
        HttpManagementRequestsService.installService(requestProcessorName, serviceTarget);

        ServerEnvironment environment = (ServerEnvironment) context.getServiceRegistry(false).getRequiredService(ServerEnvironmentService.SERVICE_NAME).getValue();
        final UndertowHttpManagementService undertowService = new UndertowHttpManagementService(consoleMode, environment.getProductConfig().getConsoleSlot());
        ServiceBuilder<HttpManagement> undertowBuilder = serviceTarget.addService(UndertowHttpManagementService.SERVICE_NAME, undertowService)
                .addDependency(Services.JBOSS_SERVER_CONTROLLER, ModelController.class, undertowService.getModelControllerInjector())
                .addDependency(SocketBindingManagerImpl.SOCKET_BINDING_MANAGER, SocketBindingManager.class, undertowService.getSocketBindingManagerInjector())
                .addDependency(ControlledProcessStateService.SERVICE_NAME, ControlledProcessStateService.class, undertowService.getControlledProcessStateServiceInjector())
                .addDependency(HttpListenerRegistryService.SERVICE_NAME, ListenerRegistry.class, undertowService.getListenerRegistry())
                .addDependency(requestProcessorName, ManagementHttpRequestProcessor.class, undertowService.getRequestProcessorValue())
                .addInjection(undertowService.getAllowedOriginsInjector(), allowedOrigins);

            if (socketBindingServiceName != null) {
                undertowBuilder.addDependency(socketBindingServiceName, SocketBinding.class, undertowService.getSocketBindingInjector());
            }
            if (secureSocketBindingServiceName != null) {
                undertowBuilder.addDependency(secureSocketBindingServiceName, SocketBinding.class, undertowService.getSecureSocketBindingInjector());
            }

        if (securityRealm != null) {
            SecurityRealm.ServiceUtil.addDependency(undertowBuilder, undertowService.getSecurityRealmInjector(), securityRealm, false);
        }

        undertowBuilder.install();

        // Add service preventing the server from shutting down
        final HttpShutdownService shutdownService = new HttpShutdownService();
        final ServiceName shutdownName = UndertowHttpManagementService.SERVICE_NAME.append("shutdown");
        serviceTarget.addService(shutdownName, shutdownService)
                .addDependency(requestProcessorName, ManagementHttpRequestProcessor.class, shutdownService.getProcessorValue())
                .addDependency(Services.JBOSS_SERVER_EXECUTOR, Executor.class, shutdownService.getExecutorValue())
                .addDependency(ManagementChannelRegistryService.SERVICE_NAME, ManagementChannelRegistryService.class, shutdownService.getMgmtChannelRegistry())
                .addDependency(UndertowHttpManagementService.SERVICE_NAME)
                .setInitialMode(ServiceController.Mode.ACTIVE)
                .install();

        if(httpUpgrade) {
            final String hostName = WildFlySecurityManager.getPropertyPrivileged(ServerEnvironment.NODE_NAME, null);

            ServiceName tmpDirPath = ServiceName.JBOSS.append("server", "path", "jboss.server.temp.dir");
            RemotingServices.installSecurityServices(serviceTarget, ManagementRemotingServices.HTTP_CONNECTOR, securityRealm, null, tmpDirPath);
            NativeManagementServices.installRemotingServicesIfNotInstalled(serviceTarget, hostName, context.getServiceRegistry(false));
            final String httpConnectorName;
            if (socketBindingServiceName != null || (secureSocketBindingServiceName == null)) {
                httpConnectorName = ManagementRemotingServices.HTTP_CONNECTOR;
            } else {
                httpConnectorName = ManagementRemotingServices.HTTPS_CONNECTOR;
            }

            OptionMap options = createConnectorOptions(context, model);
            RemotingHttpUpgradeService.installServices(serviceTarget, ManagementRemotingServices.HTTP_CONNECTOR, httpConnectorName, ManagementRemotingServices.MANAGEMENT_ENDPOINT, options);
        }
    }

    private static OptionMap createConnectorOptions(final OperationContext context, final ModelNode model) throws OperationFailedException {
        Builder builder = OptionMap.builder();

        builder.set(RemotingOptions.SASL_PROTOCOL, HttpManagementResourceDefinition.SASL_PROTOCOL.resolveModelAttribute(context, model).asString());
        ModelNode serverName = HttpManagementResourceDefinition.SERVER_NAME.resolveModelAttribute(context, model);
        if (serverName.isDefined()) {
            builder.set(RemotingOptions.SERVER_NAME, serverName.asString());
        }

        return builder.getMap();
    }

}
