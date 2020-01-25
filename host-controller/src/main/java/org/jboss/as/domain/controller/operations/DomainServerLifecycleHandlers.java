/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESTROY_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.KILL_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESUME_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STOP_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND_SERVERS;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.domain.controller.resources.DomainResolver;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.process.ProcessInfo;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * An operation handler for the :stop-servers, :restart-servers and :start-servers commands. This belongs in the
 * domain model but needs access to the server inventory which is initialized when setting up the host model.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class DomainServerLifecycleHandlers {

    private static final AttributeDefinition BLOCKING = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.BLOCKING, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    private static final AttributeDefinition START_MODE = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.START_MODE, ModelType.STRING, true)
            .setDefaultValue(new ModelNode(StartMode.NORMAL.toString()))
            .setValidator(new EnumValidator<>(StartMode.class, true, true))
            .build();

    @Deprecated
    private static final AttributeDefinition TIMEOUT = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.TIMEOUT, ModelType.INT, true)
            .setMeasurementUnit(MeasurementUnit.SECONDS)
            .setDefaultValue(ModelNode.ZERO)
            .setAlternatives(ModelDescriptionConstants.SUSPEND_TIMEOUT)
            .setDeprecated(ModelVersion.create(9, 0))
            .build();

    private static final AttributeDefinition SUSPEND_TIMEOUT = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.SUSPEND_TIMEOUT, ModelType.INT, true)
            .setMeasurementUnit(MeasurementUnit.SECONDS)
            .setAlternatives(ModelDescriptionConstants.TIMEOUT)
            .setDefaultValue(ModelNode.ZERO)
            .build();

    private static final AttributeDefinition HOST_SUSPEND_TIMEOUT = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.SUSPEND_TIMEOUT, ModelType.INT, true)
            .setMeasurementUnit(MeasurementUnit.SECONDS)
            .setDefaultValue(ModelNode.ZERO)
            .build();

    public static final String RESTART_SERVERS_NAME = RESTART_SERVERS;
    public static final String START_SERVERS_NAME = START_SERVERS;
    public static final String STOP_SERVERS_NAME = STOP_SERVERS;
    public static final String SUSPEND_SERVERS_NAME = SUSPEND_SERVERS;
    public static final String RESUME_SERVERS_NAME = RESUME_SERVERS;
    public static final String KILL_SERVERS_NAME = KILL_SERVERS;
    public static final String DESTROY_SERVERS_NAME = DESTROY_SERVERS;

    public static void initializeServerInventory(ServerInventory serverInventory) {
        StopServersLifecycleHandler.INSTANCE.setServerInventory(serverInventory);
        StartServersLifecycleHandler.INSTANCE.setServerInventory(serverInventory);
        RestartServersLifecycleHandler.INSTANCE.setServerInventory(serverInventory);
        ReloadServersLifecycleHandler.INSTANCE.setServerInventory(serverInventory);
        SuspendServersLifecycleHandler.INSTANCE.setServerInventory(serverInventory);
        ResumeServersLifecycleHandler.INSTANCE.setServerInventory(serverInventory);
        KillServersLifecycleHandler.INSTANCE.setServerInventory(serverInventory);
        DestroyServersLifecycleHandler.INSTANCE.setServerInventory(serverInventory);
        HostSuspendServersLifecycleHandler.INSTANCE.setServerInventory(serverInventory);
        HostResumeServersLifecycleHandler.INSTANCE.setServerInventory(serverInventory);
    }

    public static void registerDomainHandlers(ManagementResourceRegistration registration) {
        registerHandlers(registration, false);
    }

    public static void registerServerGroupHandlers(ManagementResourceRegistration registration) {
        registerHandlers(registration, true);
    }

    public static void registerHostHandlers(ManagementResourceRegistration registration) {
        registration.registerOperationHandler(getHostSuspendOperationDefinition(HostSuspendServersLifecycleHandler.OPERATION_NAME), HostSuspendServersLifecycleHandler.INSTANCE);
        registration.registerOperationHandler(getHostResumeOperationDefinition(HostResumeServersLifecycleHandler.OPERATION_NAME), HostResumeServersLifecycleHandler.INSTANCE);
    }

    private static void registerHandlers(ManagementResourceRegistration registration, boolean serverGroup) {
        registration.registerOperationHandler(getStopOperationDefinition(serverGroup, StopServersLifecycleHandler.OPERATION_NAME), StopServersLifecycleHandler.INSTANCE);
        registration.registerOperationHandler(getOperationDefinition(serverGroup, StartServersLifecycleHandler.OPERATION_NAME), StartServersLifecycleHandler.INSTANCE);
        registration.registerOperationHandler(getOperationDefinition(serverGroup, RestartServersLifecycleHandler.OPERATION_NAME), RestartServersLifecycleHandler.INSTANCE);
        registration.registerOperationHandler(getOperationDefinition(serverGroup, ReloadServersLifecycleHandler.OPERATION_NAME), ReloadServersLifecycleHandler.INSTANCE);
        registration.registerOperationHandler(getSuspendOperationDefinition(serverGroup, SuspendServersLifecycleHandler.OPERATION_NAME), SuspendServersLifecycleHandler.INSTANCE);
        registration.registerOperationHandler(getSuspendOperationDefinition(serverGroup, ResumeServersLifecycleHandler.OPERATION_NAME), ResumeServersLifecycleHandler.INSTANCE);
        if ( serverGroup ){
            registration.registerOperationHandler( getKillDestroyOperationDefinition(KillServersLifecycleHandler.OPERATION_NAME), KillServersLifecycleHandler.INSTANCE);
            registration.registerOperationHandler( getKillDestroyOperationDefinition(DestroyServersLifecycleHandler.OPERATION_NAME), DestroyServersLifecycleHandler.INSTANCE);
        }
    }

    private static OperationDefinition getKillDestroyOperationDefinition(String operationName) {
        return new SimpleOperationDefinitionBuilder(operationName,
                DomainResolver.getResolver(ModelDescriptionConstants.SERVER_GROUP))
                .setRuntimeOnly()
                .build();
    }

    private static OperationDefinition getOperationDefinition(boolean serverGroup, String operationName) {
        return new SimpleOperationDefinitionBuilder(operationName,
                DomainResolver.getResolver(serverGroup ? ModelDescriptionConstants.SERVER_GROUP : ModelDescriptionConstants.DOMAIN))
                .addParameter(BLOCKING)
                .addParameter(START_MODE)
                .setRuntimeOnly()
                .build();
    }

    private static OperationDefinition getStopOperationDefinition(boolean serverGroup, String operationName) {
        return new SimpleOperationDefinitionBuilder(operationName,
                DomainResolver.getResolver(serverGroup ? ModelDescriptionConstants.SERVER_GROUP : ModelDescriptionConstants.DOMAIN))
                .addParameter(BLOCKING)
                .addParameter(TIMEOUT)
                .addParameter(SUSPEND_TIMEOUT)
                .setRuntimeOnly()
                .build();
    }


    private static OperationDefinition getSuspendOperationDefinition(boolean serverGroup, String operationName) {
        SimpleOperationDefinitionBuilder builder = new SimpleOperationDefinitionBuilder(operationName,
                DomainResolver.getResolver(serverGroup ? ModelDescriptionConstants.SERVER_GROUP : ModelDescriptionConstants.DOMAIN))
                .setRuntimeOnly();
        if (operationName.equals(SUSPEND_SERVERS_NAME)) {
            builder.setParameters(TIMEOUT)
                    .addParameter(SUSPEND_TIMEOUT);
        }
        return builder.build();
    }

    private static OperationDefinition getHostSuspendOperationDefinition(String operationName) {
        SimpleOperationDefinitionBuilder builder = new SimpleOperationDefinitionBuilder(operationName, HostResolver.getResolver(HOST))
                .addParameter(HOST_SUSPEND_TIMEOUT)
                .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
                .setRuntimeOnly();

        return builder.build();
    }

    private static OperationDefinition getHostResumeOperationDefinition(String operationName) {
        SimpleOperationDefinitionBuilder builder = new SimpleOperationDefinitionBuilder(operationName, HostResolver.getResolver(HOST))
                .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
                .setRuntimeOnly();

        return builder.build();
    }

    private abstract static class AbstractHackLifecycleHandler implements OperationStepHandler {
        volatile ServerInventory serverInventory;

        protected AbstractHackLifecycleHandler() {
        }

        /**
         * To be called when setting up the host model
         */
        void setServerInventory(ServerInventory serverInventory) {
            this.serverInventory = serverInventory;
        }

        String getServerGroupName(final ModelNode operation) {
            final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
            if (address.size() == 0) {
                return null;
            }
            return address.getLastElement().getValue();
        }

        Set<String> getServersForGroup(final ModelNode model, final String groupName) {
            if (groupName == null) {
                return Collections.emptySet();
            }
            final String hostName = model.get(HOST).keys().iterator().next();
            final ModelNode serverConfig = model.get(HOST, hostName).get(SERVER_CONFIG);
            if (!serverConfig.isDefined()) {
                return Collections.emptySet();
            }
            final Set<String> servers = new HashSet<String>();
            for (Property config : serverConfig.asPropertyList()) {
                if (groupName.equals(config.getValue().get(GROUP).asString())) {
                    servers.add(config.getName());
                }
            }
            return servers;
        }

        Set<String> getServersForHost(final Resource hostResource) {
            final Set<String> servers = new HashSet<>();
            final ModelNode hostModel = Resource.Tools.readModel(hostResource);
            final ModelNode serverConfig = hostModel.get(SERVER_CONFIG);
            if (serverConfig.isDefined()) {
                for (Property config : serverConfig.asPropertyList()) {
                    servers.add(config.getName());
                }
            }
            return servers;
        }
    }

    private static class StopServersLifecycleHandler extends AbstractHackLifecycleHandler {
        static final String OPERATION_NAME = STOP_SERVERS_NAME;
        static final StopServersLifecycleHandler INSTANCE = new StopServersLifecycleHandler();

        @Override
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            context.acquireControllerLock();
            context.readResource(PathAddress.EMPTY_ADDRESS, false);
            renameTimeoutToSuspendTimeout(operation);

            final String group = getServerGroupName(operation);
            final boolean blocking = BLOCKING.resolveModelAttribute(context, operation).asBoolean();
            final int suspendTimeout = SUSPEND_TIMEOUT.resolveModelAttribute(context, operation).asInt();

            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    // Even though we don't read from the service registry, we are modifying a service
                    context.getServiceRegistry(true);
                    if (group != null) {
                        final Set<String> waitForServers = new HashSet<String>();
                        final ModelNode model = Resource.Tools.readModel(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true));
                        for (String server : getServersForGroup(model, group)) {
                            serverInventory.stopServer(server, suspendTimeout);
                            waitForServers.add(server);
                        }
                        if (blocking) {
                            serverInventory.awaitServersState(waitForServers, false);
                        }
                    } else {
                        serverInventory.stopServers(suspendTimeout, blocking);
                    }
                    context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                }
            }, Stage.RUNTIME);
        }
    }

    private static class StartServersLifecycleHandler extends AbstractHackLifecycleHandler {
        static final String OPERATION_NAME = START_SERVERS_NAME;
        static final StartServersLifecycleHandler INSTANCE = new StartServersLifecycleHandler();

        @Override
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            if (context.getRunningMode() == RunningMode.ADMIN_ONLY) {
                throw new OperationFailedException(HostControllerLogger.ROOT_LOGGER.cannotStartServersInvalidMode(context.getRunningMode()));
            }
            context.acquireControllerLock();
            context.readResource(PathAddress.EMPTY_ADDRESS, false);
            final ModelNode model = Resource.Tools.readModel(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true));
            final String group = getServerGroupName(operation);
            final boolean blocking = BLOCKING.resolveModelAttribute(context, operation).asBoolean();
            final boolean suspend = START_MODE.resolveModelAttribute(context, operation).asString().toLowerCase(Locale.ENGLISH).equals(StartMode.SUSPEND.toString());
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    final String hostName = model.get(HOST).keys().iterator().next();
                    final ModelNode serverConfig = model.get(HOST, hostName).get(SERVER_CONFIG);
                    final Set<String> serversInGroup = getServersForGroup(model, group);
                    final Set<String> waitForServers = new HashSet<String>();
                    if (serverConfig.isDefined()) {
                        // Even though we don't read from the service registry, we are modifying a service
                        context.getServiceRegistry(true);
                        for (Property config : serverConfig.asPropertyList()) {
                            final ServerStatus status = serverInventory.determineServerStatus(config.getName());
                            if (status != ServerStatus.STARTING && status != ServerStatus.STARTED) {
                                if (group == null || serversInGroup.contains(config.getName())) {
                                    if (status != ServerStatus.STOPPED) {
                                        serverInventory.stopServer(config.getName(), 0);
                                    }
                                    serverInventory.startServer(config.getName(), model, false, suspend);
                                    waitForServers.add(config.getName());
                                }
                            }
                        }
                        if (blocking) {
                            serverInventory.awaitServersState(waitForServers, true);
                        }
                    }
                    context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                }
            }, Stage.RUNTIME);
        }
    }

    private static class RestartServersLifecycleHandler extends AbstractHackLifecycleHandler {
        static final String OPERATION_NAME = RESTART_SERVERS_NAME;
        static final RestartServersLifecycleHandler INSTANCE = new RestartServersLifecycleHandler();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.acquireControllerLock();
            context.readResource(PathAddress.EMPTY_ADDRESS, false);
            final ModelNode model = Resource.Tools.readModel(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true));
            final String group = getServerGroupName(operation);
            final boolean blocking = BLOCKING.resolveModelAttribute(context, operation).asBoolean();
            final boolean suspend = START_MODE.resolveModelAttribute(context, operation).asString().toLowerCase(Locale.ENGLISH).equals(StartMode.SUSPEND.toString());
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    // Even though we don't read from the service registry, we are modifying a service
                    context.getServiceRegistry(true);
                    Map<String, ProcessInfo> processes = serverInventory.determineRunningProcesses(true);
                    final Set<String> serversInGroup = getServersForGroup(model, group);
                    final Set<String> waitForServers = new HashSet<String>();
                    for (String serverName : processes.keySet()) {
                        final String serverModelName = serverInventory.getProcessServerName(serverName);
                        if (group == null || serversInGroup.contains(serverModelName)) {
                            serverInventory.restartServer(serverModelName, 0, model, false, suspend);
                            waitForServers.add(serverModelName);
                        }
                    }
                    if (blocking) {
                        serverInventory.awaitServersState(waitForServers, true);
                    }
                    context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                }
            }, Stage.RUNTIME);
        }

    }

    private static class ReloadServersLifecycleHandler extends AbstractHackLifecycleHandler {
        static final String OPERATION_NAME = RELOAD_SERVERS;
        static final ReloadServersLifecycleHandler INSTANCE = new ReloadServersLifecycleHandler();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.acquireControllerLock();
            context.readResource(PathAddress.EMPTY_ADDRESS, false);
            final ModelNode model = Resource.Tools.readModel(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true));
            final String group = getServerGroupName(operation);
            final boolean blocking = BLOCKING.resolveModelAttribute(context, operation).asBoolean();
            final boolean suspend = START_MODE.resolveModelAttribute(context, operation).asString().toLowerCase(Locale.ENGLISH).equals(StartMode.SUSPEND.toString());
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    // Even though we don't read from the service registry, we are modifying a service
                    context.getServiceRegistry(true);
                    Map<String, ProcessInfo> processes = serverInventory.determineRunningProcesses(true);
                    final Set<String> serversInGroup = getServersForGroup(model, group);
                    final Set<String> waitForServers = new HashSet<String>();
                    for (String serverName : processes.keySet()) {
                        final String serverModelName = serverInventory.getProcessServerName(serverName);
                        if (group == null || serversInGroup.contains(serverModelName)) {
                            serverInventory.reloadServer(serverModelName, false, suspend);
                            waitForServers.add(serverModelName);
                        }
                    }
                    if (blocking) {
                        serverInventory.awaitServersState(waitForServers, true);
                    }
                    context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                }
            }, Stage.RUNTIME);
        }

    }

    private static class SuspendServersLifecycleHandler extends AbstractHackLifecycleHandler {
        static final String OPERATION_NAME = SUSPEND_SERVERS_NAME;
        static final SuspendServersLifecycleHandler INSTANCE = new SuspendServersLifecycleHandler();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.acquireControllerLock();
            context.readResource(PathAddress.EMPTY_ADDRESS, false);
            renameTimeoutToSuspendTimeout(operation);

            final ModelNode model = Resource.Tools.readModel(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true));
            final String group = getServerGroupName(operation);
            final int suspendTimeout = SUSPEND_TIMEOUT.resolveModelAttribute(context, operation).asInt();
            final BlockingTimeout blockingTimeout = BlockingTimeout.Factory.getProxyBlockingTimeout(context);


            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    // Even though we don't read from the service registry, we are modifying a service
                    context.getServiceRegistry(true);
                    Map<String, ProcessInfo> processes = serverInventory.determineRunningProcesses(true);
                    final Set<String> serversInGroup = getServersForGroup(model, group);
                    final Set<String> waitForServers = new HashSet<>();
                    for (String serverName : processes.keySet()) {
                        final String serverModelName = serverInventory.getProcessServerName(serverName);
                        if (group == null || serversInGroup.contains(serverModelName)) {
                            waitForServers.add(serverModelName);
                        }
                    }
                    final List<ModelNode> errorResponses  = serverInventory.suspendServers(waitForServers, suspendTimeout, blockingTimeout);
                    if ( !errorResponses.isEmpty() ){
                        context.getFailureDescription().set(errorResponses);
                    }
                    context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                }
            }, Stage.RUNTIME);
        }
    }

    private static class ResumeServersLifecycleHandler extends AbstractHackLifecycleHandler {
        static final String OPERATION_NAME = RESUME_SERVERS_NAME;
        static final ResumeServersLifecycleHandler INSTANCE = new ResumeServersLifecycleHandler();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.acquireControllerLock();
            context.readResource(PathAddress.EMPTY_ADDRESS, false);
            final ModelNode model = Resource.Tools.readModel(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true));
            final String group = getServerGroupName(operation);
            final BlockingTimeout blockingTimeout = BlockingTimeout.Factory.getProxyBlockingTimeout(context);

            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    // Even though we don't read from the service registry, we are modifying a service
                    context.getServiceRegistry(true);
                    Map<String, ProcessInfo> processes = serverInventory.determineRunningProcesses(true);
                    final Set<String> serversInGroup = getServersForGroup(model, group);
                    final Set<String> waitForServers = new HashSet<String>();
                    for (String serverName : processes.keySet()) {
                        final String serverModelName = serverInventory.getProcessServerName(serverName);
                        if (group == null || serversInGroup.contains(serverModelName)) {
                            waitForServers.add(serverModelName);
                        }
                    }
                    final List<ModelNode> errorResponses = serverInventory.resumeServers(waitForServers, blockingTimeout);
                    if ( !errorResponses.isEmpty() ){
                        context.getFailureDescription().set(errorResponses);
                    }
                    context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                }
            }, Stage.RUNTIME);
        }
    }

    private static class KillServersLifecycleHandler extends AbstractHackLifecycleHandler {
        static final String OPERATION_NAME = KILL_SERVERS_NAME;
        static final KillServersLifecycleHandler INSTANCE = new KillServersLifecycleHandler();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.acquireControllerLock();
            context.readResource(PathAddress.EMPTY_ADDRESS, false);
            final ModelNode model = Resource.Tools.readModel(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true));
            final String group = getServerGroupName(operation);

            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    context.authorize(operation, EnumSet.of(Action.ActionEffect.WRITE_RUNTIME)).failIfDenied(operation);
                    context.completeStep(new OperationContext.ResultHandler() {
                        @Override
                        public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                            Map<String, ProcessInfo> processes = serverInventory.determineRunningProcesses(true);
                            final Set<String> serversInGroup = getServersForGroup(model, group);
                            final Set<String> serversToKill = new HashSet<>();
                            for (String serverName : processes.keySet()) {
                                final String serverModelName = serverInventory.getProcessServerName(serverName);
                                if (group == null || serversInGroup.contains(serverModelName)) {
                                    serversToKill.add(serverModelName);
                                }
                            }
                            for (String s : serversToKill) {
                                serverInventory.killServer(s);
                            }
                            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                        }
                    });
                }
            }, Stage.RUNTIME);
        }
    }

    private static class DestroyServersLifecycleHandler extends AbstractHackLifecycleHandler {
        static final String OPERATION_NAME = DESTROY_SERVERS_NAME;
        static final DestroyServersLifecycleHandler INSTANCE = new DestroyServersLifecycleHandler();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.acquireControllerLock();
            context.readResource(PathAddress.EMPTY_ADDRESS, false);
            final ModelNode model = Resource.Tools.readModel(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true));
            final String group = getServerGroupName(operation);

            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    context.authorize(operation, EnumSet.of(Action.ActionEffect.WRITE_RUNTIME)).failIfDenied(operation);
                    context.completeStep(new OperationContext.ResultHandler() {
                        @Override
                        public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                            Map<String, ProcessInfo> processes = serverInventory.determineRunningProcesses(true);
                            final Set<String> serversInGroup = getServersForGroup(model, group);
                            final Set<String> serversToDestroy = new HashSet<>();
                            for (String serverName : processes.keySet()) {
                                final String serverModelName = serverInventory.getProcessServerName(serverName);
                                if (group == null || serversInGroup.contains(serverModelName)) {
                                    serversToDestroy.add(serverModelName);
                                }
                            }
                            for (String s : serversToDestroy) {
                                serverInventory.destroyServer(s);
                            }
                            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                        }
                    });
                }
            }, Stage.RUNTIME);
        }
    }

    private static class HostSuspendServersLifecycleHandler extends AbstractHackLifecycleHandler {
        static final String OPERATION_NAME = SUSPEND_SERVERS_NAME;
        static final HostSuspendServersLifecycleHandler INSTANCE = new HostSuspendServersLifecycleHandler();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.acquireControllerLock();
            final Resource hostResource = context.readResource(PathAddress.EMPTY_ADDRESS);
            final int suspendTimeout = HOST_SUSPEND_TIMEOUT.resolveModelAttribute(context, operation).asInt();
            final BlockingTimeout blockingTimeout = BlockingTimeout.Factory.getProxyBlockingTimeout(context);

            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    context.getServiceRegistry(true);
                    Set<String> servers = getServersForHost(hostResource);
                    final List<ModelNode> errorResponses = serverInventory.suspendServers(servers, suspendTimeout, blockingTimeout);
                    if (!errorResponses.isEmpty()) {
                        context.getFailureDescription().set(errorResponses);
                    }
                    context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    private static class HostResumeServersLifecycleHandler extends AbstractHackLifecycleHandler {
        static final String OPERATION_NAME = RESUME_SERVERS_NAME;
        static final HostResumeServersLifecycleHandler INSTANCE = new HostResumeServersLifecycleHandler();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.acquireControllerLock();
            final Resource hostResource = context.readResource(PathAddress.EMPTY_ADDRESS);
            final BlockingTimeout blockingTimeout = BlockingTimeout.Factory.getProxyBlockingTimeout(context);

            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    context.getServiceRegistry(true);
                    Set<String> servers = getServersForHost(hostResource);
                    final List<ModelNode> errorResponses = serverInventory.resumeServers(servers, blockingTimeout);
                    if (!errorResponses.isEmpty()) {
                        context.getFailureDescription().set(errorResponses);
                    }
                    context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    public static void registerServerLifeCycleOperationsTransformers(ResourceTransformationDescriptionBuilder builder) {
        builder.addOperationTransformationOverride(SUSPEND_SERVERS)
                .setReject()
                .end()
                .discardOperations(RESUME_SERVERS) //If the legacy slave was not able to suspend a server, then nothing is suspended and the "resume" can be interpreted as having worked.
                .addOperationTransformationOverride(STOP_SERVERS)
                .addRename(SUSPEND_TIMEOUT, TIMEOUT.getName())
                .addRejectCheck(RejectAttributeChecker.DEFINED, TIMEOUT)
                .addRejectCheck(RejectAttributeChecker.DEFINED, SUSPEND_TIMEOUT)
                .end();
    }

    public static void registerSuspendedStartTransformers(ResourceTransformationDescriptionBuilder builder) {
        builder.addOperationTransformationOverride(START_SERVERS)
                .setDiscard(DiscardAttributeChecker.DEFAULT_VALUE, START_MODE)
                .addRejectCheck(RejectAttributeChecker.DEFINED, START_MODE)
                .end()
                .addOperationTransformationOverride(RELOAD_SERVERS)
                .setDiscard(DiscardAttributeChecker.DEFAULT_VALUE, START_MODE)
                .addRejectCheck(RejectAttributeChecker.DEFINED, START_MODE)
                .end()
                .addOperationTransformationOverride(RESTART_SERVERS)
                .setDiscard(DiscardAttributeChecker.DEFAULT_VALUE, START_MODE)
                .addRejectCheck(RejectAttributeChecker.DEFINED, START_MODE)
                .end();

    }

    public static void registerKillDestroyTransformers(ResourceTransformationDescriptionBuilder builder) {
        builder.addOperationTransformationOverride(KILL_SERVERS)
                .setReject()
                .end()
                .addOperationTransformationOverride(DESTROY_SERVERS)
                .setReject()
                .end();
    }

    public static void registerTimeoutToSuspendTimeoutRename(ResourceTransformationDescriptionBuilder builder) {
        builder.addOperationTransformationOverride(SUSPEND_SERVERS)
                .addRename(SUSPEND_TIMEOUT, TIMEOUT.getName())
                .end()
                .addOperationTransformationOverride(STOP_SERVERS)
                .addRename(SUSPEND_TIMEOUT, TIMEOUT.getName())
                .end();
    }

    enum StartMode {
        NORMAL("normal"),
        SUSPEND("suspend");

        private final String localName;

        StartMode(String localName) {
            this.localName = localName;
        }

        @Override
        public String toString() {
            return localName;
        }
    }

    private static void renameTimeoutToSuspendTimeout(ModelNode operation) {
        if (!operation.hasDefined(SUSPEND_TIMEOUT.getName()) && operation.hasDefined(TIMEOUT.getName())) {
            operation.get(SUSPEND_TIMEOUT.getName()).set(operation.get(TIMEOUT.getName()));
            operation.remove(TIMEOUT.getName());
        }
    }
}
