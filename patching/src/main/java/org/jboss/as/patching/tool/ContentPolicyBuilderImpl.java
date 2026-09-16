/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tool;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Emanuel Muckenhuber
 */
class ContentPolicyBuilderImpl implements ContentPolicyBuilder {

    boolean overrideAll;
    boolean ignoreModulesChanges;
    final List<String> override = new ArrayList<String>();
    final List<String> preserve = new ArrayList<String>();

    @Override
    public ContentPolicyBuilder ignoreModuleChanges() {
        ignoreModulesChanges = true;
        return this;
    }

    @Override
    public ContentPolicyBuilder overrideItem(String path) {
        override.add(path);
        return this;
    }

    @Override
    public ContentPolicyBuilder preserveItem(String path) {
        preserve.add(path);
        return this;
    }

    @Override
    public ContentPolicyBuilder overrideAll() {
        overrideAll = true;
        return this;
    }

}
