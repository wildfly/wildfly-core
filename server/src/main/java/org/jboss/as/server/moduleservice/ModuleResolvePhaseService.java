/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.moduleservice;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.inject.InjectionException;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Module phase resolve service. Basically this service attempts to resolve
 * every dynamic transitive dependency of a module, and allows the module resolved service
 * to start once this is complete.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ModuleResolvePhaseService implements Service<ModuleResolvePhaseService> {

    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("module", "resolve", "phase");

    private final String moduleIdentifier;

    private final Set<String> alreadyResolvedModules;

    private final int phaseNumber;

    /**
     * module specification that were resolved this phase. These are injected as the relevant spec services start.
     */
    private final Set<ModuleDefinition> moduleSpecs = Collections.synchronizedSet(new HashSet<ModuleDefinition>());

    private ModuleResolvePhaseService(final String moduleIdentifier, final Set<String> alreadyResolvedModules, final int phaseNumber) {
        this.moduleIdentifier = moduleIdentifier;
        this.alreadyResolvedModules = alreadyResolvedModules;
        this.phaseNumber = phaseNumber;
    }

    @Override
    public void start(final StartContext startContext) throws StartException {
        Set<ModuleDependency> nextPhaseIdentifiers = new HashSet<>();
        final Set<String> nextAlreadySeen = new HashSet<>(alreadyResolvedModules);
        for (final ModuleDefinition spec : moduleSpecs) {
            if (spec != null) { //this can happen for optional dependencies
                for (ModuleDependency dep : spec.getDependencies()) {
                    if (dep.isOptional()) continue; // we don't care about optional dependencies
                    if (ServiceModuleLoader.isDynamicModule(dep.getDependencyModule())) {
                        if (!alreadyResolvedModules.contains(dep.getDependencyModule())) {
                            nextAlreadySeen.add(dep.getDependencyModule());
                            nextPhaseIdentifiers.add(dep);
                        }
                    }
                }
            }
        }
        if (nextPhaseIdentifiers.isEmpty()) {
            ServiceModuleLoader.installModuleResolvedService(startContext.getChildTarget(), moduleIdentifier);
        } else {
            installService(startContext.getChildTarget(), moduleIdentifier, phaseNumber + 1, nextPhaseIdentifiers, nextAlreadySeen);
        }
    }

    public static void installService(final ServiceTarget serviceTarget, final ModuleDefinition moduleDefinition) {
        String moduleIdentifier = moduleDefinition.getModuleIdentifier().toString();
        final ModuleResolvePhaseService nextPhaseService = new ModuleResolvePhaseService(moduleIdentifier, Collections.singleton(moduleDefinition.getModuleIdentifier().toString()), 0);
        nextPhaseService.getModuleSpecs().add(moduleDefinition);
        ServiceBuilder<ModuleResolvePhaseService> builder = serviceTarget.addService(moduleResolvePhaseServiceName(moduleIdentifier, 0), nextPhaseService);
        builder.install();
    }

    private static void installService(final ServiceTarget serviceTarget, final String moduleIdentifier, int phaseNumber, final Set<ModuleDependency> nextPhaseIdentifiers, final Set<String> nextAlreadySeen) {
        final ModuleResolvePhaseService nextPhaseService = new ModuleResolvePhaseService(moduleIdentifier, nextAlreadySeen, phaseNumber);
        ServiceBuilder<ModuleResolvePhaseService> builder = serviceTarget.addService(moduleResolvePhaseServiceName(moduleIdentifier, phaseNumber), nextPhaseService);
        for (ModuleDependency module : nextPhaseIdentifiers) {
            builder.addDependency(ServiceModuleLoader.moduleSpecServiceName(module.getDependencyModule()), ModuleDefinition.class, new Injector<>() {
                ModuleDefinition definition;

                @Override
                public synchronized void inject(final ModuleDefinition o) throws InjectionException {
                    nextPhaseService.getModuleSpecs().add(o);
                    this.definition = o;
                }

                @Override
                public synchronized void uninject() {
                    nextPhaseService.getModuleSpecs().remove(definition);
                    this.definition = null;
                }
            });
        }
        builder.install();
    }

    @Override
    public void stop(final StopContext stopContext) {

    }

    @Override
    public ModuleResolvePhaseService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public Set<ModuleDefinition> getModuleSpecs() {
        return moduleSpecs;
    }

    /** @deprecated this method will be made private */
    @Deprecated(forRemoval = true)
    public static ServiceName moduleSpecServiceName(ModuleIdentifier identifier, int phase) {
        return moduleResolvePhaseServiceName(identifier.toString(), phase);
    }

    private static ServiceName moduleResolvePhaseServiceName(String identifier, int phase) {
        if (!ServiceModuleLoader.isDynamicModule(identifier)) {
            throw ServerLogger.ROOT_LOGGER.missingModulePrefix(identifier, ServiceModuleLoader.MODULE_PREFIX);
        }
        return SERVICE_NAME.append(identifier).append("" + phase);
    }
}
