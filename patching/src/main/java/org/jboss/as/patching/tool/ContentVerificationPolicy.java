/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tool;

import org.jboss.as.patching.metadata.ContentItem;
import org.jboss.as.patching.metadata.ContentType;

/**
 * Policy for content verification.
 *
 * @author Emanuel Muckenhuber
 */
public interface ContentVerificationPolicy {

    /**
     * Update existing Policy.
     *
     * Ignored content validation.
     *
     * @param item the content item
     * @return whether a mismatch leads to an abort in the patch execution
     */
    boolean ignoreContentValidation(final ContentItem item);

    /**
     * Preserve existing Policy.
     *
     * Excluded content execution.
     *
     * @param item the content item
     * @return whether the execution of the content task should be skipped
     */
    boolean preserveExisting(final ContentItem item);

    ContentVerificationPolicy STRICT = new ContentVerificationPolicy() {

        @Override
        public boolean ignoreContentValidation(ContentItem item) {
            return false;
        }

        @Override
        public boolean preserveExisting(ContentItem item) {
            return false;
        }
    };

    ContentVerificationPolicy OVERRIDE_ALL = new ContentVerificationPolicy() {

        @Override
        public boolean ignoreContentValidation(ContentItem item) {
            return true;
        }

        @Override
        public boolean preserveExisting(ContentItem item) {
            return false;
        }
    };

    ContentVerificationPolicy PRESERVE_ALL = new ContentVerificationPolicy() {

        @Override
        public boolean ignoreContentValidation(ContentItem item) {
            return false;
        }

        @Override
        public boolean preserveExisting(ContentItem item) {
            return item.getContentType() == ContentType.MISC;
        }
    };

}
