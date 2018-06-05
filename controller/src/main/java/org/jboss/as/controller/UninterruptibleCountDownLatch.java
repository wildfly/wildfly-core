/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced CountDownLatch providing uninterruptible variants of await() methods.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class UninterruptibleCountDownLatch extends CountDownLatch {

    public UninterruptibleCountDownLatch(final int count) {
        super(count);
    }

    /**
     * Behaves the same way as {@link #await()} except it never throws InterruptedException.
     * Thread interrupt status will be preserved if thread have been interrupted inside this method.
     */
    public void awaitUninterruptibly() {
        boolean interrupted = Thread.interrupted();
        try {
            while (true) {
                try {
                    await();
                    break;
                } catch (InterruptedException ie) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    /**
     * Behaves the same way as {@link #await(long,TimeUnit)} except it never throws InterruptedException.
     * Thread interrupt status will be preserved if thread have been interrupted inside this method.
     * @param timeout the maximum time to wait
     * @param unit the time unit of the {@code timeout} argument
     * @return {@code true} if the count reached zero and {@code false}
     *         if the waiting time elapsed before the count reached zero
     */
    public boolean awaitUninterruptibly(final long timeout, final TimeUnit unit) {
        boolean interrupted = Thread.interrupted();
        long now = System.nanoTime();
        long remaining = unit.toNanos(timeout);
        try {
            while (true) {
                if (remaining <= 0L) return false;
                try {
                    return await(remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException ie) {
                    interrupted = true;
                    remaining -= (-now + (now = System.nanoTime()));
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

}
