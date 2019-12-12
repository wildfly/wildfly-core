/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.as.server.moduleservice;

import java.io.File;
import java.util.function.Consumer;

import org.jboss.as.server.Services;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Service that manages external modules.
 * <p>
 * Once external modules are installed there is currently no way to safely remove the module spec service, however as they are
 * on-demand services if all dependent services are stopped then the actual {@link org.jboss.modules.Module} will be unloaded.
 * <p>
 * TODO: support removing modules when msc can tell us that nothing depends on the service.
 *
 * @author Stuart Douglas
 * @author Ales Justin
 *
 */
public class ExternalModuleService implements Service<ExternalModuleService>, ExternalModule {

    private boolean started;
    private final Consumer<ExternalModuleService> externalModuleServiceConsumer;

    private ExternalModuleService(final Consumer<ExternalModuleService> externalModuleServiceConsumer) {
        this.externalModuleServiceConsumer = externalModuleServiceConsumer;
    }

    @Override
    public boolean isValidFile(String path) {
        File f = new File(path);
        return f.exists() && !f.isDirectory();
    }

    @Override
    public ModuleIdentifier addExternalModule(String moduleName, ServiceRegistry serviceRegistry, ServiceTarget serviceTarget) {
        return addExternalModule(moduleName, moduleName, serviceRegistry, serviceTarget);
    }

    @Override
    public ModuleIdentifier addExternalModule(String moduleName, String path, ServiceRegistry serviceRegistry, ServiceTarget serviceTarget) {
        ModuleIdentifier identifier = ModuleIdentifier.create(EXTERNAL_MODULE_PREFIX + moduleName);
        ServiceName serviceName = ServiceModuleLoader.moduleSpecServiceName(identifier);
        ServiceController<?> controller = serviceRegistry.getService(serviceName);
        if (controller == null) {
            ExternalModuleSpecService service = new ExternalModuleSpecService(identifier, new File(path));
            serviceTarget.addService(serviceName)
                    .setInstance(service)
                    .setInitialMode(Mode.ON_DEMAND)
                    .install();
        }
        return identifier;
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        if (started) {
            throw ServerLogger.ROOT_LOGGER.externalModuleServiceAlreadyStarted();
        }
        this.externalModuleServiceConsumer.accept(this);
        started = true;
    }

    @Override
    public void stop(StopContext context) {}

    @Override
    public ExternalModuleService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public static void addService(final ServiceTarget serviceTarget, final ServiceName externalModuleServiceName) {
        final ServiceBuilder<?> serviceBuilder = serviceTarget.addService(externalModuleServiceName);
        final Consumer<ExternalModuleService> provides = serviceBuilder.provides(Services.JBOSS_EXTERNAL_MODULE_SERVICE);

        serviceBuilder.setInstance(new ExternalModuleService(provides))
                .install();
    }
}
