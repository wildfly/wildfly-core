/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.host.controller.model.host;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_ORGANIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.services.path.PathResourceDefinition.PATH_CAPABILITY;

import org.jboss.as.controller.BootErrorCollector;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.ExtensionRegistryType;
import org.jboss.as.controller.extension.ExtensionResourceDefinition;
import org.jboss.as.controller.extension.MutableRootResourceRegistrationProvider;
import org.jboss.as.controller.operations.common.NamespaceAddHandler;
import org.jboss.as.controller.operations.common.NamespaceRemoveHandler;
import org.jboss.as.controller.operations.common.ProcessStateAttributeHandler;
import org.jboss.as.controller.operations.common.ResolveExpressionHandler;
import org.jboss.as.controller.operations.common.SchemaLocationAddHandler;
import org.jboss.as.controller.operations.common.SchemaLocationRemoveHandler;
import org.jboss.as.controller.operations.common.SnapshotDeleteHandler;
import org.jboss.as.controller.operations.common.SnapshotListHandler;
import org.jboss.as.controller.operations.common.SnapshotTakeHandler;
import org.jboss.as.controller.operations.common.ValidateAddressOperationHandler;
import org.jboss.as.controller.operations.common.ValidateOperationHandler;
import org.jboss.as.controller.operations.common.XmlMarshallingHandler;
import org.jboss.as.controller.operations.global.GlobalInstallationReportHandler;
import org.jboss.as.controller.operations.global.ReadAttributeHandler;
import org.jboss.as.controller.operations.global.ReadConfigAsFeaturesOperationHandler;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.domain.controller.operations.DomainServerLifecycleHandlers;
import org.jboss.as.domain.controller.operations.HostProcessReloadHandler;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management.audit.EnvironmentNameReader;
import org.jboss.as.host.controller.DirectoryGrouping;
import org.jboss.as.host.controller.HostControllerConfigurationPersister;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.HostControllerService;
import org.jboss.as.host.controller.HostModelUtil;
import org.jboss.as.host.controller.HostRunningModeControl;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.as.host.controller.descriptions.HostEnvironmentResourceDefinition;
import org.jboss.as.host.controller.discovery.DiscoveryOptionResourceDefinition;
import org.jboss.as.host.controller.discovery.DiscoveryOptionsResourceDefinition;
import org.jboss.as.host.controller.discovery.StaticDiscoveryResourceDefinition;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.host.controller.model.jvm.JvmResourceDefinition;
import org.jboss.as.host.controller.operations.DomainControllerWriteAttributeHandler;
import org.jboss.as.host.controller.operations.HostShutdownHandler;
import org.jboss.as.host.controller.operations.HostSpecifiedInterfaceAddHandler;
import org.jboss.as.host.controller.operations.HostSpecifiedInterfaceRemoveHandler;
import org.jboss.as.host.controller.operations.HostXmlMarshallingHandler;
import org.jboss.as.host.controller.operations.InstallationReportHandler;
import org.jboss.as.host.controller.operations.IsMasterHandler;
import org.jboss.as.host.controller.operations.LocalHostControllerInfoImpl;
import org.jboss.as.host.controller.operations.ResolveExpressionOnHostHandler;
import org.jboss.as.host.controller.operations.StartServersHandler;
import org.jboss.as.host.controller.resources.HttpManagementResourceDefinition;
import org.jboss.as.host.controller.resources.NativeManagementResourceDefinition;
import org.jboss.as.host.controller.resources.ServerConfigResourceDefinition;
import org.jboss.as.host.controller.resources.StoppedServerResource;
import org.jboss.as.platform.mbean.PlatformMBeanResourceRegistrar;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.controller.resources.CapabilityRegistryResourceDefinition;
import org.jboss.as.server.controller.resources.ModuleLoadingResourceDefinition;
import org.jboss.as.server.controller.resources.ServerRootResourceDefinition;
import org.jboss.as.server.controller.resources.ServiceContainerResourceDefinition;
import org.jboss.as.server.controller.resources.SystemPropertyResourceDefinition;
import org.jboss.as.server.operations.CleanObsoleteContentHandler;
import org.jboss.as.server.operations.InstanceUuidReadHandler;
import org.jboss.as.server.operations.RunningModeReadHandler;
import org.jboss.as.server.operations.SuspendStateReadHandler;
import org.jboss.as.server.operations.WriteConfigHandler;
import org.jboss.as.server.services.net.InterfaceResourceDefinition;
import org.jboss.as.server.services.net.SocketBindingGroupResourceDefinition;
import org.jboss.as.server.services.net.SpecifiedInterfaceResolveHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
public class HostResourceDefinition extends SimpleResourceDefinition {

    private static final RuntimeCapability<Void> HOST_RUNTIME_CAPABILITY = RuntimeCapability
            .Builder.of("org.wildfly.host.controller", false)
            .build();

    public static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NAME, ModelType.STRING)
            .setRequired(false)
            .setMinSize(1)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.DOMAIN_NAMES)
            .build();

    static final SimpleAttributeDefinition PRODUCT_NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PRODUCT_NAME, ModelType.STRING)
            .setRequired(false)
            .setMinSize(1)
            .build();

    static final SimpleAttributeDefinition RELEASE_VERSION = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.RELEASE_VERSION, ModelType.STRING)
            .setRequired(false)
            .setMinSize(1)
            .build();

    static final SimpleAttributeDefinition RELEASE_CODENAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.RELEASE_CODENAME, ModelType.STRING)
            .setRequired(false)
            .setMinSize(1)
            .build();
    static final SimpleAttributeDefinition PRODUCT_VERSION = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PRODUCT_VERSION, ModelType.STRING)
            .setRequired(false)
            .setMinSize(1)
            .build();
    static final SimpleAttributeDefinition MANAGEMENT_MAJOR_VERSION = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION, ModelType.INT)
            .setMinSize(1)
            .build();
    static final SimpleAttributeDefinition MANAGEMENT_MINOR_VERSION = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.MANAGEMENT_MINOR_VERSION, ModelType.INT)
            .setMinSize(1)
            .build();
    static final SimpleAttributeDefinition MANAGEMENT_MICRO_VERSION = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.MANAGEMENT_MICRO_VERSION, ModelType.INT)
            .setMinSize(1)
            .build();
    //This is just there for bw compatibility, it had no read handler before this change
    static final SimpleAttributeDefinition SERVER_STATE = new SimpleAttributeDefinitionBuilder("server-state", ModelType.STRING)
            .setRequired(false)
            .setMinSize(1)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    public static final SimpleAttributeDefinition UUID = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.UUID, ModelType.STRING, false)
            .setValidator(new StringLengthValidator(1, true))
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    public static final SimpleAttributeDefinition ORGANIZATION_IDENTIFIER = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.ORGANIZATION, ModelType.STRING, true)
            .setValidator(new StringLengthValidator(1, true))
            .build();

    public static final SimpleAttributeDefinition DOMAIN_ORGANIZATION_IDENTIFIER = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.DOMAIN_ORGANIZATION, ModelType.STRING, true)
            .setValidator(new StringLengthValidator(1, true))
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    // the current runtime configuration state, replaces HOST_STATE
    public static final SimpleAttributeDefinition RUNTIME_CONFIGURATION_STATE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.RUNTIME_CONFIGURATION_STATE, ModelType.STRING)
            .setMinSize(1)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    public static final SimpleAttributeDefinition HOST_STATE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.HOST_STATE, ModelType.STRING)
            .setMinSize(1)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    public static final SimpleAttributeDefinition DIRECTORY_GROUPING = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.DIRECTORY_GROUPING, ModelType.STRING, true).
            addFlag(AttributeAccess.Flag.RESTART_ALL_SERVICES).
            setDefaultValue(DirectoryGrouping.defaultValue().toModelNode()).
            setValidator(EnumValidator.create(DirectoryGrouping.class)).
            setAllowExpression(true).
            build();
    public static final SimpleAttributeDefinition MASTER = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.PRIMARY, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.FALSE)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setResourceOnly()
            .build();

    public static final ObjectTypeAttributeDefinition DC_LOCAL = new ObjectTypeAttributeDefinition.Builder(ModelDescriptionConstants.LOCAL)
            .build();

    public static final ObjectTypeAttributeDefinition DC_REMOTE = new ObjectTypeAttributeDefinition.Builder(
                ModelDescriptionConstants.REMOTE,
                DomainControllerWriteAttributeHandler.PROTOCOL,
                DomainControllerWriteAttributeHandler.HOST,
                DomainControllerWriteAttributeHandler.PORT,
                DomainControllerWriteAttributeHandler.AUTHENTICATION_CONTEXT,
                DomainControllerWriteAttributeHandler.USERNAME,
                DomainControllerWriteAttributeHandler.IGNORE_UNUSED_CONFIG,
                DomainControllerWriteAttributeHandler.ADMIN_ONLY_POLICY)
            .build();

    public static final ObjectTypeAttributeDefinition DOMAIN_CONTROLLER = new ObjectTypeAttributeDefinition.Builder(ModelDescriptionConstants.DOMAIN_CONTROLLER, DC_LOCAL, DC_REMOTE)
            .setRequired(true)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.DOMAIN_CONTROLLER)
            .build();

    private final HostControllerConfigurationPersister configurationPersister;
    private final HostControllerEnvironment environment;
    private final HostRunningModeControl runningModeControl;
    private final HostFileRepository localFileRepository;
    private final LocalHostControllerInfoImpl hostControllerInfo;
    private final ServerInventory serverInventory;
    private final HostFileRepository remoteFileRepository;
    private final ContentRepository contentRepository;
    private final DomainController domainController;
    private final ExtensionRegistry hostExtensionRegistry;
    private final IgnoredDomainResourceRegistry ignoredRegistry;
    private final ControlledProcessState processState;
    private final PathManagerService pathManager;
    private final DelegatingConfigurableAuthorizer authorizer;
    private final ManagementSecurityIdentitySupplier securityIdentitySupplier;
    private final ManagedAuditLogger auditLogger;
    private final BootErrorCollector bootErrorCollector;

    public HostResourceDefinition(final String hostName,
                                  final HostControllerConfigurationPersister configurationPersister,
                                  final HostControllerEnvironment environment,
                                  final HostRunningModeControl runningModeControl,
                                  final HostFileRepository localFileRepository,
                                  final LocalHostControllerInfoImpl hostControllerInfo,
                                  final ServerInventory serverInventory,
                                  final HostFileRepository remoteFileRepository,
                                  final ContentRepository contentRepository,
                                  final DomainController domainController,
                                  final ExtensionRegistry hostExtensionRegistry,
                                  final IgnoredDomainResourceRegistry ignoredRegistry,
                                  final ControlledProcessState processState,
                                  final PathManagerService pathManager,
                                  final DelegatingConfigurableAuthorizer authorizer,
                                  final ManagementSecurityIdentitySupplier securityIdentitySupplier,
                                  final ManagedAuditLogger auditLogger,
                                  final BootErrorCollector bootErrorCollector) {
        super(new Parameters(PathElement.pathElement(HOST, hostName), HostModelUtil.getResourceDescriptionResolver())
                .setCapabilities(HOST_RUNTIME_CAPABILITY,
                        PATH_CAPABILITY.fromBaseCapability(HostControllerEnvironment.HOME_DIR),
                        PATH_CAPABILITY.fromBaseCapability(HostControllerEnvironment.DOMAIN_CONFIG_DIR),
                        PATH_CAPABILITY.fromBaseCapability(HostControllerEnvironment.DOMAIN_DATA_DIR),
                        PATH_CAPABILITY.fromBaseCapability(HostControllerEnvironment.DOMAIN_LOG_DIR),
                        PATH_CAPABILITY.fromBaseCapability(HostControllerEnvironment.DOMAIN_TEMP_DIR),
                        PATH_CAPABILITY.fromBaseCapability(HostControllerEnvironment.CONTROLLER_TEMP_DIR),
                        PATH_CAPABILITY.fromBaseCapability(ServerEnvironment.SERVER_BASE_DIR),
                        PATH_CAPABILITY.fromBaseCapability(ServerEnvironment.SERVER_CONFIG_DIR),
                        PATH_CAPABILITY.fromBaseCapability(ServerEnvironment.SERVER_DATA_DIR),
                        PATH_CAPABILITY.fromBaseCapability(ServerEnvironment.SERVER_LOG_DIR),
                        PATH_CAPABILITY.fromBaseCapability(ServerEnvironment.SERVER_TEMP_DIR)));
        this.configurationPersister = configurationPersister;
        this.environment = environment;
        this.runningModeControl = runningModeControl;
        this.localFileRepository = localFileRepository;
        this.hostControllerInfo = hostControllerInfo;
        this.serverInventory = serverInventory;
        this.remoteFileRepository = remoteFileRepository;
        this.contentRepository = contentRepository;
        this.domainController = domainController;
        this.hostExtensionRegistry = hostExtensionRegistry;
        this.ignoredRegistry = ignoredRegistry;
        this.processState = processState;
        this.pathManager = pathManager;
        this.authorizer = authorizer;
        this.securityIdentitySupplier = securityIdentitySupplier;
        this.auditLogger = auditLogger;
        this.bootErrorCollector = bootErrorCollector;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration hostRegistration) {
        super.registerAttributes(hostRegistration);
        hostRegistration.registerReadWriteAttribute(DIRECTORY_GROUPING, null, new ReloadRequiredWriteAttributeHandler(
                DIRECTORY_GROUPING) {
            @Override
            protected boolean requiresRuntime(OperationContext context) {
                return context.getRunningMode() == RunningMode.NORMAL && !context.isBooting();
            }

        });
        hostRegistration.registerReadWriteAttribute(ORGANIZATION_IDENTIFIER, null, new ModelOnlyWriteAttributeHandler(ORGANIZATION_IDENTIFIER));
        // provide the domain-organization, this was defined here, but never had any handlers or storage defined.
        hostRegistration.registerReadOnlyAttribute(DOMAIN_ORGANIZATION_IDENTIFIER, new ReadAttributeHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        ModelNode rootModel = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS,false).getModel();
                        context.getResult().set(rootModel.get(DOMAIN_ORGANIZATION));
                    }
                }
        );
        hostRegistration.registerReadOnlyAttribute(PRODUCT_NAME, null);
        hostRegistration.registerReadOnlyAttribute(UUID, new InstanceUuidReadHandler(environment));
        hostRegistration.registerReadOnlyAttribute(SERVER_STATE, null);
        hostRegistration.registerReadOnlyAttribute(RELEASE_VERSION, null);
        hostRegistration.registerReadOnlyAttribute(RELEASE_CODENAME, null);
        hostRegistration.registerReadOnlyAttribute(PRODUCT_VERSION, null);
        hostRegistration.registerReadOnlyAttribute(MANAGEMENT_MAJOR_VERSION, null);
        hostRegistration.registerReadOnlyAttribute(MANAGEMENT_MINOR_VERSION, null);
        hostRegistration.registerReadOnlyAttribute(MANAGEMENT_MICRO_VERSION, null);
        hostRegistration.registerReadOnlyAttribute(MASTER, IsMasterHandler.INSTANCE);
        hostRegistration.registerReadOnlyAttribute(ServerRootResourceDefinition.NAMESPACES, null);
        hostRegistration.registerReadOnlyAttribute(ServerRootResourceDefinition.SCHEMA_LOCATIONS, null);
        hostRegistration.registerReadWriteAttribute(HostResourceDefinition.NAME, environment.getProcessNameReadHandler(), environment.getHostNameWriteHandler(hostControllerInfo));
        hostRegistration.registerReadOnlyAttribute(HostResourceDefinition.RUNTIME_CONFIGURATION_STATE, new ProcessStateAttributeHandler(processState));
        hostRegistration.registerReadOnlyAttribute(HostResourceDefinition.HOST_STATE, new ProcessStateAttributeHandler(processState));
        hostRegistration.registerReadOnlyAttribute(ServerRootResourceDefinition.RUNNING_MODE, new RunningModeReadHandler(runningModeControl));
        hostRegistration.registerReadOnlyAttribute(ServerRootResourceDefinition.SUSPEND_STATE, SuspendStateReadHandler.INSTANCE);
    }


    @Override
    public void registerOperations(ManagementResourceRegistration hostRegistration) {
        super.registerOperations(hostRegistration);
        hostRegistration.registerOperationHandler(NamespaceAddHandler.DEFINITION, NamespaceAddHandler.INSTANCE);
        hostRegistration.registerOperationHandler(NamespaceRemoveHandler.DEFINITION, NamespaceRemoveHandler.INSTANCE);
        hostRegistration.registerOperationHandler(SchemaLocationAddHandler.DEFINITION, SchemaLocationAddHandler.INSTANCE);
        hostRegistration.registerOperationHandler(SchemaLocationRemoveHandler.DEFINITION, SchemaLocationRemoveHandler.INSTANCE);

        hostRegistration.registerOperationHandler(GlobalInstallationReportHandler.DEFINITION, GlobalInstallationReportHandler.INSTANCE, false);
        hostRegistration.registerOperationHandler(InstallationReportHandler.DEFINITION, InstallationReportHandler.createOperation(environment), false);

        hostRegistration.registerOperationHandler(ReadConfigAsFeaturesOperationHandler.DEFINITION, new ReadConfigAsFeaturesOperationHandler(), true);

        hostRegistration.registerOperationHandler(ValidateAddressOperationHandler.DEFINITION, ValidateAddressOperationHandler.INSTANCE);

        hostRegistration.registerOperationHandler(ResolveExpressionHandler.DEFINITION, ResolveExpressionHandler.INSTANCE);
        hostRegistration.registerOperationHandler(ResolveExpressionOnHostHandler.DEFINITION, ResolveExpressionOnHostHandler.INSTANCE);
        hostRegistration.registerOperationHandler(SpecifiedInterfaceResolveHandler.DEFINITION, SpecifiedInterfaceResolveHandler.INSTANCE);
        hostRegistration.registerOperationHandler(CleanObsoleteContentHandler.DEFINITION, CleanObsoleteContentHandler.createOperation(contentRepository));
        hostRegistration.registerOperationHandler(WriteConfigHandler.DEFINITION, WriteConfigHandler.INSTANCE);

        XmlMarshallingHandler xmh = new HostXmlMarshallingHandler(configurationPersister.getHostPersister(), hostControllerInfo);
        hostRegistration.registerOperationHandler(XmlMarshallingHandler.DEFINITION, xmh);


        StartServersHandler ssh = new StartServersHandler(environment, serverInventory, runningModeControl);
        hostRegistration.registerOperationHandler(StartServersHandler.DEFINITION, ssh);

        if (environment.getProcessType() != ProcessType.EMBEDDED_HOST_CONTROLLER) {
            HostShutdownHandler hsh = new HostShutdownHandler(domainController, serverInventory, environment);
            hostRegistration.registerOperationHandler(HostShutdownHandler.DEFINITION, hsh);
        }

        HostProcessReloadHandler reloadHandler = new HostProcessReloadHandler(HostControllerService.HC_SERVICE_NAME,
                runningModeControl, processState, environment, hostControllerInfo);
        hostRegistration.registerOperationHandler(HostProcessReloadHandler.getDefinition(hostControllerInfo), reloadHandler);


        DomainServerLifecycleHandlers.initializeServerInventory(serverInventory);

        DomainServerLifecycleHandlers.registerHostHandlers(hostRegistration);

        ValidateOperationHandler validateOperationHandler = hostControllerInfo.isMasterDomainController() ? ValidateOperationHandler.INSTANCE : ValidateOperationHandler.SLAVE_HC_INSTANCE;
        hostRegistration.registerOperationHandler(ValidateOperationHandler.DEFINITION_HIDDEN, validateOperationHandler);


        SnapshotDeleteHandler snapshotDelete = new SnapshotDeleteHandler(configurationPersister.getHostPersister());
        hostRegistration.registerOperationHandler(SnapshotDeleteHandler.DEFINITION, snapshotDelete);
        SnapshotListHandler snapshotList = new SnapshotListHandler(configurationPersister.getHostPersister());
        hostRegistration.registerOperationHandler(SnapshotListHandler.DEFINITION, snapshotList);
        SnapshotTakeHandler snapshotTake = new SnapshotTakeHandler(configurationPersister.getHostPersister());
        hostRegistration.registerOperationHandler(SnapshotTakeHandler.DEFINITION, snapshotTake);

        ignoredRegistry.registerResources(hostRegistration);

    }

    @Override
    public void registerChildren(final ManagementResourceRegistration hostRegistration) {
        super.registerChildren(hostRegistration);

        //Extensions
        hostRegistration.registerSubModel(new ExtensionResourceDefinition(hostExtensionRegistry, true,
                ExtensionRegistryType.HOST,
                new MutableRootResourceRegistrationProvider() {
                    @Override
                    public ManagementResourceRegistration getRootResourceRegistrationForUpdate(OperationContext context) {
                        return hostRegistration;
                    }
                }));

        // System Properties
        hostRegistration.registerSubModel(SystemPropertyResourceDefinition.createForDomainOrHost(SystemPropertyResourceDefinition.Location.HOST));

        /////////////////////////////////////////
        // Core Services

        // Central Management
        ResourceDefinition nativeManagement = new NativeManagementResourceDefinition(hostControllerInfo);
        ResourceDefinition httpManagement = HttpManagementResourceDefinition.create(hostControllerInfo, environment);

        // audit log environment reader
        final EnvironmentNameReader environmentNameReader = new EnvironmentNameReader() {
            @Override
            public boolean isServer() {
                return false;
            }

            @Override
            public String getServerName() {
                return null;
            }

            @Override
            public String getHostName() {
                return environment.getHostControllerName();
            }

            @Override
            public String getProductName() {
                if (environment.getProductConfig() != null && environment.getProductConfig().getProductName() != null) {
                    return environment.getProductConfig().getProductName();
                }
                return null;
            }
        };
        hostRegistration.registerSubModel(CoreManagementResourceDefinition.forHost(authorizer, securityIdentitySupplier, auditLogger, pathManager, environmentNameReader, bootErrorCollector, nativeManagement, httpManagement));

        // Other core services
        hostRegistration.registerSubModel(new ServiceContainerResourceDefinition());

        //host-environment
        hostRegistration.registerSubModel(HostEnvironmentResourceDefinition.of(environment));
        hostRegistration.registerSubModel(ModuleLoadingResourceDefinition.INSTANCE);

        // Platform MBeans
        PlatformMBeanResourceRegistrar.registerPlatformMBeanResources(hostRegistration);
        hostRegistration.registerSubModel(new CapabilityRegistryResourceDefinition(domainController.getCapabilityRegistry()));

        // discovery options
        ManagementResourceRegistration discoveryOptions = hostRegistration.registerSubModel(DiscoveryOptionsResourceDefinition.INSTANCE);
        discoveryOptions.registerSubModel(new StaticDiscoveryResourceDefinition(hostControllerInfo));
        discoveryOptions.registerSubModel(new DiscoveryOptionResourceDefinition(hostControllerInfo));

        // Jvms
        hostRegistration.registerSubModel(JvmResourceDefinition.GLOBAL);

        //Paths
        // TODO why resolvable?
        hostRegistration.registerSubModel(PathResourceDefinition.createResolvableSpecified(pathManager));

        //interface
        ManagementResourceRegistration interfaces = hostRegistration.registerSubModel(new InterfaceResourceDefinition(
                new HostSpecifiedInterfaceAddHandler(),
                new HostSpecifiedInterfaceRemoveHandler(),
                true,
                true
        ));
        interfaces.registerOperationHandler(SpecifiedInterfaceResolveHandler.DEFINITION, SpecifiedInterfaceResolveHandler.INSTANCE);

        //server configurations
        hostRegistration.registerSubModel(new ServerConfigResourceDefinition(hostControllerInfo, serverInventory, pathManager, processState, environment.getDomainDataDir()));
        hostRegistration.registerSubModel(new StoppedServerResource(serverInventory));

        hostRegistration.registerSubModel(SocketBindingGroupResourceDefinition.INSTANCE);
    }
}
