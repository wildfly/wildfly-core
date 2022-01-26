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

package org.jboss.as.host.controller;

import static java.security.AccessController.doPrivileged;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIBE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLE_AUTO_START;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_CONNECTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_LAUNCH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.domain.controller.HostConnectionInfo.Events.create;
import static org.jboss.as.domain.http.server.ConsoleAvailability.CONSOLE_AVAILABILITY_CAPABILITY;
import static org.jboss.as.host.controller.HostControllerService.HC_EXECUTOR_SERVICE_NAME;
import static org.jboss.as.host.controller.HostControllerService.HC_SCHEDULED_EXECUTOR_SERVICE_NAME;
import static org.jboss.as.host.controller.logging.HostControllerLogger.DOMAIN_LOGGER;
import static org.jboss.as.host.controller.logging.HostControllerLogger.ROOT_LOGGER;
import static org.jboss.as.remoting.Protocol.REMOTE;
import static org.jboss.as.remoting.Protocol.REMOTE_HTTP;
import static org.jboss.as.remoting.Protocol.REMOTE_HTTPS;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.BootContext;
import org.jboss.as.controller.Cancellable;
import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ProcessStateNotifier;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.ModelControllerServiceInitialization;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ProxyOperationAddressTranslator;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.TransformingProxyController;
import org.jboss.as.controller.ModelController.OperationTransactionControl;
import org.jboss.as.controller.access.InVmAccess;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.audit.ManagedAuditLoggerImpl;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.ImmutableCapabilityRegistry;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.MutableRootResourceRegistrationProvider;
import org.jboss.as.controller.extension.RuntimeHostControllerInfoAccessor;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.persistence.ExtensibleConfigurationPersister;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.ResourceProvider;
import org.jboss.as.controller.remote.AbstractModelControllerOperationHandlerFactoryService;
import org.jboss.as.controller.remote.ModelControllerOperationHandlerFactory;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.domain.controller.HostConnectionInfo;
import org.jboss.as.domain.controller.HostConnectionInfo.Event;
import org.jboss.as.domain.controller.HostRegistrations;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.domain.controller.SlaveRegistrationException;
import org.jboss.as.domain.controller.operations.ApplyExtensionsHandler;
import org.jboss.as.domain.controller.operations.DomainModelIncludesValidator;
import org.jboss.as.domain.controller.operations.coordination.PrepareStepHandler;
import org.jboss.as.domain.controller.resources.DomainRootDefinition;
import org.jboss.as.domain.http.server.ConsoleAvailability;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management.security.DomainManagedServerCallbackHandler;
import org.jboss.as.host.controller.RemoteDomainConnectionService.RemoteFileRepository;
import org.jboss.as.host.controller.discovery.DiscoveryOption;
import org.jboss.as.host.controller.discovery.DomainControllerManagementInterface;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.host.controller.mgmt.DomainHostExcludeRegistry;
import org.jboss.as.host.controller.mgmt.HostControllerRegistrationHandler;
import org.jboss.as.host.controller.mgmt.MasterDomainControllerOperationHandlerService;
import org.jboss.as.host.controller.mgmt.ServerToHostOperationHandlerFactoryService;
import org.jboss.as.host.controller.mgmt.ServerToHostProtocolHandler;
import org.jboss.as.host.controller.mgmt.SlaveHostPinger;
import org.jboss.as.host.controller.model.host.AdminOnlyDomainConfigPolicy;
import org.jboss.as.host.controller.operations.LocalHostControllerInfoImpl;
import org.jboss.as.host.controller.operations.StartServersHandler;
import org.jboss.as.host.controller.resources.ServerConfigResourceDefinition;
import org.jboss.as.process.CommandLineConstants;
import org.jboss.as.process.ExitCodes;
import org.jboss.as.process.ProcessControllerClient;
import org.jboss.as.process.ProcessInfo;
import org.jboss.as.process.ProcessMessageHandler;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.as.repository.LocalFileRepository;
import org.jboss.as.server.BootstrapListener;
import org.jboss.as.server.ExternalManagementRequestExecutor;
import org.jboss.as.server.RuntimeExpressionResolver;
import org.jboss.as.server.controller.resources.VersionModelInitializer;
import org.jboss.as.server.deployment.ContentCleanerService;
import org.jboss.as.server.mgmt.UndertowHttpManagementService;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.threads.AsyncFutureTask;
import org.jboss.threads.JBossThreadFactory;
import org.wildfly.common.Assert;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Creates the service that acts as the {@link org.jboss.as.controller.ModelController} for a Host Controller process.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class DomainModelControllerService extends AbstractControllerService implements DomainController, HostModelUtil.HostModelRegistrar, HostRegistrations {

    public static final ServiceName SERVICE_NAME = HostControllerService.HC_SERVICE_NAME.append("model", "controller");
    public static final ServiceName CLIENT_FACTORY_SERVICE_NAME = CLIENT_FACTORY_CAPABILITY.getCapabilityServiceName();

    private static final int PINGER_POOL_SIZE;
    static {
        int poolSize = -1;
        try {
            poolSize = Integer.parseInt(WildFlySecurityManager.getPropertyPrivileged("jboss.as.domain.ping.pool.size", "5"));
        } catch (Exception e) {
            // TODO log
        } finally {
            PINGER_POOL_SIZE = Math.max(1, poolSize);
        }
    }

    private volatile HostControllerConfigurationPersister hostControllerConfigurationPersister;
    private final HostControllerEnvironment environment;
    private final HostRunningModeControl runningModeControl;
    private final LocalHostControllerInfoImpl hostControllerInfo;
    private final HostFileRepository localFileRepository;
    private final RemoteFileRepository remoteFileRepository;
    private final InjectedValue<ProcessControllerConnectionService> injectedProcessControllerConnection = new InjectedValue<ProcessControllerConnectionService>();
    private final ConcurrentMap<String, ProxyController> hostProxies;
    private final DomainSlaveHostRegistrations slaveHostRegistrations = new DomainSlaveHostRegistrations();
    private final Map<String, ProxyController> serverProxies;
    private final PrepareStepHandler prepareStepHandler;
    private final BootstrapListener bootstrapListener;
    private ManagementResourceRegistration modelNodeRegistration;
    private final ContentRepository contentRepository;
    private final ExtensionRegistry hostExtensionRegistry;
    private final ExtensionRegistry extensionRegistry;
    private final ControlledProcessState processState;
    private final IgnoredDomainResourceRegistry ignoredRegistry;
    private final PathManagerService pathManager;
    private final ExpressionResolver expressionResolver;
    private final DomainDelegatingResourceDefinition rootResourceDefinition;
    private final CapabilityRegistry capabilityRegistry;
    private final DomainHostExcludeRegistry domainHostExcludeRegistry;
    private final AtomicBoolean domainConfigAvailable = new AtomicBoolean(false);
    private final PartialModelIndicator partialModelIndicator = new PartialModelIndicator() {
        @Override
        public boolean isModelPartial() {
            return !domainConfigAvailable.get();
        }
    };

    private final AtomicBoolean serverInventoryLock = new AtomicBoolean();
    // @GuardedBy(serverInventoryLock), after the HC started reads just use the volatile value
    private volatile ServerInventory serverInventory;

    private volatile ScheduledExecutorService pingScheduler;
    private volatile ManagementResourceRegistration hostModelRegistration;
    private volatile MasterDomainControllerClient masterDomainControllerClient;
    private volatile Supplier<ConsoleAvailability> consoleAvailabilitySupplier;

    static void addService(final ServiceTarget serviceTarget,
                                                            final HostControllerEnvironment environment,
                                                            final HostRunningModeControl runningModeControl,
                                                            final ControlledProcessState processState,
                                                            final BootstrapListener bootstrapListener,
                                                            final PathManagerService pathManager,
                                                            final CapabilityRegistry capabilityRegistry,
                                                            final ThreadGroup threadGroup) {

        final ConcurrentMap<String, ProxyController> hostProxies = new ConcurrentHashMap<String, ProxyController>();
        final Map<String, ProxyController> serverProxies = new ConcurrentHashMap<String, ProxyController>();
        final LocalHostControllerInfoImpl hostControllerInfo = new LocalHostControllerInfoImpl(processState, environment);
        final ContentRepository contentRepository = ContentRepository.Factory.create(environment.getDomainContentDir(), environment.getDomainTempDir());
        ContentRepository.Factory.addService(serviceTarget, contentRepository);
        final IgnoredDomainResourceRegistry ignoredRegistry = new IgnoredDomainResourceRegistry(hostControllerInfo);
        final ManagedAuditLogger auditLogger = createAuditLogger(environment);
        final DelegatingConfigurableAuthorizer authorizer = new DelegatingConfigurableAuthorizer();
        final ManagementSecurityIdentitySupplier securityIdentitySupplier = new ManagementSecurityIdentitySupplier();
        final RuntimeHostControllerInfoAccessor hostControllerInfoAccessor = new DomainHostControllerInfoAccessor(hostControllerInfo);
        final ProcessType processType = environment.getProcessType();
        final ExtensionRegistry hostExtensionRegistry = new ExtensionRegistry(processType, runningModeControl, auditLogger, authorizer, securityIdentitySupplier, hostControllerInfoAccessor);
        final ExtensionRegistry extensionRegistry = new ExtensionRegistry(processType, runningModeControl, auditLogger, authorizer, securityIdentitySupplier, hostControllerInfoAccessor);
        final PrepareStepHandler prepareStepHandler = new PrepareStepHandler(hostControllerInfo,
                hostProxies, serverProxies, ignoredRegistry, extensionRegistry);
        final RuntimeExpressionResolver expressionResolver = new RuntimeExpressionResolver();
        hostExtensionRegistry.setResolverExtensionRegistry(expressionResolver);
        final DomainHostExcludeRegistry domainHostExcludeRegistry = new DomainHostExcludeRegistry();
        HostControllerEnvironmentService.addService(environment, serviceTarget);

        final ServiceBuilder<?> sb = serviceTarget.addService(SERVICE_NAME);
        final Supplier<ExecutorService> esSupplier = sb.requires(HC_EXECUTOR_SERVICE_NAME);
        final DomainModelControllerService service = new DomainModelControllerService(esSupplier, environment, runningModeControl, processState,
                hostControllerInfo, contentRepository, hostProxies, serverProxies, prepareStepHandler,
                ignoredRegistry, bootstrapListener, pathManager, expressionResolver, new DomainDelegatingResourceDefinition(),
                hostExtensionRegistry, extensionRegistry, auditLogger, authorizer, securityIdentitySupplier, capabilityRegistry, domainHostExcludeRegistry);
        sb.setInstance(service);
        sb.addDependency(ProcessControllerConnectionService.SERVICE_NAME, ProcessControllerConnectionService.class, service.injectedProcessControllerConnection);
        sb.requires(PATH_MANAGER_CAPABILITY.getCapabilityServiceName()); // ensure this is up
        service.consoleAvailabilitySupplier = sb.requires(CONSOLE_AVAILABILITY_CAPABILITY.getCapabilityServiceName());
        sb.install();

        ExternalManagementRequestExecutor.install(serviceTarget, threadGroup,
                EXECUTOR_CAPABILITY.getCapabilityServiceName(), service.getStabilityMonitor());
    }

    private DomainModelControllerService(final Supplier<ExecutorService> executorService,
                                         final HostControllerEnvironment environment,
                                         final HostRunningModeControl runningModeControl,
                                         final ControlledProcessState processState,
                                         final LocalHostControllerInfoImpl hostControllerInfo,
                                         final ContentRepository contentRepository,
                                         final ConcurrentMap<String, ProxyController> hostProxies,
                                         final Map<String, ProxyController> serverProxies,
                                         final PrepareStepHandler prepareStepHandler,
                                         final IgnoredDomainResourceRegistry ignoredRegistry,
                                         final BootstrapListener bootstrapListener,
                                         final PathManagerService pathManager,
                                         final ExpressionResolver expressionResolver,
                                         final DomainDelegatingResourceDefinition rootResourceDefinition,
                                         final ExtensionRegistry hostExtensionRegistry,
                                         final ExtensionRegistry extensionRegistry,
                                         final ManagedAuditLogger auditLogger,
                                         final DelegatingConfigurableAuthorizer authorizer,
                                         final ManagementSecurityIdentitySupplier securityIdentitySupplier,
                                         final CapabilityRegistry capabilityRegistry,
                                         final DomainHostExcludeRegistry domainHostExcludeRegistry) {
        super(executorService, null, environment.getProcessType(), runningModeControl, null, processState,
                rootResourceDefinition, prepareStepHandler, expressionResolver, auditLogger, authorizer, securityIdentitySupplier, capabilityRegistry, null);
        this.environment = environment;
        this.runningModeControl = runningModeControl;
        this.processState = processState;
        this.hostControllerInfo = hostControllerInfo;
        this.localFileRepository = new LocalFileRepository(environment.getDomainBaseDir(), environment.getDomainContentDir(), environment.getDomainConfigurationDir());

        this.remoteFileRepository = new RemoteFileRepository(localFileRepository);
        this.contentRepository = contentRepository;
        this.hostProxies = hostProxies;
        this.serverProxies = serverProxies;
        this.prepareStepHandler = prepareStepHandler;
        this.prepareStepHandler.setServerInventory(new DelegatingServerInventory());
        this.ignoredRegistry = ignoredRegistry;
        this.bootstrapListener = bootstrapListener;
        this.hostExtensionRegistry = hostExtensionRegistry;
        this.extensionRegistry = extensionRegistry;
        this.pathManager = pathManager;
        this.expressionResolver = expressionResolver;
        this.rootResourceDefinition = rootResourceDefinition;
        this.capabilityRegistry = capabilityRegistry;
        this.domainHostExcludeRegistry = domainHostExcludeRegistry;
    }

    private static ManagedAuditLogger createAuditLogger(HostControllerEnvironment environment) {
        return new ManagedAuditLoggerImpl(environment.getProductConfig().resolveVersion(), false);
    }

    @Override
    public RunningMode getCurrentRunningMode() {
        return runningModeControl.getRunningMode();
    }

    @Override
    public LocalHostControllerInfo getLocalHostInfo() {
        return hostControllerInfo;
    }

    @Override
    public void registerRemoteHost(final String hostName, final ManagementChannelHandler handler, final Transformers transformers,
                                   final Long remoteConnectionId, final boolean registerProxyController) throws SlaveRegistrationException {
        if (!hostControllerInfo.isMasterDomainController()) {
            throw SlaveRegistrationException.forHostIsNotMaster();
        }

        if (runningModeControl.getRunningMode() == RunningMode.ADMIN_ONLY) {
            throw SlaveRegistrationException.forMasterInAdminOnlyMode(runningModeControl.getRunningMode());
        }

        final PathElement pe = PathElement.pathElement(ModelDescriptionConstants.HOST, hostName);
        final PathAddress addr = PathAddress.pathAddress(pe);
        ProxyController existingController = modelNodeRegistration.getProxyController(addr);

        if (existingController != null || hostControllerInfo.getLocalHostName().equals(pe.getValue())){
            throw SlaveRegistrationException.forHostAlreadyExists(pe.getValue());
        }

        final SlaveHostPinger pinger = remoteConnectionId == null ? null : new SlaveHostPinger(hostName, handler, pingScheduler, remoteConnectionId);
        final String address = handler.getRemoteAddress().getHostAddress();
        slaveHostRegistrations.registerHost(hostName, pinger, address);

        if (registerProxyController) {
            // Create the proxy controller
            final TransformingProxyController hostControllerClient = TransformingProxyController.Factory.create(handler, transformers, addr, ProxyOperationAddressTranslator.HOST);

            modelNodeRegistration.registerProxyController(pe, hostControllerClient);
            hostProxies.put(hostName, hostControllerClient);
        }
    }

    @Override
    public boolean isHostRegistered(String id) {
        final DomainSlaveHostRegistrations.DomainHostConnection registration = slaveHostRegistrations.getRegistration(id);
        return registration != null && registration.isConnected();
    }

    @Override
    public void unregisterRemoteHost(String id, Long remoteConnectionId, boolean cleanShutdown) {
        DomainSlaveHostRegistrations.DomainHostConnection hostRegistration = slaveHostRegistrations.getRegistration(id);
        if (hostRegistration != null) {
            if ((remoteConnectionId == null || remoteConnectionId.equals(hostRegistration.getRemoteConnectionId()))) {
                final SlaveHostPinger pinger = hostRegistration.getPinger();
                if (pinger != null) {
                    pinger.cancel();
                }
                boolean registered = hostProxies.remove(id) != null;
                modelNodeRegistration.unregisterProxyController(PathElement.pathElement(HOST, id));

                if (registered) {
                    final String address = hostRegistration.getAddress();
                    final Event event = cleanShutdown ? create(HostConnectionInfo.EventType.UNREGISTERED, address) : create(HostConnectionInfo.EventType.UNCLEAN_UNREGISTRATION, address);
                    slaveHostRegistrations.unregisterHost(id, event);
                    if (!cleanShutdown) {
                        DOMAIN_LOGGER.lostConnectionToRemoteHost(id);
                    } else {
                        DOMAIN_LOGGER.unregisteredRemoteSlaveHost(id);
                    }
                }
            }
        }

    }

    @Override
    public void addHostEvent(String hostName, Event event) {
        slaveHostRegistrations.addEvent(hostName, event);
    }

    @Override
    public void pruneExpired() {
        slaveHostRegistrations.pruneExpired();
    }

    @Override
    public void pruneDisconnected() {
        slaveHostRegistrations.pruneDisconnected();
    }

    @Override
    public HostConnectionInfo getHostInfo(String hostName) {
        return slaveHostRegistrations.getRegistration(hostName);
    }

    @Override
    public void pingRemoteHost(String id) {
        DomainSlaveHostRegistrations.DomainHostConnection reg = slaveHostRegistrations.getRegistration(id);
        if (reg != null && reg.getPinger() != null && !reg.getPinger().isCancelled()) {
            reg.getPinger().schedulePing(SlaveHostPinger.SHORT_TIMEOUT, 0);
        }
    }

    @Override
    public void registerRunningServer(final ProxyController serverControllerClient) {
        PathAddress pa = serverControllerClient.getProxyNodeAddress();
        PathElement pe = pa.getElement(1);
        if (modelNodeRegistration.getProxyController(pa) != null) {
            throw HostControllerLogger.ROOT_LOGGER.serverNameAlreadyRegistered(pe.getValue());
        }
        ROOT_LOGGER.registeringServer(pe.getValue());
        // Register the proxy
        final ManagementResourceRegistration hostRegistration = modelNodeRegistration.getSubModel(PathAddress.pathAddress(PathElement.pathElement(HOST, hostControllerInfo.getLocalHostName())));
        hostRegistration.registerProxyController(pe, serverControllerClient);
        // Register local operation overrides
        final ManagementResourceRegistration serverRegistration = hostRegistration.getSubModel(PathAddress.EMPTY_ADDRESS.append(pe));
        ServerConfigResourceDefinition.registerServerLifecycleOperations(serverRegistration, serverInventory);
        serverProxies.put(pe.getValue(), serverControllerClient);
    }

    @Override
    public void unregisterRunningServer(String serverName) {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement(HOST, hostControllerInfo.getLocalHostName()));
        PathElement pe = PathElement.pathElement(RUNNING_SERVER, serverName);
        ROOT_LOGGER.unregisteringServer(serverName);
        ManagementResourceRegistration hostRegistration = modelNodeRegistration.getSubModel(pa);
        hostRegistration.unregisterProxyController(pe);
        serverProxies.remove(serverName);
    }

    @Override
    public void reportServerInstability(String serverName) {
        MasterDomainControllerClient mdc = masterDomainControllerClient;
        if (mdc != null) {
            mdc.reportServerInstability(serverName);
        }
    }

    @Override
    public ModelNode getProfileOperations(String profileName) {
        ModelNode operation = new ModelNode();

        operation.get(OP).set(DESCRIBE);
        operation.get(OP_ADDR).set(PathAddress.pathAddress(PathElement.pathElement(PROFILE, profileName)).toModelNode());
        operation.get(SERVER_LAUNCH).set(true);

        ModelNode rsp = getValue().execute(operation, null, null, null);
        if (!rsp.hasDefined(OUTCOME) || !SUCCESS.equals(rsp.get(OUTCOME).asString())) {
            ModelNode msgNode = rsp.get(FAILURE_DESCRIPTION);
            String msg = msgNode.isDefined() ? msgNode.toString() : HostControllerLogger.ROOT_LOGGER.failedProfileOperationsRetrieval();
            throw new RuntimeException(msg);
        }
        return rsp.require(RESULT);
    }

    @Override
    public HostFileRepository getLocalFileRepository() {
        return localFileRepository;
    }

    @Override
    public HostFileRepository getRemoteFileRepository() {
        if (hostControllerInfo.isMasterDomainController()) {
            throw HostControllerLogger.ROOT_LOGGER.cannotAccessRemoteFileRepository();
        }
        return remoteFileRepository;
    }

    @Override
    public void start(StartContext context) throws StartException {
        final ExecutorService executorService = getExecutorService();
        this.hostControllerConfigurationPersister = new HostControllerConfigurationPersister(environment, hostControllerInfo, executorService, hostExtensionRegistry, extensionRegistry);
        setConfigurationPersister(hostControllerConfigurationPersister);
        prepareStepHandler.setExecutorService(executorService);
        ThreadFactory pingerThreadFactory = doPrivileged(new PrivilegedAction<JBossThreadFactory>() {
            public JBossThreadFactory run() {
                return new JBossThreadFactory(new ThreadGroup("proxy-pinger-threads"), Boolean.TRUE, null, "%G - %t", null, null);
            }
        });
        pingScheduler = Executors.newScheduledThreadPool(PINGER_POOL_SIZE, pingerThreadFactory);

        ContentCleanerService.addServiceOnHostController(context.getChildTarget(), DomainModelControllerService.SERVICE_NAME,
                CLIENT_FACTORY_SERVICE_NAME, HC_EXECUTOR_SERVICE_NAME, HC_SCHEDULED_EXECUTOR_SERVICE_NAME);

        super.start(context);

        pingScheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    slaveHostRegistrations.pruneExpired();
                } catch (Exception e) {
                    HostControllerLogger.DOMAIN_LOGGER.debugf(e, "failed to execute eviction task");
                }
            }
        }, 1, 1, TimeUnit.MINUTES);

    }

    @Override
    protected void initModel(ManagementModel managementModel, Resource modelControllerResource) {
        ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
        HostModelUtil.createRootRegistry(rootRegistration, environment,
                ignoredRegistry, this, processType, authorizer, modelControllerResource,
                hostControllerInfo, managementModel.getCapabilityRegistry());
        VersionModelInitializer.registerRootResource(managementModel.getRootResource(), environment != null ? environment.getProductConfig() : null);
        CoreManagementResourceDefinition.registerDomainResource(managementModel.getRootResource(), authorizer.getWritableAuthorizerConfiguration());
        this.modelNodeRegistration = managementModel.getRootResourceRegistration();

        final RuntimeCapabilityRegistry capabilityReg = managementModel.getCapabilityRegistry(); // use the one from the model as it is what is being published
        capabilityReg.registerCapability(
                new RuntimeCapabilityRegistration(PATH_MANAGER_CAPABILITY, CapabilityScope.GLOBAL, new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));
        capabilityReg.registerCapability(
                        new RuntimeCapabilityRegistration(EXECUTOR_CAPABILITY, CapabilityScope.GLOBAL, new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));
        capabilityReg.registerCapability(
                new RuntimeCapabilityRegistration(PROCESS_STATE_NOTIFIER_CAPABILITY, CapabilityScope.GLOBAL, new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));
        capabilityReg.registerCapability(
                new RuntimeCapabilityRegistration(CONSOLE_AVAILABILITY_CAPABILITY, CapabilityScope.GLOBAL, new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));

        // Record the core capabilities with the root MRR so reads of it will show it as their provider
        // This also gets them recorded as 'possible capabilities' in the capability registry
        rootRegistration.registerCapability(PATH_MANAGER_CAPABILITY);
        rootRegistration.registerCapability(EXECUTOR_CAPABILITY);
        rootRegistration.registerCapability(PROCESS_STATE_NOTIFIER_CAPABILITY);
        rootRegistration.registerCapability(CONSOLE_AVAILABILITY_CAPABILITY);

        // Register the slave host info
        ResourceProvider.Tool.addResourceProvider(HOST_CONNECTION, new ResourceProvider() {
            @Override
            public boolean has(String name) {
                return slaveHostRegistrations.contains(name);
            }

            @Override
            public Resource get(String name) {
                return PlaceholderResource.INSTANCE;
            }

            @Override
            public boolean hasChildren() {
                return true;
            }

            @Override
            public Set<String> children() {
                return slaveHostRegistrations.getHosts();
            }

            @Override
            public void register(String name, Resource resource) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void register(String value, int index, Resource resource) {
            }

            @Override
            public Resource remove(String name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ResourceProvider clone() {
                return this;
            }
        }, managementModel.getRootResource().getChild(CoreManagementResourceDefinition.PATH_ELEMENT));

    }

    @Override
    protected OperationStepHandler createExtraValidationStepHandler() {
        return new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                if (!context.isBooting()) {
                    PathAddress addr = context.getCurrentAddress();
                    if (addr.size() > 0 && addr.getLastElement().getKey().equals(SUBSYSTEM)) {
                        //For subsystem adds in domain mode we need to check that the new subsystem does not break the rule
                        //that when profile includes are used, we don't allow overriding subsystems.
                        DomainModelIncludesValidator.addValidationStep(context, operation);
                    }
                }
            }
        };
    }

    // See superclass start. This method is invoked from a separate non-MSC thread after start. So we can do a fair
    // bit of stuff
    @Override
    protected void boot(final BootContext context) throws ConfigurationPersistenceException {

        final ServiceTarget serviceTarget = context.getServiceTarget();
        boolean ok = false;
        boolean reachedServers = false;

        try {
            // Install server inventory callback
            ServerInventoryCallbackService.install(serviceTarget);

            // handler for domain server auth.
            DomainManagedServerCallbackHandler.install(serviceTarget);
            // Parse the host.xml and invoke all the ops. The ops should rollback on any Stage.RUNTIME failure
            List<ModelNode> hostBootOps = hostControllerConfigurationPersister.load();
            if (hostBootOps.isEmpty()) { // booting with empty config
                ok = bootEmptyConfig(context);
                return;
            }

            // We run the first op ("/host=foo:add()") separately to let it set up the host ManagementResourceRegistration
            ModelNode addHostOp = hostBootOps.remove(0);
            HostControllerLogger.ROOT_LOGGER.debug("Invoking the initial host=foo:add() op");
            //Disable model validation here since it will will fail
            ok = boot(Collections.singletonList(addHostOp), true, true);

            // Add the controller initialization operation
            hostBootOps.add(registerModelControllerServiceInitializationBootStep(context));

            //Pass in a custom mutable root resource registration provider for the remaining host model ops boot
            //This will be used to make sure that any extensions added in parallel get registered in the host model
            if (ok) {
                HostControllerLogger.ROOT_LOGGER.debug("Invoking remaining host.xml ops");
                ok = boot(hostBootOps, true, true, new MutableRootResourceRegistrationProvider() {
                    public ManagementResourceRegistration getRootResourceRegistrationForUpdate(OperationContext context) {
                        return hostModelRegistration;
                    }
                });
            }

            final RunningMode currentRunningMode = runningModeControl.getRunningMode();

            if (ok) {
                // Now we know our management interface configuration. Install the server inventory
                Future<ServerInventory> inventoryFuture = installServerInventory(serviceTarget);

                // Now we know our discovery configuration.
                List<DiscoveryOption> discoveryOptions = hostControllerInfo.getRemoteDomainControllerDiscoveryOptions();
                if (hostControllerInfo.isMasterDomainController() && (discoveryOptions != null)) {
                    // Install the discovery service
                    installDiscoveryService(serviceTarget, discoveryOptions);
                }

                boolean useLocalDomainXml = hostControllerInfo.isMasterDomainController();
                boolean isCachedDc = environment.isUseCachedDc();

                if (!useLocalDomainXml) {
                    // Block for the ServerInventory
                    establishServerInventory(inventoryFuture);

                    boolean discoveryConfigured = (discoveryOptions != null) && !discoveryOptions.isEmpty();
                    if (currentRunningMode != RunningMode.ADMIN_ONLY) {
                        if (discoveryConfigured) {
                            // Try and connect.
                            // If can't connect && !environment.isUseCachedDc(), abort
                            // Otherwise if can't connect, use local domain.xml and start trying to reconnect later
                            DomainConnectResult connectResult = connectToDomainMaster(serviceTarget, currentRunningMode, isCachedDc, false);
                            if (connectResult == DomainConnectResult.ABORT) {
                                ok = false;
                            } else if (connectResult == DomainConnectResult.FAILED) {
                                useLocalDomainXml = true;
                            }
                        } else {
                            // Invalid configuration; no way to get the domain config
                            ROOT_LOGGER.noDomainControllerConfigurationProvided(currentRunningMode,
                                    CommandLineConstants.ADMIN_ONLY, RunningMode.ADMIN_ONLY);
                            SystemExiter.abort(ExitCodes.HOST_CONTROLLER_ABORT_EXIT_CODE);
                        }
                    } else {
                        // We're in admin-only mode. See how we handle access control config
                        // if cached-dc is specified, we try and use the last configuration we have before failing.
                        if (isCachedDc) {
                            useLocalDomainXml = true;
                        }
                        switch (hostControllerInfo.getAdminOnlyDomainConfigPolicy()) {
                            case ALLOW_NO_CONFIG:
                                // our current setup is good, if we're using --cached-dc, we'll try and load the config below
                                // if not, we'll start empty.
                                break;
                            case FETCH_FROM_MASTER:
                                if (discoveryConfigured) {
                                    // Try and connect.
                                    // If can't connect && !environment.isUseCachedDc(), abort
                                    // Otherwise if can't connect, use local domain.xml but DON'T start trying to reconnect later
                                    DomainConnectResult connectResult = connectToDomainMaster(serviceTarget, currentRunningMode, isCachedDc, true);
                                    ok = connectResult != DomainConnectResult.ABORT;
                                } else {
                                    // try and use a local cached version below before failing
                                    if (isCachedDc) {
                                        break;
                                    }
                                    // otherwise, this is an invalid configuration; no way to get the domain config
                                    ROOT_LOGGER.noDomainControllerConfigurationProvidedForAdminOnly(
                                            ModelDescriptionConstants.ADMIN_ONLY_POLICY,
                                            AdminOnlyDomainConfigPolicy.REQUIRE_LOCAL_CONFIG,
                                            CommandLineConstants.CACHED_DC, RunningMode.ADMIN_ONLY);
                                    SystemExiter.abort(ExitCodes.HOST_CONTROLLER_ABORT_EXIT_CODE);
                                    break;
                                }
                                break;
                            case REQUIRE_LOCAL_CONFIG:
                                // if we have a cached copy, and --cached-dc we can try to use that below
                                if (isCachedDc) {
                                    break;
                                }

                                // otherwise, this is an invalid configuration; no way to get the domain config
                                ROOT_LOGGER.noAccessControlConfigurationAvailable(currentRunningMode,
                                        ModelDescriptionConstants.ADMIN_ONLY_POLICY,
                                        AdminOnlyDomainConfigPolicy.REQUIRE_LOCAL_CONFIG,
                                        CommandLineConstants.CACHED_DC, currentRunningMode);
                                SystemExiter.abort(ExitCodes.HOST_CONTROLLER_ABORT_EXIT_CODE);
                                break;
                            default:
                                throw new IllegalStateException(hostControllerInfo.getAdminOnlyDomainConfigPolicy().toString());
                        }
                    }

                }

                if (useLocalDomainXml) {
                    if (!hostControllerInfo.isMasterDomainController() && isCachedDc) {
                        ROOT_LOGGER.usingCachedDC(CommandLineConstants.CACHED_DC, ConfigurationPersisterFactory.CACHED_DOMAIN_XML);
                    }

                    // parse the domain.xml and load the steps
                    // TODO look at having LocalDomainControllerAdd do this, using Stage.IMMEDIATE for the steps
                    ConfigurationPersister domainPersister = hostControllerConfigurationPersister.getDomainPersister();

                    // if we're using --cached-dc, we have to have had a persisted copy of the domain config for this to work
                    // otherwise we fail and can't continue.
                    List<ModelNode> domainBootOps = domainPersister.load();

                    HostControllerLogger.ROOT_LOGGER.debug("Invoking domain.xml ops");
                    // https://issues.jboss.org/browse/WFCORE-3897
                    domainConfigAvailable.set(true);
                    ok = boot(domainBootOps, false);
                    domainConfigAvailable.set(ok);

                    if (!ok && runningModeControl.getRunningMode().equals(RunningMode.ADMIN_ONLY)) {
                        ROOT_LOGGER.reportAdminOnlyDomainXmlFailure();
                        ok = true;
                    }

                    if (ok && processType != ProcessType.EMBEDDED_HOST_CONTROLLER) {
                        InternalExecutor executor = new InternalExecutor();
                        ManagementRemotingServices.installManagementChannelServices(serviceTarget, ManagementRemotingServices.MANAGEMENT_ENDPOINT,
                                new ModelControllerOperationHandlerFactory() {
                                    @Override
                                    public AbstractModelControllerOperationHandlerFactoryService newInstance(
                                            final Consumer<AbstractModelControllerOperationHandlerFactoryService> serviceConsumer,
                                            final Supplier<ModelController> modelControllerSupplier,
                                            final Supplier<ExecutorService> executorSupplier,
                                            final Supplier<ScheduledExecutorService> scheduledExecutorSupplier) {
                                        return new MasterDomainControllerOperationHandlerService(
                                                serviceConsumer,
                                                modelControllerSupplier,
                                                executorSupplier,
                                                scheduledExecutorSupplier,
                                                DomainModelControllerService.this,
                                                executor,
                                                executor,
                                                environment.getDomainTempDir(),
                                                DomainModelControllerService.this,
                                                domainHostExcludeRegistry);
                                    }
                                },
                                DomainModelControllerService.SERVICE_NAME, ManagementRemotingServices.DOMAIN_CHANNEL,
                                HC_EXECUTOR_SERVICE_NAME, HC_SCHEDULED_EXECUTOR_SERVICE_NAME);

                        // Block for the ServerInventory
                        establishServerInventory(inventoryFuture);
                    }

                    // register local host controller
                    final String hostName = hostControllerInfo.getLocalHostName();
                    slaveHostRegistrations.registerHost(hostName, null, "local");
                }
            }

            if (ok && hostControllerInfo.getAdminOnlyDomainConfigPolicy() != AdminOnlyDomainConfigPolicy.ALLOW_NO_CONFIG) {
                final ModelNode validate = new ModelNode();
                validate.get(OP).set("validate");
                validate.get(OP_ADDR).setEmptyList();
                final ModelNode result = internalExecute(OperationBuilder.create(validate).build(), OperationMessageHandler.DISCARD, OperationTransactionControl.COMMIT, new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        DomainModelIncludesValidator.validateAtBoot(context, operation);
                    }
                }).getResponseNode();

                if (!SUCCESS.equals(result.get(OUTCOME).asString())) {
                    throw HostControllerLogger.ROOT_LOGGER.bootConfigValidationFailed(result.get(FAILURE_DESCRIPTION));
                }

            }

            if (ok && processType != ProcessType.EMBEDDED_HOST_CONTROLLER) {
                // Install the server > host operation handler
                ServerToHostOperationHandlerFactoryService.install(serviceTarget, ServerInventoryService.SERVICE_NAME,
                        getExecutorService(), new InternalExecutor(), this, expressionResolver, environment.getDomainTempDir());

                // demand native mgmt services
                final ServiceBuilder nativeSB = serviceTarget.addService(ServiceName.JBOSS.append("native-mgmt-startup"), Service.NULL);
                nativeSB.requires(ManagementRemotingServices.channelServiceName(ManagementRemotingServices.MANAGEMENT_ENDPOINT, ManagementRemotingServices.SERVER_CHANNEL));
                nativeSB.install();

                // demand http mgmt services
                if (capabilityRegistry.hasCapability(UndertowHttpManagementService.EXTENSIBLE_HTTP_MANAGEMENT_CAPABILITY.getName(), CapabilityScope.GLOBAL)) {
                    final ServiceBuilder httpSB = serviceTarget.addService(ServiceName.JBOSS.append("http-mgmt-startup"), Service.NULL);
                    httpSB.requires(UndertowHttpManagementService.SERVICE_NAME);
                    httpSB.install();
                }

                reachedServers = true;
                if (currentRunningMode == RunningMode.NORMAL) {
                    startServers(false);
                }
            }

        } catch (Exception e) {
            ROOT_LOGGER.caughtExceptionDuringBoot(e);
            if (!reachedServers) {
                ok = false;
            }
        } finally {
            if (ok) {
                try {
                    if (runningModeControl.getRunningMode() == RunningMode.NORMAL) {
                        finishBoot(true);
                        if (hostControllerInfo.isMasterDomainController()) {
                           //Force the activation of the web console here before starting the servers
                           consoleAvailabilitySupplier.get().setAvailable();
                        }
                        startServers(true);
                        clearBootingReadOnlyFlag();
                    } else {
                        finishBoot();
                    }
                } finally {
                    // Trigger the started message
                    Notification notification = new Notification(ModelDescriptionConstants.BOOT_COMPLETE_NOTIFICATION, PathAddress.pathAddress(PathElement.pathElement(CORE_SERVICE, MANAGEMENT),
                            PathElement.pathElement(SERVICE, MANAGEMENT_OPERATIONS)), ControllerLogger.MGMT_OP_LOGGER.bootComplete());
                    getNotificationSupport().emit(notification);

                    String message;
                    String hostConfig = environment.getHostConfigurationFile().getMainFile().getName();
                    if (environment.getDomainConfigurationFile() != null) { //for slave HC is null
                        String domainConfig = environment.getDomainConfigurationFile().getMainFile().getName();
                        message = ROOT_LOGGER.configFilesInUse(domainConfig, hostConfig);
                    } else {
                        message = ROOT_LOGGER.configFileInUse(hostConfig);
                    }
                    bootstrapListener.printBootStatistics(message);
                }
            } else {
                // Die!
                String message;
                if (environment.getDomainConfigurationFile() != null) {
                    message = ROOT_LOGGER.configFilesInUse(environment.getDomainConfigurationFile().getMainFile().getName(), environment.getHostConfigurationFile().getMainFile().getName());
                } else {
                    message = ROOT_LOGGER.configFileInUse(environment.getHostConfigurationFile().getMainFile().getName());
                }
                String failed = ROOT_LOGGER.unsuccessfulBoot(message);
                ROOT_LOGGER.fatal(failed);
                bootstrapListener.bootFailure(failed);

                // don't exit if we're embedded
                if (processType != ProcessType.EMBEDDED_HOST_CONTROLLER) {
                    SystemExiter.abort(ExitCodes.HOST_CONTROLLER_ABORT_EXIT_CODE);
                }
            }
        }
    }

    private boolean bootEmptyConfig(final BootContext context) throws OperationFailedException, ConfigurationPersistenceException {
        HostControllerLogger.ROOT_LOGGER.debug("Invoking initial empty config host controller boot");
        boolean ok = boot(Collections.singletonList(registerModelControllerServiceInitializationBootStep(context)), true, true);
        // until a host is added with the host add op, there is no root description provider delegate. We just install a non-resolving one for now, so the
        // CLI doesn't get a lot of NPEs from :read-resource-description etc.
        SimpleResourceDefinition def = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(null, new NonResolvingResourceDescriptionResolver()));
        rootResourceDefinition.setFakeDelegate(def);
        // just initialize the persister and return, we have to wait for /host=foo:add()
        hostControllerConfigurationPersister.initializeDomainConfigurationPersister(false);
        return ok;
    }

    @Override
    protected final PartialModelIndicator getPartialModelIndicator() {
        return partialModelIndicator;
    }

    private Future<ServerInventory> installServerInventory(final ServiceTarget serviceTarget) {
        if (hostControllerInfo.getHttpManagementSecureInterface() != null && !hostControllerInfo.getHttpManagementSecureInterface().isEmpty()
                && hostControllerInfo.getHttpManagementSecurePort() > 0) {
            return ServerInventoryService.install(serviceTarget, this, runningModeControl, environment, extensionRegistry,
                    hostControllerInfo.getHttpManagementSecureInterface(), hostControllerInfo.getHttpManagementSecurePort(), REMOTE_HTTPS.toString());
        }
        if (hostControllerInfo.getNativeManagementInterface() != null && !hostControllerInfo.getNativeManagementInterface().isEmpty()
                && hostControllerInfo.getNativeManagementPort() > 0) {
            return ServerInventoryService.install(serviceTarget, this, runningModeControl, environment, extensionRegistry,
                    hostControllerInfo.getNativeManagementInterface(), hostControllerInfo.getNativeManagementPort(), REMOTE.toString());
        }
        if (processType == ProcessType.EMBEDDED_HOST_CONTROLLER) {
            return getPlaceHolderInventory();
        }
        return ServerInventoryService.install(serviceTarget, this, runningModeControl, environment, extensionRegistry,
                hostControllerInfo.getHttpManagementInterface(), hostControllerInfo.getHttpManagementPort(), REMOTE_HTTP.toString());
    }

    private void installDiscoveryService(final ServiceTarget serviceTarget, List<DiscoveryOption> discoveryOptions) {
        List<DomainControllerManagementInterface> interfaces = new ArrayList<>();
        if (hostControllerInfo.getNativeManagementInterface() != null && !hostControllerInfo.getNativeManagementInterface().isEmpty()
                && hostControllerInfo.getNativeManagementPort() > 0) {
            interfaces.add(new DomainControllerManagementInterface(hostControllerInfo.getNativeManagementPort(),
                    hostControllerInfo.getNativeManagementInterface(), REMOTE));
        }
        if (hostControllerInfo.getHttpManagementSecureInterface() != null && !hostControllerInfo.getHttpManagementSecureInterface().isEmpty()
                && hostControllerInfo.getHttpManagementSecurePort() > 0) {
            interfaces.add(new DomainControllerManagementInterface(hostControllerInfo.getHttpManagementSecurePort(),
                    hostControllerInfo.getHttpManagementSecureInterface(), REMOTE_HTTPS));
        }
        if (hostControllerInfo.getHttpManagementInterface() != null && !hostControllerInfo.getHttpManagementInterface().isEmpty()
                && hostControllerInfo.getHttpManagementPort() > 0) {
            interfaces.add(new DomainControllerManagementInterface(hostControllerInfo.getHttpManagementPort(),
                    hostControllerInfo.getHttpManagementInterface(), REMOTE_HTTP));
        }
        DiscoveryService.install(serviceTarget, discoveryOptions, interfaces, hostControllerInfo.isMasterDomainController());
    }

    private enum DomainConnectResult {
        CONNECTED,
        FAILED,
        ABORT
    }

    private DomainConnectResult connectToDomainMaster(ServiceTarget serviceTarget, RunningMode currentRunningMode,
                                                      boolean usingCachedDC, boolean adminOnly) {
        Future<MasterDomainControllerClient> clientFuture = RemoteDomainConnectionService.install(serviceTarget,
                getValue(),
                extensionRegistry,
                hostControllerInfo,
                hostControllerInfo.getAuthenticationContext(),
                remoteFileRepository,
                contentRepository,
                ignoredRegistry,
                new DomainModelControllerService.InternalExecutor(),
                this,
                environment,
                getExecutorService(),
                currentRunningMode,
                serverProxies,
                domainConfigAvailable);
        masterDomainControllerClient = getFuture(clientFuture);
        //Registers us with the master and gets down the master copy of the domain model to our DC
        // if --cached-dc is used and the DC is unavailable, we'll use a cached copy of the domain config
        // (if available), and poll for reconnection to the DC. Once the DC becomes available again, the domain
        // config will be re-synchronized.
        try {
            masterDomainControllerClient.register();
            return DomainConnectResult.CONNECTED;
        } catch (Exception e) {
            //We could not connect to the host
            ROOT_LOGGER.cannotConnectToMaster(e);
            if (!usingCachedDC) {
                if (currentRunningMode == RunningMode.ADMIN_ONLY) {
                    ROOT_LOGGER.fetchConfigFromDomainMasterFailed(currentRunningMode,
                            ModelDescriptionConstants.ADMIN_ONLY_POLICY,
                            AdminOnlyDomainConfigPolicy.REQUIRE_LOCAL_CONFIG,
                            CommandLineConstants.CACHED_DC);

                }
                SystemExiter.abort(ExitCodes.HOST_CONTROLLER_ABORT_EXIT_CODE);
                // If we got here, the Exiter didn't really exit. Must be embedded.
                // Inform the caller so it knows not to proceed with boot.
                return DomainConnectResult.ABORT;
            } else if (!adminOnly) {
                // Register a service that will try again once we reach RUNNING state
                DeferredDomainConnectService.install(serviceTarget, masterDomainControllerClient);
            }
            return DomainConnectResult.FAILED;
        }
    }

    @Override
    protected ModelControllerServiceInitializationParams getModelControllerServiceInitializationParams() {
        final ServiceLoader<ModelControllerServiceInitialization> sl = ServiceLoader.load(ModelControllerServiceInitialization.class);
        return new ModelControllerServiceInitializationParams(sl) {

            @Override
            public String getHostName() {
                return hostControllerInfo.getLocalHostName();
            }
        };
    }

    private void establishServerInventory(Future<ServerInventory> future) {
        synchronized (serverInventoryLock) {
            try {
                serverInventory = getFuture(future);
                serverInventoryLock.set(true);
            } finally {
                serverInventoryLock.notifyAll();
            }
        }
    }

    private <T> T getFuture(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void startServers(boolean enabledAutoStart) {
        ModelNode addr = new ModelNode();
        addr.add(HOST, hostControllerInfo.getLocalHostName());
        ModelNode op = Util.getEmptyOperation(StartServersHandler.OPERATION_NAME, addr);
        op.get(ENABLE_AUTO_START).set(enabledAutoStart);

        getValue().execute(op, null, null, null);
    }


    @Override
    public void stop(final StopContext context) {
        synchronized (serverInventoryLock) {
            try {
                serverInventory = null;
                serverInventoryLock.set(false);
            } finally {
                serverInventoryLock.notifyAll();
            }
        }
        extensionRegistry.clear();
        domainConfigAvailable.set(false);
        super.stop(context);
    }

    protected void stopAsynchronous(StopContext context)  {
        pingScheduler.shutdownNow();
    }


    @Override
    public void stopLocalHost() {
        stopLocalHost(0);
    }

    @Override
    public void stopLocalHost(int exitCode) {
        final ProcessControllerClient client = injectedProcessControllerConnection.getValue().getClient();
        processState.setStopping();
        try {
            client.shutdown(exitCode);
        } catch (IOException e) {
            throw HostControllerLogger.ROOT_LOGGER.errorClosingDownHost(e);
        }
    }

    @Override
    public void registerHostModel(String hostName, ManagementResourceRegistration root) {
        hostModelRegistration =
                HostModelUtil.createHostRegistry(hostName, root, hostControllerConfigurationPersister, environment, runningModeControl,
                        localFileRepository, hostControllerInfo, new DelegatingServerInventory(), remoteFileRepository, contentRepository,
                        this, hostExtensionRegistry, extensionRegistry, ignoredRegistry, processState, pathManager, authorizer,
                        securityIdentitySupplier, getAuditLogger(), getBootErrorCollector());
    }


    public void initializeMasterDomainRegistry(final ManagementResourceRegistration root,
            final ExtensibleConfigurationPersister configurationPersister, final ContentRepository contentRepository,
            final HostFileRepository fileRepository,
            final ExtensionRegistry extensionRegistry, final PathManagerService pathManager) {
        initializeDomainResource(root, configurationPersister, contentRepository, fileRepository, true,
                hostControllerInfo, extensionRegistry, null, pathManager);
    }

    public void initializeSlaveDomainRegistry(final ManagementResourceRegistration root,
            final ExtensibleConfigurationPersister configurationPersister, final ContentRepository contentRepository,
            final HostFileRepository fileRepository, final LocalHostControllerInfo hostControllerInfo,
            final ExtensionRegistry extensionRegistry,
            final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry, final PathManagerService pathManagery) {
        initializeDomainResource(root, configurationPersister, contentRepository, fileRepository, false, hostControllerInfo,
                extensionRegistry, ignoredDomainResourceRegistry, pathManager);
    }

    private void initializeDomainResource(final ManagementResourceRegistration root, final ExtensibleConfigurationPersister configurationPersister,
            final ContentRepository contentRepo, final HostFileRepository fileRepository, final boolean isMaster,
            final LocalHostControllerInfo hostControllerInfo,
            final ExtensionRegistry extensionRegistry, final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry,
            final PathManagerService pathManager) {

        DomainRootDefinition domainRootDefinition = new DomainRootDefinition(this, environment, configurationPersister, contentRepo, fileRepository, isMaster, hostControllerInfo,
                extensionRegistry, ignoredDomainResourceRegistry, pathManager, authorizer, securityIdentitySupplier, this, domainHostExcludeRegistry, getMutableRootResourceRegistrationProvider());
        rootResourceDefinition.setDelegate(domainRootDefinition, root);
    }

    private class DelegatingServerInventory implements ServerInventory {

        /*
         * WFLY-2370. Max period a caller to this class can wait for boot ops to complete
         * in the ModelController and boot moves on to starting the ServerInventory.
         * Generally this should be a very small window, as the boot ops install
         * very few services other than the management interface that would
         * let user requests hit this class in the first place.
         */
        private static final long SERVER_INVENTORY_TIMEOUT = 10000;

        private synchronized ServerInventory getServerInventory() {
            if (processType == ProcessType.EMBEDDED_HOST_CONTROLLER) {
                return getServerInventory(true);
            }
            return getServerInventory(false);
        }

        private synchronized ServerInventory getServerInventory(boolean placeHolder) {

            try {
                if (placeHolder) {
                    return getPlaceHolderInventory().get();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            ServerInventory result = null;
            synchronized (serverInventoryLock) {
                if (serverInventoryLock.get()) {
                    // Usual case
                    result = serverInventory;
                } else {
                    try {
                        serverInventoryLock.wait(SERVER_INVENTORY_TIMEOUT);
                        if (serverInventoryLock.get()) {
                            result = serverInventory;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            if (result == null) {
                // Odd case. TODO i18n message
                throw new IllegalStateException();
            }
            return result;
        }

        @Override
        public ProxyController serverCommunicationRegistered(String serverProcessName, ManagementChannelHandler channelHandler) {
            return getServerInventory().serverCommunicationRegistered(serverProcessName, channelHandler);
        }

        @Override
        public boolean serverReconnected(String serverProcessName, ManagementChannelHandler channelHandler) {
            return getServerInventory().serverReconnected(serverProcessName, channelHandler);
        }

        @Override
        public void serverProcessAdded(String serverProcessName) {
            getServerInventory().serverProcessAdded(serverProcessName);
        }

        @Override
        public void serverStartFailed(String serverProcessName) {
            getServerInventory().serverStartFailed(serverProcessName);
        }

        @Override
        public void serverUnstable(String serverProcessName) {
            getServerInventory().serverUnstable(serverProcessName);
        }

        @Override
        public void serverStarted(String serverProcessName) {
            getServerInventory().serverStarted(serverProcessName);
        }

        @Override
        public void serverProcessStopped(String serverProcessName) {
            getServerInventory().serverProcessStopped(serverProcessName);
        }

        @Override
        public String getServerProcessName(String serverName) {
            return getServerInventory().getServerProcessName(serverName);
        }

        @Override
        public String getProcessServerName(String processName) {
            return getServerInventory().getProcessServerName(processName);
        }

        @Override
        public ServerStatus reloadServer(String serverName, boolean blocking, boolean suspend) {
            return getServerInventory().reloadServer(serverName, blocking, suspend);
        }

        @Override
        public void processInventory(Map<String, ProcessInfo> processInfos) {
            getServerInventory().processInventory(processInfos);
        }

        @Override
        public Map<String, ProcessInfo> determineRunningProcesses() {
            return getServerInventory().determineRunningProcesses();
        }

        @Override
        public Map<String, ProcessInfo> determineRunningProcesses(boolean serversOnly) {
            return getServerInventory().determineRunningProcesses(serversOnly);
        }

        @Override
        public ServerStatus determineServerStatus(String serverName) {
            return getServerInventory().determineServerStatus(serverName);
        }

        @Override
        public ServerStatus startServer(String serverName, ModelNode domainModel) {
            return getServerInventory().startServer(serverName, domainModel);
        }

        @Override
        public ServerStatus startServer(String serverName, ModelNode domainModel, boolean blocking, boolean suspend) {
            return getServerInventory().startServer(serverName, domainModel, blocking, suspend);
        }

        @Override
        public void reconnectServer(String serverName, ModelNode domainModel, String authKey, boolean running, boolean stopping) {
            getServerInventory().reconnectServer(serverName, domainModel, authKey, running, stopping);
        }

        @Override
        public ServerStatus restartServer(String serverName, int gracefulTimeout, ModelNode domainModel) {
            return getServerInventory().restartServer(serverName, gracefulTimeout, domainModel);
        }

        @Override
        public ServerStatus restartServer(String serverName, int gracefulTimeout, ModelNode domainModel, boolean blocking, boolean suspend) {
            return getServerInventory().restartServer(serverName, gracefulTimeout, domainModel, blocking, suspend);
        }

        @Override
        public ServerStatus stopServer(String serverName, int gracefulTimeout) {
            return getServerInventory().stopServer(serverName, gracefulTimeout);
        }

        @Override
        public ServerStatus stopServer(String serverName, int gracefulTimeout, boolean blocking) {
            return getServerInventory().stopServer(serverName, gracefulTimeout, blocking);
        }

        @Override
        public CallbackHandler getServerCallbackHandler() {
            return getServerInventory().getServerCallbackHandler();
        }

        @Override
        public void stopServers(int gracefulTimeout) {
            getServerInventory().stopServers(gracefulTimeout);
        }

        @Override
        public void stopServers(int gracefulTimeout, boolean blockUntilStopped) {
            getServerInventory().stopServers(gracefulTimeout, blockUntilStopped);
        }

        @Override
        public void connectionFinished() {
            getServerInventory().connectionFinished();
        }

        @Override
        public void serverProcessStarted(String processName) {
            getServerInventory().serverProcessStarted(processName);
        }

        @Override
        public void serverProcessRemoved(String processName) {
            getServerInventory().serverProcessRemoved(processName);
        }

        @Override
        public void operationFailed(String processName, ProcessMessageHandler.OperationType type) {
            getServerInventory().operationFailed(processName, type);
        }

        @Override
        public void destroyServer(String serverName) {
            getServerInventory().destroyServer(serverName);
        }

        @Override
        public void killServer(String serverName) {
            getServerInventory().killServer(serverName);
        }

        @Override
        public void awaitServersState(Collection<String> serverNames, boolean started) {
            getServerInventory().awaitServersState(serverNames, started);
        }

        @Override
        public List<ModelNode> suspendServers(Set<String> serverNames, BlockingTimeout blockingTimeout) {
            return getServerInventory().suspendServers(serverNames, blockingTimeout);
        }

        @Override
        public List<ModelNode> resumeServers(Set<String> serverNames, BlockingTimeout blockingTimeout) {
            return getServerInventory().resumeServers(serverNames, blockingTimeout);
        }

        @Override
        public List<ModelNode> suspendServers(Set<String> serverNames, int timeout, BlockingTimeout blockingTimeout) {
            return getServerInventory().suspendServers(serverNames, timeout, blockingTimeout);
        }
    }

    @Override
    public ExtensionRegistry getExtensionRegistry() {
        return extensionRegistry;
    }

    @Override
    public ImmutableCapabilityRegistry getCapabilityRegistry() {
        return capabilityRegistry;
    }

    @Override
    public ExpressionResolver getExpressionResolver() {
        return expressionResolver;
    }

    private static class DomainDelegatingResourceDefinition extends org.jboss.as.controller.DelegatingResourceDefinition{
        void setDelegate(DomainRootDefinition delegate, ManagementResourceRegistration root) {
            super.setDelegate(delegate);
            delegate.initialize(root);
        }

        // this is only used for providing a delegate before /host:add() is called. Once the host is added
        // it will be replace with the real delegate.
        void setFakeDelegate(ResourceDefinition delegate) {
            super.setDelegate(delegate);
        }

        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            //These will be registered later
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            //These will be registered later
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            //These will be registered later
        }

        @Override
        public void registerNotifications(ManagementResourceRegistration resourceRegistration) {
            //These will be registered later
        }

        @Override
        public void registerCapabilities(ManagementResourceRegistration resourceRegistration) {
            //These will be registered later
        }

        @Override
        public void registerAdditionalRuntimePackages(ManagementResourceRegistration resourceRegistration) {
            //These will be registered later
        }
    }

    final class InternalExecutor implements HostControllerRegistrationHandler.OperationExecutor, ServerToHostProtocolHandler.OperationExecutor, MasterDomainControllerOperationHandlerService.TransactionalOperationExecutor {

        @Override
        public ModelNode execute(Operation operation, OperationMessageHandler handler, OperationTransactionControl control,
                OperationStepHandler step) {
            Function<DomainModelControllerService, OperationResponse> function = new Function<DomainModelControllerService, OperationResponse>() {
                @Override
                public OperationResponse apply(DomainModelControllerService controllerService) {
                    return InVmAccess.runInVm((PrivilegedAction<OperationResponse>) () -> controllerService.internalExecute(operation, handler, control, step));
                }
            };
            return SecurityActions.privilegedExecution(function, DomainModelControllerService.this).getResponseNode();
        }

        @Override
        public ModelNode installSlaveExtensions(List<ModelNode> extensions) {
            Operation operation = ApplyExtensionsHandler.getOperation(extensions);
            OperationStepHandler stepHandler = modelNodeRegistration.getOperationHandler(PathAddress.EMPTY_ADDRESS, ApplyExtensionsHandler.OPERATION_NAME);
            Function<DomainModelControllerService, OperationResponse> function = new Function<DomainModelControllerService, OperationResponse>() {
                @Override
                public OperationResponse apply(DomainModelControllerService controllerService) {
                    return InVmAccess.runInVm((PrivilegedAction<OperationResponse>) () -> controllerService.internalExecute(operation, OperationMessageHandler.logging, OperationTransactionControl.COMMIT, stepHandler, false, true));
                }
            };
            return SecurityActions.privilegedExecution(function, DomainModelControllerService.this).getResponseNode();
        }

        @Override
        @SuppressWarnings("deprecation")
        public ModelNode joinActiveOperation(ModelNode operation, OperationMessageHandler handler,
                                             OperationTransactionControl control,
                                             OperationStepHandler step, int permit) {
            Function<DomainModelControllerService, ModelNode> function = new Function<DomainModelControllerService, ModelNode>() {
                @Override
                public ModelNode apply(DomainModelControllerService controllerService) {
                    return InVmAccess.runInVm((PrivilegedAction<ModelNode>) () -> controllerService.executeReadOnlyOperation(operation, handler, control, step, permit));
                }
            };
            return SecurityActions.privilegedExecution(function, DomainModelControllerService.this);
        }

        @Override
        public OperationResponse executeAndAttemptLock(Operation operation, OperationMessageHandler handler,
                OperationTransactionControl control, OperationStepHandler step) {
            Function<DomainModelControllerService, OperationResponse> function = new Function<DomainModelControllerService, OperationResponse>() {
                @Override
                public OperationResponse apply(DomainModelControllerService controllerService) {
                    return InVmAccess.runInVm((PrivilegedAction<OperationResponse>) () -> controllerService.internalExecute(operation, handler, control, step, true));
                }
            };
            return SecurityActions.privilegedExecution(function, DomainModelControllerService.this);
        }

        @Override
        public ModelNode executeReadOnly(ModelNode operation, OperationStepHandler handler, OperationTransactionControl control) {

            Function<DomainModelControllerService, ModelNode> function = new Function<DomainModelControllerService, ModelNode>() {
                @Override
                public ModelNode apply(DomainModelControllerService controllerService) {
                    return InVmAccess.runInVm((PrivilegedAction<ModelNode>) () -> controllerService.executeReadOnlyOperation(operation, control,  handler));
                }
            };
            return SecurityActions.privilegedExecution(function, DomainModelControllerService.this);
        }

        @Override
        public ModelNode executeReadOnly(ModelNode operation, Resource model, OperationStepHandler handler, OperationTransactionControl control) {
            Function<DomainModelControllerService, ModelNode> function = new Function<DomainModelControllerService, ModelNode>() {
                @Override
                public ModelNode apply(DomainModelControllerService controllerService) {
                    return InVmAccess.runInVm((PrivilegedAction<ModelNode>) () -> controllerService.executeReadOnlyOperation(operation, model, control, handler));
                }
            };
            return SecurityActions.privilegedExecution(function, DomainModelControllerService.this);
        }

        @Override
        public void acquireReadlock(final Integer operationID) throws IllegalArgumentException, InterruptedException {
            Assert.checkNotNullParam("operationID", operationID);
            // acquire a read (shared mode) lock for this registration, released in releaseReadlock
            acquireReadLock(operationID);
        }

        @Override
        public void releaseReadlock(final Integer operationID) throws IllegalArgumentException {
            Assert.checkNotNullParam("operationID", operationID);
            releaseReadLock(operationID);
        }
    }

    private static final class DomainHostControllerInfoAccessor implements RuntimeHostControllerInfoAccessor {
        private final LocalHostControllerInfoImpl hostControllerInfo;

        public DomainHostControllerInfoAccessor(LocalHostControllerInfoImpl hostControllerInfo) {
            this.hostControllerInfo = hostControllerInfo;
        }

        @Override
        public HostControllerInfo getHostControllerInfo(OperationContext context) throws OperationFailedException {
            if (context.isBooting() && context.getCurrentStage() == OperationContext.Stage.MODEL) {
                throw ControllerLogger.ROOT_LOGGER.onlyAccessHostControllerInfoInRuntimeStage();
            }
            return new HostControllerInfo() {
                public boolean isMasterHc() {
                    return hostControllerInfo.isMasterDomainController();
                }
            };
        }

    }

    private class FutureServerInventory extends AsyncFutureTask<ServerInventory>{

        public FutureServerInventory() {
            super(null);
        }

        private void setInventory(ServerInventory inventory) {
            super.setResult(inventory);
        }

    }

    // this is a placeholder object used in certain cases where the live inventory is not available
    // e.g. offline embedded mode
    private FutureServerInventory getPlaceHolderInventory() {

        FutureServerInventory future = new FutureServerInventory();
        ServerInventory inventory = new ServerInventory() {
            @Override
            public String getServerProcessName(String serverName) {
                return ManagedServer.getServerProcessName(serverName);
            }

            @Override
            public String getProcessServerName(String processName) {
                return ManagedServer.getServerName(processName);
            }

            @Override
            public Map<String, ProcessInfo> determineRunningProcesses() {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, ProcessInfo> determineRunningProcesses(boolean serversOnly) {
                return Collections.emptyMap();
            }

            @Override
            public ServerStatus determineServerStatus(String serverName) {
                return ServerStatus.STOPPED;
            }

            @Override
            public ServerStatus startServer(String serverName, ModelNode domainModel) {
                return ServerStatus.STOPPED;
            }

            @Override
            public ServerStatus startServer(String serverName, ModelNode domainModel, boolean blocking, boolean suspend) {
                return ServerStatus.STOPPED;
            }

            @Override
            public ServerStatus restartServer(String serverName, int gracefulTimeout, ModelNode domainModel) {
                return ServerStatus.STOPPED;
            }

            @Override
            public ServerStatus restartServer(String serverName, int gracefulTimeout, ModelNode domainModel, boolean blocking, boolean suspend) {
                return ServerStatus.STOPPED;
            }

            @Override
            public ServerStatus stopServer(String serverName, int gracefulTimeout) {
                return ServerStatus.STARTED;
            }

            @Override
            public ServerStatus stopServer(String serverName, int gracefulTimeout, boolean blocking) {
                return ServerStatus.STARTED;
            }

            @Override
            public void stopServers(int gracefulTimeout) {

            }

            @Override
            public void stopServers(int gracefulTimeout, boolean blockUntilStopped) {

            }

            @Override
            public void reconnectServer(String serverName, ModelNode domainModel, String authKey, boolean running, boolean stopping) {
            }

            @Override
            public ServerStatus reloadServer(String serverName, boolean blocking, boolean suspend) {
                return ServerStatus.STOPPED;
            }

            @Override
            public void destroyServer(String serverName) {

            }

            @Override
            public void killServer(String serverName) {

            }

            @Override
            public CallbackHandler getServerCallbackHandler() {
                CallbackHandler callback = new CallbackHandler() {
                    @Override
                    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                    }
                };
                return callback;
            }

            @Override
            public ProxyController serverCommunicationRegistered(String serverProcessName, ManagementChannelHandler channelHandler) {
                return new ProxyController() {
                    @Override
                    public PathAddress getProxyNodeAddress() {
                        return null;
                    }

                    @Override
                    public void execute(ModelNode operation, OperationMessageHandler handler,
                                        ProxyOperationControl control, OperationAttachments attachments,
                                        BlockingTimeout blockingTimeout) {
                    }
                };
            }

            @Override
            public boolean serverReconnected(String serverProcessName, ManagementChannelHandler channelHandler) {
                return true;
            }

            @Override
            public void serverStarted(String serverProcessName) {

            }

            @Override
            public void serverStartFailed(String serverProcessName) {
            }

            @Override
            public void serverUnstable(String serverProcessName) {

            }

            @Override
            public void serverProcessStopped(String serverProcessName) {
            }

            @Override
            public void connectionFinished() {
            }

            @Override
            public void serverProcessAdded(String processName) {
            }

            @Override
            public void serverProcessStarted(String processName) {
            }

            @Override
            public void serverProcessRemoved(String processName) {
            }

            @Override
            public void operationFailed(String processName, ProcessMessageHandler.OperationType type) {
            }

            @Override
            public void processInventory(Map<String, ProcessInfo> processInfos) {
            }

            @Override
            public void awaitServersState(Collection<String> serverNames, boolean started) {
            }

            @Override
            public List<ModelNode> suspendServers(Set<String> serverNames, BlockingTimeout blockingTimeout) {
                return Collections.emptyList();
            }

            @Override
            public List<ModelNode> resumeServers(Set<String> serverNames, BlockingTimeout blockingTimeout) {
                return Collections.emptyList();
            }

            @Override
            public List<ModelNode> suspendServers(Set<String> serverNames, int timeout, BlockingTimeout blockingTimeout) {
                return Collections.emptyList();
            }
        };
        future.setInventory(inventory);
        return future;
    }

    private static class DeferredDomainConnectService implements Service<Void>, PropertyChangeListener {
        private final MasterDomainControllerClient domainControllerClient;
        private final InjectedValue<ProcessStateNotifier> injectedValue = new InjectedValue<>();
        private boolean activated;
        private volatile Cancellable connectionFuture;

        private static void install(ServiceTarget target, MasterDomainControllerClient domainControllerClient) {
            DeferredDomainConnectService service = new DeferredDomainConnectService(domainControllerClient);
            target.addService(DomainModelControllerService.SERVICE_NAME.append("deferred-domain-connect"), service)
                    .addDependency(ControlledProcessStateService.INTERNAL_SERVICE_NAME, ProcessStateNotifier.class, service.injectedValue)
                    .install();
        }

        private DeferredDomainConnectService(MasterDomainControllerClient domainControllerClient) {
            this.domainControllerClient = domainControllerClient;
        }

        @Override
        public void start(StartContext context) throws StartException {
            ProcessStateNotifier cpsn = injectedValue.getValue();
            cpsn.addPropertyChangeListener(this);
        }

        @Override
        public void stop(StopContext context) {
            Cancellable toCancel = connectionFuture;
            if (toCancel != null) {
                toCancel.cancel();
            }
        }

        @Override
        public Void getValue() throws IllegalStateException, IllegalArgumentException {
            return null;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            ControlledProcessState.State newState = (ControlledProcessState.State) evt.getNewValue();
            if (newState == ControlledProcessState.State.RUNNING) {
                boolean callReconnect;
                synchronized (this) {
                    callReconnect = !activated;
                    activated = true;
                }
                if (callReconnect) {
                    connectionFuture = domainControllerClient.pollForConnect();
                }

            }
        }
    }
}
