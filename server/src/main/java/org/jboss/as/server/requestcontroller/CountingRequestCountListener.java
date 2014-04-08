/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.server.requestcontroller;

import org.jboss.as.server.shutdown.ServerActivityListener;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RequestCountListener that till n notification have been received before notifying
 * its delegate.
 *
 * @author Stuart Douglas
 */
class CountingRequestCountListener implements ServerActivityListener {

    private final AtomicInteger count;

    private final ServerActivityListener delegate;

    private final AtomicBoolean canceled = new AtomicBoolean(false);

    CountingRequestCountListener(int count, ServerActivityListener delegate) {
        this.count = new AtomicInteger(count);
        this.delegate = delegate;
    }

    @Override
    public void requestsComplete() {
        if (count.decrementAndGet() == 0) {
            delegate.requestsComplete();
        }
    }

    @Override
    public void unPaused() {
        if (canceled.compareAndSet(false, true)) {
            delegate.unPaused();
        }
    }
}
