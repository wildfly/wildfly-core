/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.embedded;

import static org.jboss.logging.Logger.Level.WARN;

import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.jboss.logging.Logger;
import org.jboss.modules.Main;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.log.ModuleLogger;
import org.jboss.modules.log.NoopModuleLogger;
import org.wildfly.core.embedded.logging.EmbeddedLogger;

/**
 * A context for the logging environment.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LoggerContext implements Context {
    private static final String MODULE_ID_LOGGING = "org.jboss.logging";

    private final ModuleLoader moduleLoader;

    private volatile ModuleLogger loggerToRestore;

    /**
     * Creates a new logging environment context.
     *
     * @param moduleLoader the module loader to load the logging module
     */
    LoggerContext(final ModuleLoader moduleLoader) {
        this.moduleLoader = moduleLoader;
    }

    @Override
    public void activate() {
        final Module logModule;
        try {
            logModule = moduleLoader.loadModule(MODULE_ID_LOGGING);
        } catch (final ModuleLoadException mle) {
            throw EmbeddedLogger.ROOT_LOGGER.moduleLoaderError(mle, MODULE_ID_LOGGING, moduleLoader);
        }

        final ModuleClassLoader logModuleClassLoader = logModule.getClassLoader();
        final ClassLoader tccl = getTccl();
        try {
            setTccl(logModuleClassLoader);
            loggerToRestore = Module.getModuleLogger();
            Module.setModuleLogger(new JBossLoggingModuleLogger());
        } finally {
            // Reset TCCL
            setTccl(tccl);
        }
    }

    @Override
    public void restore() {
        final Module logModule;
        try {
            logModule = moduleLoader.loadModule(MODULE_ID_LOGGING);
        } catch (final ModuleLoadException mle) {
            throw EmbeddedLogger.ROOT_LOGGER.moduleLoaderError(mle, MODULE_ID_LOGGING, moduleLoader);
        }

        final ModuleClassLoader logModuleClassLoader = logModule.getClassLoader();
        final ClassLoader tccl = getTccl();
        try {
            setTccl(logModuleClassLoader);
            final ModuleLogger loggerToRestore = this.loggerToRestore;
            if (loggerToRestore == null) {
                Module.setModuleLogger(NoopModuleLogger.getInstance());
            } else {
                Module.setModuleLogger(loggerToRestore);
            }
        } finally {
            // Reset TCCL
            setTccl(tccl);
        }
    }

    private static ClassLoader getTccl() {
        if (System.getSecurityManager() == null) {
            return Thread.currentThread().getContextClassLoader();
        }
        return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                return Thread.currentThread().getContextClassLoader();
            }
        });
    }

    private static void setTccl(final ClassLoader cl) {
        if (System.getSecurityManager() == null) {
            Thread.currentThread().setContextClassLoader(cl);
        } else {
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    Thread.currentThread().setContextClassLoader(cl);
                    return null;
                }
            });
        }
    }

    private static class JBossLoggingModuleLogger implements ModuleLogger {

        private final Logger logger;
        private final Logger defineLogger;

        private JBossLoggingModuleLogger() {
            logger = Logger.getLogger("org.jboss.modules");
            defineLogger = Logger.getLogger("org.jboss.modules.define");
        }

        @Override
        public void trace(final String message) {
            if (logger.isTraceEnabled()) {
                logger.trace(message);
            }
        }

        @Override
        public void trace(final String format, final Object arg1) {
            if (logger.isTraceEnabled()) {
                logger.tracef(format, arg1);
            }
        }

        @Override
        public void trace(final String format, final Object arg1, final Object arg2) {
            if (logger.isTraceEnabled()) {
                logger.tracef(format, arg1, arg2);
            }
        }

        @Override
        public void trace(final String format, final Object arg1, final Object arg2, final Object arg3) {
            if (logger.isTraceEnabled()) {
                logger.tracef(format, arg1, arg2, arg3);
            }
        }

        @Override
        public void trace(final String format, final Object... args) {
            if (logger.isTraceEnabled()) {
                logger.tracef(format, args);
            }
        }

        @Override
        public void trace(final Throwable t, final String message) {
            if (logger.isTraceEnabled()) {
                logger.trace(message, t);
            }
        }

        @Override
        public void trace(final Throwable t, final String format, final Object arg1) {
            if (logger.isTraceEnabled()) {
                logger.tracef(t, format, arg1);
            }
        }

        @Override
        public void trace(final Throwable t, final String format, final Object arg1, final Object arg2) {
            if (logger.isTraceEnabled()) {
                logger.tracef(t, format, arg1, arg2);
            }
        }

        @Override
        public void trace(final Throwable t, final String format, final Object arg1, final Object arg2, final Object arg3) {
            if (logger.isTraceEnabled()) {
                logger.tracef(t, format, arg1, arg2, arg3);
            }
        }

        @Override
        public void trace(final Throwable t, final String format, final Object... args) {
            if (logger.isTraceEnabled()) {
                logger.tracef(t, format, args);
            }
        }

        @Override
        public void greeting() {
            if (logger.isInfoEnabled()) {
                logger.infof("JBoss Modules version %s", Main.getVersionString());
            }
        }

        @Override
        public void moduleDefined(final String name, final ModuleLoader moduleLoader) {
            if (logger.isDebugEnabled()) {
                logger.debugf("Module %s defined by %s", name, moduleLoader);
            }
        }

        @Override
        public void classDefineFailed(final Throwable throwable, final String className, final Module module) {
            if (defineLogger.isEnabled(WARN)) {
                defineLogger.warnf(throwable, "Failed to define class %s in %s", className, module);
            }
        }

        @Override
        public void classDefined(final String name, final Module module) {
            if (defineLogger.isTraceEnabled()) {
                defineLogger.tracef("Defined class %s in %s", name, module);
            }
        }

        @Override
        public void overlaysDirectoryNotReadable(final File file) {
            if (logger.isEnabled(WARN)) {
                logger.warnf("Overlays directory exists but is not readable: %s", file.getPath());
            }
        }

        @Override
        public void overlaysMetadataNotReadable(final File file) {
            if (logger.isEnabled(WARN)) {
                logger.warnf("Overlays metadata file exists but is not readable: %s", file.getPath());
            }
        }

        @Override
        public void overlayRootNotReadable(final File file) {
            if (logger.isEnabled(WARN)) {
                logger.warnf("Overlay root directory doesn't exists or is not readable: %s", file.getPath());
            }
        }
    }
}
