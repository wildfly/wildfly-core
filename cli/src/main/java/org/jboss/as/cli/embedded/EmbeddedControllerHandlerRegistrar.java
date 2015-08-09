/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.cli.embedded;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.CommandRegistry;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Registers handlers for the embedded ops if the CLI is running in an embedded environment.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class EmbeddedControllerHandlerRegistrar {

    private static final boolean hasModules;
    private static final boolean modular;

    static {
        Class<?> clazz = null;
        Object obj = null;
        String systemPkgs = WildFlySecurityManager.getPropertyPrivileged("jboss.modules.system.pkgs", null);
        try {
            // Set jboss.modules.system.pkgs before loading Module class, as it reads it in a static initializer
            //
            // Note that if it turns out we're already in a modular environment, setting this does
            // nothing, as the prop has already been read. Does no harm either.
            //
            // Why these packages?
            // 1) msc, dmr, controller.client are part of the StandaloneServer API
            // 2) org.jboss.threads is unfortunately part of the ModelControllerClient API
            // 3) org.jboss.modules -- dunno, seems vaguely logical; the AS7 embedded code included this so I do too
            // 4) org.jboss.logging and org.jboss.logmanager -- logmanager.LogManager is installed as
            //    the JDK log manager, and it uses its LogContext class and the static field therein
            //    heavily. If the CLI-side LogContext class and the modular side LogContext class are different,
            //    nothing works properly. Since logmanager is a system pkg, the org.jboss.logging API stuff must be too
            WildFlySecurityManager.setPropertyPrivileged("jboss.modules.system.pkgs",
                    "org.jboss.modules,org.jboss.msc,org.jboss.dmr,org.jboss.threads," +
                    "org.jboss.as.controller.client,org.jboss.logging,org.jboss.logmanager");

            String classname = "org.jboss.modules.Module";
            ClassLoader cl = EmbeddedControllerHandlerRegistrar.class.getClassLoader();
            clazz = cl.loadClass(classname);
            Class[] parameterTypes = {ClassLoader.class, boolean.class};
            Method method = clazz.getDeclaredMethod("forClassLoader", parameterTypes);
            Object[] args = {cl, Boolean.TRUE}; // TODO false?
            obj = method.invoke(null, args);
        } catch (Exception e) {
            // not available
        } finally {
            // Restore the system packages var
            if (systemPkgs == null) {
                WildFlySecurityManager.clearPropertyPrivileged("jboss.modules.system.pkgs");
            } else {
                WildFlySecurityManager.setPropertyPrivileged("jboss.modules.system.pkgs",systemPkgs);
            }
        }
        hasModules = clazz != null;
        modular = obj != null;
    }

    public static final AtomicReference<EmbeddedServerLaunch> registerEmbeddedCommands(CommandRegistry commandRegistry, CommandContext ctx) throws CommandLineException {
        AtomicReference<EmbeddedServerLaunch> serverReference = new AtomicReference<>();
        if (hasModules) {
            commandRegistry.registerHandler(EmbedServerHandler.create(serverReference, ctx, modular), "embed-server");
            commandRegistry.registerHandler(new StopEmbeddedServerHandler(serverReference), "stop-embedded-server");
            commandRegistry.registerHandler(EmbedHostControllerHandler.create(serverReference, ctx, modular), "embed-host-controller");
            commandRegistry.registerHandler(new StopEmbeddedHostControllerHandler(serverReference), "stop-embedded-host-controller");
        }
        return serverReference;
    }

}
