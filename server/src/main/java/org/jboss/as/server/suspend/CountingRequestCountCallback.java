/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.suspend;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * RequestCountListener that till n notification have been received before notifying
 * its delegate.
 *
 * @author Stuart Douglas
 */
public class CountingRequestCountCallback implements ServerActivityCallback {

    private final AtomicInteger count;

    private final ServerActivityCallback delegate;

    public CountingRequestCountCallback(int count, ServerActivityCallback delegate) {
        this.count = new AtomicInteger(count);
        this.delegate = delegate;
    }

    @Override
    public void done() {
        if (count.decrementAndGet() == 0) {
            if(delegate != null) {
                delegate.done();
            }
        }
    }

}
