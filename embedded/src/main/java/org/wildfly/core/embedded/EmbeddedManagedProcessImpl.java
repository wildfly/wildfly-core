/*
Copyright 2015 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.wildfly.core.embedded;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.core.embedded.logging.EmbeddedLogger;

/**
 * Indirection to the {@link StandaloneServer} or {@link HostController}; used to encapsulate access to the underlying
 * embedded instance in a manner that does not directly link this class. Necessary to avoid {@link ClassCastException}
 * when this class is loaded by the application {@link ClassLoader} (or any other hierarchical CL) while the server is
 * loaded by a modular environment.
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 * @author Thomas.Diesler@jboss.com.
 */
class EmbeddedManagedProcessImpl implements EmbeddedManagedProcess, StandaloneServer, HostController { // in the future using other classes to implement StandaloneServer and HostController is fine

    private final Object managedProcess;
    private final Method methodStart;
    private final Method methodStop;
    private final Method methodGetModelControllerClient;
    private final Method methodGetCurrentProcessState;
    private final Context context;

    EmbeddedManagedProcessImpl(Class<?> processClass, Object managedProcess, Context context) {
        this.managedProcess = managedProcess;
        // Get a handle on the {@link EmbeddedManagedProcess} methods
        try {
            methodStart = processClass.getMethod("start");
            methodStop = processClass.getMethod("stop");
            methodGetModelControllerClient = processClass.getMethod("getModelControllerClient");
            methodGetCurrentProcessState = processClass.getMethod("getProcessState");
        } catch (final NoSuchMethodException nsme) {
            throw EmbeddedLogger.ROOT_LOGGER.cannotGetReflectiveMethod(nsme, nsme.getMessage(), processClass.getName());
        }
        this.context = context;
    }

    @Override
    public void start() throws EmbeddedProcessStartException {
        context.activate();
        invokeOnServer(methodStart);
    }

    @Override
    public void stop()  {
        try {
            safeInvokeOnServer(methodStop);
        } finally {
            context.restore();
        }
    }

    @Override
    public String getProcessState() {
        return (String) safeInvokeOnServer(methodGetCurrentProcessState);
    }

    @Override
    public ModelControllerClient getModelControllerClient()  {
        return (ModelControllerClient) safeInvokeOnServer(methodGetModelControllerClient);
    }

    private Object safeInvokeOnServer(final Method method, Object... args) {
        assert method != methodStart;
        try {
            return invokeOnServer(method, args);
        } catch (EmbeddedProcessStartException unexpected) {
            // Programming mistake of some sort as only "start" should throw this
            // and this method should not be used for start (see assert above)
            throw new RuntimeException(unexpected);
        }
    }

    private Object invokeOnServer(final Method method, Object... args) throws EmbeddedProcessStartException {
        try {
            return method.invoke(managedProcess, args);
        } catch (RuntimeException rte) {
            throw rte;
        } catch (Exception ex) {
            Throwable cause = ex;
            if (ex instanceof InvocationTargetException) {
                cause = ex.getCause();
            }
            if (cause instanceof EmbeddedProcessStartException) {
                throw (EmbeddedProcessStartException) cause;
            }
            // TODO This logger method is badly named.
            throw EmbeddedLogger.ROOT_LOGGER.cannotInvokeStandaloneServer(cause, method.getName());
        }
    }
}
