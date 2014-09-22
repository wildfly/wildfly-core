/*
 * Copyright (C) 2014 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.server.deployment;

import static java.security.AccessController.doPrivileged;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ModelController;
import org.jboss.as.repository.ContentRepository;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.as.server.Services;
import org.jboss.threads.JBossThreadFactory;
import org.wildfly.security.manager.action.GetAccessControlContextAction;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class DeploymentContentCleanerService implements Service<ContentRepositoryCleaner> {
    /**
     * The conten repository cleaner will test content for clean-up every 5 minutes.
     */
    public static final long DEFAULT_INTERVAL = 100000L;
    /**
     * Standard ServiceName under which a service controller for an instance of
     * @code Service<ContentRepository> would be registered.
     */
    private static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("content-repository-cleaner");

    private final InjectedValue<ContentRepository> contentRepositoryValue = new InjectedValue<>();
    private final InjectedValue<ModelController> controllerValue = new InjectedValue<>();
    private final InjectedValue<ScheduledExecutorService> scheduledExecutorValue = new InjectedValue<>();
    private final InjectedValue<ControlledProcessStateService> controlledProcessStateServiceValue = new InjectedValue<>();

    private ContentRepositoryCleaner deploymentContentCleaner;
    private final long interval;
    private TimeUnit unit = TimeUnit.MILLISECONDS;

    public static void addService(final ServiceTarget serviceTarget, final Long interval, TimeUnit unit) {
        final ThreadFactory threadFactory = new JBossThreadFactory(new ThreadGroup("ContentRepositoryCleaner-threads"),
                Boolean.FALSE, null, "%G - %t", null, null, doPrivileged(GetAccessControlContextAction.getInstance()));
        final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1, threadFactory);

        final DeploymentContentCleanerService service = new DeploymentContentCleanerService(interval, unit);
        ServiceBuilder<ContentRepositoryCleaner> builder = serviceTarget.addService(SERVICE_NAME, service)
                .addDependency(Services.JBOSS_SERVER_CONTROLLER, ModelController.class, service.controllerValue)
                .addDependency(ContentRepository.SERVICE_NAME, ContentRepository.class, service.contentRepositoryValue)
                .addDependency(ControlledProcessStateService.SERVICE_NAME, ControlledProcessStateService.class, service.controlledProcessStateServiceValue)
                .addInjection(service.getScheduledExecutorValue(), scheduledExecutorService);
        builder.install();
    }

    public static void addServiceOnHostController(final ServiceName hostControllerServiceName,
            final ServiceTarget serviceTarget, final Long interval, TimeUnit unit) {
        final ThreadFactory threadFactory = new JBossThreadFactory(new ThreadGroup("ContentRepositoryCleaner-threads"),
                Boolean.FALSE, null, "%G - %t", null, null, doPrivileged(GetAccessControlContextAction.getInstance()));
        final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1, threadFactory);

        final DeploymentContentCleanerService service = new DeploymentContentCleanerService(interval, unit);
        ServiceBuilder<ContentRepositoryCleaner> builder = serviceTarget.addService(SERVICE_NAME, service)
                .addDependency(hostControllerServiceName, ModelController.class, service.controllerValue)
                .addDependency(ContentRepository.SERVICE_NAME, ContentRepository.class, service.contentRepositoryValue)
                .addDependency(ControlledProcessStateService.SERVICE_NAME, ControlledProcessStateService.class, service.controlledProcessStateServiceValue)
                .addInjection(service.getScheduledExecutorValue(), scheduledExecutorService);
        builder.install();
    }

    DeploymentContentCleanerService(Long interval, TimeUnit unit) {
        this.interval = interval == null || interval <= 0L ? DEFAULT_INTERVAL : interval;
        this.unit = unit;
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        final ContentRepositoryCleaner contentCleaner = new ContentRepositoryCleaner(contentRepositoryValue.getValue(),
                controllerValue.getValue().createClient(Executors.newCachedThreadPool()), controlledProcessStateServiceValue.getValue(),
                scheduledExecutorValue.getValue(), unit.toMillis(interval));
        this.deploymentContentCleaner = contentCleaner;
        deploymentContentCleaner.startScan();
    }

    @Override
    public synchronized void stop(StopContext context) {
        final ContentRepositoryCleaner contentCleaner = this.deploymentContentCleaner;
        this.deploymentContentCleaner = null;
        contentCleaner.stopScan();
        scheduledExecutorValue.getValue().shutdown();
    }

    @Override
    public synchronized ContentRepositoryCleaner getValue() throws IllegalStateException, IllegalArgumentException {
        return deploymentContentCleaner;
    }

    public InjectedValue<ContentRepository> getContentRepositoryValue() {
        return contentRepositoryValue;
    }

    public InjectedValue<ModelController> getControllerValue() {
        return controllerValue;
    }

    public InjectedValue<ScheduledExecutorService> getScheduledExecutorValue() {
        return scheduledExecutorValue;
    }
}
