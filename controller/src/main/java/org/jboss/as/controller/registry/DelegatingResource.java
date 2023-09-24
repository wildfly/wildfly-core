/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.dmr.ModelNode;

/**
 * {@link Resource} implementation that simply delegates to another
 * {@link Resource}. Intended as a convenience class to allow overriding
 * of standard behaviors and also as a means to support a copy-on-write/publish-on-commit
 * semantic for the management resource tree.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class DelegatingResource extends ResourceProvider.ResourceProviderRegistry implements Resource {

    /**
     * Provides a delegate for use by a {@code DelegatingResource}.
     * Does not need to provide the same delegate for every call, allowing a copy-on-write
     * semantic for the underlying {@code Resource}.
     */
    public interface ResourceDelegateProvider {
        /**
         * Gets the delegate.
         * @return the delegate. Cannot return {@code null}
         */
        Resource getDelegateResource();
    }

    private final ResourceDelegateProvider delegateProvider;

    /**
     * Creates a new DelegatingResource with a fixed delegate.
     *
     * @param delegate the delegate. Cannot be {@code null}
     */
    public DelegatingResource(final Resource delegate) {
        this(new ResourceDelegateProvider() {
            @Override
            public Resource getDelegateResource() {
                return delegate;
            }
        });
    }

    /**
     * Creates a new DelegatingResource with a possibly changing delegate.
     *
     * @param delegateProvider provider of the delegate. Cannot be {@code null}
     */
    public DelegatingResource(ResourceDelegateProvider delegateProvider) {
        assert delegateProvider != null;
        assert delegateProvider.getDelegateResource() != null;
        this.delegateProvider = delegateProvider;
    }

    @SuppressWarnings({"CloneDoesntCallSuperClone"})
    @Override
    public Resource clone() {
        return getDelegate().clone();
    }

    public Resource getChild(PathElement element) {
        return getDelegate().getChild(element);
    }

    public Set<ResourceEntry> getChildren(String childType) {
        return getDelegate().getChildren(childType);
    }

    public Set<String> getChildrenNames(String childType) {
        return getDelegate().getChildrenNames(childType);
    }

    public Set<String> getChildTypes() {
        return getDelegate().getChildTypes();
    }

    public ModelNode getModel() {
        return getDelegate().getModel();
    }

    public boolean hasChild(PathElement element) {
        return getDelegate().hasChild(element);
    }

    public boolean hasChildren(String childType) {
        return getDelegate().hasChildren(childType);
    }

    public boolean isModelDefined() {
        return getDelegate().isModelDefined();
    }

    public boolean isProxy() {
        return getDelegate().isProxy();
    }

    public boolean isRuntime() {
        return getDelegate().isRuntime();
    }

    public Resource navigate(PathAddress address) {
        return getDelegate().navigate(address);
    }

    public void registerChild(PathElement address, Resource resource) {
        getDelegate().registerChild(address, resource);
    }

    @Override
    public void registerChild(PathElement address, int index, Resource resource) {
        getDelegate().registerChild(address, index, resource);
    }

    public Resource removeChild(PathElement address) {
        return getDelegate().removeChild(address);
    }

    public Resource requireChild(PathElement element) {
        return getDelegate().requireChild(element);
    }

    public void writeModel(ModelNode newModel) {
        getDelegate().writeModel(newModel);
    }

    private Resource getDelegate() {
        return this.delegateProvider.getDelegateResource();
    }

    @Override
    protected void registerResourceProvider(String type, ResourceProvider provider) {
        ResourceProvider.Tool.addResourceProvider(type, provider, getDelegate());
    }

    @Override
    public Set<String> getOrderedChildTypes() {
        return getDelegate().getOrderedChildTypes();
    }
}
