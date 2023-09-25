/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Abstract {@code Resource} implementation.
 *
 * <p>Concurrency note: this class is *not* thread safe</p>
 *
 * @author Emanuel Muckenhuber
 */
public abstract class AbstractModelResource extends ResourceProvider.ResourceProviderRegistry implements Resource {

    /** The children. */
    private final Map<String, ResourceProvider> children = new LinkedHashMap<String, ResourceProvider>();
    private final boolean runtimeOnly;
    private final Set<String> orderedChildTypes;

    protected AbstractModelResource() {
        this(false);
    }

    protected AbstractModelResource(boolean runtimeOnly) {
        this(runtimeOnly, Collections.emptySet(), true);
    }

    protected AbstractModelResource(boolean runtimeOnly, String...orderedChildTypes) {
        this(runtimeOnly, arrayToSet(orderedChildTypes), true);
    }

    protected AbstractModelResource(boolean runtimeOnly, Set<String> orderedChildTypes) {
        this(runtimeOnly, orderedChildTypes, false);
    }

    AbstractModelResource(boolean runtimeOnly, Set<String> orderedChildTypes, boolean safe) {
        this.runtimeOnly = runtimeOnly;
        this.orderedChildTypes = safe && orderedChildTypes != null
                ? orderedChildTypes
                : (orderedChildTypes == null || orderedChildTypes.isEmpty())
                    ? Collections.<String>emptySet()
                    : Collections.unmodifiableSet(new HashSet<>(orderedChildTypes));
    }

    private static Set<String> arrayToSet(String[] array) {
        Set<String> set;
        if (array.length == 0) {
            set = Collections.emptySet();
        } else {
            set = new HashSet<String>();
            for (String type : array) {
                set.add(type);
            }
            set = Collections.unmodifiableSet(set);
        }
        return set;
    }

    @Override
    public Resource getChild(final PathElement address) {
        final ResourceProvider provider = getProvider(address.getKey());
        if(provider == null) {
            return null;
        }
        return provider.get(address.getValue());
    }

    @Override
    public boolean hasChild(final PathElement address) {
        final ResourceProvider provider = getProvider(address.getKey());
        if(provider == null) {
            return false;
        }
        if(address.isWildcard()) {
            return provider.hasChildren();
        }
        return provider.has(address.getValue());
    }

    @Override
    public Resource requireChild(final PathElement address) {
        final Resource resource = getChild(address);
        if(resource == null) {
            throw new NoSuchResourceException(address);
        }
        return resource;
    }

    @Override
    public boolean hasChildren(final String childType) {
        final ResourceProvider provider = getProvider(childType);
        return provider != null && provider.hasChildren();
    }

    @Override
    public Resource navigate(final PathAddress address) {
        return Tools.navigate(this, address);
    }

    @Override
    public Set<String> getChildrenNames(final String childType) {
        final ResourceProvider provider = getProvider(childType);
        if(provider == null) {
            return Collections.emptySet();
        }
        return provider.children();
    }

    @Override
    public Set<String> getChildTypes() {
        synchronized (children) {
            return new LinkedHashSet<String>(children.keySet());
        }
    }

    @Override
    public Set<ResourceEntry> getChildren(final String childType) {
        final ResourceProvider provider = getProvider(childType);
        if(provider == null) {
            return Collections.emptySet();
        }
        final Set<ResourceEntry> children = new LinkedHashSet<ResourceEntry>();
        for(final String name : provider.children()) {
            final Resource resource = provider.get(name);
            children.add(new DelegateResource(resource) {
                @Override
                public String getName() {
                    return name;
                }

                @Override
                public PathElement getPathElement() {
                    return PathElement.pathElement(childType, name);
                }
            });
        }
        return children;
    }

    @Override
    public void registerChild(final PathElement address, final Resource resource) {
        if(address.isMultiTarget()) {
            throw new IllegalArgumentException();
        }
        getOrCreateProvider(address.getKey()).register(address.getValue(), resource);
    }

    @Override
    public void registerChild(final PathElement address, final int index, final Resource resource) {
        if(address.isMultiTarget()) {
            throw new IllegalArgumentException();
        }
        if (index >= 0 && !orderedChildTypes.contains(address.getKey())) {
            throw ControllerLogger.ROOT_LOGGER.indexedChildResourceRegistrationNotAvailable(address);
        }
        getOrCreateProvider(address.getKey()).register(address.getValue(), index, resource);
    }

    @Override
    public Resource removeChild(PathElement address) {
        synchronized (children) {
            final ResourceProvider provider = getProvider(address.getKey());
            if(provider == null) {
                return null;
            }
            final Resource removed = provider.remove(address.getValue());
            // Cleanup default resource providers
            if ((provider instanceof DefaultResourceProvider) && !provider.hasChildren()) {
                children.remove(address.getKey());
            }
            return removed;
        }
    }

    @Override
    public boolean isProxy() {
        return false;
    }

    @Override
    public boolean isRuntime() {
        return runtimeOnly;
    }

    @Override
    public Set<String> getOrderedChildTypes() {
        return orderedChildTypes;
    }

    protected void registerResourceProvider(final String type, final ResourceProvider provider) {
        synchronized (children) {
            if (children.containsKey(type)) {
                throw ControllerLogger.ROOT_LOGGER.duplicateResourceType(type);
            }
            children.put(type, provider);
        }
    }

    protected final ResourceProvider getProvider(final String type) {
        synchronized (children) {
            return children.get(type);
        }
    }

    protected ResourceProvider getOrCreateProvider(final String type) {
        synchronized (children) {
            final ResourceProvider provider = children.get(type);
            if(provider != null) {
                return provider;
            } else {
                final ResourceProvider newProvider = new DefaultResourceProvider();
                children.put(type, newProvider);
                return newProvider;
            }
        }
    }

    @Override
    public abstract Resource clone();

    protected void cloneProviders(AbstractModelResource clone) {
        synchronized (children) {
            for (final Map.Entry<String, ResourceProvider> entry : children.entrySet()) {
                clone.registerResourceProvider(entry.getKey(), entry.getValue().clone());
            }
        }
    }

    private static class DefaultResourceProvider implements ResourceProvider {

        private final Map<String, Resource> children = new LinkedHashMap<String, Resource>();

        protected DefaultResourceProvider() {
        }

        @Override
        public Set<String> children() {
            synchronized (children) {
                return new LinkedHashSet<String>(children.keySet());
            }
        }

        @Override
        public boolean has(String name) {
            synchronized (children) {
                return children.get(name) != null;
            }
        }

        @Override
        public Resource get(String name) {
            synchronized (children) {
                return children.get(name);
            }
        }

        @Override
        public boolean hasChildren() {
            return ! children().isEmpty();
        }

        @Override
        public void register(String name, Resource resource) {
            synchronized (children) {
                if (children.containsKey(name)) {
                    throw ControllerLogger.ROOT_LOGGER.duplicateResource(name);
                }
                children.put(name, resource);
            }
        }

        @Override
        public void register(String name, int index, Resource resource) {
            synchronized (children) {
                if (children.containsKey(name)) {
                    throw ControllerLogger.ROOT_LOGGER.duplicateResource(name);
                }

                if (index < 0 || index >= children.size()) {
                    children.put(name, resource);
                } else {
                    List<Map.Entry<String, Resource>> list = new ArrayList<Map.Entry<String,Resource>>(children.entrySet());
                    children.clear();
                    boolean done = false;
                    int i = 0;
                    for (Map.Entry<String, Resource> entry : list) {
                        if (!done) {
                            if (i++ < index) {
                                children.put(entry.getKey(), entry.getValue());
                            } else {
                                children.put(name, resource);
                                children.put(entry.getKey(), entry.getValue());
                                done = true;
                            }
                        } else {
                            children.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
        }

        @Override
        public Resource remove(String name) {
            synchronized (children) {
                return children.remove(name);
            }
        }

        @Override
        public ResourceProvider clone() {
            final DefaultResourceProvider provider = new DefaultResourceProvider();
            synchronized (children) {
                for (final Map.Entry<String, Resource> entry : children.entrySet()) {
                    provider.register(entry.getKey(), entry.getValue().clone());
                }
            }
            return provider;
        }
    }

    abstract static class DelegateResource implements ResourceEntry {
        final Resource delegate;
        protected DelegateResource(Resource delegate) {
            this.delegate = checkNotNullParam("delegate", delegate);
        }

        @Override
        public Resource getChild(PathElement element) {
            return delegate.getChild(element);
        }

        @Override
        public Set<ResourceEntry> getChildren(String childType) {
            return delegate.getChildren(childType);
        }

        @Override
        public Set<String> getChildrenNames(String childType) {
            return delegate.getChildrenNames(childType);
        }

        @Override
        public Set<String> getChildTypes() {
            return delegate.getChildTypes();
        }

        @Override
        public ModelNode getModel() {
            return delegate.getModel();
        }

        @Override
        public boolean hasChild(PathElement element) {
            return delegate.hasChild(element);
        }

        @Override
        public boolean hasChildren(String childType) {
            return delegate.hasChildren(childType);
        }

        @Override
        public boolean isModelDefined() {
            return delegate.isModelDefined();
        }

        @Override
        public Resource navigate(PathAddress address) {
            return delegate.navigate(address);
        }

        @Override
        public void registerChild(PathElement address, Resource resource) {
            delegate.registerChild(address, resource);
        }

        @Override
        public void registerChild(PathElement address, int index, Resource resource) {
            delegate.registerChild(address, index, resource);
        }

        @Override
        public Resource removeChild(PathElement address) {
            return delegate.removeChild(address);
        }

        @Override
        public Resource requireChild(PathElement element) {
            return delegate.requireChild(element);
        }

        @Override
        public void writeModel(ModelNode newModel) {
            delegate.writeModel(newModel);
        }

        @Override
        public boolean isRuntime() {
            return delegate.isRuntime();
        }

        @Override
        public boolean isProxy() {
            return delegate.isProxy();
        }

        public Set<String> getOrderedChildTypes() {
            return delegate.getOrderedChildTypes();
        }

        @SuppressWarnings({"CloneDoesntCallSuperClone"})
        @Override
        public Resource clone() {
           return delegate.clone();
        }

        @Override
        public int hashCode() {
            return this.getPathElement().hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof ResourceEntry)) return false;
            return this.getPathElement().equals(((ResourceEntry) object).getPathElement());
        }
    }

}
