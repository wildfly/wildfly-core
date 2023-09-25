/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.scanner;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_DEPLOYED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_UNDEPLOYED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OWNER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_BOOTING;
import static org.jboss.as.controller.registry.NotificationHandlerRegistration.ANY_ADDRESS;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.PATH_MANAGER_CAPABILITY;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.SCANNER_CAPABILITY;

import java.io.File;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.ProcessStateNotifier;
import org.jboss.as.controller.ModelControllerClientFactory;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.notification.NotificationFilter;
import org.jboss.as.controller.notification.NotificationHandlerRegistry;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.server.deployment.scanner.api.DeploymentOperations;
import org.jboss.as.server.deployment.scanner.api.DeploymentScanner;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Service responsible creating a {@code DeploymentScanner}
 *
 * @author Emanuel Muckenhuber
 */
public class DeploymentScannerService implements Service<DeploymentScanner> {

    private final PathAddress resourceAddress;
    private final long interval;
    private TimeUnit unit = TimeUnit.MILLISECONDS;
    private final boolean enabled;
    private final boolean autoDeployZipped;
    private final boolean autoDeployExploded;
    private final boolean autoDeployXml;
    private final long deploymentTimeout;
    private final String relativeTo;
    private final String path;
    private final boolean rollbackOnRuntimeFailure;
    private static final NotificationFilter DEPLOYMENT_FILTER = (Notification notification) -> {
        if (DEPLOYMENT_UNDEPLOYED_NOTIFICATION.equals(notification.getType()) || DEPLOYMENT_DEPLOYED_NOTIFICATION.equals(notification.getType())) {
            ModelNode notificationData = notification.getData();
            if (notificationData.hasDefined(SERVER_BOOTING) && notificationData.get(SERVER_BOOTING).asBoolean()) {
                return false;
            }
            if (notificationData.hasDefined(OWNER)) {
                List<Property> properties = notificationData.get(OWNER).asPropertyList();
                if (properties.size() >= 2) {
                    return !"deployment-scanner".equals(properties.get(0).getValue().asString())
                            && !"scanner".equals(properties.get(1).getName());
                }
            }
            return true;
        }
        return false;
    };

    /**
     * The created scanner.
     */
    private FileSystemDeploymentService scanner;

    private final Consumer<DeploymentScanner> serviceConsumer;
    private final Supplier<PathManager> pathManager;
    private final Supplier<NotificationHandlerRegistry> notificationRegistry;
    private final Supplier<ModelControllerClientFactory> clientFactory;
    private final Supplier<ProcessStateNotifier> processStateNotifier;
    private final ScheduledExecutorService scheduledExecutor;
    private volatile PathManager.Callback.Handle callbackHandle;

    public static ServiceName getServiceName(String repositoryName) {
        return SCANNER_CAPABILITY.getCapabilityServiceName(repositoryName);
    }

    /**
     * Add the deployment scanner service to a batch.
     *
     * @param context           context for the operation that is adding this service
     * @param resourceAddress   the address of the resource that manages the service
     * @param relativeTo        the relative to
     * @param path              the path
     * @param scanInterval      the scan interval
     * @param unit              the unit of {@code scanInterval}
     * @param autoDeployZip     whether zipped content should be auto-deployed
     * @param autoDeployExploded whether exploded content should be auto-deployed
     * @param autoDeployXml     whether xml content should be auto-deployed
     * @param scanEnabled       scan enabled
     * @param deploymentTimeout the deployment timeout
     * @param rollbackOnRuntimeFailure rollback on runtime failures
     * @param bootTimeService   the deployment scanner used in the boot time scan
     * @param scheduledExecutorService executor to use for asynchronous tasks
     * @return the controller for the deployment scanner service
     */
    public static void addService(final OperationContext context, final PathAddress resourceAddress, final String relativeTo, final String path,
                                                                  final int scanInterval, TimeUnit unit, final boolean autoDeployZip,
                                                                  final boolean autoDeployExploded, final boolean autoDeployXml, final boolean scanEnabled, final long deploymentTimeout, boolean rollbackOnRuntimeFailure,
                                                                  final FileSystemDeploymentService bootTimeService, final ScheduledExecutorService scheduledExecutorService) {
        final RuntimeCapability<Void> capName =  SCANNER_CAPABILITY.fromBaseCapability(resourceAddress.getLastElement().getValue());
        final CapabilityServiceBuilder<?> sb = context.getCapabilityServiceTarget().addCapability(capName);
        final Consumer<DeploymentScanner> serviceConsumer = sb.provides(capName);
        final Supplier<PathManager> pathManager = sb.requiresCapability(PATH_MANAGER_CAPABILITY, PathManager.class);
        final Supplier<NotificationHandlerRegistry> notificationRegistry = sb.requiresCapability("org.wildfly.management.notification-handler-registry", NotificationHandlerRegistry.class);
        final Supplier<ModelControllerClientFactory> clientFactory = sb.requiresCapability("org.wildfly.management.model-controller-client-factory", ModelControllerClientFactory.class);
        final Supplier<ProcessStateNotifier> processStateNotifier = sb.requiresCapability("org.wildfly.management.process-state-notifier", ProcessStateNotifier.class);
        sb.requires(org.jboss.as.server.deployment.Services.JBOSS_DEPLOYMENT_CHAINS);
        final DeploymentScannerService service = new DeploymentScannerService(
                serviceConsumer, pathManager, notificationRegistry, clientFactory, processStateNotifier, scheduledExecutorService,
                resourceAddress, relativeTo, path, scanInterval, unit, autoDeployZip,
                autoDeployExploded, autoDeployXml, scanEnabled, deploymentTimeout, rollbackOnRuntimeFailure, bootTimeService);
        sb.setInstance(service);
        sb.install();
    }

    private DeploymentScannerService(final Consumer<DeploymentScanner> serviceConsumer, final Supplier<PathManager> pathManager,
                                     final Supplier<NotificationHandlerRegistry> notificationRegistry, final Supplier<ModelControllerClientFactory> clientFactory,
                                     final Supplier<ProcessStateNotifier> processStateNotifier, final ScheduledExecutorService scheduledExecutor,
                                     final PathAddress resourceAddress, final String relativeTo, final String path, final int interval, final TimeUnit unit, final boolean autoDeployZipped,
                                     final boolean autoDeployExploded, final boolean autoDeployXml, final boolean enabled, final long deploymentTimeout,
                                     final boolean rollbackOnRuntimeFailure, final FileSystemDeploymentService bootTimeService) {
        this.serviceConsumer = serviceConsumer;
        this.pathManager = pathManager;
        this.notificationRegistry = notificationRegistry;
        this.clientFactory = clientFactory;
        this.scheduledExecutor = scheduledExecutor;
        this.processStateNotifier = processStateNotifier;
        this.resourceAddress = resourceAddress;
        this.relativeTo = relativeTo;
        this.path = path;
        this.interval = interval;
        this.unit = unit;
        this.autoDeployZipped = autoDeployZipped;
        this.autoDeployExploded = autoDeployExploded;
        this.autoDeployXml = autoDeployXml;
        this.enabled = enabled;
        this.rollbackOnRuntimeFailure = rollbackOnRuntimeFailure;
        this.deploymentTimeout = deploymentTimeout;
        this.scanner = bootTimeService;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void start(StartContext context) throws StartException {
        try {

            final DeploymentOperations.Factory factory = new DeploymentOperations.Factory() {
                @Override
                public DeploymentOperations create() {
                    return new DefaultDeploymentOperations(clientFactory.get(), scheduledExecutor);
                }
            };

            //if this is the first start we want to use the same scanner that was used at boot time
            if (scanner == null) {
                final PathManager pathManager = this.pathManager.get();
                final String pathName = pathManager.resolveRelativePathEntry(path, relativeTo);
                File relativePath = null;
                if (relativeTo != null) {
                    relativePath = new File(pathManager.getPathEntry(relativeTo).resolvePath());
                    callbackHandle = pathManager.registerCallback(pathName, PathManager.ReloadServerCallback.create(), PathManager.Event.UPDATED, PathManager.Event.REMOVED);
                }

                final FileSystemDeploymentService scanner = new FileSystemDeploymentService(resourceAddress, relativeTo, new File(pathName),
                        relativePath, factory, scheduledExecutor);

                scanner.setScanInterval(unit.toMillis(interval));
                scanner.setAutoDeployExplodedContent(autoDeployExploded);
                scanner.setAutoDeployZippedContent(autoDeployZipped);
                scanner.setAutoDeployXMLContent(autoDeployXml);
                scanner.setRuntimeFailureCausesRollback(rollbackOnRuntimeFailure);
                scanner.setDeploymentTimeout(deploymentTimeout);
                this.scanner = scanner;
            } else {
                // The boot-time scanner should use our DeploymentOperations.Factory
                this.scanner.setDeploymentOperationsFactory(factory);
            }
            // Provide the scanner a ProcessStateNotifier so it can do cleanup work when boot completes.
            // We do this for both a boot-time scanner or one we constructed ourselves above
            this.scanner.setProcessStateNotifier(processStateNotifier.get());
            notificationRegistry.get().registerNotificationHandler(ANY_ADDRESS, scanner, DEPLOYMENT_FILTER);
            if (enabled) {
                scanner.startScanner();
            }
            serviceConsumer.accept(scanner);
        } catch (Exception e) {
            throw new StartException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void stop(StopContext context) {
        serviceConsumer.accept(null);
        final DeploymentScanner scanner = this.scanner;
        notificationRegistry.get().unregisterNotificationHandler(ANY_ADDRESS, this.scanner, DEPLOYMENT_FILTER);
        this.scanner = null;
        scanner.stopScanner();
        scheduledExecutor.shutdown();
        if (callbackHandle != null) {
            callbackHandle.remove();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized DeploymentScanner getValue() throws IllegalStateException {
        final DeploymentScanner scanner = this.scanner;
        if (scanner == null) {
            throw new IllegalStateException();
        }
        return scanner;
    }

}
