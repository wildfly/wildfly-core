/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.embedded;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.LogContextSelector;
import org.jboss.stdio.StdioContext;
import org.jboss.stdio.StdioContextSelector;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * {@link org.jboss.stdio.StdioContextSelector} and {@link org.jboss.logmanager.LogContextSelector}
 * that uses an {@link java.lang.InheritableThreadLocal} as a source of the contexts.
 * <p>
 * Note that if the logger is a CLI logger the default contexts will be used regardless of the thread-local contexts.
 * </p>
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
class ThreadLocalContextSelector implements LogContextSelector, StdioContextSelector {

    private final InheritableThreadLocal<Contexts> threadLocal = new InheritableThreadLocal<>();

    private final Contexts localContexts;
    private final Contexts defaultContexts;
    private final ClassLoader cliClassLoader;

    ThreadLocalContextSelector(Contexts local, Contexts defaults) {
        assert local != null;
        assert local.getLogContext() != null; // local.stdioContext can be null
        assert defaults != null;
        assert defaults.getStdioContext() != null;
        assert defaults.getLogContext() != null;
        this.localContexts = local;
        this.defaultContexts = defaults;
        cliClassLoader = ThreadLocalContextSelector.class.getClassLoader();
    }

    Contexts pushLocal() {
        Contexts result = threadLocal.get();
        threadLocal.set(localContexts);
        return result;
    }

    void restore(Contexts toRestore) {
        threadLocal.set(toRestore);
    }

    @Override
    public StdioContext getStdioContext() {
        // CLI loggers should only use the default stdio context regardless if the thread-local context is set.
        final ClassLoader tccl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        if (tccl != null && tccl.equals(cliClassLoader)) {
            return defaultContexts.getStdioContext();
        }
        Contexts threadContext = threadLocal.get();
        StdioContext local = threadContext != null ? threadContext.getStdioContext() : null;
        return local == null ? defaultContexts.getStdioContext() : local;
    }

    @Override
    public LogContext getLogContext() {
        // CLI loggers should only use the default stdio context regardless if the thread-local context is set This
        // allows the context configured for CLI, e.g. jboss-cli-logging.properties.
        final ClassLoader tccl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        if (tccl != null && tccl.equals(cliClassLoader)) {
            return defaultContexts.getLogContext();
        }
        Contexts threadContext = threadLocal.get();
        LogContext local = threadContext != null ? threadContext.getLogContext() : null;
        return local == null ? defaultContexts.getLogContext() : local;
    }

}
