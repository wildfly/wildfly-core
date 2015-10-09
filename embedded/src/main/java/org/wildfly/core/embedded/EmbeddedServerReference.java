/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.core.embedded;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.core.embedded.logging.EmbeddedLogger;

/**
 * Indirection to the {@link StandaloneServer}; used to encapsulate access to the underlying embedded AS Server instance in a
 * manner that does not directly link this class. Necessary to avoid {@link ClassCastException} when this class is loaded by the
 * application {@link ClassLoader} (or any other hierarchical CL) while the server is loaded by a modular environment.
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 * @author Thomas.Diesler@jboss.com
 */
public final class EmbeddedServerReference implements StandaloneServer, HostController {

    private final Object server;
    private final Class<?> serverClass;
    private final Method methodStart;
    private final Method methodStop;
    private final Method methodGetModelControllerClient;

    EmbeddedServerReference(Class<?> serverClass, Object serverImpl) {
        this.server = serverImpl;
        this.serverClass = serverClass;
        // Get a handle on the {@link StandaloneServer} methods
        try {
            methodStart = serverClass.getMethod("start");
            methodStop = serverClass.getMethod("stop");
            methodGetModelControllerClient = serverClass.getMethod("getModelControllerClient");
        } catch (final NoSuchMethodException nsme) {
            throw EmbeddedLogger.ROOT_LOGGER.cannotGetReflectiveMethod(nsme, nsme.getMessage(), serverClass.getName());
        }
    }

    @Override
    public void start()  {
        invokeOnServer(methodStart);
    }

    @Override
    public void stop()  {
        invokeOnServer(methodStop);
    }

    @Override
    public ModelControllerClient getModelControllerClient()  {
        ModelControllerClient client = (ModelControllerClient) invokeOnServer(methodGetModelControllerClient);
        return client;
    }

    public StandaloneServer getStandaloneServer() {
        return (StandaloneServer) server;
    }

    @Override
    public HostController getHostController() {
        return (HostController) server;
    }

    public boolean isHostController() {
        return HostController.class.getName().equals(serverClass.getName());
    }

    private Object invokeOnServer(final Method method, Object... args) {
        try {
            return method.invoke(server, args);
        } catch (RuntimeException rte) {
            throw rte;
        } catch (Exception ex) {
            Throwable cause = ex;
            if (ex instanceof InvocationTargetException) {
                cause = ((InvocationTargetException)ex).getCause();
            }
            throw EmbeddedLogger.ROOT_LOGGER.cannotInvokeStandaloneServer(cause, method.getName());
        }
    }
}
