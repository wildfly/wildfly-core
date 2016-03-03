/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Basic lock implementation using a permit value to allow reentrancy. The lock will only be released when all
 * participants which previously acquired the lock have called {@linkplain #unlock}.
 *
 * The lock supports two mutually exclusive modes, shared and exclusive. If shared locks are acquired and held
 * then the exclusive lock may not be acquired, and if the exclusive lock is held, the shared locks may not be acquired.
 * For an existing "permit holder" (operationId), the lock may be reentrantly re-acquired.
 *
 * @author Emanuel Muckenhuber
 * @author Ken Wills
 */
class ModelControllerLock {
    private final Sync sync = new Sync();

    /**
     * Attempts to acquire in exclusive mode. This will allow any other consumers using the same {@code permit} to
     * also acquire. This is typically used for a write lock.
     * @param permit - the permit Integer for this operation. May not be {@code null}.
     * @throws IllegalStateException - if the permit is null.
     */
    void lock(final Integer permit) {
        if (permit == null) {
            throw new IllegalArgumentException();
        }
        sync.acquire(permit);
    }

    /**
     * Attempts to acquire the lock in shared mode. In this mode the lock may be shared over a number
     * of different permit holders, and blocking the exclusive look from being acquired. Typically used for read locks to
     * allow multiple readers concurrently.
     * @param permit - the permit Integer for this operation. May not be {@code null}.
     */
    void lockShared(final Integer permit) {
        if (permit == null) {
            throw new IllegalArgumentException();
        }
        sync.acquireShared(permit);
    }

    /** Attempts exclusive acquisition with a max wait time.
     * @param permit - the permit Integer for this operation. May not be {@code null}.
     * @param timeout - the time value to wait for acquiring the lock
     * @param unit - See {@code TimeUnit} for valid values
     * @return {@code boolean} true on success.
     */
    boolean lock(final Integer permit, final long timeout, final TimeUnit unit) {
        boolean result = false;
        try {
            result = lockInterruptibly(permit, timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result;
    }

    /** Attempts shared acquisition with a max wait time.
     * @param permit - the permit Integer for this operation. May not be {@code null}.
     * @param timeout - the time value to wait for acquiring the lock
     * @param unit - See {@code TimeUnit} for valid values
     * @return {@code boolean} true on success.
     */
    boolean lockShared(final Integer permit, final long timeout, final TimeUnit unit) {
        boolean result = false;
        try {
            result = lockSharedInterruptibly(permit, timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result;
    }

    /**
     * Acquire the exclusive lock allowing the acquisition to be interrupted.
     * @param permit - the permit Integer for this operation. May not be {@code null}.
     * @throws InterruptedException - if the acquiring thread is interrupted.
     * @throws IllegalArgumentException if {@code permit} is null.
     */
    void lockInterruptibly(final Integer permit) throws InterruptedException {
        if (permit == null) {
            throw new IllegalArgumentException();
        }
        sync.acquireInterruptibly(permit);
    }

    /**
     * Acquire the shared lock allowing the acquisition to be interrupted.
     * @param permit - the permit Integer for this operation. May not be {@code null}.
     * @throws InterruptedException - if the acquiring thread is interrupted.
     * @throws IllegalArgumentException if {@code permit} is null.
     */
    void lockSharedInterruptibly(final Integer permit) throws InterruptedException {
        if (permit == null) {
            throw new IllegalArgumentException();
        }
        sync.acquireSharedInterruptibly(permit);
    }

    /**
     * Acquire the exclusive lock, with a max wait timeout to acquire.
     * @param permit - the permit Integer for this operation. May not be {@code null}.
     * @param timeout - the timeout scalar quantity.
     * @param unit - see {@code TimeUnit} for quantities.
     * @return {@code boolean} true on successful acquire.
     * @throws InterruptedException - if the acquiring thread was interrupted.
     * @throws IllegalArgumentException if {@code permit} is null.
     */
    boolean lockInterruptibly(final Integer permit, final long timeout, final TimeUnit unit) throws InterruptedException {
        if (permit == null) {
            throw new IllegalArgumentException();
        }
        return sync.tryAcquireNanos(permit, unit.toNanos(timeout));
    }

    /**
     * Acquire the shared lock, with a max wait timeout to acquire.
     * @param permit - the permit Integer for this operation. May not be {@code null}.
     * @param timeout - the timeout scalar quantity.
     * @param unit - see {@code TimeUnit} for quantities.
     * @return {@code boolean} true on successful acquire.
     * @throws InterruptedException - if the acquiring thread was interrupted.
     * @throws IllegalArgumentException if {@code permit} is null.
     */
    boolean lockSharedInterruptibly(final Integer permit, final long timeout, final TimeUnit unit) throws InterruptedException {
        if (permit == null) {
            throw new IllegalArgumentException();
        }
        return sync.tryAcquireSharedNanos(permit, unit.toNanos(timeout));
    }

    /**
     * Unlock a previously held exclusive lock. In the case of multiple lock holders, the underlying lock is only
     * released when all of the holders have called #unlock.
     * @param permit - the permit Integer for this operation. May not be {@code null}.
     * @throws IllegalArgumentException if {@code permit} is null.
     */
    void unlock(final Integer permit) {
        if (permit == null) {
            throw new IllegalArgumentException();
        }
        sync.release(permit);
    }

    /**
     * Unlock a previously held shared lock. In the case of multiple lock holders, the underlying lock is only
     * released when all of the holders have called #unlock. In the case of shared mode lock holders, they may
     * be a variety of different permit holders, as the shared mode lock is not tagged with a single owner as
     * the exclusive lock is.
     * @param permit - the permit Integer for this operation. May not be {@code null}.
     * @throws IllegalArgumentException if {@code permit} is null.
     */
    void unlockShared(final Integer permit) {
        if (permit == null) {
            throw new IllegalArgumentException();
        }
        sync.releaseShared(permit);
    }

    /**
     * Attempt to query and acquire the exclusive lock
     * @param permit - the permit Integer for this operation. May not be {@code null}.
     * @return {@code boolean} true if the lock was acquired, false if not available
     * for locking in exclusive mode, or already locked shared.
     */
    boolean detectDeadlockAndGetLock(final int permit) {
        return sync.tryAcquire(permit);
    }

    /**
     * Implementation {@link AbstractQueuedSynchronizer} that maintains
     * lock state in a single {@code int}, managed by #getState() and #compareAndSet().
     */
    private class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 1L;

        // reserves the top 16 bytes for exclusive / shared mode short
        private static final int EXCLUSIVE_SHIFT = 30;
        private static final int COUNT_MASK = (1 << EXCLUSIVE_SHIFT) - 1;
        private static final int MAX_COUNT = COUNT_MASK;

        // values for indicating shared / exclusive modes.
        private static final short NOT_LOCKED = 0;
        private static final short EXCLUSIVE = 1;
        private static final short SHARED = 2;

        // the current permit holder in exclusive mode.
        private int permitHolder;

        @Override
        protected final boolean tryAcquire(final int permit) {
            return internalAcquire(permit, true) == 1;
        }

        @Override
        protected final int tryAcquireShared(final int permit) {
            return internalAcquire(permit, false);
        }

        @Override
        protected final boolean tryRelease(final int permit) {
            return internalRelease(permit, true);
        }

        @Override
        protected final boolean tryReleaseShared(final int permit) {
            return internalRelease(permit, false);
        }

        private void setCurrentPermitHolder(final int permit) {
            this.permitHolder = permit;
        }

        private int getCurrentPermitHolder() {
            return permitHolder;
        }

        private int getCount(final int value) {
            return (value & COUNT_MASK);
        }

        private int getLockMode(final int value) {
            return ((value >>> EXCLUSIVE_SHIFT));
        }

        // store creates the int state value, storing it as (short)lockMode(short)lockCount
        private int makeState(final int lockMode, final int count) {
            assert lockMode == EXCLUSIVE || lockMode == SHARED;
            assert count > 0;
            if (count < 0 || count > MAX_COUNT)
                throw new IllegalMonitorStateException("Maximum lock count exceeded.");
            int state = ((lockMode) << EXCLUSIVE_SHIFT) | ((count) & COUNT_MASK);
            // tmp asserts
            assert count == getCount(state);
            assert lockMode == getLockMode(state);
            return state;
        }

        /**
         * Attempt to acquire the lock in the specified lock mode.
         *
         * @param permit - the lock permit object, for exclusive locks, multiple acquires for the same permit are allowed.
         * @param exclusive - Whether to attempt to acquire the exclusive (true) or shared lock (false).
         * @return {@code int} < 0 for failure, > 0 for success.
         */
        private int internalAcquire(final int permit, final boolean exclusive) {

            // loop until the CAS is successful and the state has been updated.
            for (; ; ) {
                // read the current state
                int state = getState();

                int count = getCount(state);
                int mode = getLockMode(state);

                // can't acquire exclusive when already in shared mode.
                if (mode == SHARED && exclusive)
                    return -1;

                // (1) If getCurrentPermitHolder is a stale read, then state has been updated
                // by another thread, which means either mode or count will no longer match
                // and the CAS below will fail until the next getState() above.
                // (2) If state has been reentrantly updated with the same operation id, then
                // count will have changed (either +/-), and the CAS will fail even if the permit
                // matches.
                // (3) If the lock mode is exclusive, then shared acquire is not
                // allowed.
                if (mode == EXCLUSIVE && (getCurrentPermitHolder() != permit || !exclusive))
                    return -1;

                short next = (short) (count + 1); // increase lock count
                if (next < 0) {
                    throw new IllegalMonitorStateException("Maximum lock count exceeded.");
                }
                int newState = makeState(exclusive ? EXCLUSIVE : SHARED, next);
                if (compareAndSetState(state, newState)) {
                    if (exclusive)
                        setCurrentPermitHolder(permit);
                    return 1;
                }
            }
        }

        /**
         * Attempt to release the lock in the specified lock mode.
         * @param permit - the lock permit value.
         * @param exclusive - Whether to attempt to release the lock in the the exclusive (true) or shared lock (false) modes.
         * @return {@code boolean} true for success.
         */
        private boolean internalRelease(int permit, final boolean exclusive) {

            for (; ; ) {
                int state = getState();
                int mode = getLockMode(state);
                int next = getCount(state) - 1; // decrease lock count
                if (next < 0) {
                    throw new IllegalMonitorStateException("Lock count exceeded.");
                }
                switch (mode) {
                    case NOT_LOCKED:
                        throw new IllegalMonitorStateException(exclusive ? "Write Lock not held." : "Read Lock not held.");
                    case EXCLUSIVE:
                        if (! exclusive)
                            throw new IllegalMonitorStateException("Read lock release not allowed in exclusive mode.");
                        if (getCurrentPermitHolder() != permit)
                            return false;
                        break;
                    case SHARED:
                        if (exclusive)
                            throw new IllegalMonitorStateException("Write lock release not allowed in shared mode.");
                        break;
                    default:
                        throw new IllegalMonitorStateException("Unknown lock mode.");
                }
                int newState = (next == 0 ? 0 : makeState(exclusive ? EXCLUSIVE : SHARED, next));
                if (compareAndSetState(state, newState)) {
                    // don't need to reset permit, it'll be written to on the next exclusive lock acquire
                    return next == 0;
                }
            }
        }

    }
}


