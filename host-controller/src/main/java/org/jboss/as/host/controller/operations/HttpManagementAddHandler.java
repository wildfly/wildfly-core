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

package org.jboss.as.host.controller.operations;

import static org.jboss.as.controller.management.BaseHttpInterfaceResourceDefinition.ALLOWED_ORIGINS;
import static org.jboss.as.controller.management.BaseHttpInterfaceResourceDefinition.HTTP_MANAGEMENT_CAPABILITY;
import static org.jboss.as.controller.management.BaseHttpInterfaceResourceDefinition.SECURITY_REALM;
import static org.jboss.as.host.controller.logging.HostControllerLogger.ROOT_LOGGER;
import static org.jboss.as.host.controller.resources.HttpManagementResourceDefinition.ATTRIBUTE_DEFINITIONS;
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
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.domain.http.server.ConsoleMode;
import org.jboss.as.domain.http.server.ManagementHttpRequestProcessor;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.host.controller.DomainModelControllerService;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.HostControllerService;
import org.jboss.as.host.controller.resources.HttpManagementResourceDefinition;
import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.remoting.HttpListenerRegistryService;
import org.jboss.as.remoting.RemotingHttpUpgradeService;
import org.jboss.as.remoting.management.ManagementChannelRegistryService;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.as.server.mgmt.HttpManagementRequestsService;
import org.jboss.as.server.mgmt.HttpShutdownService;
import org.jboss.as.server.mgmt.UndertowHttpManagementService;
import org.jboss.as.server.services.net.NetworkInterfaceService;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.remoting3.RemotingOptions;
import org.xnio.OptionMap;
import org.xnio.OptionMap.Builder;

/**
 * A handler that activates the HTTP management API.
 *
 * @author Jason T. Greene
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class HttpManagementAddHandler extends AbstractAddStepHandler {

    public static final String OPERATION_NAME = ModelDescriptionConstants.ADD;

    private final LocalHostControllerInfoImpl hostControllerInfo;
    private final HostControllerEnvironment environment;

    public HttpManagementAddHandler(final LocalHostControllerInfoImpl hostControllerInfo, final HostControllerEnvironment environment) {
        super(HTTP_MANAGEMENT_CAPABILITY, ATTRIBUTE_DEFINITIONS);
        this.hostControllerInfo = hostControllerInfo;
        this.environment = environment;
    }

    @Override
    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
        if (operation.hasDefined(ModelDescriptionConstants.HTTP_UPGRADE_ENABLED)) {
            boolean httpUpgradeEnabled = operation.remove(ModelDescriptionConstants.HTTP_UPGRADE_ENABLED).asBoolean();
            ModelNode httpUpgrade = operation.get(ModelDescriptionConstants.HTTP_UPGRADE);
            if (httpUpgrade.hasDefined(ModelDescriptionConstants.ENABLED)) {
                boolean httpUpgradeDotEnabled = httpUpgrade.require(ModelDescriptionConstants.ENABLED).asBoolean();
                if (httpUpgradeEnabled != httpUpgradeDotEnabled) {
                    throw ROOT_LOGGER.deprecatedAndCurrentParameterMismatch(ModelDescriptionConstants.HTTP_UPGRADE_ENABLED, ModelDescriptionConstants.ENABLED);
                }
            } else {
                httpUpgrade.set(ModelDescriptionConstants.ENABLED, httpUpgradeEnabled);
            }
        }

        super.populateModel(operation, model);
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return (context.getProcessType() != ProcessType.EMBEDDED_HOST_CONTROLLER);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        populateHostControllerInfo(hostControllerInfo, context, model);
        // DomainModelControllerService requires this service
        installHttpManagementServices(environment, hostControllerInfo, context, model);
    }

    @Override
    protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {

        HttpManagementRemoveHandler.clearHostControllerInfo(hostControllerInfo);
    }

    static void populateHostControllerInfo(final LocalHostControllerInfoImpl hostControllerInfo, final OperationContext context, final ModelNode model) throws OperationFailedException {
        hostControllerInfo.setHttpManagementInterface(HttpManagementResourceDefinition.INTERFACE.resolveModelAttribute(context, model).asString());
        final ModelNode portNode = HttpManagementResourceDefinition.HTTP_PORT.resolveModelAttribute(context, model);
        hostControllerInfo.setHttpManagementPort(portNode.isDefined() ? portNode.asInt() : -1);
        final ModelNode secureAddress = HttpManagementResourceDefinition.SECURE_INTERFACE.resolveModelAttribute(context, model);
        hostControllerInfo.setHttpManagementSecureInterface(secureAddress.isDefined() ? secureAddress.asString() : null);
        final ModelNode securePortNode = HttpManagementResourceDefinition.HTTPS_PORT.resolveModelAttribute(context, model);
        hostControllerInfo.setHttpManagementSecurePort(securePortNode.isDefined() ? securePortNode.asInt() : -1);
    }

    static void installHttpManagementServices(final HostControllerEnvironment environment, final LocalHostControllerInfo hostControllerInfo, final OperationContext context,
                                              final ModelNode model) throws OperationFailedException {

        boolean httpUpgrade = model.hasDefined(ModelDescriptionConstants.HTTP_UPGRADE)
                && HttpManagementResourceDefinition.ENABLED.resolveModelAttribute(context,
                        model.require(ModelDescriptionConstants.HTTP_UPGRADE)).asBoolean();
        boolean onDemand = context.isBooting();
        OptionMap options = createConnectorOptions(context, model);
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
        ServiceTarget serviceTarget = context.getServiceTarget();
        String interfaceName = hostControllerInfo.getHttpManagementInterface();
        int port = hostControllerInfo.getHttpManagementPort();
        String secureInterfaceName = hostControllerInfo.getHttpManagementSecureInterface();
        int securePort = hostControllerInfo.getHttpManagementSecurePort();

        ROOT_LOGGER.creatingHttpManagementService(interfaceName, port, securePort);

        final List<String> allowedOrigins = ALLOWED_ORIGINS.unwrap(context, model);

        final String securityRealm;
        final ModelNode realmNode = SECURITY_REALM.resolveModelAttribute(context, model);
        if (realmNode.isDefined()) {
            securityRealm = realmNode.asString();
        } else {
            securityRealm = null;
            ROOT_LOGGER.noSecurityRealmDefined();
        }

        boolean consoleEnabled = HttpManagementResourceDefinition.CONSOLE_ENABLED.resolveModelAttribute(context, model).asBoolean();
        ConsoleMode consoleMode;
        if (consoleEnabled){
            consoleMode = context.getRunningMode() == RunningMode.ADMIN_ONLY ? ConsoleMode.ADMIN_ONLY : ConsoleMode.CONSOLE;
        } else {
            consoleMode = ConsoleMode.NO_CONSOLE;
        }

        // Track active requests
        final ServiceName requestProcessorName = UndertowHttpManagementService.SERVICE_NAME.append("requests");
        HttpManagementRequestsService.installService(requestProcessorName, serviceTarget);

        final UndertowHttpManagementService service = new UndertowHttpManagementService(consoleMode, environment.getProductConfig().getConsoleSlot());
        ServiceBuilder<?> builder = serviceTarget.addService(UndertowHttpManagementService.SERVICE_NAME, service)
                .addDependency(
                        NetworkInterfaceService.JBOSS_NETWORK_INTERFACE.append(interfaceName),
                        NetworkInterfaceBinding.class, service.getInterfaceInjector())
                .addDependency(
                        NetworkInterfaceService.JBOSS_NETWORK_INTERFACE.append(secureInterfaceName),
                        NetworkInterfaceBinding.class, service.getSecureInterfaceInjector())
                .addDependency(DomainModelControllerService.SERVICE_NAME, ModelController.class, service.getModelControllerInjector())
                .addDependency(ControlledProcessStateService.SERVICE_NAME, ControlledProcessStateService.class, service.getControlledProcessStateServiceInjector())
                .addDependency(HttpListenerRegistryService.SERVICE_NAME, ListenerRegistry.class, service.getListenerRegistry())
                .addDependency(requestProcessorName, ManagementHttpRequestProcessor.class, service.getRequestProcessorValue())
                .addInjection(service.getPortInjector(), port)
                .addInjection(service.getSecurePortInjector(), securePort)
                .addInjection(service.getAllowedOriginsInjector(), allowedOrigins);

        if (securityRealm != null) {
            SecurityRealm.ServiceUtil.addDependency(builder, service.getSecurityRealmInjector(), securityRealm, false);
        }

        builder.setInitialMode(onDemand ? ServiceController.Mode.ON_DEMAND : ServiceController.Mode.ACTIVE)
                .install();

        // Add service preventing the server from shutting down
        final HttpShutdownService shutdownService = new HttpShutdownService();
        final ServiceName shutdownName = UndertowHttpManagementService.SERVICE_NAME.append("shutdown");
        final ServiceController<?> shutdown = serviceTarget.addService(shutdownName, shutdownService)
                .addDependency(requestProcessorName, ManagementHttpRequestProcessor.class, shutdownService.getProcessorValue())
                .addDependency(HostControllerService.HC_EXECUTOR_SERVICE_NAME, Executor.class, shutdownService.getExecutorValue())
                .addDependency(ManagementChannelRegistryService.SERVICE_NAME, ManagementChannelRegistryService.class, shutdownService.getMgmtChannelRegistry())
                .addDependency(UndertowHttpManagementService.SERVICE_NAME)
                .setInitialMode(ServiceController.Mode.ACTIVE)
                .install();

        if (httpUpgrade) {
            ServiceName serverCallbackService = ServiceName.JBOSS.append("host", "controller", "server-inventory", "callback");
            ServiceName tmpDirPath = ServiceName.JBOSS.append("server", "path", "jboss.domain.temp.dir");
            ManagementRemotingServices.installSecurityServices(serviceTarget, ManagementRemotingServices.HTTP_CONNECTOR, securityRealm, serverCallbackService, tmpDirPath);

            NativeManagementServices.installRemotingServicesIfNotInstalled(serviceTarget, hostControllerInfo.getLocalHostName(), serviceRegistry,onDemand);
            final String httpConnectorName;
            if (port > -1 || securePort < 0) {
                httpConnectorName = ManagementRemotingServices.HTTP_CONNECTOR;
            } else {
                httpConnectorName = ManagementRemotingServices.HTTPS_CONNECTOR;
            }

            RemotingHttpUpgradeService.installServices(serviceTarget, ManagementRemotingServices.HTTP_CONNECTOR, httpConnectorName, ManagementRemotingServices.MANAGEMENT_ENDPOINT, options);
        }
    }

    static OptionMap createConnectorOptions(final OperationContext context, final ModelNode model) throws OperationFailedException {
        Builder builder = OptionMap.builder();

        builder.set(RemotingOptions.SASL_PROTOCOL, HttpManagementResourceDefinition.SASL_PROTOCOL.resolveModelAttribute(context, model).asString());
        ModelNode serverName = HttpManagementResourceDefinition.SERVER_NAME.resolveModelAttribute(context, model);
        if (serverName.isDefined()) {
            builder.set(RemotingOptions.SERVER_NAME, serverName.asString());
        }

        return builder.getMap();
    }

}
