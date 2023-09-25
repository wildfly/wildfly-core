/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.client.helpers.standalone;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.jboss.as.controller.client.ModelControllerClient;

/**
 * A simple helper for server deployments.
 *
 * @author thomas.diesler@jboss.com
 * @since 22-Mar-2012
 */
public class ServerDeploymentHelper {

    private final ServerDeploymentManager deploymentManager;

    public ServerDeploymentHelper(ModelControllerClient client) {
        deploymentManager = ServerDeploymentManager.Factory.create(client);
    }

    public ServerDeploymentHelper(ServerDeploymentManager deploymentManager) {
        this.deploymentManager = deploymentManager;
    }

    public String deploy(String runtimeName, InputStream input) throws ServerDeploymentException {
        ServerDeploymentPlanResult planResult;
        List<DeploymentAction> actions = new ArrayList<DeploymentAction>();
        try {
            DeploymentPlanBuilder builder = deploymentManager.newDeploymentPlan();
            AddDeploymentPlanBuilder addBuilder = builder.add(runtimeName, input);
            actions.add(addBuilder.getLastAction());
            builder = addBuilder.andDeploy();
            actions.add(builder.getLastAction());
            DeploymentPlan plan = builder.build();
            Future<ServerDeploymentPlanResult> future = deploymentManager.execute(plan);
            planResult = future.get();
        } catch (Exception ex) {
            throw new ServerDeploymentException(ex);
        }
        for (DeploymentAction action : actions) {
            ServerDeploymentActionResult actionResult = planResult.getDeploymentActionResult(action.getId());
            if (actionResult.getDeploymentException() != null)
                throw new ServerDeploymentException(actionResult);
        }
        return runtimeName;
    }

    public String replace(String runtimeName, String replaceName, InputStream input, boolean removeUndeployed) throws ServerDeploymentException {
        ServerDeploymentPlanResult planResult;
        List<DeploymentAction> actions = new ArrayList<DeploymentAction>();
        try {
            DeploymentPlanBuilder builder = deploymentManager.newDeploymentPlan();
            AddDeploymentPlanBuilder addBuilder = builder.add(runtimeName, input);
            actions.add(addBuilder.getLastAction());
            ReplaceDeploymentPlanBuilder replaceBuilder = addBuilder.andReplace(replaceName);
            actions.add(replaceBuilder.getLastAction());
            if (removeUndeployed) {
                builder = replaceBuilder.andRemoveUndeployed();
                actions.add(builder.getLastAction());
            } else {
                builder = replaceBuilder;
            }
            DeploymentPlan plan = builder.build();
            Future<ServerDeploymentPlanResult> future = deploymentManager.execute(plan);
            planResult = future.get();
        } catch (Exception ex) {
            throw new ServerDeploymentException(ex);
        }
        for (DeploymentAction action : actions) {
            ServerDeploymentActionResult actionResult = planResult.getDeploymentActionResult(action.getId());
            if (actionResult.getDeploymentException() != null)
                throw new ServerDeploymentException(actionResult);
        }
        return runtimeName;
    }

    public void undeploy(String runtimeName) throws ServerDeploymentException {
        ServerDeploymentPlanResult planResult;
        List<DeploymentAction> actions = new ArrayList<DeploymentAction>();
        try {
            DeploymentPlanBuilder builder = deploymentManager.newDeploymentPlan();
            UndeployDeploymentPlanBuilder undeployBuilder = builder.undeploy(runtimeName);
            actions.add(undeployBuilder.getLastAction());
            builder = undeployBuilder.andRemoveUndeployed();
            actions.add(builder.getLastAction());
            DeploymentPlan plan = builder.build();
            Future<ServerDeploymentPlanResult> future = deploymentManager.execute(plan);
            planResult = future.get();
        } catch (Exception ex) {
            throw new ServerDeploymentException(ex);
        }
        for (DeploymentAction action : actions) {
            ServerDeploymentActionResult actionResult = planResult.getDeploymentActionResult(action.getId());
            if (actionResult.getDeploymentException() != null)
                throw new ServerDeploymentException(actionResult);
        }
    }

    public static class ServerDeploymentException extends Exception {
        private static final long serialVersionUID = 1L;
        private final ServerDeploymentActionResult actionResult;

        private ServerDeploymentException(ServerDeploymentActionResult actionResult) {
            super(actionResult.getDeploymentException());
            this.actionResult = actionResult;
        }

        private ServerDeploymentException(Throwable cause) {
            super(cause);
            actionResult = null;
        }

        public ServerDeploymentActionResult getActionResult() {
            return actionResult;
        }
    }
}
