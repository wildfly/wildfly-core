/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.controller.resources;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESTROY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.KILL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESUME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STOP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND;
import static org.jboss.as.controller.services.path.PathResourceDefinition.PATH_CAPABILITY;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.BootErrorCollector;
import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.NoopOperationStepHandler;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.ExtensionRegistryType;
import org.jboss.as.controller.extension.ExtensionResourceDefinition;
import org.jboss.as.controller.extension.MutableRootResourceRegistrationProvider;
import org.jboss.as.controller.operations.common.ConfigurationPublishHandler;
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
import org.jboss.as.controller.operations.common.XmlFileMarshallingHandler;
import org.jboss.as.controller.operations.common.XmlMarshallingHandler;
import org.jboss.as.controller.operations.global.GlobalInstallationReportHandler;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.operations.global.ReadConfigAsFeaturesOperationHandler;
import org.jboss.as.controller.operations.global.ReadFeatureDescriptionHandler;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.persistence.ExtensibleConfigurationPersister;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.RuntimePackageDependency;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management.audit.EnvironmentNameReader;
import org.jboss.as.domain.management.security.WhoAmIOperation;
import org.jboss.as.platform.mbean.PlatformMBeanResourceRegistrar;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.DeployerChainAddHandler;
import org.jboss.as.server.DomainServerCommunicationServices;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.ServerEnvironment.LaunchType;
import org.jboss.as.server.ServerEnvironmentResourceDescription;
import org.jboss.as.server.Services;
import org.jboss.as.server.controller.descriptions.ServerDescriptionConstants;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.deployment.DeploymentFullReplaceHandler;
import org.jboss.as.server.deployment.DeploymentReplaceHandler;
import org.jboss.as.server.deployment.DeploymentUploadBytesHandler;
import org.jboss.as.server.deployment.DeploymentUploadStreamAttachmentHandler;
import org.jboss.as.server.deployment.DeploymentUploadURLHandler;
import org.jboss.as.server.deploymentoverlay.DeploymentOverlayDefinition;
import org.jboss.as.server.mgmt.HttpManagementResourceDefinition;
import org.jboss.as.server.mgmt.NativeManagementResourceDefinition;
import org.jboss.as.server.mgmt.NativeRemotingManagementResourceDefinition;
import org.jboss.as.server.operations.CleanObsoleteContentHandler;
import org.jboss.as.server.operations.InstallationReportHandler;
import org.jboss.as.server.operations.InstanceUuidReadHandler;
import org.jboss.as.server.operations.LaunchTypeHandler;
import org.jboss.as.server.operations.ProcessTypeHandler;
import org.jboss.as.server.operations.RunningModeReadHandler;
import org.jboss.as.server.operations.ServerDomainProcessReloadHandler;
import org.jboss.as.server.operations.ServerDomainProcessShutdownHandler;
import org.jboss.as.server.operations.ServerProcessReloadHandler;
import org.jboss.as.server.operations.ServerProcessStateHandler;
import org.jboss.as.server.operations.ServerResumeHandler;
import org.jboss.as.server.operations.ServerShutdownHandler;
import org.jboss.as.server.operations.ServerSuspendHandler;
import org.jboss.as.server.operations.ServerVersionOperations.DefaultEmptyListAttributeHandler;
import org.jboss.as.server.operations.SetServerGroupHostHandler;
import org.jboss.as.server.operations.SuspendStateReadHandler;
import org.jboss.as.server.operations.WriteConfigHandler;
import org.jboss.as.server.services.net.InterfaceResourceDefinition;
import org.jboss.as.server.services.net.NetworkInterfaceRuntimeHandler;
import org.jboss.as.server.services.net.SocketBindingGroupResourceDefinition;
import org.jboss.as.server.services.net.SpecifiedInterfaceAddHandler;
import org.jboss.as.server.services.net.SpecifiedInterfaceRemoveHandler;
import org.jboss.as.server.services.net.SpecifiedInterfaceResolveHandler;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ServerRootResourceDefinition extends SimpleResourceDefinition {

    public static final String WILDFLY_EE_API = "wildflyee.api";

    private static final ParameterValidator NOT_NULL_STRING_LENGTH_ONE_VALIDATOR = new StringLengthValidator(1, false, false);

    public static final AttributeDefinition NAMESPACES = new PropertiesAttributeDefinition.Builder(ModelDescriptionConstants.NAMESPACES, false)
                .build();

    public static final AttributeDefinition SCHEMA_LOCATIONS = new PropertiesAttributeDefinition.Builder(ModelDescriptionConstants.SCHEMA_LOCATIONS, false)
            .build();

    public static final SimpleAttributeDefinition NAME = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.NAME, ModelType.STRING, true)
            .setValidator(new StringLengthValidator(1, true))
            .build();
    public static final SimpleAttributeDefinition UUID = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.UUID, ModelType.STRING, false)
            .setValidator(new StringLengthValidator(1, true))
            .setStorageRuntime()
            .build();
    public static final SimpleAttributeDefinition ORGANIZATION_IDENTIFIER = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.ORGANIZATION, ModelType.STRING, true)
            .setValidator(new StringLengthValidator(1, true))
            .build();

    public static final SimpleAttributeDefinition SERVER_GROUP = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.SERVER_GROUP, ModelType.STRING)
            .build();

    public static final SimpleAttributeDefinition HOST = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.HOST, ModelType.STRING)
            .build();

    public static final SimpleAttributeDefinition RELEASE_VERSION = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.RELEASE_VERSION, ModelType.STRING, false)
            .setValidator(NOT_NULL_STRING_LENGTH_ONE_VALIDATOR)
            .build();
    public static final SimpleAttributeDefinition RELEASE_CODENAME = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.RELEASE_CODENAME, ModelType.STRING, false)
            .setValidator(NOT_NULL_STRING_LENGTH_ONE_VALIDATOR)
            .build();
    public static final SimpleAttributeDefinition PRODUCT_NAME = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.PRODUCT_NAME, ModelType.STRING, true)
            .setValidator(NOT_NULL_STRING_LENGTH_ONE_VALIDATOR)
            .build();
    public static final SimpleAttributeDefinition PRODUCT_VERSION = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.PRODUCT_VERSION, ModelType.STRING, true)
            .setValidator(NOT_NULL_STRING_LENGTH_ONE_VALIDATOR)
            .build();
    public static final SimpleAttributeDefinition MANAGEMENT_MAJOR_VERSION = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION, ModelType.INT, false)
            .setValidator(new IntRangeValidator(1))
            .build();
    public static final SimpleAttributeDefinition MANAGEMENT_MINOR_VERSION = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.MANAGEMENT_MINOR_VERSION, ModelType.INT, false)
            .setValidator(new IntRangeValidator(0))
            .build();
    public static final SimpleAttributeDefinition MANAGEMENT_MICRO_VERSION = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.MANAGEMENT_MICRO_VERSION, ModelType.INT, false)
            .setValidator(new IntRangeValidator(0))
            .build();
    public static final SimpleAttributeDefinition PROFILE_NAME = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.PROFILE_NAME, ModelType.STRING, false)
            .setValidator(NOT_NULL_STRING_LENGTH_ONE_VALIDATOR)
            .build();
    public static final SimpleAttributeDefinition NULL_PROFILE_NAME = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.PROFILE_NAME, ModelType.STRING, true)
            .build();
    // replaces SERVER_STATE below, aliased in #ProcessStateAttributeHandler
    public static final SimpleAttributeDefinition RUNTIME_CONFIGURATION_STATE = SimpleAttributeDefinitionBuilder.create(ServerDescriptionConstants.RUNTIME_CONFIGURATION_STATE, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setValidator(NOT_NULL_STRING_LENGTH_ONE_VALIDATOR)
            .build();
    public static final SimpleAttributeDefinition SERVER_STATE = SimpleAttributeDefinitionBuilder.create(ServerDescriptionConstants.PROCESS_STATE, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setValidator(NOT_NULL_STRING_LENGTH_ONE_VALIDATOR)
            .build();
    public static final SimpleAttributeDefinition PROCESS_TYPE = SimpleAttributeDefinitionBuilder.create(ServerDescriptionConstants.PROCESS_TYPE, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setValidator(NOT_NULL_STRING_LENGTH_ONE_VALIDATOR)
            .build();
    public static final SimpleAttributeDefinition LAUNCH_TYPE = SimpleAttributeDefinitionBuilder.create(ServerDescriptionConstants.LAUNCH_TYPE, ModelType.STRING)
            .setValidator(EnumValidator.create(LaunchType.class))
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    public static final AttributeDefinition RUNNING_MODE = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.RUNNING_MODE, ModelType.STRING)
            .setValidator(EnumValidator.create(RunningMode.class))
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    public static final AttributeDefinition SUSPEND_STATE = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.SUSPEND_STATE, ModelType.STRING)
            .setValidator(EnumValidator.create(SuspendController.State.class))
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    /** The 'blocking' parameter for domain server lifecycle ops executed on the HC */
    public static final AttributeDefinition BLOCKING = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.BLOCKING, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    /** The 'start-mode' parameter for domain server lifecycle ops executed on the HC */
    public static final AttributeDefinition START_MODE = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.START_MODE, ModelType.STRING)
            .setRequired(false)
            .setDefaultValue(new ModelNode(ModelDescriptionConstants.NORMAL))
            .setValidator(EnumValidator.create(StartMode.class))
            .build();

    /**
     * The 'timeout' parameter for server lifecycle ops
     * @deprecated Since Version 9.0.0, use suspend-timeout instead.
     */
    @Deprecated(forRemoval = false) // false because this @Deprected is just to help us remember to remove this some day. We don't want IDE errors about it.
    public static final SimpleAttributeDefinition TIMEOUT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.TIMEOUT, ModelType.INT)
            .setDefaultValue(ModelNode.ZERO)
            .setRequired(false)
            .setMeasurementUnit(MeasurementUnit.SECONDS)
            .setAlternatives(ModelDescriptionConstants.SUSPEND_TIMEOUT)
            .setDeprecated(ModelVersion.create(9, 0))
            .build();

    /**
     * The 'suspend-timeout' parameter for server lifecycle ops
     */
    public static final AttributeDefinition SUSPEND_TIMEOUT = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.SUSPEND_TIMEOUT, ModelType.INT)
            .setDefaultValue(ModelNode.ZERO)
            .setRequired(false)
            .setMeasurementUnit(MeasurementUnit.SECONDS)
            .setAlternatives(ModelDescriptionConstants.TIMEOUT)
            .build();

    private final boolean isDomain;
    private final ContentRepository contentRepository;
    private final ExtensibleConfigurationPersister extensibleConfigurationPersister;
    private final ServerEnvironment serverEnvironment;
    private final ControlledProcessState processState;
    private final RunningModeControl runningModeControl;
    private final ExtensionRegistry extensionRegistry;
    private final boolean parallelBoot;
    private final PathManagerService pathManager;
    private final DomainServerCommunicationServices.OperationIDUpdater operationIDUpdater;
    private final DelegatingConfigurableAuthorizer authorizer;
    private final ManagementSecurityIdentitySupplier securityIdentitySupplier;
    private final ManagedAuditLogger auditLogger;
    private final CapabilityRegistry capabilityRegistry;
    private final MutableRootResourceRegistrationProvider rootResourceRegistrationProvider;
    private final BootErrorCollector bootErrorCollector;

    public ServerRootResourceDefinition(
            final ContentRepository contentRepository,
            final ExtensibleConfigurationPersister extensibleConfigurationPersister,
            final ServerEnvironment serverEnvironment,
            final ControlledProcessState processState,
            final RunningModeControl runningModeControl,
            final ExtensionRegistry extensionRegistry,
            final boolean parallelBoot,
            final PathManagerService pathManager,
            final DomainServerCommunicationServices.OperationIDUpdater operationIDUpdater,
            final DelegatingConfigurableAuthorizer authorizer,
            final ManagementSecurityIdentitySupplier securityIdentitySupplier,
            final ManagedAuditLogger auditLogger,
            final MutableRootResourceRegistrationProvider rootResourceRegistrationProvider,
            final BootErrorCollector bootErrorCollector,
            final CapabilityRegistry capabilityRegistry) {
        super(new Parameters(ResourceRegistration.root(), ServerDescriptions.getResourceDescriptionResolver(SERVER, false))
                .addCapabilities(PATH_CAPABILITY.fromBaseCapability(ServerEnvironment.HOME_DIR),
                        PATH_CAPABILITY.fromBaseCapability(ServerEnvironment.SERVER_BASE_DIR),
                        PATH_CAPABILITY.fromBaseCapability(ServerEnvironment.SERVER_CONFIG_DIR),
                        PATH_CAPABILITY.fromBaseCapability(ServerEnvironment.SERVER_DATA_DIR),
                        PATH_CAPABILITY.fromBaseCapability(ServerEnvironment.SERVER_LOG_DIR),
                        PATH_CAPABILITY.fromBaseCapability(ServerEnvironment.SERVER_TEMP_DIR),
                        PATH_CAPABILITY.fromBaseCapability(ServerEnvironment.CONTROLLER_TEMP_DIR))
                        .setFeature(true));
        this.contentRepository = contentRepository;
        this.extensibleConfigurationPersister = extensibleConfigurationPersister;
        this.serverEnvironment = serverEnvironment;
        this.processState = processState;
        this.runningModeControl = runningModeControl;
        this.extensionRegistry = extensionRegistry;
        this.parallelBoot = parallelBoot;
        this.pathManager = pathManager;
        this.operationIDUpdater = operationIDUpdater;
        this.auditLogger = auditLogger;
        this.capabilityRegistry = capabilityRegistry;

        this.isDomain = serverEnvironment == null || serverEnvironment.getLaunchType() == LaunchType.DOMAIN;
        this.authorizer = authorizer;
        this.securityIdentitySupplier = securityIdentitySupplier;
        this.rootResourceRegistrationProvider = rootResourceRegistrationProvider;
        this.bootErrorCollector = bootErrorCollector;
    }

    @Override
    public void registerCapabilities(ManagementResourceRegistration resourceRegistration) {
        super.registerCapabilities(resourceRegistration);
        // In the domain mode add a few more paths
        if(serverEnvironment.getLaunchType() == ServerEnvironment.LaunchType.DOMAIN) {
            if(serverEnvironment.getDomainBaseDir() != null) {
                resourceRegistration.registerCapability(PATH_CAPABILITY.fromBaseCapability(ServerEnvironment.DOMAIN_BASE_DIR));
            }
            if(serverEnvironment.getDomainConfigurationDir() != null) {
                resourceRegistration.registerCapability(PATH_CAPABILITY.fromBaseCapability(ServerEnvironment.DOMAIN_CONFIG_DIR));
            }
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        GlobalOperationHandlers.registerGlobalOperations(resourceRegistration, ProcessType.STANDALONE_SERVER);
        resourceRegistration.registerOperationHandler(ReadFeatureDescriptionHandler.DEFINITION, ReadFeatureDescriptionHandler.getInstance(capabilityRegistry), true);
        resourceRegistration.registerOperationHandler(ReadConfigAsFeaturesOperationHandler.DEFINITION, new ReadConfigAsFeaturesOperationHandler(), true);

        resourceRegistration.registerOperationHandler(ValidateOperationHandler.DEFINITION, ValidateOperationHandler.INSTANCE);

        // Other root resource operations
        resourceRegistration.registerOperationHandler(CompositeOperationHandler.DEFINITION, CompositeOperationHandler.INSTANCE, false);

        SimpleOperationDefinitionBuilder xmlMarshallingHandlerBuilder = XmlMarshallingHandler.createOperationDefinitionBuilder();
        if(resourceRegistration.enables(XmlFileMarshallingHandler.DEFINITION)) {
            xmlMarshallingHandlerBuilder.setDeprecated(ModelVersion.create(24, 0, 0 ));
        }
        resourceRegistration.registerOperationHandler(xmlMarshallingHandlerBuilder.build(), new XmlMarshallingHandler(extensibleConfigurationPersister));
        resourceRegistration.registerOperationHandler(XmlFileMarshallingHandler.DEFINITION, new XmlFileMarshallingHandler(extensibleConfigurationPersister));
        resourceRegistration.registerOperationHandler(NamespaceAddHandler.DEFINITION, NamespaceAddHandler.INSTANCE);
        resourceRegistration.registerOperationHandler(NamespaceRemoveHandler.DEFINITION, NamespaceRemoveHandler.INSTANCE);
        resourceRegistration.registerOperationHandler(SchemaLocationAddHandler.DEFINITION, SchemaLocationAddHandler.INSTANCE);
        resourceRegistration.registerOperationHandler(SchemaLocationRemoveHandler.DEFINITION, SchemaLocationRemoveHandler.INSTANCE);
        resourceRegistration.registerOperationHandler(ValidateAddressOperationHandler.DEFINITION, ValidateAddressOperationHandler.INSTANCE, false);

        DeploymentUploadBytesHandler.register(resourceRegistration, contentRepository);
        DeploymentUploadURLHandler.register(resourceRegistration, contentRepository);
        DeploymentUploadStreamAttachmentHandler.register(resourceRegistration, contentRepository);
        resourceRegistration.registerOperationHandler(DeploymentAttributes.REPLACE_DEPLOYMENT_DEFINITION, DeploymentReplaceHandler.create(contentRepository));
        resourceRegistration.registerOperationHandler(DeploymentAttributes.FULL_REPLACE_DEPLOYMENT_DEFINITION, DeploymentFullReplaceHandler.create(contentRepository));

        if (!isDomain) {
            if(serverEnvironment.useGit()) {
                resourceRegistration.registerOperationHandler(ConfigurationPublishHandler.DEFINITION,
                        new ConfigurationPublishHandler(extensibleConfigurationPersister));
            }
            SnapshotDeleteHandler snapshotDelete = new SnapshotDeleteHandler(extensibleConfigurationPersister);
            resourceRegistration.registerOperationHandler(SnapshotDeleteHandler.DEFINITION, snapshotDelete);
            SnapshotListHandler snapshotList = new SnapshotListHandler(extensibleConfigurationPersister);
            resourceRegistration.registerOperationHandler(SnapshotListHandler.DEFINITION, snapshotList);
            SnapshotTakeHandler snapshotTake = new SnapshotTakeHandler(extensibleConfigurationPersister);
            resourceRegistration.registerOperationHandler(SnapshotTakeHandler.DEFINITION, snapshotTake);
            resourceRegistration.registerOperationHandler(WriteConfigHandler.DEFINITION, WriteConfigHandler.INSTANCE);
        }

        // Ops for internal control of the server by the HC
        if (isDomain) {
            resourceRegistration.registerOperationHandler(ServerProcessStateHandler.RELOAD_DEFINITION, ServerProcessStateHandler.SET_RELOAD_REQUIRED_HANDLER);
            resourceRegistration.registerOperationHandler(SetServerGroupHostHandler.DEFINITION, SetServerGroupHostHandler.INSTANCE);
        }
        // Keep the set-restart-required for backwards compatibility
        resourceRegistration.registerOperationHandler(ServerProcessStateHandler.RESTART_DEFINITION, ServerProcessStateHandler.SET_RESTART_REQUIRED_HANDLER);

        resourceRegistration.registerOperationHandler(ResolveExpressionHandler.DEFINITION, ResolveExpressionHandler.INSTANCE, false);

        resourceRegistration.registerOperationHandler(SpecifiedInterfaceResolveHandler.DEFINITION, SpecifiedInterfaceResolveHandler.INSTANCE);
        resourceRegistration.registerOperationHandler(WhoAmIOperation.DEFINITION, WhoAmIOperation.createOperation(authorizer), true);
        resourceRegistration.registerOperationHandler(GlobalInstallationReportHandler.DEFINITION, GlobalInstallationReportHandler.INSTANCE, false);
        resourceRegistration.registerOperationHandler(InstallationReportHandler.DEFINITION, InstallationReportHandler.createOperation(serverEnvironment), false);
        resourceRegistration.registerOperationHandler(CleanObsoleteContentHandler.DEFINITION, CleanObsoleteContentHandler.createOperation(contentRepository), false);

        // Lifecycle ops
        if (isDomain) {
            // WFCORE-3804 Include OperationDefinition data for the ops on the /host=*/server=* resource
            // that are handled directly by the HC. Just register a no-op handler as these ops are
            // not actually executed in the domain server process. We're just getting the operation
            // description info recorded so /host=x/server=y:read-resource-description etc, which are executed
            // in this process, can report it.
            //
            // We don't register 'start' because for this code to even run the user already started the server
            // and we don't need to offer 'start' any more
            resourceRegistration.registerOperationHandler(getDomainServerLifecycleDefinition(STOP, ModelType.STRING, null, BLOCKING, TIMEOUT, SUSPEND_TIMEOUT), NoopOperationStepHandler.WITHOUT_RESULT);
            resourceRegistration.registerOperationHandler(getDomainServerLifecycleDefinition(RESTART, ModelType.STRING, null, BLOCKING, START_MODE), NoopOperationStepHandler.WITHOUT_RESULT);
            resourceRegistration.registerOperationHandler(getDomainServerLifecycleDefinition(DESTROY, null, null), NoopOperationStepHandler.WITHOUT_RESULT);
            resourceRegistration.registerOperationHandler(getDomainServerLifecycleDefinition(KILL, null, null), NoopOperationStepHandler.WITHOUT_RESULT);

            // For other lifecycle ops, we register functioning handlers. The OperationDefinition for the ops
            // however reflects what the HC /host=x/server=y resource exposes, not necessarily what these
            // handlers process.
            final ServerDomainProcessReloadHandler reloadHandler = new ServerDomainProcessReloadHandler(Services.JBOSS_AS, runningModeControl, processState, operationIDUpdater, serverEnvironment);
            resourceRegistration.registerOperationHandler(getDomainServerLifecycleDefinition(RELOAD, ModelType.STRING, null, BLOCKING, START_MODE), reloadHandler);

            resourceRegistration.registerOperationHandler(getDomainServerLifecycleDefinition(SUSPEND, null, null, TIMEOUT, SUSPEND_TIMEOUT), ServerSuspendHandler.INSTANCE);

            resourceRegistration.registerOperationHandler(getDomainServerLifecycleDefinition(RESUME, null, null), ServerResumeHandler.INSTANCE);

            // This one is completely private, only for use by the HC
            resourceRegistration.registerOperationHandler(ServerDomainProcessShutdownHandler.DOMAIN_DEFINITION, new ServerDomainProcessShutdownHandler());

        } else {


            ServerProcessReloadHandler.registerStandardReloadOperation(resourceRegistration, runningModeControl, processState, serverEnvironment, extensibleConfigurationPersister);
            ServerProcessReloadHandler.registerEnhancedReloadOperation(resourceRegistration, runningModeControl, processState, serverEnvironment, extensibleConfigurationPersister);

            resourceRegistration.registerOperationHandler(ServerSuspendHandler.DEFINITION, ServerSuspendHandler.INSTANCE);
            resourceRegistration.registerOperationHandler(ServerResumeHandler.DEFINITION, ServerResumeHandler.INSTANCE);
        }

        // The System.exit() based shutdown command is only valid for a server process directly launched from the command line
        if (serverEnvironment.getLaunchType() == ServerEnvironment.LaunchType.STANDALONE) {
            ServerShutdownHandler serverShutdownHandler = new ServerShutdownHandler(processState, serverEnvironment);
            resourceRegistration.registerOperationHandler(ServerShutdownHandler.DEFINITION, serverShutdownHandler);
        }

    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        if (isDomain) {
            resourceRegistration.registerReadOnlyAttribute(NAME, serverEnvironment.getProcessNameReadHandler());
            resourceRegistration.registerReadWriteAttribute(PROFILE_NAME, null, ModelOnlyWriteAttributeHandler.INSTANCE);
        } else {
            resourceRegistration.registerReadWriteAttribute(NAME, serverEnvironment.getProcessNameReadHandler(), serverEnvironment.getProcessNameWriteHandler());
            // The legacy "undefined" profile-name
            resourceRegistration.registerReadOnlyAttribute(NULL_PROFILE_NAME, null);
            resourceRegistration.registerReadWriteAttribute(ORGANIZATION_IDENTIFIER, null, ModelOnlyWriteAttributeHandler.INSTANCE);
        }
        resourceRegistration.registerReadOnlyAttribute(LAUNCH_TYPE, new LaunchTypeHandler(serverEnvironment.getLaunchType()));

        resourceRegistration.registerReadOnlyAttribute(RUNTIME_CONFIGURATION_STATE, new ProcessStateAttributeHandler(processState));
        resourceRegistration.registerReadOnlyAttribute(SERVER_STATE, new ProcessStateAttributeHandler(processState));
        resourceRegistration.registerReadOnlyAttribute(PROCESS_TYPE, ProcessTypeHandler.INSTANCE);
        resourceRegistration.registerReadOnlyAttribute(RUNNING_MODE, new RunningModeReadHandler(runningModeControl));
        resourceRegistration.registerReadOnlyAttribute(SUSPEND_STATE, SuspendStateReadHandler.INSTANCE);
        resourceRegistration.registerReadOnlyAttribute(UUID, new InstanceUuidReadHandler(serverEnvironment));


        resourceRegistration.registerReadOnlyAttribute(MANAGEMENT_MAJOR_VERSION, null);
        resourceRegistration.registerReadOnlyAttribute(MANAGEMENT_MINOR_VERSION, null);
        resourceRegistration.registerReadOnlyAttribute(MANAGEMENT_MICRO_VERSION, null);

        resourceRegistration.registerReadOnlyAttribute(RELEASE_VERSION, null);
        resourceRegistration.registerReadOnlyAttribute(RELEASE_CODENAME, null);

        resourceRegistration.registerReadOnlyAttribute(PRODUCT_NAME, null);
        resourceRegistration.registerReadOnlyAttribute(PRODUCT_VERSION, null);

        resourceRegistration.registerReadOnlyAttribute(NAMESPACES, DefaultEmptyListAttributeHandler.INSTANCE);
        resourceRegistration.registerReadOnlyAttribute(SCHEMA_LOCATIONS, DefaultEmptyListAttributeHandler.INSTANCE);

        if (isDomain) {
            resourceRegistration.registerReadOnlyAttribute(HOST, null);
            resourceRegistration.registerReadOnlyAttribute(SERVER_GROUP, null);
        }
    }

    @Override
    public void registerNotifications(ManagementResourceRegistration resourceRegistration) {
        GlobalNotifications.registerGlobalNotifications(resourceRegistration, ProcessType.STANDALONE_SERVER);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {

        // Server environment
        resourceRegistration.registerSubModel(ServerEnvironmentResourceDescription.of(serverEnvironment));

        // System Properties
        resourceRegistration.registerSubModel(SystemPropertyResourceDefinition.createForStandaloneServer(serverEnvironment));

        // Central Management
        // Start with the base /core-service=management MNR. The Resource for this is added by ServerService itself, so there is no add/remove op handlers
        final EnvironmentNameReader environmentReader = new EnvironmentNameReader() {
            public boolean isServer() {
                return true;
            }

            public String getServerName() {
                return serverEnvironment.getServerName();
            }

            public String getHostName() {
                return serverEnvironment.getHostControllerName();
            }


            public String getProductName() {
                if (serverEnvironment.getProductConfig() != null && serverEnvironment.getProductConfig().getProductName() != null) {
                    return serverEnvironment.getProductConfig().getProductName();
                }
                return null;
            }
        };
        final ResourceDefinition managementDefinition;
        if (isDomain) {
            managementDefinition = CoreManagementResourceDefinition.forDomainServer(authorizer, securityIdentitySupplier, auditLogger, pathManager, environmentReader, bootErrorCollector);
        } else {
            managementDefinition = CoreManagementResourceDefinition.forStandaloneServer(authorizer, securityIdentitySupplier, auditLogger, pathManager, environmentReader, bootErrorCollector,
                    NativeManagementResourceDefinition.INSTANCE, NativeRemotingManagementResourceDefinition.INSTANCE,
                    HttpManagementResourceDefinition.INSTANCE);
        }
        resourceRegistration.registerSubModel(managementDefinition);

        // Other core services
        resourceRegistration.registerSubModel(new ServiceContainerResourceDefinition());

        //module loading
        resourceRegistration.registerSubModel(ModuleLoadingResourceDefinition.INSTANCE);

        // Platform MBeans
        PlatformMBeanResourceRegistrar.registerPlatformMBeanResources(resourceRegistration);

        // Paths
        // TODO why not resolvable?
        resourceRegistration.registerSubModel(PathResourceDefinition.createSpecified(pathManager));

        //capability registry
        resourceRegistration.registerSubModel(new CapabilityRegistryResourceDefinition(capabilityRegistry));

        // Interfaces
        ManagementResourceRegistration interfaces = resourceRegistration.registerSubModel(new InterfaceResourceDefinition(
                SpecifiedInterfaceAddHandler.INSTANCE,
                SpecifiedInterfaceRemoveHandler.INSTANCE,
                true,
                false
        ));
        interfaces.registerReadOnlyAttribute(NetworkInterfaceRuntimeHandler.RESOLVED_ADDRESS, NetworkInterfaceRuntimeHandler.INSTANCE);
        interfaces.registerOperationHandler(SpecifiedInterfaceResolveHandler.DEFINITION, SpecifiedInterfaceResolveHandler.INSTANCE);

        resourceRegistration.registerSubModel(SocketBindingGroupResourceDefinition.INSTANCE);

        // Deployments
        ManagementResourceRegistration deployments = resourceRegistration.registerSubModel(ServerDeploymentResourceDefinition.create(contentRepository, serverEnvironment));

        //deployment overlays
        resourceRegistration.registerSubModel(new DeploymentOverlayDefinition(false, contentRepository, null));

        // The sub-deployments registry
        deployments.registerSubModel(ServerSubDeploymentResourceDefinition.create());

        // Extensions
        resourceRegistration.registerSubModel(new ExtensionResourceDefinition(extensionRegistry, parallelBoot, ExtensionRegistryType.SERVER, rootResourceRegistrationProvider));
        extensionRegistry.setPathManager(pathManager);

        // Util
        resourceRegistration.registerOperationHandler(DeployerChainAddHandler.DEFINITION, DeployerChainAddHandler.INSTANCE, false);
    }

    public static OperationDefinition getDomainServerLifecycleDefinition(String name, ModelType replyType,
                                                                         ModelVersion deprecatedSince, AttributeDefinition... parameters) {
        SimpleOperationDefinitionBuilder builder = new SimpleOperationDefinitionBuilder(name, ServerDescriptions.getResourceDescriptionResolver(RUNNING_SERVER))
                .setRuntimeOnly()
                .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY);
        for (AttributeDefinition param : parameters) {
            builder.addParameter(param);
        }
        if (replyType != null) {
            builder.setReplyType(replyType);
        }
        if (deprecatedSince != null) {
            builder.setDeprecated(deprecatedSince);
        }
        return builder.build();
    }

    /**
     * Renames the deprecated attribute 'timeout' by 'suspend-timeout' for the current operation.
     * <p>
     * If the 'timeout' attribute is found in the operation and 'suspend-timeout' is not being used, the value of this
     * 'timeout' is used as the 'suspend-timeout' and the 'timeout' attribute is removed from the operation.
     *
     * @param operation The current operation
     */
    public static void renameTimeoutToSuspendTimeout(ModelNode operation) {
        if (!operation.hasDefined(SUSPEND_TIMEOUT.getName()) && operation.hasDefined(TIMEOUT.getName())) {
            operation.get(SUSPEND_TIMEOUT.getName()).set(operation.get(TIMEOUT.getName()));
            operation.remove(TIMEOUT.getName());
        }
    }

    private enum StartMode {
        NORMAL(ModelDescriptionConstants.NORMAL),
        SUSPEND(ModelDescriptionConstants.SUSPEND);

        private final String localName;

        StartMode(String localName) {
            this.localName = localName;
        }

        @Override
        public String toString() {
            return localName;
        }
    }

    @Override
    public void registerAdditionalRuntimePackages(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerAdditionalRuntimePackages(
                RuntimePackageDependency.optional(WILDFLY_EE_API)
        );
    }
}
