/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.services.path;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.services.path.PathResourceDefinition.PATH_CAPABILITY;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathEntry.PathResolver;
import org.jboss.as.controller.services.path.PathManager.Callback.Handle;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * {@code PathManager} implementation that exposes additional methods used by the management operation handlers used
 * for paths, and also exposes the the {@code PathManager} as an MSC {@code Service}.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class PathManagerService implements PathManager, Service<PathManager> {

    /** @deprecated ServiceName should be obtained from capability 'org.wildfly.management.path-manager'.*/
    @Deprecated(forRemoval = true)
    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("path", "manager");

    //@GuardedBy(pathEntries)
    private final Map<String, PathEntry> pathEntries = new HashMap<String, PathEntry>();

    //@GuardedBy(pathEntries)
    private final Map<String, Set<String>> dependenctRelativePaths = new HashMap<String, Set<String>>();

    //@GuardedBy(callbacks)
    private final Map<String, Map<Event, Set<Callback>>> callbacks = new HashMap<String, Map<Event, Set<Callback>>>();

    private final RuntimeCapabilityRegistry capabilityRegistry;

    protected PathManagerService() {
        this.capabilityRegistry = null;
    }

    protected PathManagerService(RuntimeCapabilityRegistry capabilityRegistry) {
        this.capabilityRegistry = capabilityRegistry;
    }

    /**
     * Add child resources to the given resource, one for each {@link PathEntry} currently associated with this
     * path manager. Used to initialize the model with resources for the standard paths that are not part of
     * the persistent configuration.
     * @param resource the resource to which children should be added.
     */
    public final void addPathManagerResources(Resource resource) {
        synchronized (pathEntries) {
            for (PathEntry pathEntry : pathEntries.values()) {
                resource.registerChild(PathElement.pathElement(PATH, pathEntry.getName()), new HardcodedPathResource(PATH, pathEntry));
            }
        }
    }

    @Override
    public final String resolveRelativePathEntry(String path, String relativeTo) {
        if (relativeTo == null) {
            return AbsolutePathService.convertPath(path);
        } else {
            PathEntry pathEntry;
            synchronized (pathEntries) {
                pathEntry = pathEntries.get(relativeTo);
                if (pathEntry == null) {
                    throw ControllerLogger.ROOT_LOGGER.pathEntryNotFound(relativeTo);
                }
                return RelativePathService.doResolve(pathEntry.resolvePath(), path);
            }
        }
    }

    @Override
    public final Handle registerCallback(String name, Callback callback, Event...events) {
        synchronized (callbacks) {
            Map<Event, Set<Callback>> callbacksByEvent = callbacks.get(name);
            if (callbacksByEvent == null) {
                callbacksByEvent = new HashMap<PathManager.Event, Set<Callback>>();
                callbacks.put(name, callbacksByEvent);
            }
            for (Event event : events) {
                Set<Callback> callbackSet = callbacksByEvent.get(event);
                if (callbackSet == null) {
                    callbackSet = new HashSet<PathManager.Callback>();
                    callbacksByEvent.put(event, callbackSet);
                }
                callbackSet.add(callback);
            }
        }
        return new HandleImpl(name, callback, events);
    }

    @Override
    public final void start(StartContext context) throws StartException {
    }

    @Override
    public final void stop(StopContext context) {
    }

    @Override
    public final PathManagerService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    /**
     * Add a {@code PathEntry} and install a {@code Service<String>} for one of the standard read-only paths
     * that are determined from this process' environment. Not to be used for paths stored in the persistent
     * configuration.
     * @param serviceTarget service target to use for the service installation
     * @param pathName the logical name of the path within the model. Cannot be {@code null}
     * @param path  the value of the path within the model. This is an absolute path. Cannot be {@code null}
     * @return the controller for the installed {@code Service<String>}
     */
    protected final ServiceController<?> addHardcodedAbsolutePath(final ServiceTarget serviceTarget, final String pathName, final String path) {
        ServiceController<?>  controller = addAbsolutePathService(serviceTarget, pathName, path);
        addPathEntry(pathName, path, null, true);
        if (capabilityRegistry != null) {
            RuntimeCapability<Void> pathCapability = PATH_CAPABILITY.fromBaseCapability(pathName);
            capabilityRegistry.registerCapability(
                    new RuntimeCapabilityRegistration(pathCapability, CapabilityScope.GLOBAL, new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));
        }
        return controller;
    }

    @Override
    public final PathEntry getPathEntry(String pathName) {
        synchronized (pathEntries) {
            PathEntry pathEntry = pathEntries.get(pathName);
            if (pathEntry == null) {
                throw ControllerLogger.ROOT_LOGGER.pathEntryNotFound(pathName);
            }
            return pathEntry;
        }
    }

    final void changePathServices(final OperationContext operationContext, String pathName, String path) {
        PathEntry pathEntry = findPathEntry(pathName);

        removePathService(operationContext, pathName);
        if (pathEntry.getRelativeTo() == null) {
            addAbsolutePathService(operationContext.getServiceTarget(), pathEntry.getName(), path);
        } else {
            addRelativePathService(operationContext.getServiceTarget(), pathEntry.getName(), path, false, pathEntry.getRelativeTo());
        }
    }

    final void changeRelativePathServices(final OperationContext operationContext, String pathName, String relativeTo) {
        PathEntry pathEntry = findPathEntry(pathName);

        removePathService(operationContext, pathEntry.getName());
        if (relativeTo == null) {
            addAbsolutePathService(operationContext.getServiceTarget(), pathEntry.getName(), pathEntry.getPath());
        } else {
            addRelativePathService(operationContext.getServiceTarget(), pathEntry.getName(), pathEntry.getPath(), false, relativeTo);
        }
    }

    /**
     * Removes any {@code Service<String>} for the given path.
     * @param operationContext the operation context associated with the management operation making this request. Cannot be {@code null}
     * @param pathName  the name of the relevant path. Cannot be {@code null}
     */
    final void removePathService(final OperationContext operationContext, final String pathName) {
        final ServiceController<?> serviceController = operationContext.getServiceRegistry(true).getService(AbstractPathService.pathNameOf(pathName));
        if (serviceController != null) {
            operationContext.removeService(serviceController);
        }
    }

    /**
     * Removes the entry for a path and sends an {@link org.jboss.as.controller.services.path.PathManager.Event#REMOVED}
     * notification to any registered
     * {@linkplain org.jboss.as.controller.services.path.PathManager.Callback#pathEvent(Event, PathEntry) callbacks}.
     * @param pathName the logical name of the path within the model. Cannot be {@code null}
     * @param check {@code true} if a check for the existence of other paths that depend on {@code pathName}
     *              as their {@link PathEntry#getRelativeTo() relative-to} value should be performed
     * @throws OperationFailedException if {@code check} is {@code true} and other paths depend on the path being removed
     */
    final void removePathEntry(final String pathName, boolean check) throws OperationFailedException{
        synchronized (pathEntries) {
            PathEntry pathEntry = pathEntries.get(pathName);
            if (pathEntry.isReadOnly()) {
                throw ControllerLogger.ROOT_LOGGER.pathEntryIsReadOnly(pathName);
            }

            Set<String> dependents = dependenctRelativePaths.get(pathName);
            if (check && dependents != null) {
                throw ControllerLogger.ROOT_LOGGER.cannotRemovePathWithDependencies(pathName, dependents);
            }
            pathEntries.remove(pathName);
            triggerCallbacksForEvent(pathEntry, Event.REMOVED);
            if (pathEntry.getRelativeTo() != null) {
                dependents = dependenctRelativePaths.get(pathEntry.getRelativeTo());
                if (dependents != null) {
                    dependents.remove(pathEntry.getName());
                    if (dependents.isEmpty()) {
                        dependenctRelativePaths.remove(pathEntry.getRelativeTo());
                    }
                }
            }
        }
    }

    /**
     * Install a {@code Service<String>} for the given path.
     * @param serviceTarget the service target associated with the management operation making this request. Cannot be {@code null}
     * @param pathName  the name of the relevant path. Cannot be {@code null}
     * @param path  the value of the path within the model. This is either an absolute path or
     *              the relative portion of the path. Cannot be {@code null}
     *
     * @return the service controller for the {@code Service<String>}
     */
    final ServiceController<?> addAbsolutePathService(final ServiceTarget serviceTarget, final String pathName, final String path) {
        return AbsolutePathService.addService(pathName, path, serviceTarget);
    }

    /**
     * Install an {@code Service<String>} for the given path.
     * @param serviceTarget the service target associated with the management operation making this request. Cannot be {@code null}
     * @param pathName the name of the relevant path. Cannot be {@code null}
     * @param path  the value of the path within the model. This is either an absolute path or
     *              the relative portion of the path. Cannot be {@code null}
     * @param possiblyAbsolute {@code true} if the path may be absolute and a check should be performed before installing
     *                         a service variant that depends on the service associated with {@code relativeTo}
     * @param relativeTo the name of the path this path is relative to. If {@code null} this is an absolute path
     * @return the service controller for the {@code Service<String>}
     */
    final ServiceController<?> addRelativePathService(final ServiceTarget serviceTarget, final String pathName, final String path,
                                                      final boolean possiblyAbsolute, final String relativeTo) {
        if (possiblyAbsolute && AbstractPathService.isAbsoluteUnixOrWindowsPath(path)) {
            return addAbsolutePathService(serviceTarget, pathName, path);
        } else {
            return RelativePathService.addService(AbstractPathService.pathNameOf(pathName), path, possiblyAbsolute, relativeTo, serviceTarget);
        }
    }

    /**
     * Adds an entry for a path and sends an {@link org.jboss.as.controller.services.path.PathManager.Event#ADDED}
     * notification to any registered {@linkplain org.jboss.as.controller.services.path.PathManager.Callback callbacks}.
     *
     * @param pathName the logical name of the path within the model. Cannot be {@code null}
     * @param path  the value of the path within the model. This is either an absolute path or
     *              the relative portion of the path. Cannot be {@code null}
     * @param relativeTo the name of the path this path is relative to. If {@code null} this is an absolute path
     * @param readOnly {@code true} if the path is immutable, and cannot be removed or modified via a management operation
     * @return the entry that represents the path
     *
     * @throws RuntimeException if an entry with the given {@code pathName} is already registered
     */
    final PathEntry addPathEntry(final String pathName, final String path, final String relativeTo, final boolean readOnly) {
        PathEntry pathEntry;
        synchronized (pathEntries) {
            if (pathEntries.containsKey(pathName)) {
                throw ControllerLogger.ROOT_LOGGER.pathEntryAlreadyExists(pathName);
            }
            pathEntry = new PathEntry(pathName, path, relativeTo, readOnly, relativeTo == null ? absoluteResolver : relativeResolver);
            pathEntries.put(pathName, pathEntry);

            if (relativeTo != null) {
                addDependent(pathName, relativeTo);
            }
        }
        triggerCallbacksForEvent(pathEntry, Event.ADDED);
        return pathEntry;
    }

    /**
     * Updates the {@link PathEntry#getRelativeTo() relative to} value for an entry and sends an
     * {@link org.jboss.as.controller.services.path.PathManager.Event#UPDATED}
     * notification to any registered
     * {@linkplain org.jboss.as.controller.services.path.PathManager.Callback#pathEvent(Event, PathEntry) callbacks}.
     * @param pathName the logical name of the path within the model. Cannot be {@code null}
     * @param relativePath the new name of the path this path is relative to. If {@code null} this is an absolute path
     * @param check {@code true} if a check for the existence of an entry for the new {@code relativePath} value
     *                          should be performed
     * @throws OperationFailedException if {@code check} is {@code true} and no path exists whose name matches {@code relativePath}
     */
    final void changeRelativePath(String pathName, String relativePath, boolean check) throws OperationFailedException {
        PathEntry pathEntry = findPathEntry(pathName);
        synchronized (pathEntries) {
            if (check && relativePath != null && pathEntries.get(relativePath) == null) {
                // TODO per method signature and usage in PathWriteAttributeHandler this should throw OFE.
                // But leave it for now as a better way to deal with this is to have capabilities for paths
                // and use capability resolution to detect invalid references
                throw ControllerLogger.ROOT_LOGGER.pathEntryNotFound(pathName);
            }
            if (pathEntry.getRelativeTo() != null) {
                Set<String> dependents = dependenctRelativePaths.get(pathEntry.getRelativeTo());
                dependents.remove(pathEntry.getName());
            }
            pathEntry.setRelativeTo(relativePath);
            pathEntry.setPathResolver(relativePath == null ? absoluteResolver : relativeResolver);
            addDependent(pathEntry.getName(), pathEntry.getRelativeTo());
        }
        triggerCallbacksForEvent(pathEntry, Event.UPDATED);
    }

    /**
     * Updates the {@link PathEntry#getPath() path} value for an entry and sends an
     * {@link org.jboss.as.controller.services.path.PathManager.Event#UPDATED}
     * notification to any registered
     * {@linkplain org.jboss.as.controller.services.path.PathManager.Callback#pathEvent(Event, PathEntry) callbacks}.
     * @param pathName the logical name of the path within the model. Cannot be {@code null}
     * @param path     the value of the path within the model. This is either an absolute path or
     *                 the relative portion of the path. Cannot be {@code null}
     */
    final void changePath(String pathName, String path) {
        PathEntry pathEntry = findPathEntry(pathName);
        pathEntry.setPath(path);
        triggerCallbacksForEvent(pathEntry, Event.UPDATED);
    }

    //Must be called with pathEntries lock taken
    private void addDependent(String pathName, String relativeTo) {
        if (relativeTo != null) {
            Set<String> dependents = dependenctRelativePaths.get(relativeTo);
            if (dependents == null) {
                dependents = new HashSet<String>();
                dependenctRelativePaths.put(relativeTo, dependents);
            }
            dependents.add(pathName);
        }
    }


    private PathEntry findPathEntry(String pathName) {
        PathEntry pathEntry;
        synchronized (pathEntries) {
            pathEntry = pathEntries.get(pathName);
            if (pathEntry == null) {
                throw ControllerLogger.ROOT_LOGGER.pathEntryNotFound(pathName);
            }
        }
        return pathEntry;
    }

    private void triggerCallbacksForEvent(PathEntry pathEntry, Event event) {
        Set<PathEntry> allEntries = null;
        synchronized (pathEntries) {
            if (event == Event.UPDATED) {
                allEntries = new LinkedHashSet<PathEntry>();
                allEntries.add(pathEntry);
                getAllDependents(allEntries, pathEntry.getName());
            } else {
                allEntries = Collections.singleton(pathEntry);
            }
        }

        Map<PathEntry, Set<Callback>> triggerCallbacks = new LinkedHashMap<PathEntry, Set<Callback>>();
        synchronized (callbacks) {
            for (PathEntry pe : allEntries) {
                Map<Event, Set<Callback>> callbacksByEvent = callbacks.get(pe.getName());
                if (callbacksByEvent != null) {
                    Set<Callback> callbacksForEntry = callbacksByEvent.get(event);
                    if (callbacksForEntry != null) {
                        triggerCallbacks.put(pe, new LinkedHashSet<Callback>(callbacksForEntry));
                    }
                }
            }
        }

        for (Map.Entry<PathEntry, Set<Callback>> entry : triggerCallbacks.entrySet()) {
            for (Callback cb : entry.getValue()) {
                cb.pathEvent(event, entry.getKey());
            }
        }
    }

    /**
     * Creates a {@link org.jboss.as.controller.services.path.PathManager.PathEventContext} and passes it to the
     * {@link org.jboss.as.controller.services.path.PathManager.Callback#pathModelEvent(PathEventContext, String)} method
     * of any callbacks to allow the to record if the event should trigger a restart or reload required state. The caller
     * can then use the {@link PathEventContextImpl#isInstallServices()} method to check if further updates to
     * the path manager should be made.
     * @param operationContext the operation context associated with the management operation making this request
     * @param name  the name of the relevant path. Cannot be {@code null}
     * @param event the event. Cannot be {@code null}
     * @return the path event context to use to check if further updates should be made
     */
    PathEventContextImpl checkRestartRequired(OperationContext operationContext, String name, Event event) {
        Set<String> allEntries = null;
        synchronized (pathEntries) {
            if (event == Event.UPDATED) {
                allEntries = new LinkedHashSet<String>();
                allEntries.add(name);
                getAllDependentsForRestartCheck(allEntries, name);
            } else {
                allEntries = Collections.singleton(name);
            }
        }

        Map<String, Set<Callback>> triggerCallbacks = new LinkedHashMap<String, Set<Callback>>();
        synchronized (callbacks) {
            for (String pathName : allEntries) {
                Map<Event, Set<Callback>> callbacksByEvent = callbacks.get(pathName);
                if (callbacksByEvent != null) {
                    Set<Callback> callbacksForEntry = callbacksByEvent.get(event);
                    if (callbacksForEntry != null) {
                        triggerCallbacks.put(pathName, new LinkedHashSet<Callback>(callbacksForEntry));
                    }
                }
            }
        }

        PathEventContextImpl pathEventContext = new PathEventContextImpl(operationContext, event);
        for (Map.Entry<String, Set<Callback>> entry : triggerCallbacks.entrySet()) {
            for (Callback cb : entry.getValue()) {
                cb.pathModelEvent(pathEventContext, entry.getKey());
                if (pathEventContext.restart) {
                    return pathEventContext;
                }
            }
        }
        return pathEventContext;
    }

    //Call with pathEntries lock taken
    private void getAllDependents(Set<PathEntry> result, String name) {
        Set<String> depNames = dependenctRelativePaths.get(name);
        if (depNames == null) {
            return;
        }
        for (String dep : depNames) {
            PathEntry entry = pathEntries.get(dep);
            if (entry != null) {
                result.add(entry);
                getAllDependents(result, dep);
            }
        }
    }

    //Call with pathEntries lock taken
    private void getAllDependentsForRestartCheck(Set<String> result, String name) {
        Set<String> depNames = dependenctRelativePaths.get(name);
        if (depNames == null) {
            return;
        }
        for (String dep : depNames) {
            PathEntry entry = pathEntries.get(dep);
            if (entry != null) {
                result.add(dep);
                getAllDependentsForRestartCheck(result, dep);
            }
        }
    }


    private final PathResolver absoluteResolver = new PathResolver() {
        @Override
        public String resolvePath(String name, String path, String relativeTo, PathResolver resolver) {
            return AbsolutePathService.convertPath(path);
        }

        @Override
        public boolean isResolved(String relativeTo) {
            return true;
        }
    };

    private final PathResolver relativeResolver = new PathResolver() {

        @Override
        public String resolvePath(String name,  String path, String relativeTo, PathResolver resolver) {
            PathEntry relativeEntry;
            synchronized (pathEntries) {
                relativeEntry = pathEntries.get(relativeTo);
                if (relativeEntry == null) {
                    throw new IllegalStateException("Could not find relativeTo path '" + relativeTo + "' for relative path '" + name);
                }
            }
            return RelativePathService.doResolve(relativeEntry.resolvePath(), path);
        }

        @Override
        public boolean isResolved(String relativeTo) {
            synchronized (pathEntries) {
                return pathEntries.containsKey(relativeTo);
            }

        }
    };

    private class HandleImpl implements Handle {
        private final String pathName;
        private final Callback callback;
        private final Event[] events;

        public HandleImpl(String pathName, Callback callback, Event...events) {
            this.pathName = pathName;
            this.callback = callback;
            this.events = events;
        }

        public void remove() {
            synchronized (callbacks) {
                Map<Event, Set<Callback>> callbacksByEvent = callbacks.get(pathName);
                if (callbacksByEvent != null) {
                    for (Event event : events) {
                        Set<Callback> callbackSet = callbacksByEvent.get(event);
                        if (callbackSet != null) {
                            callbackSet.remove(callback);
                        }
                        if (callbackSet != null && callbackSet.isEmpty()) {
                            callbacksByEvent.remove(event);
                        }
                    }
                    if (callbacksByEvent.isEmpty()) {
                        callbacks.remove(pathName);
                    }
                }
            }
        }
    }

    static class PathEventContextImpl implements PathEventContext {
        private final OperationContext operationContext;
        private final Event event;
        private volatile boolean reload;
        private volatile boolean restart;

        PathEventContextImpl(OperationContext operationContext, Event event) {
            this.operationContext = operationContext;
            this.event = event;
        }

        public boolean isBooting() {
            return operationContext.isBooting();
        }

        public boolean isNormalServer() {
            return operationContext.isNormalServer();
        }

        public boolean isResourceServiceRestartAllowed() {
            return operationContext.isResourceServiceRestartAllowed();
        }

        public void reloadRequired() {
            reload = true;
            operationContext.reloadRequired();
        }

        public void restartRequired() {
            restart = true;
            operationContext.restartRequired();
        }

        @Override
        public Event getEvent() {
            return event;
        }

        void revert() {
            if (restart) {
                operationContext.revertRestartRequired();
            }
            if (reload) {
                operationContext.revertReloadRequired();
            }
        }

        boolean isInstallServices() {
            return !restart && !reload;
        }
    }
}
