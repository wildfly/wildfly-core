/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import java.util.Arrays;

import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.runner.PatchingTaskContext;

/**
 * Represents a condition that has to be satisfied during the patch application process
 * before a certain modification can take place.
 *
 * @author Alexey Loubyansky
 */
public interface ModificationCondition {

    boolean isSatisfied(PatchingTaskContext ctx) throws PatchingException;

    class Factory {

        /**
         * Creates a condition which will be satisfied in case the path relative
         * to the installation root directory exists
         *
         * @param path  path relative to the installation root directory
         * @return  true if the path exists, otherwise - false
         */
        public static ModificationCondition exists(ContentItem contentItem) {
            return new ExistsCondition(contentItem);
        }

        public static ModificationCondition fromString(String condition) throws PatchingException {
            assert condition != null : "condition is null";
            if(condition.startsWith(ExistsCondition.ID)) {
                String str = condition.substring(ExistsCondition.ID.length() + 1);
                int i = str.indexOf(':');
                if(i < 0) {
                    throw PatchLogger.ROOT_LOGGER.contentItemTypeMissing(condition);
                }
                final String typeStr = str.substring(0, i);
                final ContentType type = ContentType.valueOf(typeStr);
                str = str.substring(i + 1);
                ContentItem item;
                switch(type) {
                    case MISC:
                        final String[] s = str.split("/");
                        final int length = s.length;
                        final String name = s[length - 1];
                        final String[] itemPath = Arrays.copyOf(s, length - 1);
                        item = new MiscContentItem(name, itemPath, null);
                        break;
                    case MODULE:
                    case BUNDLE:
                    default:
                        throw new PatchingException(PatchLogger.ROOT_LOGGER.unsupportedContentType(type.name()));
                }
                return ModificationCondition.Factory.exists(item);
            }
            throw PatchLogger.ROOT_LOGGER.unrecognizedConditionFormat(condition);
        }

    }

    public static final class ExistsCondition implements ModificationCondition {

        static final String ID = "exists";

        private final ContentItem contentItem;

        private ExistsCondition(ContentItem contentItem) {
            assert contentItem != null : "contentItem is null";
            this.contentItem = contentItem;
        }

        public ContentItem getContentItem() {
            return contentItem;
        }

        @Override
        public boolean isSatisfied(PatchingTaskContext ctx) throws PatchingException {
            return ctx.getTargetFile(contentItem).exists();
        }

        @Override
        public String toString() {
            final StringBuilder buf = new StringBuilder();
            buf.append(ID).append(':');
            switch(contentItem.getContentType()) {
                case MISC:
                    buf.append(ContentType.MISC.toString());
                    break;
                case MODULE:
                    buf.append(ContentType.MODULE.toString());
                    break;
                case BUNDLE:
                    buf.append(ContentType.BUNDLE.toString());
                    break;
                default:
                    throw new IllegalStateException(PatchLogger.ROOT_LOGGER.unsupportedContentType(contentItem.getContentType().name()));
            }
            return buf.append(':').append(contentItem.getRelativePath()).toString();
        }
    }
}
