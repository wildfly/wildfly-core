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

package org.jboss.as.host.controller.resources;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESTROY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.KILL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESUME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STOP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.BLOCKING;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.START_MODE;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.SUSPEND_TIMEOUT;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.TIMEOUT;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.getDomainServerLifecycleDefinition;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.NoopOperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.domain.controller.operations.SocketBindingGroupResourceDefinition;
import org.jboss.as.domain.controller.resources.ServerGroupResourceDefinition;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.as.host.controller.model.jvm.JvmResourceDefinition;
import org.jboss.as.host.controller.operations.ServerAddHandler;
import org.jboss.as.host.controller.operations.ServerProcessHandlers;
import org.jboss.as.host.controller.operations.ServerReloadHandler;
import org.jboss.as.host.controller.operations.ServerRemoveHandler;
import org.jboss.as.host.controller.operations.ServerRestartHandler;
import org.jboss.as.host.controller.operations.ServerRestartRequiredServerConfigWriteAttributeHandler;
import org.jboss.as.host.controller.operations.ServerResumeHandler;
import org.jboss.as.host.controller.operations.ServerStartHandler;
import org.jboss.as.host.controller.operations.ServerStatusHandler;
import org.jboss.as.host.controller.operations.ServerStopHandler;
import org.jboss.as.host.controller.operations.ServerSuspendHandler;
import org.jboss.as.server.controller.resources.SystemPropertyResourceDefinition;
import org.jboss.as.server.controller.resources.SystemPropertyResourceDefinition.Location;
import org.jboss.as.server.services.net.InterfaceResourceDefinition;
import org.jboss.as.server.services.net.SpecifiedInterfaceAddHandler;
import org.jboss.as.server.services.net.SpecifiedInterfaceRemoveHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a {@code server-config} resource under a host.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ServerConfigResourceDefinition extends SimpleResourceDefinition {

    static final String SERVER_CONFIG_CAPABILITY_NAME = "org.wildfly.domain.server-config";
    public static final RuntimeCapability SERVER_CONFIG_CAPABILITY = RuntimeCapability.Builder.of(SERVER_CONFIG_CAPABILITY_NAME, true)
            .build();

    public static final AttributeDefinition NAME = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.NAME, ModelType.STRING).setResourceOnly().build();

    public static final SimpleAttributeDefinition AUTO_START = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.AUTO_START, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE).build();

    public static final SimpleAttributeDefinition UPDATE_AUTO_START_WITH_SERVER_STATUS = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.UPDATE_AUTO_START_WITH_SERVER_STATUS, ModelType.BOOLEAN, true)
            .setAllowExpression(false)
            .setDefaultValue(ModelNode.FALSE).build();

    public static final SimpleAttributeDefinition SOCKET_BINDING_GROUP = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.SOCKET_BINDING_GROUP, ModelType.STRING, true)
            .setCapabilityReference(SocketBindingGroupResourceDefinition.SOCKET_BINDING_GROUP_CAPABILITY_NAME, SERVER_CONFIG_CAPABILITY_NAME)
            .build();

    public static final SimpleAttributeDefinition SOCKET_BINDING_DEFAULT_INTERFACE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SOCKET_BINDING_DEFAULT_INTERFACE, ModelType.STRING, true)
            .setAllowExpression(false)
            .setXmlName(Attribute.DEFAULT_INTERFACE.getLocalName())
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, true))
            .setCapabilityReference("org.wildfly.network.interface", SERVER_CONFIG_CAPABILITY)
            .build();

    public static final SimpleAttributeDefinition SOCKET_BINDING_PORT_OFFSET = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.SOCKET_BINDING_PORT_OFFSET, ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.ZERO)
            .setXmlName(Attribute.PORT_OFFSET.getLocalName())
            .setValidator(new IntRangeValidator(-65535, 65535, true, true))
            .build();

    public static final SimpleAttributeDefinition GROUP = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.GROUP, ModelType.STRING)
            .setCapabilityReference(ServerGroupResourceDefinition.SERVER_GROUP_CAPABILITY_NAME, SERVER_CONFIG_CAPABILITY_NAME)
            .build();

    public static final SimpleAttributeDefinition STATUS = SimpleAttributeDefinitionBuilder.create(ServerStatusHandler.ATTRIBUTE_NAME, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setValidator(new EnumValidator<ServerStatus>(ServerStatus.class, false, false))
            .build();

    /**
     * Bogus attribute that we accidentally registered in AS 7.1.2/EAP 6 even though it didn't appear in the
     * resource description. So for compatibility we register it here as well, and include it in the description
     * to be consistent and to avoid having to do hacks just to not register it.
     */
    public static final AttributeDefinition PRIORITY  = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.PRIORITY, ModelType.INT, true)
            .setDeprecated(ModelVersion.create(1,4))
            .build();
    /**
     * Bogus attribute that we accidentally registered in AS 7.1.2/EAP 6 even though it didn't appear in the
     * resource description. So for compatibility we register it here as well, and include it in the description
     * to be consistent and to avoid having to do hacks just to not register it.
     */
    public static final AttributeDefinition CPU_AFFINITY  = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.CPU_AFFINITY, ModelType.STRING, true)
            .setDeprecated(ModelVersion.create(1,4))
            .build();

    /** The attributes that can be written by the {@code add} operation */
    public static final List<SimpleAttributeDefinition> WRITABLE_ATTRIBUTES = Arrays.asList(AUTO_START, UPDATE_AUTO_START_WITH_SERVER_STATUS, SOCKET_BINDING_GROUP, SOCKET_BINDING_DEFAULT_INTERFACE, SOCKET_BINDING_PORT_OFFSET, GROUP);

    private final ServerInventory serverInventory;
    private final PathManagerService pathManager;

    /**
     * Creates a ServerConfigResourceDefinition.
     * @param hostControllerInfo
     * @param serverInventory  the server inventory to use for runtime server lifecycle operations. May be {@code null}
     *                         in which case no such operations will be registered
     * @param pathManager the {@link PathManagerService} to use for the child {@code path} resources. Cannot be {@code null}
     */
    public ServerConfigResourceDefinition(final LocalHostControllerInfo hostControllerInfo, final ServerInventory serverInventory, final PathManagerService pathManager, final ControlledProcessState processState, final File domainDataDir) {
        super(new SimpleResourceDefinition.Parameters(PathElement.pathElement(SERVER_CONFIG), HostResolver.getResolver(SERVER_CONFIG, false))
                .setAddHandler(ServerAddHandler.create(hostControllerInfo, serverInventory, processState, domainDataDir))
                .setRemoveHandler(ServerRemoveHandler.INSTANCE)
                .addCapabilities(SERVER_CONFIG_CAPABILITY));

        assert pathManager != null : "pathManager is null";

        this.serverInventory = serverInventory;
        this.pathManager = pathManager;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {

        resourceRegistration.registerReadOnlyAttribute(NAME, ReadResourceNameOperationStepHandler.INSTANCE);

        resourceRegistration.registerReadWriteAttribute(AUTO_START, null, new ModelOnlyWriteAttributeHandler(AUTO_START));
        resourceRegistration.registerReadWriteAttribute(UPDATE_AUTO_START_WITH_SERVER_STATUS, null, new ModelOnlyWriteAttributeHandler(UPDATE_AUTO_START_WITH_SERVER_STATUS));
        resourceRegistration.registerReadWriteAttribute(SOCKET_BINDING_GROUP, null, ServerRestartRequiredServerConfigWriteAttributeHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(SOCKET_BINDING_DEFAULT_INTERFACE, null, ServerRestartRequiredServerConfigWriteAttributeHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(SOCKET_BINDING_PORT_OFFSET, null, ServerRestartRequiredServerConfigWriteAttributeHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(GROUP, null, ServerRestartRequiredServerConfigWriteAttributeHandler.INSTANCE);

        // For compatibility, register these should-be-removed attributes, with no-op handlers
        resourceRegistration.registerReadWriteAttribute(PRIORITY, NoopOperationStepHandler.WITH_RESULT, NoopOperationStepHandler.WITHOUT_RESULT);
        resourceRegistration.registerReadWriteAttribute(CPU_AFFINITY, NoopOperationStepHandler.WITH_RESULT, NoopOperationStepHandler.WITHOUT_RESULT);

        if (serverInventory != null) {
            resourceRegistration.registerMetric(STATUS, new ServerStatusHandler(serverInventory));
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);

        if (serverInventory != null) {
            registerServerLifecycleOperations(resourceRegistration, serverInventory);
        }
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {

        //server paths
        resourceRegistration.registerSubModel(PathResourceDefinition.createSpecifiedNoServices());

        resourceRegistration.registerSubModel(new InterfaceResourceDefinition(
                SpecifiedInterfaceAddHandler.INSTANCE,
                SpecifiedInterfaceRemoveHandler.INSTANCE,
                true,
                false
        ));

        // Server system properties
        resourceRegistration.registerSubModel(SystemPropertyResourceDefinition.createForDomainOrHost(Location.SERVER_CONFIG));
        // Server jvm
        resourceRegistration.registerSubModel(JvmResourceDefinition.SERVER);
        // ssl=loopback
        resourceRegistration.registerSubModel(new SslLoopbackResourceDefinition());
    }

    public static void registerServerLifecycleOperations(final ManagementResourceRegistration resourceRegistration, final ServerInventory serverInventory) {
        // Lifecycle ops are deprecated on the server-config resource but not on server
        boolean serverConfig = resourceRegistration.getPathAddress().getLastElement().getKey().equals(SERVER_CONFIG);
        // Mark the lifecycle ops on the server-config resources as deprecated.
        // Some already were deprecated in version 7, so use a different deprecated version for those
        ModelVersion deprecatedVersion = serverConfig ? ModelVersion.create(9) : null;
        ModelVersion existingDeprecatedVersion = serverConfig ? ModelVersion.create(7) : null;

        resourceRegistration.registerOperationHandler(
                getDomainServerLifecycleDefinition(START, ModelType.STRING, deprecatedVersion, BLOCKING, START_MODE),
                new ServerStartHandler(serverInventory));
        resourceRegistration.registerOperationHandler(
                getDomainServerLifecycleDefinition(RESTART, ModelType.STRING, deprecatedVersion, BLOCKING, START_MODE),
                new ServerRestartHandler(serverInventory));
        resourceRegistration.registerOperationHandler(
                getDomainServerLifecycleDefinition(STOP, ModelType.STRING, deprecatedVersion, BLOCKING, TIMEOUT, SUSPEND_TIMEOUT),
                new ServerStopHandler(serverInventory));
        resourceRegistration.registerOperationHandler(
                getDomainServerLifecycleDefinition(RELOAD, ModelType.STRING, existingDeprecatedVersion, BLOCKING, START_MODE),
                new ServerReloadHandler(serverInventory));
        resourceRegistration.registerOperationHandler(
                getDomainServerLifecycleDefinition(SUSPEND, null, existingDeprecatedVersion, TIMEOUT, SUSPEND_TIMEOUT),
                new ServerSuspendHandler(serverInventory));
        resourceRegistration.registerOperationHandler(
                getDomainServerLifecycleDefinition(RESUME, null, existingDeprecatedVersion),
                new ServerResumeHandler(serverInventory));
        resourceRegistration.registerOperationHandler(
                getDomainServerLifecycleDefinition(DESTROY, null, deprecatedVersion),
                new ServerProcessHandlers.ServerDestroyHandler(serverInventory));
        resourceRegistration.registerOperationHandler(
                getDomainServerLifecycleDefinition(KILL, null, deprecatedVersion),
                new ServerProcessHandlers.ServerKillHandler(serverInventory));
    }

}
