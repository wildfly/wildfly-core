/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.registry;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.PathAddressTransformer;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.TransformerEntry;
import org.jboss.dmr.ModelNode;

/**
 * Resolved/unversioned operation transformer registry.
 *
 * @author Emanuel Muckenhuber
 */
public class OperationTransformerRegistry {

    private final PathAddressTransformer pathAddressTransformer;
    private final ResourceTransformerEntry resourceTransformer;
    private final OperationTransformerEntry defaultTransformer;
    private final boolean placeholder;
    private volatile Map<String, SubRegistry> subRegistries;
    private volatile Map<String, OperationTransformerEntry> transformerEntries;

    private static final AtomicMapFieldUpdater<OperationTransformerRegistry, String, SubRegistry> subRegistriesUpdater = AtomicMapFieldUpdater.newMapUpdater(AtomicReferenceFieldUpdater.newUpdater(OperationTransformerRegistry.class, Map.class, "subRegistries"));
    private static final AtomicMapFieldUpdater<OperationTransformerRegistry, String, OperationTransformerEntry> entriesUpdater = AtomicMapFieldUpdater.newMapUpdater(AtomicReferenceFieldUpdater.newUpdater(OperationTransformerRegistry.class, Map.class, "transformerEntries"));

    protected OperationTransformerRegistry(final PathAddressTransformer pathAddressTransformer, final ResourceTransformerEntry resourceTransformer, final OperationTransformerEntry defaultTransformer, final boolean placeholder) {
        entriesUpdater.clear(this);
        subRegistriesUpdater.clear(this);
        this.defaultTransformer = defaultTransformer;
        this.resourceTransformer = resourceTransformer;
        this.pathAddressTransformer = pathAddressTransformer;
        this.placeholder = placeholder;
    }

    public TransformerEntry getTransformerEntry(final PathAddress address, PlaceholderResolver placeholderResolver) {
        return resolveTransformerEntry(address.iterator(), placeholderResolver);
    }

    protected TransformerEntry getTransformerEntry() {
        return new TransformerEntry() {
            @Override
            public PathAddressTransformer getPathTransformation() {
                return pathAddressTransformer;
            }

            @Override
            public ResourceTransformer getResourceTransformer() {
                return resourceTransformer.getTransformer();
            }
        };
    }

    /**
     * Resolve a resource transformer for a given address.
     *
     * @param address the address
     * @param placeholderResolver a placeholder resolver used to resolve children of a placeholder registration
     * @return the resource transformer
     */
    public ResourceTransformerEntry resolveResourceTransformer(final PathAddress address, final PlaceholderResolver placeholderResolver) {
        return resolveResourceTransformer(address.iterator(), null, placeholderResolver);
    }

    /**
     * Resolve an operation transformer entry.
     *
     * @param address the address
     * @param operationName the operation name
     * @param placeholderResolver a placeholder resolver used to resolve children of a placeholder registration
     * @return the transformer entry
     */
    public OperationTransformerEntry resolveOperationTransformer(final PathAddress address, final String operationName, PlaceholderResolver placeholderResolver) {
        final Iterator<PathElement> iterator = address.iterator();
        final OperationTransformerEntry entry = resolveOperationTransformer(iterator, operationName, placeholderResolver);
        if(entry != null) {
            return entry;
        }
        // Default is forward unchanged
        return FORWARD;
    }

    /**
     * Merge a new subsystem from the global registration.
     *
     * @param registry the global registry
     * @param subsystemName the subsystem name
     * @param version the subsystem version
     */
    public void mergeSubsystem(final GlobalTransformerRegistry registry, String subsystemName, ModelVersion version) {
        final PathElement element = PathElement.pathElement(SUBSYSTEM, subsystemName);
        registry.mergeSubtree(this, PathAddress.EMPTY_ADDRESS.append(element), version);
    }

    /**
     * Get a list of path transformers for a given address.
     *
     * @param address the path address
     * @param placeholderResolver a placeholder resolver used to resolve children of a placeholder registration
     * @return a list of path transformations
     */
    public List<PathAddressTransformer> getPathTransformations(final PathAddress address, PlaceholderResolver placeholderResolver) {
        final List<PathAddressTransformer> list = new ArrayList<PathAddressTransformer>();
        final Iterator<PathElement> iterator = address.iterator();
        resolvePathTransformers(iterator, list, placeholderResolver);
        if(iterator.hasNext()) {
            while(iterator.hasNext()) {
                iterator.next();
                list.add(PathAddressTransformer.DEFAULT);
            }
        }
        return list;
    }

    public OperationTransformerRegistry getChild(final PathAddress address) {
        final Iterator<PathElement> iterator = address.iterator();
        return resolveChild(iterator);
    }

    public boolean isPlaceholder() {
        return placeholder;
    }

    private TransformerEntry resolveTransformerEntry(Iterator<PathElement> iterator, PlaceholderResolver placeholderResolver) {
        if(!iterator.hasNext()) {
            if (placeholder && placeholderResolver != null) {
                return placeholderResolver.resolveTransformerEntry(iterator);
            } else {
                return getTransformerEntry();
            }
        } else {
            final PathElement element = iterator.next();
            SubRegistry sub = subRegistriesUpdater.get(this, element.getKey());
            if(sub == null) {
                return null;
            }
            final OperationTransformerRegistry registry = sub.get(element.getValue());
            if(registry == null) {
                return null;
            }
            if (registry.placeholder) {
                return placeholderResolver != null ? placeholderResolver.resolveTransformerEntry(iterator) : registry.getTransformerEntry();
            } else {
                return registry.resolveTransformerEntry(iterator, placeholderResolver);
            }
        }
    }

    ResourceTransformerEntry getResourceTransformer() {
        return resourceTransformer;
    }

    private OperationTransformerRegistry resolveChild(final Iterator<PathElement> iterator) {

        if(! iterator.hasNext()) {
            return this;
        } else {
            final PathElement element = iterator.next();
            SubRegistry sub = subRegistriesUpdater.get(this, element.getKey());
            if(sub == null) {
                return null;
            }
            return sub.get(element.getValue(), iterator);
        }
    }

    private void resolvePathTransformers(Iterator<PathElement> iterator, List<PathAddressTransformer> list, PlaceholderResolver placeholderResolver) {
        if(iterator.hasNext()) {
            final PathElement element = iterator.next();
            SubRegistry sub = subRegistriesUpdater.get(this, element.getKey());
            if(sub != null) {
                final OperationTransformerRegistry reg = sub.get(element.getValue());
                if(reg != null) {
                    list.add(reg.getPathAddressTransformer());
                    if (reg.isPlaceholder() && placeholderResolver != null) {
                        placeholderResolver.resolvePathTransformers(iterator, list);
                    } else {
                        reg.resolvePathTransformers(iterator, list, placeholderResolver);
                    }
                    return;
                }
            }
            list.add(PathAddressTransformer.DEFAULT);
            return;
        }
    }

    PathAddressTransformer getPathAddressTransformer() {
        return pathAddressTransformer;
    }

    void registerTransformer(final PathAddress address, final String operationName, final OperationTransformer transformer) {
        registerTransformer(address.iterator(), operationName, new OperationTransformerEntry(transformer, false));
    }

    public OperationTransformerEntry getDefaultTransformer() {
        return defaultTransformer;
    }

    Map<String, OperationTransformerEntry> getTransformers() {
        return entriesUpdater.get(this);
    }

    OperationTransformerRegistry createChildRegistry(final Iterator<PathElement> iterator, PathAddressTransformer pathAddressTransformer, ResourceTransformerEntry resourceTransformer, OperationTransformerEntry defaultTransformer, boolean placeholder) {
        if(!iterator.hasNext()) {
            return this;
        } else {
            final PathElement element = iterator.next();
            return getOrCreate(element.getKey()).createChild(iterator, element.getValue(), pathAddressTransformer, resourceTransformer, defaultTransformer, placeholder);
        }
    }

    void registerTransformer(final Iterator<PathElement> iterator, String operationName, OperationTransformerEntry entry) {
        if(! iterator.hasNext()) {
            final OperationTransformerEntry existing = entriesUpdater.putIfAbsent(this, operationName, entry);
            if(existing != null) {
                throw new IllegalStateException("duplicate transformer " + operationName);
            }
        } else {
            final PathElement element = iterator.next();
            getOrCreate(element.getKey()).registerTransformer(iterator, element.getValue(), operationName, entry);
        }
    }

    private ResourceTransformerEntry resolveResourceTransformer(final Iterator<PathElement> iterator, final ResourceTransformerEntry inherited, final PlaceholderResolver placeholderResolver) {
        if(! iterator.hasNext()) {
            if(resourceTransformer == null) {
                return inherited;
            }
            return resourceTransformer;
        } else {
            final ResourceTransformerEntry inheritedEntry = resourceTransformer.inherited ? resourceTransformer : inherited;
            final PathElement element = iterator.next();
            final String key = element.getKey();
            SubRegistry registry = subRegistriesUpdater.get(this, key);
            if(registry == null) {
                return inherited;
            }
            return registry.resolveResourceTransformer(iterator, element.getValue(), inheritedEntry, placeholderResolver);
        }
    }

    private OperationTransformerEntry resolveOperationTransformer(final Iterator<PathElement> iterator, final String operationName, final PlaceholderResolver placeholderResolver) {
        if(! iterator.hasNext()) {
            if (placeholder && placeholderResolver != null) {
                return placeholderResolver.resolveOperationTransformer(iterator, operationName);
            } else {
                final OperationTransformerEntry entry = entriesUpdater.get(this, operationName);
                if(entry == null) {
                    return defaultTransformer;
                }
                return entry;
            }
        } else {
            final PathElement element = iterator.next();
            final String key = element.getKey();
            SubRegistry sub = subRegistriesUpdater.get(this, key);
            OperationTransformerEntry entry = null;
            if(sub != null) {
                final OperationTransformerRegistry registry = sub.get(element.getValue());
                if (registry != null) {
                    if (registry.placeholder) {
                        entry = placeholderResolver != null ? placeholderResolver.resolveOperationTransformer(iterator, operationName) : registry.getDefaultTransformer();
                    } else {
                        entry = registry.resolveOperationTransformer(iterator, operationName, placeholderResolver);
                    }
                }
                if (entry != null) {
                    return entry;
                }
            }
            //Look for inherited entries
            entry = entriesUpdater.get(this, operationName);
            if (entry != null && entry.isInherited()) {
                return entry;
            }
            entry = defaultTransformer;
            if (entry != null && entry.isInherited()) {
                return entry;
            }
            return null;
        }
    }


    private SubRegistry getOrCreate(final String key) {
        for (;;) {
            final Map<String, SubRegistry> subRegistries = subRegistriesUpdater.get(this);
            SubRegistry registry = subRegistries.get(key);
            if (registry != null) {
                return registry;
            } else {
                registry = new SubRegistry();
                if(subRegistriesUpdater.putAtomic(this, key, registry, subRegistries)) {
                    return registry;
                }
            }
            return registry;
        }
    }

    private static class SubRegistry {

        private static final AtomicMapFieldUpdater<SubRegistry, String, OperationTransformerRegistry> childrenUpdater = AtomicMapFieldUpdater.newMapUpdater(AtomicReferenceFieldUpdater.newUpdater(SubRegistry.class, Map.class, "entries"));
        private volatile Map<String, OperationTransformerRegistry> entries;

        private SubRegistry() {
            childrenUpdater.clear(this);
        }

        private OperationTransformerRegistry createChild(Iterator<PathElement> iterator, String value, final PathAddressTransformer pathAddressTransformer, ResourceTransformerEntry resourceTransformer, OperationTransformerEntry defaultTransformer, boolean placeholder) {
            if(! iterator.hasNext()) {
                return create(value, pathAddressTransformer, resourceTransformer, defaultTransformer, placeholder);
            } else {
                OperationTransformerRegistry entry = get(value);
                if(entry == null) {
                    entry = create(value, PathAddressTransformer.DEFAULT, GlobalTransformerRegistry.RESOURCE_TRANSFORMER, FORWARD, placeholder);
                }
                return entry.createChildRegistry(iterator, pathAddressTransformer, resourceTransformer, defaultTransformer, placeholder);
            }
        }

        private void registerTransformer(Iterator<PathElement> iterator, String value, String operationName,  OperationTransformerEntry entry) {
            OperationTransformerRegistry operationTransformerRegistry = get(value);
            if (operationTransformerRegistry == null) {
                throw ROOT_LOGGER.operationTransformerRegistryIsNull(value);
            }
            operationTransformerRegistry.registerTransformer(iterator, operationName, entry);
        }

        private OperationTransformerRegistry get(final String value) {
            OperationTransformerRegistry entry = childrenUpdater.get(this, value);
            if(entry == null) {
                entry = childrenUpdater.get(this, "*");
                if(entry == null) {
                    return null;
                }
            }
            return entry;
        }

        private OperationTransformerRegistry get(final String value, Iterator<PathElement> iterator) {
            OperationTransformerRegistry entry = childrenUpdater.get(this, value);
            if(entry == null) {
                entry = childrenUpdater.get(this, "*");
                if(entry == null) {
                    return null;
                }
            }
            return entry.resolveChild(iterator);
        }

        private OperationTransformerRegistry create(final String value, final PathAddressTransformer pathAddressTransformer, final ResourceTransformerEntry resourceTransformer, final OperationTransformerEntry defaultTransformer, boolean placeholder) {
            for(;;) {
                final Map<String, OperationTransformerRegistry> entries = childrenUpdater.get(this);
                OperationTransformerRegistry entry = entries.get(value);
                if(entry != null) {
                    return entry;
                } else {
                    entry = new OperationTransformerRegistry(pathAddressTransformer, resourceTransformer, defaultTransformer, placeholder);
                    if(childrenUpdater.putAtomic(this, value, entry, entries)) {
                        return entry;
                    }
                }
            }
        }

        private ResourceTransformerEntry resolveResourceTransformer(Iterator<PathElement> iterator, String value, ResourceTransformerEntry inheritedEntry, PlaceholderResolver placeholderResolver) {
            final OperationTransformerRegistry registry = get(value);
            if(registry == null) {
                return inheritedEntry;
            }
            return registry.resolveResourceTransformer(iterator, inheritedEntry, placeholderResolver);
        }
    }

    public static class ResourceTransformerEntry {

        private final ResourceTransformer transformer;
        private final boolean inherited;

        public ResourceTransformerEntry(ResourceTransformer transformer, boolean inherited) {
            this.transformer = transformer;
            this.inherited = inherited;
        }

        public ResourceTransformer getTransformer() {
            return transformer;
        }

        public boolean isInherited() {
            return inherited;
        }
    }

    public static class OperationTransformerEntry {

        final OperationTransformer transformer;
        final boolean inherited;

        public OperationTransformerEntry(OperationTransformer transformer, boolean inherited) {
            this.transformer = transformer;
            this.inherited = inherited;
        }

        public OperationTransformer getTransformer() {
            return transformer;
        }

        public boolean isInherited() {
            return inherited;
        }


    }

    static final OperationTransformer FORWARD_TRANSFORMER = new OperationTransformer() {

        @Override
        public TransformedOperation transformOperation(TransformationContext context, PathAddress address, ModelNode operation) {
            return new TransformedOperation(operation, OperationResultTransformer.ORIGINAL_RESULT);
        }
    };

    static final OperationTransformer DISCARD_TRANSFORMER = OperationTransformer.DISCARD;

    public static final OperationTransformerEntry DISCARD = new OperationTransformerEntry(DISCARD_TRANSFORMER, true);
    public static final OperationTransformerEntry FORWARD = new OperationTransformerEntry(FORWARD_TRANSFORMER, false);

    /**
     * An extra resolver to be used for {@link OperationTransformerRegistry} entries where {@code placeholder==true}. These placeholder entries transformers should create
     * a new {@link org.jboss.as.controller.transform.TransformationTarget} containing the {@code PlaceholderResolver} and resolve the children themselves.
     * Note that if a place holder resolver is used at a given resource address, this takes precedence over the normal transformer registry, so all children
     * must use the placeholders.
     */
    public interface PlaceholderResolver {
        /**
         * Resolves a resource transformer from the relative address of the current {@code OperationTransformerRegistry} entry
         *
         *  @param iterator an iterator of the path elements of the resource we want to transform. On the initial call, this will be at the address of the placeholder entry
         *  @param operationName the name of the operation transformer to resolve
         * @return the operation transformer, or {@code null} if not found
         */
        OperationTransformerEntry resolveOperationTransformer(final Iterator<PathElement> iterator, final String operationName);

        /**
         * Adds path address transformers to the list for the relative address and below of the current {@code OperationTransformerRegistry} entry
         *
         * @param iterator an iterator of the path elements of the resource we want to transform. On the initial call, this will be at the address of the placeholder entry
         * @param list the list of path address transformers to add the results to
         */
        void resolvePathTransformers(Iterator<PathElement> iterator, List<PathAddressTransformer> list);

        /**
         * Resolves a {@link TransformerEntry} from the relative address of the current {@code OperationTransformerRegistry} entry
         * @param iterator an iterator of the path elements of the resource we want to transform. On the initial call, this will be at the address of the placeholder entry
         * @return the transformer entry, or {@code null} if not found
         */
        TransformerEntry resolveTransformerEntry(Iterator<PathElement> iterator);
    }
}
