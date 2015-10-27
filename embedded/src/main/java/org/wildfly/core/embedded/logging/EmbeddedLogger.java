/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.wildfly.core.embedded.logging;

import static org.jboss.logging.Logger.Level.WARN;

import java.io.File;
import java.lang.reflect.Method;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.modules.ModuleLoader;
import org.wildfly.core.embedded.EmbeddedProcessStartException;

import java.lang.reflect.Method;

/**
 * Date: 05.11.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
@MessageLogger(projectCode = "WFLYEMB", length = 4)
public interface EmbeddedLogger extends BasicLogger {

    /**
     * A logger with a category of the package name.
     */
    EmbeddedLogger ROOT_LOGGER = Logger.getMessageLogger(EmbeddedLogger.class, "org.jboss.as.embedded");

    /**
     */
    @Message(id = 11, value = "Invalid JBoss home directory: %s")
    IllegalStateException invalidJBossHome(String jbossHome);

    /**
     * Creates an exception indicating the module path is invalid.
     */
    @Message(id = 12, value = "Invalid module path: %s")
    IllegalArgumentException invalidModulePath(String file);

    /**
     * Creates an exception indicating there was an error in the module loader.
     */
    @Message(id = 14, value = "Cannot load module %s from: %s")
    RuntimeException moduleLoaderError(@Cause Throwable cause, String msg, ModuleLoader moduleLoader);

    /**
     * Creates an exception indicating the variable, represented by the {@code name} parameter, is {@code null}.
     */
    @Message(id = 15, value = "%s is null")
    IllegalArgumentException nullVar(String name);

    @Message(id = 17, value = "Cannot load embedded server factory: %s")
    IllegalStateException cannotLoadEmbeddedServerFactory(@Cause ClassNotFoundException cause, String className);

    @Message(id = 18, value = "Cannot get reflective method '%s' for: %s")
    IllegalStateException cannotGetReflectiveMethod(@Cause NoSuchMethodException cause, String method, String className);

    @Message(id = 19, value = "Cannot create standalone server using factory: %s")
    IllegalStateException cannotCreateStandaloneServer(@Cause Throwable cause, Method createMethod);

    @Message(id = 20, value = "Cannot setup embedded process")
    IllegalStateException cannotSetupEmbeddedServer(@Cause Throwable cause);

    @Message(id = 21, value = "Cannot start embedded process")
    EmbeddedProcessStartException cannotStartEmbeddedServer(@Cause Throwable cause);

    // TODO This logger method is badly named.
    @Message(id = 22, value = "Cannot invoke '%s' on embedded process")
    IllegalStateException cannotInvokeStandaloneServer(@Cause Throwable cause, String methodName);

    @Message(id = 23, value = "The embedded server is stopping and invocations on the ModelControllerClient are not available")
    IllegalStateException processIsStopping();

    @Message(id = 24, value = "The embedded server is reloading and invocations on the ModelControllerClient are not yet available")
    IllegalStateException processIsReloading();

    @LogMessage(level = WARN)
    @Message(id = 25, value = "Unable to configure embedded server logging from %s")
    void cannotConfigureBootLogging(File loggingProperties);

    @Message(id = 26, value = "Cannot create host controller using factory: %s")
    IllegalStateException cannotCreateHostController(@Cause Throwable cause, Method createMethod);

}
