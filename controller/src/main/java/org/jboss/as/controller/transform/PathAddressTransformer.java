/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;

import java.util.Iterator;

/**
 * A path address transformation step. This specific type of transformer get registered alongside a resource transformer
 * entry and can be used to transform the {@linkplain PathAddress} for operation and resource transformations.
 *
 * In general this transformer is only responsible for changing the current {@linkplain PathElement} and the delegate
 * to other address transformers in the chain using {@linkplain Builder#next(org.jboss.as.controller.PathElement...)}.
 *
 * @author Emanuel Muckenhuber
 */
@FunctionalInterface
public interface PathAddressTransformer {

    /**
     * Transform an address.
     *
     * @param current the current path element
     * @param builder the address builder
     * @return the path address
     */
    PathAddress transform(PathElement current, Builder builder);

    PathAddressTransformer DEFAULT = new PathAddressTransformer() {

        @Override
        public PathAddress transform(PathElement current, Builder builder) {
            return builder.next(current);
        }

    };

    public class BasicPathAddressTransformer implements PathAddressTransformer {

        private final PathElement swap;
        public BasicPathAddressTransformer(PathElement swap) {
            this.swap = swap;
        }

        @Override
        public PathAddress transform(PathElement current, Builder builder) {
            return builder.next(swap);
        }

    }

    public class ReplaceElementKey implements PathAddressTransformer {

        private final String newKey;
        public ReplaceElementKey(String newKey) {
            this.newKey = newKey;
        }

        @Override
        public PathAddress transform(PathElement current, Builder builder) {
            final PathElement newElement = PathElement.pathElement(newKey, current.getValue());
            return builder.next(newElement);
        }

    }

    public interface Builder {

        /**
         * Get the unmodified (original) address.
         *
         * @return the original address
         */
        PathAddress getOriginal();

        /**
         * Get the current address, from the builder.
         *
         * @return the current address
         */
        PathAddress getCurrent();

        /**
         * Get the remaining elements left for transformation.
         *
         * @return the remaining elements for this address
         */
        PathAddress getRemaining();

        /**
         * Append an element to the current address and continue to the next transformer in the chain.
         *
         * @param elements the elements to append
         * @return the transformed address
         */
        PathAddress next(final PathElement... elements);

    }

    class BuilderImpl implements Builder {

        private final Iterator<PathAddressTransformer> transformers;
        private final PathAddress original;

        private PathAddress current = PathAddress.EMPTY_ADDRESS;
        private int idx = 0;

        protected BuilderImpl(Iterator<PathAddressTransformer> transformers, PathAddress original) {
            this.transformers = transformers;
            this.original = original;
        }

        @Override
        public PathAddress getOriginal() {
            return original;
        }

        @Override
        public PathAddress getCurrent() {
            return current;
        }

        @Override
        public PathAddress getRemaining() {
            return original.subAddress(idx);
        }

        @Override
        public PathAddress next(final PathElement... elements) {
            current = current.append(elements);
            final int remaining = original.size() - idx;
            if(remaining == 0) {
                return current;
            }
            if(transformers.hasNext()) {
                final PathAddressTransformer transformer = transformers.next();
                final PathElement next = original.getElement(idx++);
                return transformer.transform(next, this);
            } else {
                // This may not be an error?
                // return current.append(getRemaining());
                throw new IllegalStateException();
            }
        }

        protected PathAddress start() {
            if(original == PathAddress.EMPTY_ADDRESS || original.size() == 0) {
                return PathAddress.EMPTY_ADDRESS;
            }
            if(transformers.hasNext()) {
                final PathAddressTransformer transformer = transformers.next();
                final PathElement next = original.getElement(idx++);
                return transformer.transform(next, this);
            } else {
                return original;
            }
        }

    }

}
