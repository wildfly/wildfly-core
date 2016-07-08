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

package org.jboss.as.controller.client.helpers.standalone.impl;

import static org.jboss.as.controller.client.helpers.ClientConstants.ADD;
import static org.jboss.as.controller.client.helpers.ClientConstants.ADD_CONTENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.COMPOSITE;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_DEPLOY_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_FULL_REPLACE_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_REDEPLOY_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_REMOVE_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_REPLACE_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.DEPLOYMENT_UNDEPLOY_OPERATION;
import static org.jboss.as.controller.client.helpers.ClientConstants.EMPTY;
import static org.jboss.as.controller.client.helpers.ClientConstants.EXPLODE;
import static org.jboss.as.controller.client.helpers.ClientConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.client.helpers.ClientConstants.NAME;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP;
import static org.jboss.as.controller.client.helpers.ClientConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP_ADDR;
import static org.jboss.as.controller.client.helpers.ClientConstants.PATH;
import static org.jboss.as.controller.client.helpers.ClientConstants.REMOVE_CONTENT;
import static org.jboss.as.controller.client.helpers.ClientConstants.ROLLBACK_ON_RUNTIME_FAILURE;
import static org.jboss.as.controller.client.helpers.ClientConstants.RUNTIME_NAME;
import static org.jboss.as.controller.client.helpers.ClientConstants.STEPS;
import static org.jboss.as.controller.client.helpers.ClientConstants.TARGET_PATH;
import static org.jboss.as.controller.client.helpers.ClientConstants.TO_REPLACE;

import java.io.InputStream;
import java.util.Map.Entry;
import java.util.concurrent.Future;

import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.InitialDeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentPlanResult;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 * @author Brian Stansberry
 */
public abstract class AbstractServerDeploymentManager implements ServerDeploymentManager {

    protected AbstractServerDeploymentManager() {
    }

    /** {@inheritDoc} */
    @Override
    public InitialDeploymentPlanBuilder newDeploymentPlan() {
        return InitialDeploymentPlanBuilderFactory.newInitialDeploymentPlanBuilder();
    }

    /** {@inheritDoc} */
    @Override
    public Future<ServerDeploymentPlanResult> execute(DeploymentPlan plan) {
        if (!(plan instanceof DeploymentPlanImpl)) {
            throw ControllerClientLogger.ROOT_LOGGER.cannotUseDeploymentPlan();
        }
        DeploymentPlanImpl planImpl = (DeploymentPlanImpl) plan;
        Operation operation = getCompositeOperation(planImpl);
        Future<ModelNode> nodeFuture = executeOperation(operation);
        return new ServerDeploymentPlanResultFuture(planImpl, nodeFuture);
    }

    protected abstract Future<ModelNode> executeOperation(Operation context);

    private Operation getCompositeOperation(DeploymentPlanImpl plan) {

        ModelNode op = new ModelNode();
        op.get(OP).set(COMPOSITE);
        op.get(OP_ADDR).setEmptyList();
        ModelNode steps = op.get(STEPS);
        steps.setEmptyList();
        op.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(plan.isGlobalRollback());
        // FIXME deal with shutdown params

        OperationBuilder builder = new OperationBuilder(op);

        int stream = 0;
        for (DeploymentActionImpl action : plan.getDeploymentActionImpls()) {
            ModelNode step = new ModelNode();
            String uniqueName = action.getDeploymentUnitUniqueName();
            switch (action.getType()) {
                case ADD: {
                    configureDeploymentOperation(step, ADD, uniqueName);
                    if(action.getNewContentFileName() != null && action.getContentStream() != null) {
                        step.get(RUNTIME_NAME).set(action.getNewContentFileName());
                        builder.addInputStream(action.getContentStream());
                        step.get(CONTENT).get(0).get(INPUT_STREAM_INDEX).set(stream++);
                    } else if(action.getContents() != null && !action.getContents().isEmpty()) {
                        int index = 0;
                        for(Entry<String, InputStream> content : action.getContents().entrySet()) {
                            step.get(RUNTIME_NAME).set(content.getKey());
                            builder.addInputStream(content.getValue());
                            step.get(CONTENT).get(index).get(INPUT_STREAM_INDEX).set(stream++);
                            index++;
                        }
                    } else {
                        step.get(EMPTY).set(true);
                    }
                    break;
                }
                case DEPLOY: {
                    configureDeploymentOperation(step, DEPLOYMENT_DEPLOY_OPERATION, uniqueName);
                    break;
                }
                case FULL_REPLACE: {
                    step.get(OP).set(DEPLOYMENT_FULL_REPLACE_OPERATION);
                    step.get(OP_ADDR).setEmptyList();
                    step.get(NAME).set(uniqueName);
                    step.get(RUNTIME_NAME).set(action.getNewContentFileName());
                    builder.addInputStream(action.getContentStream());
                    step.get(CONTENT).get(0).get(INPUT_STREAM_INDEX).set(stream++);
                    break;
                }
                case REDEPLOY: {
                    configureDeploymentOperation(step, DEPLOYMENT_REDEPLOY_OPERATION, uniqueName);
                    break;
                }
                case REMOVE: {
                    configureDeploymentOperation(step, DEPLOYMENT_REMOVE_OPERATION, uniqueName);
                    break;
                }
                case REPLACE: {
                    step.get(OP).set(DEPLOYMENT_REPLACE_OPERATION);
                    step.get(OP_ADDR).setEmptyList();
                    step.get(NAME).set(uniqueName);
                    step.get(TO_REPLACE).set(action.getReplacedDeploymentUnitUniqueName());
                    break;
                }
                case UNDEPLOY: {
                    configureDeploymentOperation(step, DEPLOYMENT_UNDEPLOY_OPERATION, uniqueName);
                    break;
                }
                case ADD_CONTENT: {
                    configureDeploymentOperation(step, ADD_CONTENT, uniqueName);
                    int i = 0;
                    for (Entry<String, InputStream> content : action.getContents().entrySet()) {
                        builder.addInputStream(content.getValue());
                        step.get(CONTENT).get(i).get(INPUT_STREAM_INDEX).set(stream++);
                        step.get(CONTENT).get(i).get(TARGET_PATH).set(content.getKey());
                        i++;
                    }
                    break;
                }
                case REMOVE_CONTENT: {
                    configureDeploymentOperation(step, REMOVE_CONTENT, uniqueName);
                    step.get(PATH).setEmptyList();
                    for (Entry<String, InputStream> content : action.getContents().entrySet()) {
                        step.get(PATH).add(content.getKey());
                    }
                    break;
                }
                case EXPLODE: {
                    configureDeploymentOperation(step, EXPLODE, uniqueName);
                    break;
                }
                default: {
                    throw ControllerClientLogger.ROOT_LOGGER.unknownActionType(action.getType());
                }
            }
            steps.add(step);
        }

        return builder.build();
    }

    private void configureDeploymentOperation(ModelNode op, String operationName, String uniqueName) {
        op.get(OP).set(operationName);
        op.get(OP_ADDR).add(DEPLOYMENT, uniqueName);
    }
}
