/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.msc.service.StartException;
import org.wildfly.common.function.ExceptionSupplier;

/**
 * Shared synchronization SPI for {@link ElytronDoohickey} and any cross-extension coordinator
 * that must participate in the same lock and cycle-detection protocol.
 *
 * <p>All coordinators that initialize resources during {@code Stage.RUNTIME} <em>must</em> acquire
 * the same global lock via {@link #withLock(PathAddress, ExceptionSupplier)} and must not hold
 * any independent lock alongside it, as that would introduce a potential deadlock.</p>
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class DoohickeySimultaneity {

    private static final ThreadLocal<Deque<PathAddress>> CALL_STACK = new ThreadLocal<>() {
        @Override
        protected Deque<PathAddress> initialValue() {
            return new ArrayDeque<>();
        }
    };

    /*
     * As each Thread tracks the addresses of the relevant resources we could likely implement some form of
     * deadlock detection that does not rely on taking a global lock.
     */

    private static final Lock GLOBAL_LOCK = new ReentrantLock();

    private DoohickeySimultaneity() {
        // static utility — not instantiable
    }

    /**
     * Executes {@code supplier} under the global doohickey lock for the given {@code resourceAddress},
     * performing cycle detection before the call and stack cleanup in a {@code finally} block.
     *
     * <p>Callers must not hold any other lock when calling this method.  The sequence is:
     * <ol>
     *   <li>Acquire {@code GLOBAL_LOCK}</li>
     *   <li>Check for a cycle ({@link OperationFailedException} if detected)</li>
     *   <li>Push {@code resourceAddress} onto the per-thread call stack</li>
     *   <li>Invoke {@code supplier.get()}</li>
     *   <li>Pop the address and release the lock in {@code finally}</li>
     * </ol>
     *
     * @param <T>             the return type of the supplier
     * @param resourceAddress the address of the resource being initialized; used for cycle detection
     * @param supplier        the initialization work to perform under the lock
     * @return the value produced by {@code supplier}
     * @throws OperationFailedException if a dependency cycle is detected, or if the supplier throws
     */
    public static <T> T withLock(PathAddress resourceAddress, ExceptionSupplier<T, OperationFailedException> supplier)
            throws OperationFailedException {
        GLOBAL_LOCK.lock();
        try {
            checkCycle(resourceAddress);
            try {
                return supplier.get();
            } finally {
                CALL_STACK.get().removeFirst();
            }
        } finally {
            GLOBAL_LOCK.unlock();
        }
    }

    /**
     * Variant of {@link #withLock(PathAddress, ExceptionSupplier)} for MSC service start paths where
     * the supplier may throw {@link StartException} instead of {@link OperationFailedException}.
     *
     * @param <T>             the return type of the supplier
     * @param resourceAddress the address of the resource being initialized
     * @param supplier        the initialization work to perform under the lock
     * @return the value produced by {@code supplier}
     * @throws OperationFailedException if a dependency cycle is detected
     * @throws StartException           if the supplier throws a {@link StartException}
     */
    public static <T> T withLockForService(PathAddress resourceAddress, ExceptionSupplier<T, StartException> supplier)
            throws OperationFailedException, StartException {
        GLOBAL_LOCK.lock();
        try {
            checkCycle(resourceAddress);
            try {
                return supplier.get();
            } finally {
                CALL_STACK.get().removeFirst();
            }
        } finally {
            GLOBAL_LOCK.unlock();
        }
    }

    /**
     * Checks whether {@code resourceAddress} is already on this thread's initialization stack and, if
     * not, pushes it. Throws {@link OperationFailedException} with the cycle path if a cycle is found.
     *
     * <p>Must only be called while holding {@code GLOBAL_LOCK}.</p>
     *
     * @param resourceAddress the address about to be initialized
     * @throws OperationFailedException if the address is already on the stack
     */
    private static void checkCycle(PathAddress resourceAddress) throws OperationFailedException {
        Deque<PathAddress> currentStack = CALL_STACK.get();
        if (currentStack.contains(resourceAddress)) {
            StringBuilder sb = new StringBuilder();
            Iterator<PathAddress> iterator = currentStack.descendingIterator();
            boolean foundStart = false;
            while (iterator.hasNext()) {
                PathAddress current = iterator.next();
                foundStart = foundStart || current.equals(resourceAddress);
                if (foundStart) {
                    sb.append('{').append(current).append("}->");
                }
            }
            sb.append('{').append(resourceAddress).append('}');
            throw ROOT_LOGGER.cycleDetected(sb.toString());
        }
        currentStack.addFirst(resourceAddress);
    }

}
