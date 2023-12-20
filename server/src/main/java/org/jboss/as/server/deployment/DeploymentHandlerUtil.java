/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_DEPLOYED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_UNDEPLOYED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_BOOTING;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_HASH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.OWNER;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.PERSISTENT;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.getContents;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.getInputStream;
import static org.jboss.msc.service.ServiceController.Mode.REMOVE;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.Resource.ResourceEntry;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.as.server.deployment.annotation.AnnotationIndexSupport;
import org.jboss.as.server.deployment.transformation.DeploymentTransformer;
import org.jboss.as.server.deploymentoverlay.DeploymentOverlayIndex;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.LifecycleEvent;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceBuilder;
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
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class DeploymentHandlerUtil {

    private static final OperationContext.AttachmentKey<AnnotationIndexSupport> ANNOTATION_INDEX_SUPPORT = OperationContext.AttachmentKey.create(AnnotationIndexSupport.class);

    static class ContentItem {
        // either hash or <path, relativeTo, isArchive>
        private final byte[] hash;
        private final String path;
        private final String relativeTo;
        private final boolean isArchive;

        ContentItem(final byte[] hash) {
            this(hash, true);
        }

        ContentItem(final byte[] hash, boolean isArchive) {
            assert hash != null : "hash is null";
            this.hash = hash;
            this.isArchive = isArchive;
            this.path = null;
            this.relativeTo = null;
        }

        ContentItem(final String path, final String relativeTo, final boolean isArchive) {
            assert path != null : "path is null";
            this.path = path;
            this.relativeTo = relativeTo;
            this.isArchive = isArchive;
            this.hash = null;
        }

        byte[] getHash() {
            return hash;
        }
    }

    private static final String MANAGED_CONTENT = "managed-exploded";

    private DeploymentHandlerUtil() {
    }

    public static void deploy(final OperationContext context, final ModelNode operation, final String deploymentUnitName, final String managementName, final ContentItem... contents) throws OperationFailedException {
        assert contents != null : "contents is null";

        if (context.isNormalServer()) {
            //Checking for duplicate runtime name
            PathAddress deploymentsAddress = context.getCurrentAddress().getParent();
            Resource deploymentsParentResource = context.readResourceFromRoot(deploymentsAddress);
            for(ResourceEntry deployment : deploymentsParentResource.getChildren(DEPLOYMENT)) {
                if(!managementName.equals(deployment.getName())) {
                    ModelNode deploymentModel = deployment.getModel();
                    if(deploymentUnitName.equals(DeploymentAttributes.RUNTIME_NAME.resolveModelAttribute(context, deploymentModel).asString())
                            && DeploymentAttributes.ENABLED.resolveModelAttribute(context, deploymentModel).asBoolean()) {
                        throw ServerLogger.ROOT_LOGGER.runtimeNameMustBeUnique(managementName, deploymentUnitName);
                    }
                }
            }
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
                        doDeploy(context, deploymentUnitName, managementName, deployment, registration, mutableRegistration, contents);

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
                                final ManagementResourceRegistration mutableRegistration, final ContentItem... contents) {

        final ServiceName deploymentUnitServiceName = Services.deploymentUnitName(deploymentUnitName);

        final ServiceTarget serviceTarget = context.getServiceTarget();
        final ServiceController<?> contentService;
        // TODO: overlay service
        final ServiceName contentsServiceName = deploymentUnitServiceName.append("contents");
        boolean isExplodedContent = false;
        if (contents[0].hash != null) {
            if (contents[0].isArchive) {
                contentService = ContentServitor.addService(serviceTarget, contentsServiceName, contents[0].hash);
            } else {
                isExplodedContent = true;
                contentService = ManagedExplodedContentServitor.addService(context, contentsServiceName, managementName, contents[0].hash);
            }
        }
        else {
            final String path = contents[0].path;
            final String relativeTo = contents[0].relativeTo;
            contentService = PathContentServitor.addService(context, serviceTarget, contentsServiceName, path, relativeTo);
        }
        DeploymentOverlayIndex overlays = DeploymentOverlayIndex.createDeploymentOverlayIndex(context);
        // Get or create a shared cache of static module annotation indices to use across all deployments
        // associated with the current OperationContext
        AnnotationIndexSupport annotationIndexSupport = getAnnotationIndexCache(context);

        final ServiceBuilder<?> sb = serviceTarget.addService(deploymentUnitServiceName);
        final Consumer<DeploymentUnit> deploymentUnitConsumer = sb.provides(deploymentUnitServiceName);
        final Supplier<DeploymentMountProvider> serverDeploymentRepositorySupplier = sb.requires(DeploymentMountProvider.SERVICE_NAME);
        final Supplier<PathManager> pathManagerSupplier = sb.requires(context.getCapabilityServiceName(PathManager.SERVICE_DESCRIPTOR));
        final Supplier<VirtualFile> contentsSupplier = sb.requires(contentsServiceName);
        final RootDeploymentUnitService service = new RootDeploymentUnitService(deploymentUnitConsumer,
                serverDeploymentRepositorySupplier, pathManagerSupplier, contentsSupplier,
                deploymentUnitName, managementName, null, context.getStability(),
                registration, mutableRegistration, deploymentResource, context.getCapabilityServiceSupport(), overlays,
                annotationIndexSupport, isExplodedContent);
        final ServiceController<?> deploymentUnitController = sb.setInstance(service).install();

        contentService.addListener(new LifecycleListener() {
            @Override
            public void handleEvent(final ServiceController<?> controller, final LifecycleEvent event) {
                if (event == LifecycleEvent.REMOVED) {
                    deploymentUnitController.setMode(REMOVE);
                }
            }
        });
    }

    public static void redeploy(final OperationContext context, final String deploymentUnitName,
                                final String managementName, final ContentItem... contents) throws OperationFailedException {
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
                            doDeploy(context, deploymentUnitName, managementName, deployment, registration, mutableRegistration, contents);
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
                            doDeploy(context, deploymentUnitName, managementName, deployment, registration, mutableRegistration, contents);
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
                               final String replacedDeploymentUnitName, final ContentItem... contents) throws OperationFailedException {
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

                    doDeploy(context, deploymentUnitName, managementName, deployment, registration, mutableRegistration, contents);
                    if (originalDeployment.hasDefined(PERSISTENT.getName()) && !PERSISTENT.resolveModelAttribute(context, originalDeployment).asBoolean() && PERSISTENT.resolveModelAttribute(context, operation).asBoolean()) {
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
                        PathAddress pathAddress = context.getCurrentAddress().size() == 0 ? PathAddress.pathAddress(DEPLOYMENT, managementName) : context.getCurrentAddress();
                        context.emit(new Notification(DEPLOYMENT_UNDEPLOYED_NOTIFICATION, pathAddress, ServerLogger.ROOT_LOGGER.deploymentUndeployedNotification(managementName, deploymentUnitName), notificationData));
                    }

                    context.completeStep(new OperationContext.ResultHandler() {
                        @Override
                        public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                            if (resultAction == OperationContext.ResultAction.ROLLBACK) {

                                DeploymentResourceSupport.cleanup(deployment);
                                final String runtimeName = originalDeployment.require(RUNTIME_NAME).asString();
                                final DeploymentHandlerUtil.ContentItem[] contents = getContents(originalDeployment.require(CONTENT));
                                doDeploy(context, runtimeName, managementName, deployment, registration, mutableRegistration, contents);

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

    public static void undeploy(final OperationContext context, final ModelNode operation, final String managementName, final String runtimeName) {
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
            PathAddress pathAddress = context.getCurrentAddress().size() == 0 ? PathAddress.pathAddress(DEPLOYMENT, managementName) : context.getCurrentAddress();
            context.emit(new Notification(DEPLOYMENT_UNDEPLOYED_NOTIFICATION, pathAddress, ServerLogger.ROOT_LOGGER.deploymentUndeployedNotification(managementName, runtimeName), notificationData));

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
                                doDeploy(context, runtimeName, managementName, deployment, registration, mutableRegistration, contents);

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

    public static boolean isManaged(ModelNode contentItem) {
        return !contentItem.hasDefined(DeploymentAttributes.CONTENT_PATH.getName());
    }

    public static boolean isArchive(ModelNode contentItem) {
        return contentItem.get(DeploymentAttributes.CONTENT_ARCHIVE.getName()).asBoolean(true);
    }

    public static ModelNode getContentItem(Resource resource) {
        return resource.getModel().get(DeploymentAttributes.CONTENT_RESOURCE_ALL.getName()).get(0);
    }

    static Path getExplodedDeploymentRoot(ServerEnvironment serverEnvironment, String deploymentManagementName) {
        return Paths.get(serverEnvironment.getServerDataDir().getAbsolutePath()).resolve(MANAGED_CONTENT).resolve(deploymentManagementName);
    }

    @SuppressWarnings("deprecation")
    static DeploymentTransformer loadDeploymentTransformer() {
        Iterator<DeploymentTransformer> iter = ServiceLoader.load(DeploymentTransformer.class, DeploymentAddHandler.class.getClassLoader()).iterator();
        return iter.hasNext() ? iter.next() : null;
    }

    @SuppressWarnings("deprecation")
    static InputStream transformDeploymentBytes(OperationContext context, ModelNode contentItemNode, String name, InputStream in, DeploymentTransformer deploymentTransformer) throws IOException, OperationFailedException {
        InputStream result = in;
        if (deploymentTransformer != null) {
            try {
                result = deploymentTransformer.transform(in, name);
            } catch (RuntimeException t) {
                // Check if the InputStream is already attached to the operation request (as per CONTENT_INPUT_STREAM_INDEX check) and ignore that case
                // as calling getInputStream would of returned the already partially consumed InputStream.
                // Also verify that the thrown exception is the specific WFCORE-5198 `Error code 3`.
                if (!contentItemNode.hasDefined(DeploymentAttributes.CONTENT_INPUT_STREAM_INDEX.getName()) &&
                        t.getCause() != null && t.getCause().getCause() != null &&
                        t.getCause().getCause() instanceof IOException &&
                        t.getCause().getCause().getMessage().contains("during transformation. Error code 3")) {
                    ServerLogger.ROOT_LOGGER.tracef(t, "Ignoring transformation error and using original archive %s", name);
                    result = getInputStream(context, contentItemNode);
                } else {
                    throw t;
                }
            }
        }
        return result;
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

    public static byte[] addFromHash(ContentRepository contentRepository, ModelNode contentItemNode, String deploymentName, PathAddress address, OperationContext context) throws OperationFailedException {
        byte[] hash = contentItemNode.require(CONTENT_HASH.getName()).asBytes();
        ContentReference reference = ModelContentReference.fromModelAddress(address, hash);
        if (!contentRepository.syncContent(reference)) {
            if (context.isBooting()) {
                if (context.getRunningMode() == RunningMode.ADMIN_ONLY) {
                    // The deployment content is missing, which would be a fatal boot error if we were going to actually
                    // install services. In ADMIN-ONLY mode we allow it to give the admin a chance to correct the problem
                    ServerLogger.ROOT_LOGGER.reportAdminOnlyMissingDeploymentContent(reference.getHexHash(), deploymentName);
                } else {
                    throw ServerLogger.ROOT_LOGGER.noSuchDeploymentContentAtBoot(reference.getHexHash(), deploymentName);
                }
            } else {
                throw ServerLogger.ROOT_LOGGER.noSuchDeploymentContent(reference.getHexHash());
            }
        }
        return hash;
    }

    private static AnnotationIndexSupport getAnnotationIndexCache(OperationContext context) {
        AnnotationIndexSupport result = context.getAttachment(ANNOTATION_INDEX_SUPPORT);
        if (result == null) {
            result = new AnnotationIndexSupport();
            context.attach(ANNOTATION_INDEX_SUPPORT, result);
        }
        return result;
    }
}
