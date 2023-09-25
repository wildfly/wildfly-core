/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations;


/**
 * Tests multiple levels in the scenario where the secondary model does not support indexed adds, and we need to remove and re-add
 * all ordered children to maintain the order.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class OrderedChildResourceMoreLevelsNonIndexedAddSyncModelTestCase extends OrderedChildMoreLevelsResourceSyncModelTestCase {

    public OrderedChildResourceMoreLevelsNonIndexedAddSyncModelTestCase() {
        super(false);
    }


}