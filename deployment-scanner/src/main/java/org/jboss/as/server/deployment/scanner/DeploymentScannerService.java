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

package org.jboss.as.server.deployment.scanner;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_DEPLOYED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_UNDEPLOYED_NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OWNER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_BOOTING;
import static org.jboss.as.controller.registry.NotificationHandlerRegistration.ANY_ADDRESS;

import java.io.File;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.notification.NotificationFilter;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.server.Services;
import org.jboss.as.server.deployment.scanner.api.DeploymentOperations;
import org.jboss.as.server.deployment.scanner.api.DeploymentScanner;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

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
    private final Long deploymentTimeout;
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

    private final InjectedValue<PathManager> pathManagerValue = new InjectedValue<PathManager>();
    private final InjectedValue<ModelController> controllerValue = new InjectedValue<ModelController>();
    private final InjectedValue<ScheduledExecutorService> scheduledExecutorValue = new InjectedValue<ScheduledExecutorService>();
    private final InjectedValue<ControlledProcessStateService> controlledProcessStateServiceValue = new InjectedValue<ControlledProcessStateService>();
    private volatile PathManager.Callback.Handle callbackHandle;

    public static ServiceName getServiceName(String repositoryName) {
        return DeploymentScanner.BASE_SERVICE_NAME.append(repositoryName);
    }

    /**
     * Add the deployment scanner service to a batch.
     *
     * @param serviceTarget     the service target
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
    public static ServiceController<DeploymentScanner> addService(final ServiceTarget serviceTarget, final PathAddress resourceAddress, final String relativeTo, final String path,
                                                                  final int scanInterval, TimeUnit unit, final boolean autoDeployZip,
                                                                  final boolean autoDeployExploded, final boolean autoDeployXml, final boolean scanEnabled, final long deploymentTimeout, boolean rollbackOnRuntimeFailure,
                                                                  final FileSystemDeploymentService bootTimeService, final ScheduledExecutorService scheduledExecutorService) {
        final DeploymentScannerService service = new DeploymentScannerService(resourceAddress, relativeTo, path, scanInterval, unit, autoDeployZip,
                autoDeployExploded, autoDeployXml, scanEnabled, deploymentTimeout, rollbackOnRuntimeFailure, bootTimeService);
        final ServiceName serviceName = getServiceName(resourceAddress.getLastElement().getValue());

        return serviceTarget.addService(serviceName, service)
                .addDependency(PathManagerService.SERVICE_NAME, PathManager.class, service.pathManagerValue)
                .addDependency(Services.JBOSS_SERVER_CONTROLLER, ModelController.class, service.controllerValue)
                .addDependency(org.jboss.as.server.deployment.Services.JBOSS_DEPLOYMENT_CHAINS)
                .addDependency(ControlledProcessStateService.SERVICE_NAME, ControlledProcessStateService.class, service.controlledProcessStateServiceValue)
                .addInjection(service.scheduledExecutorValue, scheduledExecutorService)
                .setInitialMode(Mode.ACTIVE)
                .install();
    }

    private DeploymentScannerService(PathAddress resourceAddress, final String relativeTo, final String path, final int interval, final TimeUnit unit, final boolean autoDeployZipped,
                                     final boolean autoDeployExploded, final boolean autoDeployXml, final boolean enabled, final long deploymentTimeout,
                                     final boolean rollbackOnRuntimeFailure, final FileSystemDeploymentService bootTimeService) {
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
                    return new DefaultDeploymentOperations(controllerValue.getValue().createClient(scheduledExecutorValue.getValue()));
                }
            };

            //if this is the first start we want to use the same scanner that was used at boot time
            if (scanner == null) {
                final PathManager pathManager = pathManagerValue.getValue();
                final String pathName = pathManager.resolveRelativePathEntry(path, relativeTo);
                File relativePath = null;
                if (relativeTo != null) {
                    relativePath = new File(pathManager.getPathEntry(relativeTo).resolvePath());
                    callbackHandle = pathManager.registerCallback(pathName, PathManager.ReloadServerCallback.create(), PathManager.Event.UPDATED, PathManager.Event.REMOVED);
                }

                final FileSystemDeploymentService scanner = new FileSystemDeploymentService(resourceAddress, relativeTo, new File(pathName),
                        relativePath, factory, scheduledExecutorValue.getValue(), controlledProcessStateServiceValue.getValue());

                scanner.setScanInterval(unit.toMillis(interval));
                scanner.setAutoDeployExplodedContent(autoDeployExploded);
                scanner.setAutoDeployZippedContent(autoDeployZipped);
                scanner.setAutoDeployXMLContent(autoDeployXml);
                scanner.setRuntimeFailureCausesRollback(rollbackOnRuntimeFailure);
                if (deploymentTimeout != null) {
                    scanner.setDeploymentTimeout(deploymentTimeout);
                }
                this.scanner = scanner;
            } else {
                // The boot-time scanner should use our DeploymentOperations.Factory
                this.scanner.setDeploymentOperationsFactory(factory);
            }
            controllerValue.getValue().getNotificationRegistry().registerNotificationHandler(ANY_ADDRESS, scanner, DEPLOYMENT_FILTER);
            if (enabled) {
                scanner.startScanner();
            }
        } catch (Exception e) {
            throw new StartException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void stop(StopContext context) {
        final DeploymentScanner scanner = this.scanner;
        controllerValue.getValue().getNotificationRegistry().unregisterNotificationHandler(ANY_ADDRESS, this.scanner, DEPLOYMENT_FILTER);
        this.scanner = null;
        scanner.stopScanner();
        scheduledExecutorValue.getValue().shutdown();
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
