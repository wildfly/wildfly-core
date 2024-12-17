/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.moduleservice;

import java.util.function.Consumer;

import org.jboss.as.controller.UninterruptibleCountDownLatch;
import org.jboss.as.server.Bootstrap;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.Services;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.msc.service.LifecycleEvent;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * {@link ModuleLoader} that loads module definitions from msc services. Module specs are looked up in msc services that
 * correspond to the module names.
 * <p>
 * Modules are automatically removed when the corresponding service comes down.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ServiceModuleLoader extends ModuleLoader implements Service<ServiceModuleLoader> {

    // Provide logging
    private static final ServerLogger log = ServerLogger.MODULE_SERVICE_LOGGER;

    /**
     * Listener class that atomically retrieves the moduleSpec, and automatically removes the Module when the module spec
     * service is removed
     *
     * @author Stuart Douglas
     *
     */
    private class ModuleSpecLoadListener implements LifecycleListener {

        private final UninterruptibleCountDownLatch latch;
        private volatile StartException startException;
        private volatile ModuleSpec moduleSpec;

        private ModuleSpecLoadListener(final UninterruptibleCountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void handleEvent(final ServiceController<?> controller, final LifecycleEvent event) {
            latch.awaitUninterruptibly();
            switch (event) {
                case UP:
                    log.tracef("serviceStarted: %s", controller);
                    done(controller, null);
                    break;
                case FAILED:
                    log.tracef(controller.getStartException(), "serviceFailed: %s", controller);
                    done(controller, controller.getStartException());
                    break;
                case DOWN: {
                    log.tracef("serviceStopping: %s", controller);
                    ModuleSpec moduleSpec = this.moduleSpec;
                    if (moduleSpec != null) {
                        String identifier = moduleSpec.getName();
                        Module module = findLoadedModuleLocal(identifier);
                        if (module != null)
                            unloadModuleLocal(identifier, module);
                    }
                    // TODO: what if the service is restarted?
                    controller.removeListener(this);
                    break;
                }
            }
        }

        @SuppressWarnings("unchecked")
        private void done(ServiceController<?> controller, StartException reason) {
            startException = reason;
            if (startException == null) {
                moduleSpec = ((ServiceController<ModuleDefinition>)controller).getValue().getModuleSpec();
            }
        }

        public ModuleSpec getModuleSpec() throws ModuleLoadException {
            if (startException != null)
                throw new ModuleLoadException(startException.getCause());
            return moduleSpec;
        }
    }

    public static final ServiceName MODULE_SPEC_SERVICE_PREFIX = ServiceName.JBOSS.append("module", "spec", "service");

    public static final ServiceName MODULE_SERVICE_PREFIX = ServiceName.JBOSS.append("module", "service");

    public static final ServiceName MODULE_RESOLVED_SERVICE_PREFIX = ServiceName.of("module", "resolved", "service");


    public static final String MODULE_PREFIX = "deployment.";

    private final ModuleLoader mainModuleLoader;

    private volatile ServiceContainer serviceContainer;

    public ServiceModuleLoader(ModuleLoader mainModuleLoader) {
        this.mainModuleLoader = mainModuleLoader;
    }

    @Override
    protected Module preloadModule(final String name) throws ModuleLoadException {
        if (name.startsWith(MODULE_PREFIX)) {
            return super.preloadModule(name);
        } else {
            return preloadModule(name, mainModuleLoader);
        }
    }

    /**
     * @deprecated Will be made protected in line with this method in the parent class Use {@link ServiceModuleLoader#findModule(String)}
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    @SuppressWarnings("unchecked")
    @Override
    public ModuleSpec findModule(ModuleIdentifier identifier) throws ModuleLoadException {
        return findModule(identifier.toString());
    }

    @SuppressWarnings("unchecked")
    @Override
    protected ModuleSpec findModule(String identifier) throws ModuleLoadException {
        ServiceController<ModuleDefinition> controller = (ServiceController<ModuleDefinition>) serviceContainer.getService(moduleSpecServiceName(identifier));
        if (controller == null) {
            ServerLogger.MODULE_SERVICE_LOGGER.debugf("Could not load module '%s' as corresponding module spec service '%s' was not found", identifier, identifier);
            return null;
        }
        UninterruptibleCountDownLatch latch = new UninterruptibleCountDownLatch(1);
        ModuleSpecLoadListener listener = new ModuleSpecLoadListener(latch);
        try {
            synchronized (controller) {
                final State state = controller.getState();
                if (state == State.UP || state == State.START_FAILED) {
                    listener.done(controller, controller.getStartException());
                }
            }
            controller.addListener(listener);
        } finally {
            latch.countDown();
        }
        return listener.getModuleSpec();
    }

    @Override
    public String toString() {
        return "Service Module Loader";
    }

    @Override
    public void start(StartContext context) throws StartException {
        if (serviceContainer != null) {
            throw ServerLogger.ROOT_LOGGER.serviceModuleLoaderAlreadyStarted();
        }
        serviceContainer = context.getController().getServiceContainer();
    }

    @Override
    public void stop(StopContext context) {
        if (serviceContainer == null) {
            throw ServerLogger.ROOT_LOGGER.serviceModuleLoaderAlreadyStopped();
        }
        serviceContainer = null;
    }

    @Override
    public ServiceModuleLoader getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public void relinkModule(Module module) throws ModuleLoadException {
        relink(module);
    }

    public static void addService(final ServiceTarget serviceTarget, final Bootstrap.Configuration configuration) {
        final Service<ServiceModuleLoader> service = new ServiceModuleLoader(configuration.getModuleLoader());
        final ServiceBuilder<?> serviceBuilder = serviceTarget.addService(Services.JBOSS_SERVICE_MODULE_LOADER, service);
        serviceBuilder.install();
    }

    /**
     * Returns the corresponding ModuleSpec service name for the given module.
     *
     * @param identifier The module identifier
     * @return The service name of the ModuleSpec service
     * @deprecated Use {@link ServiceModuleLoader#moduleSpecServiceName(String)}
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    public static ServiceName moduleSpecServiceName(ModuleIdentifier identifier) {
        return moduleSpecServiceName(identifier.toString());
    }

    /**
     * Returns the corresponding ModuleSpec service name for the given module.
     *
     * @param identifier The module identifier string, method does not support non-canonized identifiers
     * @return The service name of the ModuleSpec service
     */
    public static ServiceName moduleSpecServiceName(String identifier) {
        if (!isDynamicModule(identifier)) {
            throw ServerLogger.ROOT_LOGGER.missingModulePrefix(identifier, MODULE_PREFIX);
        }
        return MODULE_SPEC_SERVICE_PREFIX.append(identifier);
    }

    /**
     * @deprecated Use {@link ServiceModuleLoader#installModuleResolvedService(ServiceTarget, String)}
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    public static void installModuleResolvedService(ServiceTarget serviceTarget, ModuleIdentifier identifier) {
        installModuleResolvedService(serviceTarget, identifier.toString());
    }

    /**
     * @param serviceTarget service target to use to install the service. Cannot be {@code null}.
     * @param identifier The module identifier string, method does not support non-canonized identifiers
     */
    public static void installModuleResolvedService(ServiceTarget serviceTarget, String identifier) {
        final ServiceName sn = ServiceModuleLoader.moduleResolvedServiceName(identifier);
        final ServiceBuilder<?> sb = serviceTarget.addService(sn);
        final Consumer<String> moduleIdConsumer = sb.provides(sn);
        sb.requires(moduleSpecServiceName(identifier));
        final org.jboss.msc.Service resolvedService = org.jboss.msc.Service.newInstance(moduleIdConsumer, identifier);
        sb.setInstance(resolvedService);
        sb.install();
    }

    /**
     * Returns the corresponding module resolved service name for the given module.
     *
     * The module resolved service is basically a latch that prevents the module from being loaded
     * until all the transitive dependencies that it depends upon have have their module spec services
     * come up.
     *
     * @param identifier The module identifier
     * @return The service name of the ModuleSpec service
     * @deprecated Use {@link ServiceModuleLoader#moduleResolvedServiceName(String)}
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    public static ServiceName moduleResolvedServiceName(ModuleIdentifier identifier) {
        return moduleResolvedServiceName(identifier.toString());
    }

    /**
     * Returns the corresponding module resolved service name for the given module.
     *
     * The module resolved service is basically a latch that prevents the module from being loaded
     * until all the transitive dependencies that it depends upon have have their module spec services
     * come up.
     *
     * @param identifier The module identifier string, method does not support non-canonized identifiers
     * @return The service name of the ModuleSpec service
     */
    public static ServiceName moduleResolvedServiceName(String identifier) {
        if (!isDynamicModule(identifier)) {
            throw ServerLogger.ROOT_LOGGER.missingModulePrefix(identifier, MODULE_PREFIX);
        }
        return MODULE_RESOLVED_SERVICE_PREFIX.append(identifier);
    }

    /**
     * Returns true if the module identifier is a dynamic module that will be loaded by this module loader
     *
     * @deprecated Use {@link ServiceModuleLoader#isDynamicModule(String)}
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    public static boolean isDynamicModule(ModuleIdentifier identifier) {
        return isDynamicModule(identifier.toString());
    }

    /**
     * Returns true if the module identifier is a dynamic module that will be loaded by this module loader
     *
     * @param identifier The module identifier string, method does not support non-canonized identifiers
     * @return Whether the module identifier is a dynamic module
     */
    public static boolean isDynamicModule(String identifier) {
        return identifier.startsWith(MODULE_PREFIX);
    }

    /**
     * Returns the corresponding ModuleLoadService service name for the given module.
     *
     * @param identifier The module identifier
     * @return The service name of the ModuleLoadService service
     * @deprecated Use {@link ServiceModuleLoader#moduleServiceName(String)}
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    public static ServiceName moduleServiceName(ModuleIdentifier identifier) {
        return moduleServiceName(identifier.toString());
    }

    /**
     * Returns the corresponding ModuleLoadService service name for the given module.
     *
     * @param identifier The module identifier string, method does not support non-canonized identifiers
     * @return The service name of the ModuleLoadService service
     */
    public static ServiceName moduleServiceName(String identifier) {
        if (!identifier.startsWith(MODULE_PREFIX)) {
            throw ServerLogger.ROOT_LOGGER.missingModulePrefix(identifier, MODULE_PREFIX);
        }
        return MODULE_SERVICE_PREFIX.append(identifier);
    }
}
