/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tool;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.patching.metadata.ContentItem;
import org.jboss.as.patching.metadata.ContentType;
import org.jboss.as.patching.metadata.MiscContentItem;

/**
 * @author Emanuel Muckenhuber
 */
class ContentPolicyBuilderImpl implements PatchTool.ContentPolicyBuilder {

    boolean overrideAll;
    boolean ignoreModulesChanges;
    final List<String> override = new ArrayList<String>();
    final List<String> preserve = new ArrayList<String>();

    @Override
    public ContentVerificationPolicy createPolicy() {
        return new ContentVerificationPolicy() {
            @Override
            public boolean ignoreContentValidation(ContentItem item) {
                final ContentType type = item.getContentType();
                if(type == ContentType.MODULE || type == ContentType.BUNDLE) {
                    return ignoreModulesChanges || overrideAll;
                }
                final MiscContentItem misc = (MiscContentItem) item;
                final String path = misc.getRelativePath();
                if(override.contains(path)) {
                   return true;
                }
                // Preserve should skip content verification
                if(preserve.contains(path)) {
                    return true;
                }
                return overrideAll;
            }

            @Override
            public boolean preserveExisting(ContentItem item) {
                final ContentType type = item.getContentType();
                if(type == ContentType.MISC) {
                    final MiscContentItem misc = (MiscContentItem) item;
                    final String path = misc.getRelativePath();
                    return preserve.contains(path);
                }
                return false;
            }
        };
    }

    @Override
    public PatchTool.ContentPolicyBuilder ignoreModuleChanges() {
        ignoreModulesChanges = true;
        return this;
    }

    @Override
    public PatchTool.ContentPolicyBuilder overrideItem(MiscContentItem item) {
        return overrideItem(item.getRelativePath());
    }

    @Override
    public PatchTool.ContentPolicyBuilder overrideItem(String path) {
        override.add(path);
        return this;
    }

    @Override
    public PatchTool.ContentPolicyBuilder preserveItem(MiscContentItem item) {
        return preserveItem(item.getRelativePath());
    }

    @Override
    public PatchTool.ContentPolicyBuilder preserveItem(String path) {
        preserve.add(path);
        return this;
    }

    @Override
    public PatchTool.ContentPolicyBuilder overrideAll() {
        overrideAll = true;
        return this;
    }

}
