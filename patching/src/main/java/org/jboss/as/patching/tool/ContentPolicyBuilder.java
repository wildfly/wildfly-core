/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tool;

public interface ContentPolicyBuilder {

    /**
     * Ignore all local module changes.
     *
     * @return the builder
     */
    ContentPolicyBuilder ignoreModuleChanges();

    /**
     * Override all local changes.
     *
     * @return the builder
     */
    ContentPolicyBuilder overrideAll();

    /**
     * Override a misc content item.
     *
     * @param path the path of the item
     * @return the builder
     */
    ContentPolicyBuilder overrideItem(String path);


    /**
     * Preserve an existing content item.
     *
     * @param path the path of the item
     * @return the builder
     */
    ContentPolicyBuilder preserveItem(String path);

}
