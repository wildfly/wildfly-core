/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.logging;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;

/**
 * Early initialization of logging frameworks to prevent race conditions during MSC boot.
 */
public final class EarlyLoggingInit {

    private static final String SLF4J_MODULE = "org.slf4j";

    private EarlyLoggingInit() {
    }

    /**
     * Initialize the SLF4J factory eagerly so that MSC service threads do not race
     * on the first {@code LoggerFactory.getLogger()} call during boot. Without this,
     * threads that call {@code getLogger()} while SLF4J initialization is ongoing
     * receive substitute loggers, producing spurious warnings on stderr.
     * <p>
     * Loads the {@code org.slf4j} JBoss Module through the boot module loader and
     * invokes {@code LoggerFactory.getILoggerFactory()} via reflection. If the
     * module is not available the call is silently skipped.
     */
    public static void initSlf4j() {
        final ModuleLoader loader = Module.getBootModuleLoader();
        try {
            final Module module = loader.loadModule(SLF4J_MODULE);
            Class.forName("org.slf4j.LoggerFactory", true, module.getClassLoader()).getMethod("getILoggerFactory").invoke(null);
        } catch (ModuleLoadException e) {
            // Module not available — nothing to initialize
            return;
        } catch (Throwable t) {
            ServerLogger.ROOT_LOGGER.debugf(t, "Failed to initialize SLF4J");
        }
    }
}
