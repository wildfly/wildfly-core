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
package org.jboss.as.controller.transform.description;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.OperationTransformerRegistry.OperationTransformerEntry;
import org.jboss.as.controller.registry.OperationTransformerRegistry.PlaceholderResolver;
import org.jboss.as.controller.transform.PathAddressTransformer;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.TransformerEntry;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */

class ChainedPlaceholderResolver implements PlaceholderResolver {

    private final TransformationDescription description;
    private final Map<String, SubRegistry> subRegistries;

    private ChainedPlaceholderResolver(TransformationDescription description, Map<String, SubRegistry> subRegistries) {
        this.description = description;
        this.subRegistries = subRegistries;
    }

    static ChainedPlaceholderResolver create(TransformationDescription root) {
        final Map<String, Map<String, ChainedPlaceholderResolver>> childResolvers = new HashMap<>();
        for (TransformationDescription childDesc : root.getChildren()) {
            ChainedPlaceholderResolver childResolver = create(childDesc);
            PathElement pathElement = childDesc.getPath();
            Map<String, ChainedPlaceholderResolver> subMap = childResolvers.get(pathElement.getKey());
            if (subMap == null) {
                subMap = new HashMap<>();
                childResolvers.put(pathElement.getKey(), subMap);
            }
            assert !subMap.containsKey(pathElement.getValue()) : "already have resolver for " + pathElement;
            subMap.put(pathElement.getValue(), childResolver);
        }

        final Map<String, SubRegistry> subRegistries = new HashMap<>();
        for (Map.Entry<String, Map<String, ChainedPlaceholderResolver>>  keyEntry : childResolvers.entrySet()) {
            subRegistries.put(keyEntry.getKey(), new SubRegistry(Collections.unmodifiableMap(keyEntry.getValue())));
        }
        return new ChainedPlaceholderResolver(root, Collections.unmodifiableMap(subRegistries));
    }

    TransformationDescription getDescription() {
        return description;
    }

    @Override
    public OperationTransformerEntry resolveOperationTransformer(Iterator<PathElement> iterator, String operationName) {
        //TODO Figure out if this is needed and plug in / remove
        return null;
    }

    @Override
    public TransformerEntry resolveTransformerEntry(Iterator<PathElement> iterator) {
        if(!iterator.hasNext()) {
            return getTransformerEntry();
        } else {
            final PathElement element = iterator.next();
            SubRegistry sub = subRegistries.get(element.getKey());
            if(sub == null) {
                return null;
            }
            final ChainedPlaceholderResolver registry = sub.get(element.getValue());
            if(registry == null) {
                return null;
            }
            return registry.resolveTransformerEntry(iterator);
        }
    }

    @Override
    public void resolvePathTransformers(Iterator<PathElement> iterator, List<PathAddressTransformer> list) {
    }


    private ChainedPlaceholderResolver resolveChild(final Iterator<PathElement> iterator) {

        if(! iterator.hasNext()) {
            return this;
        } else {
            final PathElement element = iterator.next();
            SubRegistry sub = subRegistries.get(element.getKey());
            if(sub == null) {
                return null;
            }
            return sub.get(element.getValue(), iterator);
        }
    }

    private TransformerEntry getTransformerEntry() {
        return new TransformerEntry() {
            @Override
            public PathAddressTransformer getPathTransformation() {
                return description.getPathAddressTransformer();
            }

            @Override
            public ResourceTransformer getResourceTransformer() {
                return description.getResourceTransformer();
            }
        };
    }

    private static class SubRegistry {

        private volatile Map<String, ChainedPlaceholderResolver> entries;

        SubRegistry(Map<String, ChainedPlaceholderResolver> entries) {
            this.entries = entries;
        }

        public OperationTransformerEntry resolveTransformer(Iterator<PathElement> iterator, String value, String operationName) {
            final ChainedPlaceholderResolver reg = get(value);
            if(reg == null) {
                return null;
            }
            return reg.resolveOperationTransformer(iterator, operationName);
        }

        ChainedPlaceholderResolver get(final String value) {
            ChainedPlaceholderResolver entry = entries.get(value);
            if(entry == null) {
                entry = entries.get("*");
                if(entry == null) {
                    return null;
                }
            }
            return entry;
        }

        ChainedPlaceholderResolver get(final String value, Iterator<PathElement> iterator) {
            ChainedPlaceholderResolver entry = entries.get(value);
            if(entry == null) {
                entry = entries.get("*");
                if(entry == null) {
                    return null;
                }
            }
            return entry.resolveChild(iterator);
        }
    }


}