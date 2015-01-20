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
import org.jboss.as.cli.CommandRegistry;

/**
 * Registers handlers for the embedded ops if the CLI is running in an embedded environment.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class EmbeddedControllerHandlerRegistrar {

    private static final boolean modular;

    static {
        Object obj = null;
        try {
            String classname = "org.jboss.modules.Module";
            ClassLoader cl = EmbeddedControllerHandlerRegistrar.class.getClassLoader();
            Class<?> clazz = cl.loadClass(classname);
            Class[] parameterTypes = {ClassLoader.class, boolean.class};
            Method method = clazz.getDeclaredMethod("forClassLoader", parameterTypes);
            Object[] args = {cl, Boolean.TRUE}; // TODO false?
            obj = method.invoke(null, args);
        } catch (Exception e) {
            // not available
        }
        modular = obj != null;
    }

    public static void registerEmbeddedCommands(CommandRegistry commandRegistry, CommandContext ctx) {
        if (modular) {
            AtomicReference<EmbeddedServerLaunch> serverReference = new AtomicReference<>();
            commandRegistry.registerHandler(EmbedServerHandler.create(serverReference, ctx), "embed-server");
            commandRegistry.registerHandler(new StopEmbeddedServerHandler(serverReference), "stop-embedded-server");
        }
    }
}
