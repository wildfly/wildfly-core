/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import org.jboss.msc.service.StopContext;
import org.jboss.threads.EventListener;

/**
* @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
*/
class StopContextEventListener implements EventListener<StopContext> {

    private static final StopContextEventListener INSTANCE = new StopContextEventListener();

    private StopContextEventListener() {
    }

    public void handleEvent(final StopContext stopContext) {
        stopContext.complete();
    }

    public static StopContextEventListener getInstance() {
        return INSTANCE;
    }
}
