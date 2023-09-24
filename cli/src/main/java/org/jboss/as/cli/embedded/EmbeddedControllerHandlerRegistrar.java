/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.embedded;

import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.CommandRegistry;
import org.jboss.modules.ModuleClassLoader;

/**
 * Registers handlers for the embedded ops if the CLI is running in an embedded environment.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class EmbeddedControllerHandlerRegistrar {

    // logmanager.LogManager is installed as the JDK log manager, and it uses its
    // LogContext class and the static field therein heavily. If the CLI-side LogContext
    // class and the modular side LogContext class are different, nothing works properly.
    // Since logmanager is a system pkg, the org.jboss.logging API stuff must be too
    static final String[] EXTENDED_SYSTEM_PKGS = new String[] {"org.jboss.logging", "org.jboss.logmanager"};
    private static final boolean hasModules;
    private static final boolean modular;

    static {
        Class<?> clazz = null;
        ClassLoader cl = EmbeddedControllerHandlerRegistrar.class.getClassLoader();
        try {
            String classname = "org.jboss.modules.Module";
            /**
             * WARNING: The class org.jboss.modules.Module MUST NOT be initialized prior to
             * create an embedded server in a non modular context.
             * The CLI could have loaded the module class during its initialization in 2 cases:
             * 1) if running in modular context, Module is loaded first place. This doesn't affect embedded server.
             * 2) if a module has been set in the VAULT Configuration. It only works in modular context, so we are fine.
             */
            clazz = Class.forName(classname, false, cl);
        } catch (Exception e) {
            // not available
        }
        // hasModules is not strictly needed, we are running with JBOSS modules in all known cases
        // keeping it to be safe.
        hasModules = clazz != null;
        modular = hasModules ? isModular(cl) : false;
    }

    private static boolean isModular(ClassLoader cl) {
        if (cl instanceof ModuleClassLoader) {
            return true;
        } else if (cl != null) {
            return isModular(cl.getParent());
        } else {
            return false;
        }
    }

    public static final AtomicReference<EmbeddedProcessLaunch> registerEmbeddedCommands(CommandRegistry commandRegistry, CommandContext ctx) throws CommandLineException {
        AtomicReference<EmbeddedProcessLaunch> serverReference = new AtomicReference<>();
        if (hasModules) {
            commandRegistry.registerHandler(EmbedServerHandler.create(serverReference, ctx, modular), "embed-server");
            commandRegistry.registerHandler(new StopEmbeddedServerHandler(serverReference), "stop-embedded-server");
            commandRegistry.registerHandler(EmbedHostControllerHandler.create(serverReference, ctx, modular), "embed-host-controller");
            commandRegistry.registerHandler(new StopEmbeddedHostControllerHandler(serverReference), "stop-embedded-host-controller");
        }
        return serverReference;
    }

}
