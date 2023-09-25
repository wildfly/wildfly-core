/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import org.jboss.as.controller.OperationClientException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * An addressable resource in the management model, representing a local model and child resources.
 * <p>Instances of this class are <b>not</b> thread-safe and need to be synchronized externally.
 *
 * @author Emanuel Muckenhuber
 */
public interface Resource extends Cloneable {

    /**
     * Get the local model.
     *
     * @return the model
     */
    ModelNode getModel();

    /**
     * Write the model.
     *
     * @param newModel the new model
     */
    void writeModel(ModelNode newModel);

    /**
     * Determine whether the model of this resource is defined.
     *
     * @return {@code true} if the local model is defined
     */
    boolean isModelDefined();

    /**
     * Determine whether this resource has a child with the given address. In case the {@code PathElement} has
     * a wildcard as value, it will determine whether this resource has any resources of a given type.
     *
     * @param element the path element
     * @return {@code true} if there is child with the given address
     */
    boolean hasChild(PathElement element);

    /**
     * Get a single child of this resource with the given address. If no such child exists this will return {@code null}.
     *
     * @param element the path element
     * @return the resource, {@code null} if there is no such child resource
     */
    Resource getChild(PathElement element);

    /**
     * Get a single child of this resource with the given address. If no such child exists a, an exception is thrown.
     *
     * @param element the path element
     * @return the resource
     * @throws NoSuchResourceException if the child does not exist
     */
    Resource requireChild(PathElement element);

    /**
     * Determine whether this resource has any child of a given type.
     *
     * @param childType the child type
     * @return {@code true} if there is any child of the given type
     */
    boolean hasChildren(String childType);

    /**
     * Navigate the resource tree.
     *
     * @param address the address
     * @return the resource
     * @throws NoSuchResourceException if any resource in the path does not exist
     */
    Resource navigate(PathAddress address);

    /**
     * Get a list of registered child types for this resource.
     *
     * @return the registered child types
     */
    Set<String> getChildTypes();

    /**
     * Get the children names for a given type.
     *
     * @param childType the child type
     * @return the names of registered child resources
     */
    Set<String> getChildrenNames(String childType);

    /**
     * Get the children for a given type.
     *
     * @param childType the child type
     * @return the registered children
     */
    Set<ResourceEntry> getChildren(String childType);

    /**
     * Register a child resource.
     *
     * @param address the address
     * @param resource the resource
     * @throws IllegalStateException for a duplicate entry
     */
    void registerChild(PathElement address, Resource resource);

    /**
     * Register a child resource
     *
     * @param address the address
     * @param index the index at which to add the resource. Existing children with this index and higher will be shifted one uo
     * @param resource the resource
     * @throws IllegalStateException for a duplicate entry or if the resource does not support ordered children
     */
    void registerChild(PathElement address, int index, Resource resource);

    /**
     * Remove a child resource.
     *
     * @param address the address
     * @return the resource
     */
    Resource removeChild(PathElement address);

    /**
     * Return the child types for which the order matters.
     *
     * @return {@code true} if the order of the children matters. If there are no ordered
     * children and empty set is returned. This method should never return {@code null}
     */
    Set<String> getOrderedChildTypes();

    /**
     * Gets whether this resource only exists in the runtime and has no representation in the
     * persistent configuration model.
     *
     * @return {@code true} if the resource has no representation in the
     * persistent configuration model; {@code false} otherwise
     */
    boolean isRuntime();

    /**
     * Gets whether operations against this resource will be proxied to a remote process.
     *
     * @return {@code true} if this resource represents a remote resource; {@code false} otherwise
     */
    boolean isProxy();

    /**
     * Creates and returns a copy of this resource.
     *
     * @return the clone. Will not return {@code null}
     */
    Resource clone();

    /**
     * Creates a shallow copy of this resource, which will only have placeholder resources
     * for immediate children. Those placeholder resources will return an empty
     * {@link org.jboss.as.controller.registry.Resource#getModel() model} and will not themselves have any children.
     * Their presence, however, allows the caller to see what immediate children exist under the target resource.
     * @return the shallow copy. Will not return {@code null}
     */
    default Resource shallowCopy() {

        final Resource copy = Resource.Factory.create();
        copy.writeModel(getModel());
        for(final String childType : getChildTypes()) {
            for(final Resource.ResourceEntry child : getChildren(childType)) {
                copy.registerChild(child.getPathElement(), PlaceholderResource.INSTANCE);
            }
        }
        return copy;
    }

    interface ResourceEntry extends Resource {

        /**
         * Get the name this resource was registered under.
         *
         * @return the resource name
         */
        String getName();

        /**
         * Get the path element this resource was registered under.
         *
         * @return the path element
         */
        PathElement getPathElement();

    }

    class Factory {

        private Factory() { }

        /**
         * Create a default resource implementation. Equivalent to {@link #create(boolean) create(false)}.
         *
         * @return the resource
         */
        public static Resource create() {
            return new BasicResource();
        }

        /**
         * Create a default resource implementation.
         *
         * @param runtimeOnly the value the resource should return from {@link Resource#isRuntime()}
         *
         * @return the resource
         */
        public static Resource create(boolean runtimeOnly) {
            return new BasicResource(runtimeOnly);
        }

        /**
         * Create a default resource implementation.
         *
         * @param runtimeOnly the value the resource should return from {@link Resource#isRuntime()}
         * @param orderedChildTypes the names of any child types where the order of the children matters
         *
         * @return the resource
         */
        public static Resource create(boolean runtimeOnly, Set<String> orderedChildTypes) {
            return new BasicResource(runtimeOnly, orderedChildTypes);
        }
    }

    class Tools {

        /**
         * A {@link ResourceFilter} that returns {@code false} for {@link Resource#isRuntime() runtime} and
         * {@link Resource#isProxy() proxy} resources.
         */
        public static final ResourceFilter ALL_BUT_RUNTIME_AND_PROXIES_FILTER = new ResourceFilter() {
            @Override
            public boolean accepts(PathAddress address, Resource resource) {
                if(resource.isRuntime() || resource.isProxy()) {
                    return false;
                }
                return true;
            }
        };

        private Tools() { }

        /**
         * Recursively reads an entire resource tree, ignoring runtime-only and proxy resources, and generates
         * a DMR tree representing all of the non-ignored resources.
         *
         * @param resource the root resource
         * @return the DMR tree
         */
        public static ModelNode readModel(final Resource resource) {
            return readModel(resource, -1);
        }

        /**
         * Reads a resource tree, recursing up to the given number of levels but ignoring runtime-only and proxy resources,
         * and generates a DMR tree representing all of the non-ignored resources.
         *
         * @param resource the model
         * @param level the number of levels to recurse, or {@code -1} for no limit
         * @return the DMR tree
         */
        public static ModelNode readModel(final Resource resource, final int level) {
            return readModel(resource, level, ALL_BUT_RUNTIME_AND_PROXIES_FILTER);
        }

        /**
         * Recursively reads an entire resource tree, ignoring runtime-only and proxy resources, and generates
         * a DMR tree representing all of the non-ignored resources.  This variant can use a resource
         * registration to help identify runtime-only and proxy resources more efficiently.
         *
         * @param resource the root resource
         * @param mrr the resource registration for {@code resource}, or {@code null}
         * @return the DMR tree
         */
        public static ModelNode readModel(final Resource resource, final ImmutableManagementResourceRegistration mrr) {
            return readModel(resource, -1, mrr, ALL_BUT_RUNTIME_AND_PROXIES_FILTER);
        }

        /**
         * Reads a resource tree, recursing up to the given number of levels but ignoring runtime-only and proxy resources,
         * and generates a DMR tree representing all of the non-ignored resources.  This variant can use a resource
         * registration to help identify runtime-only and proxy resources more efficiently.
         *
         * @param resource the model
         * @param level the number of levels to recurse, or {@code -1} for no limit
         * @param mrr the resource registration for {@code resource}, or {@code null}
         * @return the DMR tree
         */
        public static ModelNode readModel(final Resource resource, final int level, final ImmutableManagementResourceRegistration mrr) {
            return readModel(resource, level, mrr, ALL_BUT_RUNTIME_AND_PROXIES_FILTER);
        }

        /**
         * Reads a resource tree, recursing up to the given number of levels but ignoring resources not accepted
         * by the given {@code filter}, and generates a DMR tree representing all of the non-ignored resources.
         *
         * @param resource the model
         * @param level the number of levels to recurse, or {@code -1} for no limit
         * @param filter a resource filter
         * @return the model
         */
        public static ModelNode readModel(final Resource resource, final int level, final ResourceFilter filter) {
            return readModel(resource, level, null, filter);
        }

        private static ModelNode readModel(final Resource resource, final int level,
                                           final ImmutableManagementResourceRegistration mrr, final ResourceFilter filter) {
            if (filter.accepts(PathAddress.EMPTY_ADDRESS, resource)) {
                return readModel(PathAddress.EMPTY_ADDRESS, resource, level, mrr, filter);
            } else {
                return new ModelNode();
            }
        }

        private static ModelNode readModel(final PathAddress address, final Resource resource, final int level,
                                           final ImmutableManagementResourceRegistration mrr, final ResourceFilter filter) {
            final ModelNode model = resource.getModel().clone();
            final boolean recursive = level == -1 || level > 0;
            if (recursive) {
                final int newLevel = level == -1 ? -1 : level - 1;
                Set<String> validChildTypes = mrr == null ? null : getNonIgnoredChildTypes(mrr);
                for (final String childType : resource.getChildTypes()) {
                    if (validChildTypes != null && !validChildTypes.contains(childType)) {
                        continue;
                    }
                    model.get(childType).setEmptyObject();
                    for (final ResourceEntry entry : resource.getChildren(childType)) {
                        if (filter.accepts(address.append(entry.getPathElement()), resource)) {
                            ImmutableManagementResourceRegistration childMrr =
                                    mrr == null ? null : mrr.getSubModel(address.append(entry.getPathElement()));
                            model.get(childType, entry.getName()).set(readModel(entry, newLevel, childMrr, filter));
                        }
                    }
                }
            }
            return model;
        }

        private static Set<String> getNonIgnoredChildTypes(ImmutableManagementResourceRegistration mrr) {
            Set<String> result = new HashSet<>();
            for (PathElement pe : mrr.getChildAddresses(PathAddress.EMPTY_ADDRESS)) {
                ImmutableManagementResourceRegistration childMrr = mrr.getSubModel(PathAddress.pathAddress(pe));
                if (childMrr != null && !childMrr.isRemote() && !childMrr.isRuntimeOnly()) {
                    result.add(pe.getKey());
                }
            }
            return result;
        }

        /**
         * Navigate from a parent {@code resource} to the descendant resource at the given relative {@code address}.
         * <p>
         * {@link Resource#navigate(PathAddress)} implementations can use this as a standard implementation.
         * </p>
         *
         * @param resource the resource the resource. Cannot be {@code null}
         * @param address the address the address relative to {@code resource}'s address. Cannot be {@code null}
         * @return the resource the descendant resource. Will not be {@code null}
         * @throws NoSuchResourceException if there is no descendant resource at {@code address}
         */
        public static Resource navigate(final Resource resource, final PathAddress address) {
            Resource r = resource;
            for(final PathElement element : address) {
                r = r.requireChild(element);
            }
            return r;
        }

    }

    /**
     * A {@link NoSuchElementException} variant that can be thrown by {@link Resource#requireChild(PathElement)} and
     * {@link Resource#navigate(PathAddress)} implementations to indicate a client error when invoking a
     * management operation.
     */
    class NoSuchResourceException extends NoSuchElementException implements OperationClientException {

        private static final long serialVersionUID = -2409240663987141424L;

        public NoSuchResourceException(PathElement childPath) {
            this(ControllerLogger.ROOT_LOGGER.childResourceNotFound(childPath));
        }

        public NoSuchResourceException(String message) {
            super(message);
        }

        @Override
        public ModelNode getFailureDescription() {
            return new ModelNode(getLocalizedMessage());
        }

        @Override
        public String toString() {
            return super.toString() + " [ " + getFailureDescription() + " ]";
        }
    }

}
