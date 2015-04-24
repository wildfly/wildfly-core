package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.as.domain.controller.operations.coordination.ServerOperationResolver;
import org.jboss.as.domain.controller.operations.deployment.SyncModelParameters;
import org.jboss.as.host.controller.ManagedServerBootCmdFactory;
import org.jboss.as.host.controller.ManagedServerBootConfiguration;
import org.jboss.as.server.operations.ServerProcessStateHandler;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
class SyncServerStateOperationHandler implements OperationStepHandler {
    private final SyncModelParameters parameters;
    private final List<ModelNode> operations;

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

        //Get the affected servers for each op.
        final Map<String, Boolean> servers = determineServerStateChanges(context, resolver);

        context.addStep(operation, new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final Resource domainRootResource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
                final ModelNode endRoot = Resource.Tools.readModel(domainRootResource);
                final ModelNode endHostModel = endRoot.require(HOST).asPropertyList().iterator().next().getValue();

                for (String serverName : endHostModel.get(SERVER_CONFIG).keys()) {
                    // Compare boot cmd (requires restart)
                    Boolean restart = servers.get(serverName);
                    if (restart == null || !restart) {
                        //In some unit tests the start config may be null
                        ManagedServerBootConfiguration startConfig =
                                new ManagedServerBootCmdFactory(serverName, startRoot, startHostModel,
                                        parameters.getHostControllerEnvironment(),
                                        parameters.getDomainController().getExpressionResolver()).createConfiguration();

                        ManagedServerBootConfiguration endConfig =
                                new ManagedServerBootCmdFactory(serverName, endRoot, endHostModel,
                                        parameters.getHostControllerEnvironment(),
                                        parameters.getDomainController().getExpressionResolver()).createConfiguration();
                        if (startConfig == null || !startConfig.getServerLaunchCommand().equals(endConfig.getServerLaunchCommand())) {
                            servers.put(serverName, true);
                        }
                    }
                }

                for (Map.Entry<String, Boolean> entry : servers.entrySet()) {
                    final PathAddress serverAddress =
                            PathAddress.pathAddress(HOST, localHostName).append(SERVER, entry.getKey());
                    final String opName = entry.getValue() ?
                            ServerProcessStateHandler.REQUIRE_RESTART_OPERATION : ServerProcessStateHandler.REQUIRE_RELOAD_OPERATION;
                    final OperationStepHandler handler = context.getResourceRegistration().getOperationHandler(serverAddress, opName);
                    final ModelNode op = Util.createEmptyOperation(opName, serverAddress);
                    context.addStep(op, handler, OperationContext.Stage.MODEL);
                }
            }
        }, OperationContext.Stage.MODEL);


    }

    private Map<String, Boolean> determineServerStateChanges(OperationContext context, ServerOperationResolver resolver) {
        Map<String, Boolean> servers = new HashMap<>();
        for (ModelNode operation : operations) {
            Map<Set<ServerIdentity>, ModelNode> serverMap = resolver.getServerOperations(context, operation, PathAddress.pathAddress(operation.get(OP_ADDR)));
            for (Map.Entry<Set<ServerIdentity>, ModelNode> entry : serverMap.entrySet()) {
                ModelNode op = entry.getValue();
                boolean restart = op.get(OP).asString().equals(ServerProcessStateHandler.REQUIRE_RESTART_OPERATION);
                for (ServerIdentity id : entry.getKey()) {
                    String serverName = id.getServerName();
                    Boolean existing = servers.get(serverName);
                    if (existing == null || (!existing && restart)) {
                        servers.put(serverName, restart);
                    }
                }
            }
        }
        return servers;
    }

}
