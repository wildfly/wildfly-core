/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
    private final Method methodGetProcessState;
    private final Context context;

    EmbeddedManagedProcessImpl(Class<?> processClass, Object managedProcess, Context context) {
       this(processClass, managedProcess, context, null);
    }

    EmbeddedManagedProcessImpl(Class<?> processClass, Object managedProcess, Context context, Method methodGetProcessState) {
        this.managedProcess = managedProcess;
        this.methodGetProcessState = methodGetProcessState;
        // Get a handle on the {@link EmbeddedManagedProcess} methods
        try {
            methodStart = processClass.getMethod("start");
            methodStop = processClass.getMethod("stop");
            methodGetModelControllerClient = processClass.getMethod("getModelControllerClient");
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
        if (canQueryProcessState()) {
            return (String) safeInvokeOnServer(methodGetProcessState);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canQueryProcessState() {
       return (methodGetProcessState != null);
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
