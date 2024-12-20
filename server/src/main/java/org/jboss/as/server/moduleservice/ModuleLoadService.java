/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.moduleservice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.server.Services;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleNotFoundException;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * Service that loads and re-links a module once all the modules dependencies are available.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ModuleLoadService implements Service<Module> {

    private final InjectedValue<ServiceModuleLoader> serviceModuleLoader = new InjectedValue<ServiceModuleLoader>();
    private final InjectedValue<ModuleDefinition> moduleDefinitionInjectedValue = new InjectedValue<ModuleDefinition>();
    private final List<ModuleDependency> allDependencies;
    private final Collection<ModuleDependency> systemDependencies;
    private final Collection<ModuleDependency> userDependencies;
    private final Collection<ModuleDependency> localDependencies;

    private volatile Module module;

    private ModuleLoadService(final Collection<ModuleDependency> systemDependencies, final Collection<ModuleDependency> localDependencies, final Collection<ModuleDependency> userDependencies) {
        this.systemDependencies = systemDependencies;
        this.localDependencies = localDependencies;
        this.userDependencies = userDependencies;

        this.allDependencies = new ArrayList<>();
        this.allDependencies.addAll(systemDependencies);
        this.allDependencies.addAll(localDependencies);
        this.allDependencies.addAll(userDependencies);
    }

    private ModuleLoadService(final List<ModuleDependency> aliasDependencies) {
        this.systemDependencies = Collections.emptyList();
        this.localDependencies = Collections.emptyList();
        this.userDependencies = Collections.emptyList();

        this.allDependencies = new ArrayList<>(aliasDependencies);
    }

    private ModuleLoadService() {
        this.systemDependencies = Collections.emptyList();
        this.localDependencies = Collections.emptyList();
        this.userDependencies = Collections.emptyList();

        this.allDependencies = Collections.emptyList();
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        try {
            final ServiceModuleLoader moduleLoader = serviceModuleLoader.getValue();
            final Module module = moduleLoader.loadModule(moduleDefinitionInjectedValue.getValue().getModuleIdentifier());
            moduleLoader.relinkModule(module);
            for (ModuleDependency dependency : allDependencies) {
                if (dependency.isUserSpecified()) {
                    final String id = dependency.getDependencyModule();
                    try {
                        String val = moduleLoader.loadModule(id).getProperty("jboss.api");
                        if (val != null) {
                            if (val.equals("private")) {
                                ServerLogger.PRIVATE_DEP_LOGGER.privateApiUsed(moduleDefinitionInjectedValue.getValue().getModuleIdentifier().getName(), id);
                            } else if (val.equals("unsupported")) {
                                ServerLogger.UNSUPPORTED_DEP_LOGGER.unsupportedApiUsed(moduleDefinitionInjectedValue.getValue().getModuleIdentifier().getName(), id);
                            } else if (val.equals("deprecated")) {
                                ServerLogger.DEPRECATED_DEP_LOGGER.deprecatedApiUsed(moduleDefinitionInjectedValue.getValue().getModuleIdentifier().getName(), id);
                            }
                        }
                    } catch (ModuleNotFoundException ignore) {
                        //can happen with optional dependencies
                    }
                }
            }
            this.module = module;
        } catch (ModuleLoadException e) {
            throw ServerLogger.ROOT_LOGGER.failedToLoadModule(moduleDefinitionInjectedValue.getValue().getModuleIdentifier(), e);
        }
    }

    @Override
    public synchronized void stop(StopContext context) {
        // we don't actually unload the module, that is taken care of by the service module loader
        module = null;
    }

    @Override
    public Module getValue() throws IllegalStateException, IllegalArgumentException {
        return module;
    }

    private static ServiceName install(final ServiceTarget target, final String identifier, ModuleLoadService service) {
        final ServiceName serviceName = ServiceModuleLoader.moduleServiceName(identifier);
        final ServiceBuilder<Module> builder = target.addService(serviceName, service);

        builder.addDependency(Services.JBOSS_SERVICE_MODULE_LOADER, ServiceModuleLoader.class, service.getServiceModuleLoader());
        builder.addDependency(ServiceModuleLoader.moduleSpecServiceName(identifier.toString()), ModuleDefinition.class, service.getModuleDefinitionInjectedValue());
        builder.requires(ServiceModuleLoader.moduleResolvedServiceName(identifier.toString())); //don't attempt to load until all dependent module specs are up, even transitive ones
        builder.setInitialMode(Mode.ON_DEMAND);

        builder.install();
        return serviceName;
    }

    public static ServiceName install(final ServiceTarget target, final ModuleIdentifier identifier){
        final ModuleLoadService service = new ModuleLoadService();
        return install(target, identifier.toString(), service);
    }

    public static ServiceName install(final ServiceTarget target, final ModuleIdentifier identifier, final Collection<ModuleDependency> systemDependencies, final Collection<ModuleDependency> localDependencies, final Collection<ModuleDependency> userDependencies) {
        final ModuleLoadService service = new ModuleLoadService(systemDependencies, localDependencies, userDependencies);
        return install(target, identifier.toString(), service);
    }

    public static ServiceName installAliases(final ServiceTarget target, final ModuleIdentifier identifier, final List<ModuleIdentifier> aliases) {
        final ArrayList<ModuleDependency> dependencies = new ArrayList<ModuleDependency>(aliases.size());
        for (final ModuleIdentifier i : aliases) {
            dependencies.add(new ModuleDependency(null, i, false, false, false, false));
        }
        final ModuleLoadService service = new ModuleLoadService(dependencies);
        return install(target, identifier.toString(), service);
    }

    public InjectedValue<ServiceModuleLoader> getServiceModuleLoader() {
        return serviceModuleLoader;
    }

    public InjectedValue<ModuleDefinition> getModuleDefinitionInjectedValue() {
        return moduleDefinitionInjectedValue;
    }

    public List<ModuleDependency> getSystemDependencies() {
        return new ArrayList<>(systemDependencies);
    }

    public List<ModuleDependency> getUserDependencies() {
        return new ArrayList<>(userDependencies);
    }

    public List<ModuleDependency> getLocalDependencies() {
        return new ArrayList<>(localDependencies);
    }
}