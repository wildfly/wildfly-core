/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_DEPLOYED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_UNDEPLOYED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_BOOTING;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.OWNER;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.getContents;
import static org.jboss.msc.service.ServiceController.Mode.REMOVE;

import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.server.deploymentoverlay.DeploymentOverlayIndex;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.services.security.AbstractVaultReader;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.vfs.VirtualFile;

/**
 * Utility methods used by operation handlers involved with deployment.
 * <p/>
 * This class is part of the runtime operation and should not have any reference to dmr.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DeploymentHandlerUtil {

    static class ContentItem {
        // either hash or <path, relativeTo, isArchive>
        private byte[] hash;
        private String path;
        private String relativeTo;
        private boolean isArchive;

        ContentItem(final byte[] hash) {
            assert hash != null : "hash is null";
            this.hash = hash;
        }

        ContentItem(final String path, final String relativeTo, final boolean isArchive) {
            assert path != null : "path is null";
            this.path = path;
            this.relativeTo = relativeTo;
            this.isArchive = isArchive;
        }

        byte[] getHash() {
            return hash;
        }
    }

    private DeploymentHandlerUtil() {
    }

    public static void deploy(final OperationContext context, final ModelNode operation, final String deploymentUnitName, final String managementName, final AbstractVaultReader vaultReader, final ContentItem... contents) throws OperationFailedException {
        assert contents != null : "contents is null";

        if (context.isNormalServer()) {
            //
            final Resource deployment = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
            final ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
            final ManagementResourceRegistration mutableRegistration = context.getResourceRegistrationForUpdate();

            DeploymentResourceSupport.cleanup(deployment);
            ModelNode notificationData = new ModelNode();
            notificationData.get(NAME).set(managementName);
            notificationData.get(SERVER_BOOTING).set(context.isBooting());
            if (operation.hasDefined(OWNER.getName())) {
                try {
                    notificationData.get(OWNER.getName()).set(OWNER.resolveModelAttribute(context, operation));
                } catch (OperationFailedException ex) {//No resolvable owner we won't set one
                }
            }
            notificationData.get(DEPLOYMENT).set(deploymentUnitName);
            context.emit(new Notification(DEPLOYMENT_DEPLOYED_NOTIFICATION, context.getCurrentAddress(), ServerLogger.ROOT_LOGGER.deploymentDeployedNotification(managementName, deploymentUnitName), notificationData));

            context.addStep(new OperationStepHandler() {
                public void execute(OperationContext context, ModelNode operation) {
                    final ServiceName deploymentUnitServiceName = Services.deploymentUnitName(deploymentUnitName);
                    final ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
                    final ServiceController<?> deploymentController = serviceRegistry.getService(deploymentUnitServiceName);
                    if (deploymentController != null) {
                        deploymentController.setMode(ServiceController.Mode.ACTIVE);

                        context.completeStep(new OperationContext.RollbackHandler() {
                            @Override
                            public void handleRollback(OperationContext context, ModelNode operation) {
                                deploymentController.setMode(ServiceController.Mode.NEVER);
                            }
                        });
                    } else {
                        doDeploy(context, deploymentUnitName, managementName, deployment, registration, mutableRegistration, vaultReader, contents);

                        context.completeStep(new OperationContext.ResultHandler() {
                            @Override
                            public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                                if(resultAction == OperationContext.ResultAction.ROLLBACK) {
                                    if (context.hasFailureDescription()) {
                                        ServerLogger.ROOT_LOGGER.deploymentRolledBack(deploymentUnitName, getFormattedFailureDescription(context));
                                    } else {
                                        ServerLogger.ROOT_LOGGER.deploymentRolledBackWithNoMessage(deploymentUnitName);
                                    }
                                } else {
                                    ServerLogger.ROOT_LOGGER.deploymentDeployed(managementName, deploymentUnitName);
                                }
                            }
                        });
                    }
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    public static void doDeploy(final OperationContext context, final String deploymentUnitName, final String managementName,
                                final Resource deploymentResource, final ImmutableManagementResourceRegistration registration,
                                final ManagementResourceRegistration mutableRegistration, final AbstractVaultReader vaultReader, final ContentItem... contents) {
        final ServiceName deploymentUnitServiceName = Services.deploymentUnitName(deploymentUnitName);

        final ServiceTarget serviceTarget = context.getServiceTarget();
        final ServiceController<?> contentService;
        // TODO: overlay service
        final ServiceName contentsServiceName = deploymentUnitServiceName.append("contents");
        if (contents[0].hash != null)
            contentService = ContentServitor.addService(serviceTarget, contentsServiceName, contents[0].hash);
        else {
            final String path = contents[0].path;
            final String relativeTo = contents[0].relativeTo;
            contentService = PathContentServitor.addService(serviceTarget, contentsServiceName, path, relativeTo);
        }
        DeploymentOverlayIndex overlays = DeploymentOverlayIndex.createDeploymentOverlayIndex(context);

        final RootDeploymentUnitService service = new RootDeploymentUnitService(deploymentUnitName, managementName, null,
                registration, mutableRegistration, deploymentResource, context.getCapabilityServiceSupport(), vaultReader, overlays);
        final ServiceController<DeploymentUnit> deploymentUnitController = serviceTarget.addService(deploymentUnitServiceName, service)
                .addDependency(Services.JBOSS_DEPLOYMENT_CHAINS, DeployerChains.class, service.getDeployerChainsInjector())
                .addDependency(DeploymentMountProvider.SERVICE_NAME, DeploymentMountProvider.class, service.getServerDeploymentRepositoryInjector())
                .addDependency(PathManagerService.SERVICE_NAME, PathManager.class, service.getPathManagerInjector())
                .addDependency(contentsServiceName, VirtualFile.class, service.contentsInjector)
                .setInitialMode(ServiceController.Mode.ACTIVE)
                .install();

        contentService.addListener(new AbstractServiceListener<Object>() {
            @Override
            public void transition(final ServiceController<?> controller, final ServiceController.Transition transition) {
                if (transition == ServiceController.Transition.REMOVING_to_REMOVED) {
                    deploymentUnitController.setMode(REMOVE);
                }
            }
        });
    }

    public static void redeploy(final OperationContext context, final String deploymentUnitName,
                                final String managementName, final AbstractVaultReader vaultReader, final ContentItem... contents) throws OperationFailedException {
        assert contents != null : "contents is null";

        if (context.isNormalServer()) {
            //
            final Resource deployment = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
            final ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
            final ManagementResourceRegistration mutableRegistration = context.getResourceRegistrationForUpdate();

            DeploymentResourceSupport.cleanup(deployment);

            context.addStep(new OperationStepHandler() {
                public void execute(final OperationContext context, ModelNode operation) throws OperationFailedException {
                    final ServiceName deploymentUnitServiceName = Services.deploymentUnitName(deploymentUnitName);
                    context.removeService(deploymentUnitServiceName);
                    context.removeService(deploymentUnitServiceName.append("contents"));

                    final AtomicBoolean logged = new AtomicBoolean(false);
                    context.addStep(new OperationStepHandler() {
                        @Override
                        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                            doDeploy(context, deploymentUnitName, managementName, deployment, registration, mutableRegistration, vaultReader, contents);
                            context.completeStep(new OperationContext.ResultHandler() {
                                @Override
                                public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                                    if (resultAction == OperationContext.ResultAction.ROLLBACK) {
                                        if (context.hasFailureDescription()) {
                                            ServerLogger.ROOT_LOGGER.redeployRolledBack(deploymentUnitName, getFormattedFailureDescription(context));
                                            logged.set(true);
                                        } else {
                                            ServerLogger.ROOT_LOGGER.redeployRolledBackWithNoMessage(deploymentUnitName);
                                            logged.set(true);
                                        }
                                    } else {
                                        ServerLogger.ROOT_LOGGER.deploymentRedeployed(deploymentUnitName);
                                    }
                                }
                            });
                        }
                    }, OperationContext.Stage.RUNTIME, true);

                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            doDeploy(context, deploymentUnitName, managementName, deployment, registration, mutableRegistration, vaultReader, contents);
                            if (!logged.get()) {
                                if (context.hasFailureDescription()) {
                                    ServerLogger.ROOT_LOGGER.undeploymentRolledBack(deploymentUnitName, context.getFailureDescription().asString());
                                } else {
                                    ServerLogger.ROOT_LOGGER.undeploymentRolledBackWithNoMessage(deploymentUnitName);
                                }
                            }
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    public static void replace(final OperationContext context, final ModelNode originalDeployment, final String deploymentUnitName, final String managementName,
                               final String replacedDeploymentUnitName, final AbstractVaultReader vaultReader, final ContentItem... contents) throws OperationFailedException {
        assert contents != null : "contents is null";

        if (context.isNormalServer()) {
            //
            final PathElement path = PathElement.pathElement(DEPLOYMENT, managementName);
            final Resource deployment = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS.append(path));
            final ImmutableManagementResourceRegistration registration = context.getResourceRegistration().getSubModel(PathAddress.EMPTY_ADDRESS.append(path));
            final ManagementResourceRegistration mutableRegistration = context.getResourceRegistrationForUpdate().getSubModel(PathAddress.EMPTY_ADDRESS.append(path));

            DeploymentResourceSupport.cleanup(deployment);

            context.addStep(new OperationStepHandler() {
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    final ServiceName replacedDeploymentUnitServiceName = Services.deploymentUnitName(replacedDeploymentUnitName);
                    final ServiceName replacedContentsServiceName = replacedDeploymentUnitServiceName.append("contents");
                    context.removeService(replacedContentsServiceName);
                    context.removeService(replacedDeploymentUnitServiceName);

                    doDeploy(context, deploymentUnitName, managementName, deployment, registration, mutableRegistration, vaultReader, contents);

                    context.completeStep(new OperationContext.ResultHandler() {
                        @Override
                        public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                            if (resultAction == OperationContext.ResultAction.ROLLBACK) {

                                DeploymentResourceSupport.cleanup(deployment);
                                final String name = originalDeployment.require(NAME).asString();
                                final String runtimeName = originalDeployment.require(RUNTIME_NAME).asString();
                                final DeploymentHandlerUtil.ContentItem[] contents = getContents(originalDeployment.require(CONTENT));
                                doDeploy(context, runtimeName, name, deployment, registration, mutableRegistration, vaultReader, contents);

                                if (context.hasFailureDescription()) {
                                    ServerLogger.ROOT_LOGGER.replaceRolledBack(replacedDeploymentUnitName, deploymentUnitName, getFormattedFailureDescription(context));
                                } else {
                                    ServerLogger.ROOT_LOGGER.replaceRolledBackWithNoMessage(replacedDeploymentUnitName, deploymentUnitName);
                                }
                            } else {
                                ServerLogger.ROOT_LOGGER.deploymentReplaced(replacedDeploymentUnitName, deploymentUnitName);
                            }
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    public static void undeploy(final OperationContext context, final ModelNode operation, final String managementName, final String runtimeName, final AbstractVaultReader vaultReader) {
        if (context.isNormalServer()) {
            // WFCORE-1577 -- the resource we want may not be at the op address if this is called for full-replace-deployment
            PathAddress resourceAddress = context.getCurrentAddress().size() == 0 ? PathAddress.pathAddress(DEPLOYMENT, managementName) : PathAddress.EMPTY_ADDRESS;
            final Resource deployment = context.readResourceForUpdate(resourceAddress);
            final ImmutableManagementResourceRegistration registration = context.getResourceRegistration().getSubModel(resourceAddress);
            final ManagementResourceRegistration mutableRegistration = context.getResourceRegistrationForUpdate().getSubModel(resourceAddress);
            DeploymentResourceSupport.cleanup(deployment);
            ModelNode notificationData = new ModelNode();
            notificationData.get(NAME).set(managementName);
            notificationData.get(SERVER_BOOTING).set(context.isBooting());
            if (operation.hasDefined(OWNER.getName())) {
                try {
                    notificationData.get(OWNER.getName()).set(OWNER.resolveModelAttribute(context, operation));
                } catch (OperationFailedException ex) {//No resolvable owner we won't set one
                }
            }
            notificationData.get(DEPLOYMENT).set(runtimeName);
            context.emit(new Notification(DEPLOYMENT_UNDEPLOYED_NOTIFICATION, resourceAddress, ServerLogger.ROOT_LOGGER.deploymentUndeployedNotification(managementName, runtimeName), notificationData));

            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) {

                    final ServiceName deploymentUnitServiceName = Services.deploymentUnitName(runtimeName);

                    context.removeService(deploymentUnitServiceName);
                    context.removeService(deploymentUnitServiceName.append("contents"));

                    context.completeStep(new OperationContext.ResultHandler() {
                        @Override
                        public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                            if(resultAction == OperationContext.ResultAction.ROLLBACK) {

                                final ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
                                final DeploymentHandlerUtil.ContentItem[] contents = getContents(model.require(CONTENT));
                                doDeploy(context, runtimeName, managementName, deployment, registration, mutableRegistration, vaultReader, contents);

                                if (context.hasFailureDescription()) {
                                    ServerLogger.ROOT_LOGGER.undeploymentRolledBack(runtimeName, getFormattedFailureDescription(context));
                                } else {
                                    ServerLogger.ROOT_LOGGER.undeploymentRolledBackWithNoMessage(runtimeName);
                                }
                            } else {
                                ServerLogger.ROOT_LOGGER.deploymentUndeployed(managementName, runtimeName);
                            }
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    private static String getFormattedFailureDescription(OperationContext context) {
        ModelNode failureDescNode = context.getFailureDescription();
        String failureDesc = failureDescNode.toString();
//        // Strip the wrapping {} from ModelType.OBJECT types
//        if (failureDescNode.getType() == ModelType.OBJECT && failureDesc.length() > 2
//                && failureDesc.charAt(0) == '{' && failureDesc.charAt(failureDesc.length() - 1) == '}') {
//            failureDesc = failureDesc.substring(1, failureDesc.length() - 1);
//        }

        if (failureDesc.contains("\n") && failureDesc.charAt(0) != '\n') {
            failureDesc = "\n" + failureDesc;
        }
        return failureDesc;
    }
}
