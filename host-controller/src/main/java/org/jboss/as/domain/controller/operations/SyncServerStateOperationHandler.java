package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_CLIENT_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLAN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLANS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.domain.management.ModelDescriptionConstants.NAME;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.as.domain.controller.operations.coordination.ServerOperationResolver;
import org.jboss.as.domain.controller.operations.deployment.SyncModelParameters;
import org.jboss.as.host.controller.ManagedServerBootCmdFactory;
import org.jboss.as.host.controller.ManagedServerBootConfiguration;
import org.jboss.as.management.client.content.ManagedDMRContentTypeResource;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.server.deployment.ModelContentReference;
import org.jboss.as.server.operations.ServerProcessStateHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
class SyncServerStateOperationHandler implements OperationStepHandler {
    private final SyncModelParameters parameters;
    private final List<ModelNode> operations;

    private enum SyncServerResultAction {RESTART_REQUIRED, RELOAD_REQUIRED};

    public SyncServerStateOperationHandler(SyncModelParameters parameters, List<ModelNode> operations) {
        this.parameters = parameters;
        this.operations = operations;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        assert !context.isBooting() : "Should not be used when the context is booting";
        assert parameters.isFullModelTransfer() : "Should only be used during a full model transfer";

        final Resource startResource = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true);
        final ModelNode startRoot = Resource.Tools.readModel(startResource);
        final String localHostName = startResource.getChildrenNames(HOST).iterator().next();
        final ModelNode startHostModel = startRoot.require(HOST).asPropertyList().iterator().next().getValue();

        if (!startHostModel.hasDefined(SERVER_CONFIG)) {
            return;
        }

        final ServerOperationResolver resolver = new ServerOperationResolver(localHostName, parameters.getServerProxies());

        context.addStep(operation, new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final Resource domainRootResource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
                final ModelNode endRoot = Resource.Tools.readModel(domainRootResource);
                final ModelNode endHostModel = endRoot.require(HOST).asPropertyList().iterator().next().getValue();

                //Get the affected servers for each op.
                ContentDownloader contentDownloader = new ContentDownloader(startRoot, endRoot, endHostModel);
                final Map<String, SyncServerResultAction> servers =
                        determineServerStateChanges(context, domainRootResource, resolver, contentDownloader);

                for (String serverName : endHostModel.get(SERVER_CONFIG).keys()) {
                    // Compare boot cmd (requires restart)
                    SyncServerResultAction restart = servers.get(serverName);
                    if (restart == null || restart == SyncServerResultAction.RELOAD_REQUIRED) {
                        //In some unit tests the start config may be null
                        ManagedServerBootConfiguration startConfig =
                                new ManagedServerBootCmdFactory(serverName, startRoot, startHostModel,
                                        parameters.getHostControllerEnvironment(),
                                        parameters.getDomainController().getExpressionResolver(), false).createConfiguration();

                        ManagedServerBootConfiguration endConfig =
                                new ManagedServerBootCmdFactory(serverName, endRoot, endHostModel,
                                        parameters.getHostControllerEnvironment(),
                                        parameters.getDomainController().getExpressionResolver(), false).createConfiguration();
                        if (startConfig == null || !startConfig.compareServerLaunchCommand(endConfig)) {
                            servers.put(serverName, SyncServerResultAction.RESTART_REQUIRED);
                        }
                    }
                }

                for (Map.Entry<String, SyncServerResultAction> entry : servers.entrySet()) {
                    final PathAddress serverAddress =
                            PathAddress.pathAddress(HOST, localHostName).append(SERVER, entry.getKey());
                    final String opName = entry.getValue() == SyncServerResultAction.RESTART_REQUIRED ?
                            ServerProcessStateHandler.REQUIRE_RESTART_OPERATION : ServerProcessStateHandler.REQUIRE_RELOAD_OPERATION;
                    final OperationStepHandler handler = context.getResourceRegistration().getOperationHandler(serverAddress, opName);
                    final ModelNode op = Util.createEmptyOperation(opName, serverAddress);
                    context.addStep(op, handler, OperationContext.Stage.MODEL);
                }

                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                        if (resultAction == OperationContext.ResultAction.KEEP) {
                            for (ContentReference ref : contentDownloader.removedContent) {
                                parameters.getContentRepository().removeContent(ref);
                            }
                        }
                    }
                });
            }
        }, OperationContext.Stage.MODEL);
    }

    private Map<String, SyncServerResultAction> determineServerStateChanges(OperationContext context, Resource domainRootResource, ServerOperationResolver resolver,
                                                             ContentDownloader contentDownloader) {
        final Map<String, SyncServerResultAction> serverStateChanges = new HashMap<>();
        for (ModelNode operation : operations) {
            PathAddress addr = PathAddress.pathAddress(operation.get(OP_ADDR));
            contentDownloader.checkContent(operation, addr);

            Map<Set<ServerIdentity>, ModelNode> serverMap = resolver.getServerOperations(context, operation, addr);
            for (Map.Entry<Set<ServerIdentity>, ModelNode> entry : serverMap.entrySet()) {
                ModelNode op = entry.getValue();
                String opName = op.get(OP).asString();
                assert opName != null;
                boolean restart = false;

                // XXX debug operation.get(OP) vs opName
                // if we got REQUIRE_RESTART_OPERATION back, or the op.get(OP) doesn't match the operation.get(OP), flag for a restart.
                if (opName.equals(ServerProcessStateHandler.REQUIRE_RESTART_OPERATION) || (!operation.get(OP).asString().equals(opName))) {
                    restart = true;
                }

                // if we got back REQUIRE_RELOAD_OPERATION, trust that.
                if (!restart && !opName.equals(ServerProcessStateHandler.REQUIRE_RELOAD_OPERATION)) {
                    // otherwise we do an additional check, but only for changes within /profile=* with length > 1
                    // i.e. not the root profile.
                    if (addr.size() > 1 && addr.getElement(0).getKey().equals(PROFILE)) {
                        restart = checkOperationForRestartRequired(context, operation);
                    }
                }

                for (ServerIdentity id : entry.getKey()) {
                    String serverName = id.getServerName();
                    SyncServerResultAction existing = serverStateChanges.get(serverName);
                    if (existing == null || (existing == SyncServerResultAction.RELOAD_REQUIRED && restart)) {
                        serverStateChanges.put(serverName, restart ? SyncServerResultAction.RESTART_REQUIRED : SyncServerResultAction.RELOAD_REQUIRED);
                    }
                }
            }
        }
        Set<String> affectedServers = contentDownloader.pullDownContent(domainRootResource);
        for (String server : affectedServers) {
            if (!serverStateChanges.containsKey(server)) {
                serverStateChanges.put(server, SyncServerResultAction.RELOAD_REQUIRED);
            }
        }
        return serverStateChanges;
    }

    private boolean checkOperationForRestartRequired(final OperationContext context, final ModelNode operation) {
        boolean restart = false;
        final ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        // an example of this is toggling jts:
        // /profile=full/subsystem=transactions:write-attribute(name=jts, value=true)
        // previously on a reconnect to the DC the slave check would flag reload-required on a WRITE_ATTRIBUTE / UNDEFINE_ATTRIBUTE,
        // so would get flagged into reload-required, but it really should be restart-required to match what would happen doing it with the DC
        // online, and the registration of the jts attribute.
        final String opName = operation.get(OP).asString();
        if (WRITE_ATTRIBUTE_OPERATION.equals(opName) || UNDEFINE_ATTRIBUTE_OPERATION.equals(opName)) {
            // look up the attribute name we're writing, and check the flags to see if we need restart.
            final String attributeName = operation.get(NAME).asString();
            // look up if the attribute requires restart / reload
            if (registration.getAttributeAccess(address, attributeName).getFlags().contains(AttributeAccess.Flag.RESTART_JVM)) {
                restart = true;
            }
        } else { // all other ops
            final Set<OperationEntry.Flag> flags = registration.getOperationFlags(address, opName);
            if (flags.contains(OperationEntry.Flag.RESTART_JVM)) {
                restart = true;
            }
        }
        return restart;
    }

    private class ContentDownloader {
        private final ModelNode startRoot;
        private final ModelNode endRoot;
        private final Map<String, Set<String>> serversByGroup;
        private final Set<String> affectedGroups = new HashSet<>();
        private final Map<String, Set<ContentReference>> deploymentHashes = new HashMap<>();
        private final Set<String> relevantDeployments = new HashSet<String>();
        private final Set<ContentReference> requiredContent = new HashSet<>();

        private boolean updateRolloutPlans;
        private byte[] rolloutPlansHash;

        private List<ContentReference> removedContent = new ArrayList<>();

        ContentDownloader(ModelNode startRoot, ModelNode endRoot, ModelNode hostModel) {
            this.startRoot = startRoot;
            this.endRoot = endRoot;
            serversByGroup =  getOurServerGroups(hostModel);
        }

        void checkContent(ModelNode operation, PathAddress operationAddress) {
            if (!operation.get(OP).asString().equals(ADD) || operationAddress.size() == 0) {
                return;
            }
            final PathElement firstElement = operationAddress.getElement(0);
            final String contentType = firstElement.getKey();
            if (contentType == null) {
                return;
            }
            if (operationAddress.size() == 1) {
                switch (contentType) {
                    case DEPLOYMENT:
                        final String deployment = firstElement.getValue();
                        Set<ContentReference> hashes = deploymentHashes.get(deployment);
                        if (hashes == null) {
                            hashes = new HashSet<>();
                            deploymentHashes.put(deployment, hashes);
                            for (ModelNode contentItem : operation.get(CONTENT).asList()) {
                                hashes.add(ModelContentReference.fromModelAddress(operationAddress, contentItem.get(HASH).asBytes()));
                                if (parameters.getHostControllerEnvironment().isBackupDomainFiles()) {
                                    relevantDeployments.add(firstElement.getValue());
                                }
                            }
                        }
                        makeExistingDeploymentUpdatedAffected(firstElement, operation);
                        break;
                    case DEPLOYMENT_OVERLAY:
                        break;
                    case MANAGEMENT_CLIENT_CONTENT:
                        if (firstElement.getValue().equals(ROLLOUT_PLANS)) {
                            updateRolloutPlans = true;
                            //This needs special handling. Drop the existing resource and add a new one
                            if (operation.hasDefined(HASH)) {
                                rolloutPlansHash = operation.get(HASH).asBytes();
                                requiredContent.add(ModelContentReference.fromModelAddress(operationAddress, rolloutPlansHash));
                            }
                        }
                        break;
                    default:
                        return;
                }
            } else if (operationAddress.size() == 2) {
                if( firstElement.getKey().equals(SERVER_GROUP) &&
                    serversByGroup.containsKey(firstElement.getValue())) {
                    PathElement secondElement = operationAddress.getElement(1);
                    if (secondElement.getKey().equals(DEPLOYMENT)) {
                        relevantDeployments.add(secondElement.getValue());
                        affectedGroups.add(firstElement.getValue());
                    }
                } else if (firstElement.getKey().equals(DEPLOYMENT_OVERLAY)) {
                    requiredContent.add(ModelContentReference.fromModelAddress(operationAddress, operation.get(CONTENT).asBytes()));
                }
            }
            return;
        }

        private void makeExistingDeploymentUpdatedAffected(final PathElement deploymentElement, final ModelNode operation) {
            //Check if this is an existing deployment being updated
            //If so, check the hashes are different
            if (!startRoot.hasDefined(deploymentElement.getKey(), deploymentElement.getValue())) {
                return;
            }
            final ModelNode deployment = startRoot.get(deploymentElement.getKey(), deploymentElement.getValue());
            final List<ModelNode> currentContents = deployment.get(CONTENT).asList();
            final List<ModelNode> newContents = operation.get(CONTENT).asList();
            boolean changes = currentContents.size() != newContents.size();
            if (!changes) {
                final Set<byte[]> currentHashes = new HashSet<>();
                for (final ModelNode contentItem : currentContents) {
                    currentHashes.add(contentItem.get(HASH).asBytes());
                }
                for (ModelNode contentItem : newContents) {
                    if (!currentHashes.contains(contentItem.get(HASH).asBytes())) {
                        changes = true;
                        break;
                    }
                }
            }

            if (changes) {
                //There are changes, add all server groups using this deployment to the affectedGroups
                if (endRoot.hasDefined(SERVER_GROUP)) {
                    for (final Property serverGroup : endRoot.get(SERVER_GROUP).asPropertyList()) {
                        if (serverGroup.getValue().hasDefined(deploymentElement.getKey(), deploymentElement.getValue())) {
                            affectedGroups.add(serverGroup.getName());
                            relevantDeployments.add(deploymentElement.getValue());
                        }
                    }
                }
            }
        }

        Set<String> pullDownContent(final Resource domainRootResource) {
            // Make sure we have all needed deployment and management client content
            for (final String id : relevantDeployments) {
                final Set<ContentReference> hashes = deploymentHashes.remove(id);
                if (hashes != null) {
                    requiredContent.addAll(hashes);
                }
            }
            for (final ContentReference reference : requiredContent) {
                parameters.getFileRepository().getDeploymentFiles(reference);
                parameters.getContentRepository().addContentReference(reference);
            }

            if (updateRolloutPlans) {
                final PathElement rolloutPlansElement = PathElement.pathElement(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS);
                final Resource existing = domainRootResource.removeChild(rolloutPlansElement);
                if (existing != null) {
                    final ModelNode hashNode = existing.getModel().get(HASH);
                    if (hashNode.isDefined()) {
                        removedContent.add(
                                new ContentReference(PathAddress.pathAddress(rolloutPlansElement).toCLIStyleString(), hashNode.asBytes()));
                    }
                }
                ManagedDMRContentTypeResource rolloutPlansResource =
                        new ManagedDMRContentTypeResource(PathAddress.pathAddress(rolloutPlansElement), ROLLOUT_PLAN,
                                rolloutPlansHash, parameters.getContentRepository());
                domainRootResource.registerChild(rolloutPlansElement, rolloutPlansResource);
            }

            final Set<String> servers = new HashSet<>();
            for (String group : affectedGroups) {
                if (serversByGroup.containsKey(group)) {
                    servers.addAll(serversByGroup.get(group));
                }
            }
            return servers;
        }

        private Map<String, Set<String>> getOurServerGroups(final ModelNode hostModel) {
            final Map<String, Set<String>> result = new HashMap<>();
            if (hostModel.hasDefined(SERVER_CONFIG)) {
                for (final Property config : hostModel.get(SERVER_CONFIG).asPropertyList()) {
                    final String group = config.getValue().get(GROUP).asString();
                    Set<String> servers = result.get(group);
                    if (servers == null) {
                        servers = new HashSet<>();
                        result.put(group, servers);
                    }
                    servers.add(config.getName());

                }
            }
            return result;
        }
    }

}
