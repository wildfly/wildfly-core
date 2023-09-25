/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * Unit tests of {@link ModelControllerLock}.
 *
 * @author Ken Wills <kwills@redhat.com> (c) 2016 Red Hat
 */
public class ModelControllerLockTestCase {

    private static final int OP1 = 11111;
    private static final int OP2 = 22222;
    private static final int OP3 = 33333;
    private static final long DEFAULT_TIMEOUT = 1;
    private static final TimeUnit DEFAULT_TIMEUNIT = TimeUnit.MILLISECONDS;

    @Test
    public void testAcquireBasic() throws InterruptedException {
        ModelControllerLock lock = new ModelControllerLock();
        lock.lock(OP1);
        assertTrue(lock.lockInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertTrue(lock.lock(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
    }

    @Test
    public void testReacquireBasic() throws InterruptedException {
        ModelControllerLock lock = new ModelControllerLock();
        lock.lock(OP1);
        assertFalse(lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertTrue(lock.lockInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertTrue(lock.lock(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlock(OP1);
        assertFalse(lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlock(OP1);
        assertFalse(lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlock(OP1);
        lock.lock(OP2);
        assertFalse(lock.lockInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertFalse(lock.lock(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertTrue(lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlock(OP2);
        assertFalse(lock.lockInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlock(OP2);
        assertTrue(lock.lockInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testUnlockNotLockedExclusive() throws IllegalStateException {
        ModelControllerLock lock = new ModelControllerLock();
        lock.unlock(OP1);
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testTooManyExclusiveUnlocks() throws IllegalStateException {
        ModelControllerLock lock = new ModelControllerLock();
        for (int i = 0; i < 5; i++) {
            lock.lock(OP1);
        }

        for (int i = 0; i < 6; i++) {
            lock.unlock(OP1);
        }
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testUnlockNotLockedShared() throws IllegalStateException {
        ModelControllerLock lock = new ModelControllerLock();
        lock.unlockShared(OP1);
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testTooManyUnlocksShared() throws IllegalStateException {
        ModelControllerLock lock = new ModelControllerLock();
        for (int i = 0; i < 5; i++) {
            lock.lockShared(OP1);
        }

        for (int i = 0; i < 6; i++) {
            lock.unlockShared(OP1);
        }
    }

    @Test
    public void testAcquire() throws InterruptedException {
        ModelControllerLock lock = new ModelControllerLock();
        lock.lock(OP1);
        assertFalse(lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertFalse(lock.lock(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlock(OP1);
        lock.lock(OP2);
        assertFalse(lock.lockInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertFalse(lock.lock(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
    }

    @Test
    public void testExclusiveBlocksShared() throws InterruptedException {
        ModelControllerLock lock = new ModelControllerLock();
        lock.lock(OP1);
        assertFalse(lock.lockSharedInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertFalse(lock.lockSharedInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlock(OP1);
        lock.lockShared(OP2);
        assertTrue(lock.lockSharedInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertFalse(lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertTrue(lock.lockSharedInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertFalse(lock.lockInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
    }

    @Test
    public void testAcquireSharedBasic() throws InterruptedException {
        ModelControllerLock lock = new ModelControllerLock();
        lock.lockShared(OP1);
        assertTrue(lock.lockSharedInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertTrue(lock.lockShared(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlockShared(OP1);
        assertFalse(lock.lockInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlockShared(OP1);
        assertFalse(lock.lockInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlockShared(OP1);
        assertTrue(lock.lockInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
    }

    @Test
    public void testAcquireShared() throws InterruptedException {
        ModelControllerLock lock = new ModelControllerLock();
        lock.lockShared(OP1);
        lock.lockShared(OP2);
        assertTrue(lock.lockSharedInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertTrue(lock.lockSharedInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertFalse(lock.lockInterruptibly(OP3, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertTrue(lock.lockShared(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertTrue(lock.lockShared(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
    }

    @Test
    public void testSharedBlocksExclusive() throws InterruptedException {
        ModelControllerLock lock = new ModelControllerLock();
        lock.lockShared(OP1);
        assertFalse(lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertFalse(lock.lockInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlockShared(OP1);
        assertTrue(lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertFalse(lock.lockInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertTrue(lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
    }

    @Test
    public void testExclusiveAllowsPermit0() throws InterruptedException {
        ModelControllerLock lock = new ModelControllerLock();
        lock.lock(0);
        assertFalse(lock.lockSharedInterruptibly(0, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertTrue(lock.lockInterruptibly(0, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertFalse(lock.lockSharedInterruptibly(0, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
    }

    @Test
    public void testSharedAllowsPermit0() throws InterruptedException {
        ModelControllerLock lock = new ModelControllerLock();
        lock.lockShared(0);
        assertFalse(lock.lockInterruptibly(0, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertTrue(lock.lockSharedInterruptibly(0, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertFalse(lock.lockInterruptibly(0, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
    }

    @Test
    public void testExclusiveAllowsMinMaxInt() throws InterruptedException {
        ModelControllerLock lock = new ModelControllerLock();
        lock.lock(Integer.MAX_VALUE);
        assertFalse(lock.lockSharedInterruptibly(Integer.MAX_VALUE, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertTrue(lock.lockInterruptibly(Integer.MAX_VALUE, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertFalse(lock.lockSharedInterruptibly(Integer.MAX_VALUE, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlock(Integer.MAX_VALUE);
        lock.unlock(Integer.MAX_VALUE);
        assertTrue(lock.lockSharedInterruptibly(Integer.MAX_VALUE, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlockShared(Integer.MAX_VALUE);

        lock.lock(Integer.MIN_VALUE);
        assertFalse(lock.lockSharedInterruptibly(Integer.MIN_VALUE, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertTrue(lock.lockInterruptibly(Integer.MIN_VALUE, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertFalse(lock.lockSharedInterruptibly(Integer.MIN_VALUE, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlock(Integer.MIN_VALUE);
        lock.unlock(Integer.MIN_VALUE);
        assertTrue(lock.lockSharedInterruptibly(Integer.MIN_VALUE, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlockShared(Integer.MIN_VALUE);
    }

    @Test
    public void testSharedAllowsMinMaxInt() throws InterruptedException {
        ModelControllerLock lock = new ModelControllerLock();
        lock.lockShared(Integer.MAX_VALUE);
        assertFalse(lock.lockInterruptibly(Integer.MAX_VALUE, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertTrue(lock.lockSharedInterruptibly(Integer.MAX_VALUE, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertFalse(lock.lockInterruptibly(Integer.MAX_VALUE, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlockShared(Integer.MAX_VALUE);
        lock.unlockShared(Integer.MAX_VALUE);
        assertTrue(lock.lockInterruptibly(Integer.MAX_VALUE, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlock(Integer.MAX_VALUE);

        lock.lockShared(Integer.MIN_VALUE);
        assertFalse(lock.lockInterruptibly(Integer.MIN_VALUE, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertTrue(lock.lockSharedInterruptibly(Integer.MIN_VALUE, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        assertFalse(lock.lockInterruptibly(Integer.MIN_VALUE, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlockShared(Integer.MIN_VALUE);
        lock.unlockShared(Integer.MIN_VALUE);
        assertTrue(lock.lockInterruptibly(Integer.MIN_VALUE, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        lock.unlock(Integer.MIN_VALUE);
    }

    @Test
    public void testExclusiveWithThreads() throws InterruptedException {

        final ModelControllerLock lock = new ModelControllerLock();

        final boolean[] thread1aResultlockInterruptiblyOP2 = new boolean[1];
        Runnable a = new Runnable() {
            @Override
            public void run() {
                try {
                    lock.lock(OP1);
                    thread1aResultlockInterruptiblyOP2[0] = lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT); //assertFalse
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        final boolean[] thread2bResultlockInterruptiblyOP2 = new boolean[1];
        Runnable b = new Runnable() {
            @Override
            public void run() {
                try {
                    thread2bResultlockInterruptiblyOP2[0] = lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT); //assertFalse
                    lock.unlock(OP1);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        Thread t1 = new Thread(a);
        Thread t2 = new Thread(b);
        t1.start();
        t1.join();
        assertFalse(thread1aResultlockInterruptiblyOP2[0]);
        assertFalse(lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        t2.start();
        t2.join();
        assertFalse(thread2bResultlockInterruptiblyOP2[0]);
        assertTrue(lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
    }

    @Test
    public void testSharedWithThreads() throws InterruptedException {

        final ModelControllerLock lock = new ModelControllerLock();

        final boolean[] thread1aResultlockInterruptiblyOP2 = new boolean[1];
        final boolean[] thread1aResultlockSharedInterruptibly = new boolean[1];
        final boolean[] thread1aResultlockInterruptiblyOP1 = new boolean[1];
        Runnable a = new Runnable() {
            @Override
            public void run() {
                try {
                    lock.lockShared(OP1);
                    thread1aResultlockInterruptiblyOP2[0] = lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT); //assertFalse
                    thread1aResultlockSharedInterruptibly[0] = lock.lockSharedInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT); //assertTrue
                    thread1aResultlockInterruptiblyOP1[0] = lock.lockInterruptibly(OP1, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT); //assertFalse
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        final boolean[] thread2bResultlockInterruptiblyOP2check1 = new boolean[1];
        final boolean[] thread2bResultlockInterruptiblyOP2check2 = new boolean[1];
        Runnable b = new Runnable() {
            @Override
            public void run() {
                try {
                    thread2bResultlockInterruptiblyOP2check1[0] = lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT); //assertFalse
                    lock.unlockShared(OP2);
                    lock.unlockShared(OP1);
                    thread2bResultlockInterruptiblyOP2check2[0] = lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT); //assertTrue
                    lock.unlock(OP2);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        Thread t1 = new Thread(a);
        Thread t2 = new Thread(b);
        t1.start();
        t1.join();
        assertFalse(thread1aResultlockInterruptiblyOP2[0]);
        assertTrue(thread1aResultlockSharedInterruptibly[0]);
        assertFalse(thread1aResultlockInterruptiblyOP1[0]);
        assertFalse(lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
        t2.start();
        t2.join();
        assertFalse(thread2bResultlockInterruptiblyOP2check1[0]);
        assertTrue(thread2bResultlockInterruptiblyOP2check2[0]);
        assertTrue(lock.lockInterruptibly(OP2, DEFAULT_TIMEOUT, DEFAULT_TIMEUNIT));
    }
}
