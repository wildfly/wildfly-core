/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.PathAddressTransformer;
import org.jboss.as.controller.transform.ResourceTransformer;

/**
 * Versioned operation transformer registry.
 *
 * @author Emanuel Muckenhuber
 */
public class GlobalTransformerRegistry {

    private volatile Map<String, SubRegistry> subRegistries;
    private volatile Map<ModelVersion, OperationTransformerRegistry> versionedRegistries;

    private static final AtomicMapFieldUpdater<GlobalTransformerRegistry, String, SubRegistry> subRegistriesUpdater = AtomicMapFieldUpdater.newMapUpdater(AtomicReferenceFieldUpdater.newUpdater(GlobalTransformerRegistry.class, Map.class, "subRegistries"));
    private static final AtomicMapFieldUpdater<GlobalTransformerRegistry, ModelVersion, OperationTransformerRegistry> registryUpdater = AtomicMapFieldUpdater.newMapUpdater(AtomicReferenceFieldUpdater.newUpdater(GlobalTransformerRegistry.class, Map.class, "versionedRegistries"));

    public GlobalTransformerRegistry() {
        registryUpdater.clear(this);
        subRegistriesUpdater.clear(this);
    }

    /**
     * Discard an operation.
     *
     * @param address the operation handler address
     * @param major the major version
     * @param minor the minor version
     * @param operationName the operation name
     */
    public void discardOperation(final PathAddress address, int major, int minor, final String operationName) {
        registerTransformer(address.iterator(), ModelVersion.create(major, minor), operationName, OperationTransformerRegistry.DISCARD);
    }

    /**
     * Discard an operation.
     *
     * @param address the operation handler address
     * @param version the model version
     * @param operationName the operation name
     */
    public void discardOperation(final PathAddress address, ModelVersion version, final String operationName) {
        registerTransformer(address.iterator(), version, operationName, OperationTransformerRegistry.DISCARD);
    }

    /**
     * Register an operation transformer.
     *
     * @param address the operation handler address
     * @param major the major version
     * @param minor the minor version
     * @param operationName the operation name
     * @param transformer the operation transformer
     */
    public void registerTransformer(final PathAddress address, int major, int minor, String operationName, OperationTransformer transformer) {
        registerTransformer(address.iterator(), ModelVersion.create(major, minor), operationName, new OperationTransformerRegistry.OperationTransformerEntry(transformer, false));
    }

    public void createDiscardingChildRegistry(final PathAddress address, final ModelVersion version) {
        createChildRegistry(address.iterator(), version, PathAddressTransformer.DEFAULT, DISCARD, OperationTransformerRegistry.DISCARD, false);
    }

    public void createChildRegistry(final PathAddress address, final ModelVersion version, OperationTransformer transformer) {
        createChildRegistry(address.iterator(), version, PathAddressTransformer.DEFAULT, RESOURCE_TRANSFORMER, new OperationTransformerRegistry.OperationTransformerEntry(transformer, false), false);
    }

    public void createChildRegistry(final PathAddress address, final ModelVersion version, ResourceTransformer resourceTransformer, boolean inherited) {
        createChildRegistry(address.iterator(), version, PathAddressTransformer.DEFAULT, new OperationTransformerRegistry.ResourceTransformerEntry(resourceTransformer, inherited), OperationTransformerRegistry.FORWARD, false);
    }

    public void createChildRegistry(final PathAddress address, final ModelVersion version, ResourceTransformer resourceTransformer, OperationTransformer operationTransformer, boolean placeholder) {
        createChildRegistry(address, version, PathAddressTransformer.DEFAULT, resourceTransformer, operationTransformer, placeholder);
    }

    public void createChildRegistry(final PathAddress address, final ModelVersion version, PathAddressTransformer pathAddressTransformer, ResourceTransformer resourceTransformer, OperationTransformer operationTransformer, boolean placeholder) {
        createChildRegistry(address.iterator(), version, pathAddressTransformer, new OperationTransformerRegistry.ResourceTransformerEntry(resourceTransformer, false), new OperationTransformerRegistry.OperationTransformerEntry(operationTransformer, false), placeholder);
    }

    /**
     * Register an operation transformer.
     *
     * @param address the transformer address
     * @param version the model version
     * @param pathAddressTransformer the path address transformer
     * @param resourceTransformer the resource transformer
     * @param operationTransformer the operation transformer
     * @param inherited whether the transformers are inherited
     * @param placeholder if {@code true} the pathAddress-, resource-, and operationTransformers are responsible for handling children of their address via a {@link org.jboss.as.controller.registry.OperationTransformerRegistry.PlaceholderResolver}
     */
    public void createChildRegistry(final PathAddress address, final ModelVersion version, PathAddressTransformer pathAddressTransformer, ResourceTransformer resourceTransformer, OperationTransformer operationTransformer, boolean inherited, boolean placeholder) {
        createChildRegistry(address.iterator(), version, pathAddressTransformer, new OperationTransformerRegistry.ResourceTransformerEntry(resourceTransformer, false), new OperationTransformerRegistry.OperationTransformerEntry(operationTransformer, inherited), placeholder);
    }


    /**
     * Register an operation transformer.
     *
     * @param address the operation handler address
     * @param version the model version
     * @param operationName the operation name
     * @param transformer the operation transformer
     */
    public void registerTransformer(final PathAddress address, final ModelVersion version, String operationName, OperationTransformer transformer) {
        registerTransformer(address.iterator(), version, operationName, new OperationTransformerRegistry.OperationTransformerEntry(transformer, false));
    }

    public OperationTransformerRegistry mergeSubtree(final OperationTransformerRegistry parent, final PathAddress address, final Map<PathAddress, ModelVersion> subTree) {
        final OperationTransformerRegistry target = parent.createChildRegistry(address.iterator(), PathAddressTransformer.DEFAULT, RESOURCE_TRANSFORMER, OperationTransformerRegistry.FORWARD, false);
        mergeSubtree(target, subTree);
        return target;
    }

    /**
     * Merge a subtree.
     *
     * @param targetRegistry the target registry
     * @param subTree the subtree
     */
    public void mergeSubtree(final OperationTransformerRegistry targetRegistry, final Map<PathAddress, ModelVersion> subTree) {
        for(Map.Entry<PathAddress, ModelVersion> entry: subTree.entrySet()) {
            mergeSubtree(targetRegistry, entry.getKey(), entry.getValue());
        }
    }

    protected void mergeSubtree(final OperationTransformerRegistry targetRegistry, final PathAddress address, final ModelVersion version) {
        final GlobalTransformerRegistry child = navigate(address.iterator());
        if(child != null) {
            child.process(targetRegistry, address, version, Collections.<PathAddress, ModelVersion>emptyMap());
        }
    }

    public OperationTransformerRegistry create(final ModelVersion version, final Map<PathAddress, ModelVersion> versions) {
        final OperationTransformerRegistry registry = new OperationTransformerRegistry(PathAddressTransformer.DEFAULT, RESOURCE_TRANSFORMER, null, false);
        process(registry, PathAddress.EMPTY_ADDRESS, version, versions);
        return registry;
    }

    private void process(final OperationTransformerRegistry registry, final PathAddress address, final ModelVersion version, Map<PathAddress, ModelVersion> versions) {
        final OperationTransformerRegistry current = getRegistryUpdater(version);
        if(current != null) {
            final OperationTransformerRegistry.ResourceTransformerEntry resourceTransformer = current.getResourceTransformer();
            final OperationTransformerRegistry.OperationTransformerEntry defaultTransformer = current.getDefaultTransformer();
            registry.createChildRegistry(address.iterator(), current.getPathAddressTransformer(), resourceTransformer, defaultTransformer, current.isPlaceholder());
            final Map<String, OperationTransformerRegistry.OperationTransformerEntry> transformers = current.getTransformers();
            for(final Map.Entry<String, OperationTransformerRegistry.OperationTransformerEntry> transformer : transformers.entrySet()) {
                registry.registerTransformer(address, transformer.getKey(), transformer.getValue().getTransformer());
            }
        }
        final Map<String, SubRegistry> snapshot = subRegistriesUpdater.get(this);
        if(snapshot != null) {
            for(final Map.Entry<String, SubRegistry> registryEntry : snapshot.entrySet()) {
                //
                final String key = registryEntry.getKey();
                final SubRegistry subRegistry = registryEntry.getValue();
                final Map<String, GlobalTransformerRegistry> children = SubRegistry.childrenUpdater.get(subRegistry);
                for(final Map.Entry<String, GlobalTransformerRegistry> childEntry : children.entrySet()) {
                    //
                    final String value = childEntry.getKey();
                    final GlobalTransformerRegistry child = childEntry.getValue();
                    final PathAddress childAddress = address.append(PathElement.pathElement(key, value));
                    final ModelVersion childVersion = versions.containsKey(childAddress) ? versions.get(childAddress) : version;
                    child.process(registry, childAddress, childVersion, versions);
                }
            }
        }
    }

    private OperationTransformerRegistry getRegistryUpdater(final ModelVersion version) {
        int micro = version.getMicro();
        for (int i = micro; i >= 0; i--) {
            ModelVersion currentVersion = ModelVersion.create(version.getMajor(), version.getMinor(), i);
            OperationTransformerRegistry current = registryUpdater.get(this, currentVersion);
            if (current != null) {
                if(micro != i && this.getClass().desiredAssertionStatus()) {
                    ControllerLogger.MGMT_OP_LOGGER.couldNotFindTransformerRegistryFallingBack(version, currentVersion);
                }
                return current;
            }
        }
        return null;
    }

    private void createChildRegistry(final Iterator<PathElement> iterator, ModelVersion version, PathAddressTransformer pathAddressTransformer, OperationTransformerRegistry.ResourceTransformerEntry resourceTransformer, OperationTransformerRegistry.OperationTransformerEntry entry, boolean placeholder) {
        if(! iterator.hasNext()) {
            getOrCreate(version, pathAddressTransformer, resourceTransformer, entry, placeholder);
        } else {
            final PathElement element = iterator.next();
            getOrCreate(element.getKey()).getOrCreate(element.getValue()).createChildRegistry(iterator, version, pathAddressTransformer, resourceTransformer, entry, placeholder);
        }
    }

    private void registerTransformer(final Iterator<PathElement> iterator, ModelVersion version, String operationName, OperationTransformerRegistry.OperationTransformerEntry entry) {
        if(! iterator.hasNext()) {
            // by default skip the default transformer
            getOrCreate(version, PathAddressTransformer.DEFAULT, null, null, false).registerTransformer(PathAddress.EMPTY_ADDRESS.iterator(), operationName, entry);
        } else {
            final PathElement element = iterator.next();
            final SubRegistry subRegistry = getOrCreate(element.getKey());
            subRegistry.registerTransformer(iterator, element.getValue(), version, operationName,   entry);
        }
    }

    protected OperationTransformerRegistry.OperationTransformerEntry resolveTransformer(final Iterator<PathElement> iterator, ModelVersion version, String operationName) {
        if(!iterator.hasNext()) {
            final OperationTransformerRegistry registry = registryUpdater.get(this, version);
            if(registry == null) {
                return null;
            }
            return registry.resolveOperationTransformer(PathAddress.EMPTY_ADDRESS, operationName, null);
        } else {
            final PathElement element = iterator.next();
            final SubRegistry registry = subRegistriesUpdater.get(this, element.getKey());
            if(registry == null) {
                return null;
            }
            return registry.resolveTransformer(iterator, element.getValue(), version, operationName);
        }
    }

    private GlobalTransformerRegistry navigate(final Iterator<PathElement> iterator) {
        if(! iterator.hasNext()) {
            return this;
        } else {
            final PathElement element = iterator.next();
            final SubRegistry registry = subRegistriesUpdater.get(this, element.getKey());
            if(registry == null) {
                return null;
            }
            GlobalTransformerRegistry other = SubRegistry.childrenUpdater.get(registry, element.getValue());
            if(other != null) {
                return other.navigate(iterator);
            }
            return null;
        }
    }

    private SubRegistry getOrCreate(final String key) {
        for (;;) {
            final Map<String, SubRegistry> subRegistries = subRegistriesUpdater.get(this);
            SubRegistry registry = subRegistries.get(key);
            if(registry != null) {
                return registry;
            } else {
                registry = new SubRegistry();
                if (subRegistriesUpdater.putAtomic(this, key, registry, subRegistries)) {
                    return registry;
                }
            }
        }
    }

    private OperationTransformerRegistry getOrCreate(final ModelVersion version, PathAddressTransformer pathAddressTransformer, OperationTransformerRegistry.ResourceTransformerEntry resourceTransformer, final OperationTransformerRegistry.OperationTransformerEntry defaultTransformer, boolean placeholder) {
        for(;;) {
            final Map<ModelVersion, OperationTransformerRegistry> snapshot = registryUpdater.get(this);
            OperationTransformerRegistry registry = snapshot.get(version);
            if(registry != null) {
                return registry;
            } else {
                registry = new OperationTransformerRegistry(pathAddressTransformer, resourceTransformer, defaultTransformer, placeholder);
                if (registryUpdater.putAtomic(this, version, registry, snapshot)) {
                    return registry;
                }
            }
        }
    }

    private static class SubRegistry {

        private static final AtomicMapFieldUpdater<SubRegistry, String, GlobalTransformerRegistry> childrenUpdater = AtomicMapFieldUpdater.newMapUpdater(AtomicReferenceFieldUpdater.newUpdater(SubRegistry.class, Map.class, "children"));
        private volatile Map<String, GlobalTransformerRegistry> children;

        private SubRegistry() {
            childrenUpdater.clear(this);
        }

        GlobalTransformerRegistry getOrCreate(final String value) {
            for(;;) {
                final Map<String, GlobalTransformerRegistry> entries = childrenUpdater.get(this);
                GlobalTransformerRegistry entry = entries.get(value);
                if(entry != null) {
                    return entry;
                } else {
                    entry = new GlobalTransformerRegistry();
                    if (childrenUpdater.putAtomic(this, value, entry, entries)) {
                        return entry;
                    }
                }
            }
        }

        public OperationTransformerRegistry.OperationTransformerEntry resolveTransformer(Iterator<PathElement> iterator, String value, ModelVersion version, String operationName) {
            final GlobalTransformerRegistry registry = childrenUpdater.get(this, value);
            if(registry == null) {
                return null;
            }
            return registry.resolveTransformer(iterator, version, operationName);
        }

        public void registerTransformer(Iterator<PathElement> iterator, String value, ModelVersion version, String operationName, OperationTransformerRegistry.OperationTransformerEntry entry) {
            getOrCreate(value).registerTransformer(iterator, version, operationName, entry);
        }
    }

    static OperationTransformerRegistry.ResourceTransformerEntry RESOURCE_TRANSFORMER = new OperationTransformerRegistry.ResourceTransformerEntry(ResourceTransformer.DEFAULT, false);
    static OperationTransformerRegistry.ResourceTransformerEntry DISCARD = new OperationTransformerRegistry.ResourceTransformerEntry(ResourceTransformer.DISCARD, true);

}
