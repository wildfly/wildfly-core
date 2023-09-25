/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.runner;

import org.jboss.as.patching.metadata.ContentItem;
import org.jboss.as.patching.metadata.ContentType;

/**
 * Content item filter.
 *
 * @author Emanuel Muckenhuber
 */
public interface ContentItemFilter {

    /**
     * Tests whether or not the content-item should be included or not.
     *
     * @param item the content item
     * @return  <code>true</code> if and only if <code>item</code> should be included
     */
    boolean accepts(ContentItem item);

    ContentItemFilter ALL = new ContentItemFilter() {
        @Override
        public boolean accepts(ContentItem item) {
            return true;
        }
    };

    ContentItemFilter MISC_ONLY = new ContentItemFilter() {
        @Override
        public boolean accepts(ContentItem item) {
            final ContentType type = item.getContentType();
            return type == ContentType.MISC;
        }
    };

    ContentItemFilter ALL_BUT_MISC = new ContentItemFilter() {
        @Override
        public boolean accepts(ContentItem item) {
            final ContentType type = item.getContentType();
            return type != ContentType.MISC;
        }
    };

}
