/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

/**
 * @author Alexey Loubyansky
 *
 */
public interface PatchElementProvider extends UpgradeCondition {

    /**
     * Whether the provider is an add-on (or a layer)
     *
     * @return  true if the provider is an add-on, otherwise - false, which means it's a layer.
     */
    boolean isAddOn();

}
