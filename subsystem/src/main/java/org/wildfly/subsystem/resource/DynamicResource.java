/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.management.Capabilities;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.notification.NotificationHandler;
import org.jboss.as.controller.registry.DelegatingResource;
import org.jboss.as.controller.registry.NotificationHandlerRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.ServiceInstaller;

/**
 * A generic {@link Resource} decorator augmented to support additional runtime children.
 * @author Paul Ferraro
 */
public class DynamicResource extends DelegatingResource implements ResourceOperationRuntimeHandler, Function<String, ChildResourceProvider> {
    // Encapsulates everything needed at runtime trigger notifications
    interface NotificationConfiguration {
        PathAddress getParentAddress();
        NotificationHandlerRegistration getNotificationHandlerRegistration();
        Executor getExecutor();
    }

    private final Map<String, ChildResourceProvider> providers;
    private final BiFunction<Resource, Map<String, ChildResourceProvider>, Resource> factory;
    private final AtomicReference<NotificationConfiguration> configuration = new AtomicReference<>();
    private final ResourceOperationRuntimeHandler handler;

    /**
     * Constructs a new resource.
     * @param resource the concrete resource
     * @param providers a set of providers for specific child types
     */
    public DynamicResource(Resource resource, Map<String, ChildResourceProvider> providers) {
        this(resource, providers, DynamicResource::new);
    }

    /**
     * Constructs a new resource.
     * @param resource the concrete resource
     * @param providers a set of providers for specific child types
     * @param factory a function used to clone this resource
     */
    protected DynamicResource(Resource resource, Map<String, ChildResourceProvider> providers, BiFunction<Resource, Map<String, ChildResourceProvider>, Resource> factory) {
        super(resource);
        this.handler = (resource instanceof RuntimeHandlerProvider provider) ? provider.get() : ResourceOperationRuntimeHandler.configureService(new ResourceServiceConfigurator() {
            @Override
            public ResourceServiceInstaller configure(OperationContext context, ModelNode model) {
                PathAddress address = context.getCurrentAddress();
                ServiceDependency<NotificationHandlerRegistration> registration = ServiceDependency.on(NotificationHandlerRegistration.SERVICE_DESCRIPTOR);
                ServiceDependency<Executor> executor = ServiceDependency.on(Capabilities.MANAGEMENT_EXECUTOR);
                BiFunction<NotificationHandlerRegistration, Executor, NotificationConfiguration> factory = new BiFunction<>() {
                    @Override
                    public NotificationConfiguration apply(NotificationHandlerRegistration registration, Executor executor) {
                        return new NotificationConfiguration() {
                            @Override
                            public PathAddress getParentAddress() {
                                return address;
                            }

                            @Override
                            public NotificationHandlerRegistration getNotificationHandlerRegistration() {
                                return registration;
                            }

                            @Override
                            public Executor getExecutor() {
                                return executor;
                            }
                        };
                    }
                };
                // Create installer of service that captures values provided by services needed for notifications
                return ServiceInstaller.BlockingBuilder.of(registration.combine(executor, factory)).withCaptor(DynamicResource.this.configuration::set).build();
            }
        });
        // Decorate the providers of child resources, so that notifications are sent when children are dynamically added or removed
        this.providers = providers.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, this::wrap));
        this.factory = factory;
    }

    private ChildResourceProvider wrap(Map.Entry<String, ChildResourceProvider> entry) {
        String childType = entry.getKey();
        ChildResourceProvider provider = entry.getValue();
        // If this is a cloned resource, do not double wrap
        return (provider instanceof NotifyingChildResourceProvider) ? provider : new NotifyingChildResourceProvider(childType, provider, this.configuration);
    }

    @Override
    public void addRuntime(OperationContext context, ModelNode model) throws OperationFailedException {
        this.handler.addRuntime(context, model);
    }

    @Override
    public void removeRuntime(OperationContext context, ModelNode model) throws OperationFailedException {
        this.handler.removeRuntime(context, model);
    }

    @Override
    public ChildResourceProvider apply(String childType) {
        return this.providers.get(childType);
    }

    @Override
    public Resource clone() {
        return this.factory.apply(new RuntimeHandlerProviderResource(super.clone(), this.handler), this.providers);
    }

    @Override
    public Resource getChild(PathElement path) {
        ChildResourceProvider provider = this.apply(path.getKey());
        return (provider != null) ? provider.getChild(path.getValue()) : super.getChild(path);
    }

    @Override
    public Set<Resource.ResourceEntry> getChildren(String childType) {
        ChildResourceProvider provider = this.apply(childType);
        if (provider != null) {
            Set<String> names = provider.getChildren();
            Set<Resource.ResourceEntry> entries = !names.isEmpty() ? new HashSet<>() : Collections.emptySet();
            for (String name : names) {
                Resource resource = provider.getChild(name);
                entries.add(new SimpleResourceEntry(PathElement.pathElement(childType, name), resource));
            }
            return entries;
        }
        return super.getChildren(childType);
    }

    @Override
    public Set<String> getChildrenNames(String childType) {
        ChildResourceProvider provider = this.apply(childType);
        return (provider != null) ? provider.getChildren() : super.getChildrenNames(childType);
    }

    @Override
    public Set<String> getChildTypes() {
        Set<String> childTypes = new HashSet<>(super.getChildTypes());
        childTypes.addAll(this.providers.keySet());
        return childTypes;
    }

    @Override
    public boolean hasChild(PathElement path) {
        ChildResourceProvider provider = this.apply(path.getKey());
        return (provider != null) ? provider.getChild(path.getValue()) != null : super.hasChild(path);
    }

    @Override
    public boolean hasChildren(String childType) {
        ChildResourceProvider provider = this.apply(childType);
        return (provider != null) ? !provider.getChildren().isEmpty() : super.hasChildren(childType);
    }

    @Override
    public Resource navigate(PathAddress address) {
        return (address.size() == 1) ? this.requireChild(address.getLastElement()) : super.navigate(address);
    }

    @Override
    public Resource requireChild(PathElement path) {
        Resource resource = this.getChild(path);
        if (resource == null) {
            throw new NoSuchResourceException(path);
        }
        return resource;
    }

    private static class SimpleResourceEntry extends DelegatingResource implements Resource.ResourceEntry {

        private final PathElement path;

        SimpleResourceEntry(PathElement path, Resource resource) {
            super(resource);
            this.path = path;
        }

        @Override
        public String getName() {
            return this.path.getValue();
        }

        @Override
        public PathElement getPathElement() {
            return this.path;
        }

        @Override
        public int hashCode() {
            return this.path.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof Resource.ResourceEntry)) return false;
            return this.path.equals(((Resource.ResourceEntry) object).getPathElement());
        }

        @Override
        public String toString() {
            return this.path.toString();
        }
    }

    static class NotifyingChildResourceProvider implements ChildResourceProvider {
        private final String childType;
        private final ChildResourceProvider provider;
        private final AtomicReference<NotificationConfiguration> configuration;

        NotifyingChildResourceProvider(String childType, ChildResourceProvider provider, AtomicReference<NotificationConfiguration> configuration) {
            this.childType = childType;
            this.provider = provider;
            this.configuration = configuration;
        }

        @Override
        public Resource getChild(String name) {
            return this.provider.getChild(name);
        }

        @Override
        public Set<String> getChildren() {
            String childType = this.childType;
            NotificationConfiguration configuration = this.configuration.get();
            Set<String> children = this.provider.getChildren();
            return (configuration != null) ? new Set<>() {
                private void notifyAdded(String child) {
                    this.notify(ModelDescriptionConstants.RESOURCE_ADDED_NOTIFICATION, child);
                }

                private void notifyRemoved(String child) {
                    this.notify(ModelDescriptionConstants.RESOURCE_REMOVED_NOTIFICATION, child);
                }

                private void notify(String notificationType, String child) {
                    Notification notification = new Notification(notificationType, configuration.getParentAddress().append(childType, child), null);
                    Collection<NotificationHandler> handlers = configuration.getNotificationHandlerRegistration().findMatchingNotificationHandlers(notification);
                    if (!handlers.isEmpty()) {
                        configuration.getExecutor().execute(() -> handlers.forEach(handler -> handler.handleNotification(notification)));
                    }
                }

                @Override
                public boolean add(String child) {
                    boolean added = children.add(child);
                    if (added) {
                        this.notifyAdded(child);
                    }
                    return added;
                }

                @Override
                public boolean remove(Object child) {
                    boolean removed = children.remove(child);
                    if (removed) {
                        this.notifyRemoved((String) child);
                    }
                    return removed;
                }

                @Override
                public Iterator<String> iterator() {
                    Consumer<String> notifier = this::notifyRemoved;
                    Iterator<String> iterator = children.iterator();
                    return new Iterator<>() {
                        private String current = null;

                        @Override
                        public void remove() {
                            iterator.remove();
                            notifier.accept(this.current);
                        }

                        @Override
                        public boolean hasNext() {
                            return iterator.hasNext();
                        }

                        @Override
                        public String next() {
                            this.current = iterator.next();
                            return this.current;
                        }
                    };
                }

                @Override
                public int size() {
                    return children.size();
                }

                @Override
                public boolean isEmpty() {
                    return children.isEmpty();
                }

                @Override
                public boolean contains(Object child) {
                    return children.contains(child);
                }

                @Override
                public Object[] toArray() {
                    return children.toArray();
                }

                @Override
                public <T> T[] toArray(T[] array) {
                    return children.toArray(array);
                }

                @Override
                public boolean containsAll(Collection<?> elements) {
                    return children.containsAll(elements);
                }

                @Override
                public int hashCode() {
                    return children.hashCode();
                }

                @Override
                public boolean equals(Object object) {
                    return children.equals(object);
                }

                @Override
                public String toString() {
                    return children.toString();
                }

                // Optional methods, which cannot be implemented atomically
                @Override
                public boolean addAll(Collection<? extends String> newChildren) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean removeAll(Collection<?> oldChildren) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean retainAll(Collection<?> elements) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void clear() {
                    throw new UnsupportedOperationException();
                }
            } : children;
        }
    }

    interface RuntimeHandlerProvider extends Supplier<ResourceOperationRuntimeHandler> {
    }

    class RuntimeHandlerProviderResource extends DelegatingResource implements RuntimeHandlerProvider {

        private final ResourceOperationRuntimeHandler handler;

        RuntimeHandlerProviderResource(Resource resource, ResourceOperationRuntimeHandler handler) {
            super(resource);
            this.handler = handler;
        }

        @Override
        public ResourceOperationRuntimeHandler get() {
            return this.handler;
        }
    }
}
