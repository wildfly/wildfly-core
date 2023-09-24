/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.batch;

import java.util.Set;


/**
 *
 * @author Alexey Loubyansky
 */
public interface BatchManager {

    boolean isHeldback(String name);

    boolean activateNewBatch();

    boolean activateHeldbackBatch(String name);

    boolean holdbackActiveBatch(String name);

    boolean discardActiveBatch();

    Set<String> getHeldbackNames();

    boolean isBatchActive();

    Batch getActiveBatch();
}
