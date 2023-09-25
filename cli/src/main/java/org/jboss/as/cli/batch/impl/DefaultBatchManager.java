/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.batch.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.as.cli.batch.Batch;
import org.jboss.as.cli.batch.BatchManager;

/**
 *
 * @author Alexey Loubyansky
 */
public class DefaultBatchManager implements BatchManager {

    private Map<String, DefaultBatch> batches = Collections.emptyMap();
    private DefaultBatch activeBatch;

    /* (non-Javadoc)
     * @see org.jboss.as.cli.batch.BatchManager#holdbackActiveBatch(java.lang.String)
     */
    @Override
    public boolean holdbackActiveBatch(String name) {
        if(activeBatch == null) {
            return false;
        }

        if(batches.containsKey(name)) {
            return false;
        }

        if(batches.isEmpty()) {
            batches = new HashMap<String, DefaultBatch>();
        }
        batches.put(name, activeBatch);
        activeBatch = null;
        return true;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.batch.BatchManager#discardActiveBatch()
     */
    @Override
    public boolean discardActiveBatch() {
        if(activeBatch == null) {
            return false;
        }
        activeBatch = null;
        return true;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.batch.BatchManager#getHeldbackNames()
     */
    @Override
    public Set<String> getHeldbackNames() {
        return batches.keySet();
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.batch.BatchManager#getActiveBatch()
     */
    @Override
    public Batch getActiveBatch() {
        return activeBatch;
    }

    @Override
    public boolean isHeldback(String name) {
        return batches.containsKey(name);
    }

    @Override
    public boolean activateNewBatch() {
        if(activeBatch != null) {
            return false;
        }
        activeBatch = new DefaultBatch();
        return true;
    }

    @Override
    public boolean isBatchActive() {
        return activeBatch != null;
    }

    @Override
    public boolean activateHeldbackBatch(String name) {
        if(activeBatch != null) {
            return false;
        }
        if(!batches.containsKey(name)) {
            return false;
        }

        activeBatch = batches.remove(name);
        return activeBatch != null;
    }
}
