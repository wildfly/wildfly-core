/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.logmanager;

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
    }

    @Override
    public LogContext getLogContext() {
        final LogContext localContext = this.localContext.get();
        if (localContext != null) {
            return localContext;
        }
        final int counter;
        synchronized (this) {
            counter = this.counter;
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
        synchronized (this) {
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
        }
    }

    @Override
    public boolean unregisterLogContext(final ClassLoader classLoader, final LogContext logContext) {
        if (contextSelector.unregisterLogContext(classLoader, logContext)) {
            synchronized (this) {
                if (counter > 0) {
                    counter--;
                } else if (dftCounter > 0) {
                    // We don't test the log context here and just assume we're using the default. This is safe as the
                    // registered log contexts must be the default log context.
                    dftCounter--;
                }
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
        synchronized (this) {
            return counter;
        }
    }
}
