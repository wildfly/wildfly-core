/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.logmanager.ClassLoaderLogContextSelector;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.LogContextSelector;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class WildFlyLogContextSelectorImpl implements WildFlyLogContextSelector {

    private final LogContextSelector defaultLogContextSelector;
    private final ClassLoaderLogContextSelector contextSelector;

    private final ThreadLocal<LogContext> localContext = new ThreadLocal<>();

    private final ConcurrentMap<String, LogContext> profileContexts = new ConcurrentHashMap<>();
    private final Lock lock;
    private int counter;
    private int dftCounter;

    WildFlyLogContextSelectorImpl(final LogContext defaultLogContext) {
        this(() -> defaultLogContext);
    }

    WildFlyLogContextSelectorImpl(final LogContextSelector defaultLogContextSelector) {
        // There is not a way to reset the LogContextSelector after a reload. If the current selector is already a
        // WildFlyLogContextSelectorImpl we should use the previous default selector. This avoids possibly wrapping the
        // same log context several times. It should also work with the embedded CLI selector as the commands handle
        // setting and resetting the contexts.
        final LogContextSelector dft;
        if (defaultLogContextSelector instanceof WildFlyLogContextSelectorImpl) {
            dft = ((WildFlyLogContextSelectorImpl) defaultLogContextSelector).defaultLogContextSelector;
        } else {
            dft = defaultLogContextSelector;
        }
        this.defaultLogContextSelector = dft;
        counter = 0;
        dftCounter = 0;
        contextSelector = new ClassLoaderLogContextSelector(dft, true);
        this.lock = new ReentrantLock();
    }

    @Override
    public LogContext getLogContext() {
        final LogContext localContext = this.localContext.get();
        if (localContext != null) {
            return localContext;
        }
        // Not sure where, but there seems to be a race condition here.
        final int counter;
        lock.lock();
        try {
            counter = this.counter;
        } finally {
            lock.unlock();
        }
        // If we have no registered contexts we can just use the default selector. This should improve performance
        // in most cases as the call stack will not be walked. This does depend on the on what was used for the
        // default selector, however in most cases it should perform better.
        return counter > 0 ? contextSelector.getLogContext() : defaultLogContextSelector.getLogContext();
    }

    @Override
    public LogContext setLocalContext(final LogContext newValue) {
        try {
            return localContext.get();
        } finally {
            if (newValue == null) {
                localContext.remove();
            } else {
                localContext.set(newValue);
            }
        }
    }

    @Override
    public void registerLogContext(final ClassLoader classLoader, final LogContext logContext) {
        // We want to register regardless of the current counter for cases when a different log context is registered
        // later.
        contextSelector.registerLogContext(classLoader, logContext);
        lock.lock();
        try {
            if (counter > 0) {
                counter++;
            } else if (logContext != defaultLogContextSelector.getLogContext()) {
                // Move the dftCounter to the counter and add one for this specific log context
                counter = dftCounter + 1;
                dftCounter = 0;
            } else {
                // We're using the default log context at this point
                dftCounter++;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean unregisterLogContext(final ClassLoader classLoader, final LogContext logContext) {
        if (contextSelector.unregisterLogContext(classLoader, logContext)) {
            lock.lock();
            try {
                if (counter > 0) {
                    counter--;
                } else if (dftCounter > 0) {
                    // We don't test the log context here and just assume we're using the default. This is safe as the
                    // registered log contexts must be the default log context.
                    dftCounter--;
                }
            } finally {
                lock.unlock();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean addLogApiClassLoader(final ClassLoader apiClassLoader) {
        return contextSelector.addLogApiClassLoader(apiClassLoader);
    }

    @Override
    public boolean removeLogApiClassLoader(final ClassLoader apiClassLoader) {
        return contextSelector.removeLogApiClassLoader(apiClassLoader);
    }

    @Override
    public int registeredCount() {
        lock.lock();
        try {
            return counter;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public LogContext getOrCreateProfile(final String profileName) {
        LogContext result = profileContexts.get(profileName);
        if (result == null) {
            result = LogContext.create();
            final LogContext current = profileContexts.putIfAbsent(profileName, result);
            if (current != null) {
                result = current;
            }
        }
        return result;
    }

    @Override
    public LogContext getProfileContext(final String loggingProfile) {
        return loggingProfile == null ? null : profileContexts.get(loggingProfile);
    }

    @Override
    public boolean profileContextExists(final String loggingProfile) {
        return loggingProfile != null && profileContexts.containsKey(loggingProfile);
    }

    @Override
    public LogContext addProfileContext(final String loggingProfile, final LogContext context) {
        return profileContexts.put(Objects.requireNonNull(loggingProfile), context);
    }

    @Override
    public LogContext removeProfileContext(final String loggingProfile) {
        return profileContexts.remove(loggingProfile);
    }
}
