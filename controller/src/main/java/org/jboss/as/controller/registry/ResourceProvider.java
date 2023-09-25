/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import java.util.Set;

/**
 * @author Emanuel Muckenhuber
 */
public interface ResourceProvider extends Cloneable {

    boolean has(String name);
    Resource get(String name);
    boolean hasChildren();
    Set<String> children();
    void register(String name, Resource resource);
    void register(String value, int index, Resource resource);
    Resource remove(String name);
    ResourceProvider clone();

    public abstract static class ResourceProviderRegistry {

        protected abstract void registerResourceProvider(final String type, final ResourceProvider provider);

    }

    public static class Tool {

        public static void addResourceProvider(final String name, final ResourceProvider provider, final Resource resource) {
            if (resource instanceof ResourceProviderRegistry) {
                ((ResourceProviderRegistry)resource).registerResourceProvider(name, provider);
            } else {
                throw new UnsupportedOperationException();
            }
        }

    }
}
