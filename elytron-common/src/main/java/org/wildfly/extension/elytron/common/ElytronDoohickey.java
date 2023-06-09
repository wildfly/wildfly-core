/*
 * Copyright 2021 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron.common;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.StartException;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron.common.util.ElytronCommonMessages;

/**
 * The {@code Doohickey} is the central point for resource initialisation allowing a resource to be
 * initialised for it's runtime API, as a service or a combination of them both.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class ElytronDoohickey<T> implements ExceptionFunction<OperationContext, T, OperationFailedException> {

    private static final ThreadLocal<Deque<PathAddress>> CALL_STACK = new ThreadLocal() {
        @Override
        protected Deque<PathAddress> initialValue() {
            return new ArrayDeque<>();
        }
    };

    /*
     * As each Thread tracks the addresses of the relevent resources we could likely implement some form of
     * deadlock detection that does not rely on taking a global lock.
     */

    private static final Lock GLOBAL_LOCK = new ReentrantLock();

    private final PathAddress resourceAddress;

    private volatile boolean modelResolved = false;
    private volatile ExceptionSupplier<T, StartException> serviceValueSupplier;

    private volatile T value;

    protected ElytronDoohickey(final PathAddress resourceAddress) {
        this.resourceAddress = checkNotNullParam("resourceAddress", resourceAddress);
    }

    @Override
    public final T apply(final OperationContext foreignContext) throws OperationFailedException {
        // The apply method is assumed to be called from the runtime API - i.e. MSC dependencies are not available.
        if (value == null) {
            GLOBAL_LOCK.lock();
            try {
                checkCycle();
                try {
                    if (value == null) {
                        if (foreignContext == null) {
                            // If a caller that can't provide an OperationContext needs to initialize,
                            // there's a programming bug as this object should be initialized
                            // before any call paths are executed that don't come through
                            // the OperationStepHandlers that provide a context.
                            throw ElytronCommonMessages.ROOT_LOGGER.illegalNonManagementInitialization(getClass());
                        }
                        resolveRuntime(foreignContext, true);
                        value = createImmediately(foreignContext);
                    }
                } finally {
                    CALL_STACK.get().removeFirst();
                }
            } finally {
                GLOBAL_LOCK.unlock();
            }
        }

        return value;
    }

    public final T get() throws StartException {
        // The get method is assumed to be called as part of the MSC lifecycle.
        if (value == null) {
            GLOBAL_LOCK.lock();
            try {
                checkCycle();
                try {
                    if (value == null) {
                        value = serviceValueSupplier.get();
                    }
                } finally {
                    CALL_STACK.get().removeFirst();
                }
            } catch (OperationFailedException e) {
                throw new StartException(e);
            } finally {
                GLOBAL_LOCK.unlock();
            }
        }

        return value;
    }

    public final void prepareService(OperationContext context, CapabilityServiceBuilder<?> serviceBuilder) throws OperationFailedException {
        // If we know we have been initialised i.e. an early expression resolution do we want to skip the service wiring?
        serviceValueSupplier = prepareServiceSupplier(context, serviceBuilder);
    }

    public final void resolveRuntime(final OperationContext foreignContext) throws OperationFailedException {
        resolveRuntime(foreignContext, false);
    }

    private final void resolveRuntime(final OperationContext foreignContext, final boolean skipCycleCheck) throws OperationFailedException {
        // The OperationContext may not be foreign but treat it as though it is.
        if (modelResolved == false) {
            if (value == null) {
                GLOBAL_LOCK.lock();
                try {
                    if (!skipCycleCheck) {
                        checkCycle();
                    }
                    try {
                        if (modelResolved == false) {
                            ModelNode model = foreignContext.readResourceFromRoot(resourceAddress).getModel();
                            resolveRuntime(model, foreignContext);
                            modelResolved = true;
                        }
                    } finally {
                        if (!skipCycleCheck) {
                            CALL_STACK.get().removeFirst();
                        }
                    }
                } finally {
                    GLOBAL_LOCK.unlock();
                }
            }
        }
    }

    protected File resolveRelativeToImmediately(String path, String relativeTo, OperationContext foreignContext) {
        FileAttributeDefinitions.PathResolver pathResolver = FileAttributeDefinitions.pathResolver();
        pathResolver.path(path);
        if (relativeTo != null) {
            PathManager pathManager = (PathManager) foreignContext.getServiceRegistry(false)
                    .getRequiredService(PathManagerService.SERVICE_NAME).getValue();
            pathResolver.relativeTo(relativeTo, pathManager);
        }
        File resolved = pathResolver.resolve();
        pathResolver.clear();

        return resolved;
    }

    private void checkCycle() throws OperationFailedException {
        Deque<PathAddress> currentStack = CALL_STACK.get();
        if (currentStack.contains(resourceAddress)) {
            StringBuilder sb = new StringBuilder();
            Iterator<PathAddress> iterator = currentStack.descendingIterator();
            boolean foundStart = false;
            while (iterator.hasNext()) {
                PathAddress current = iterator.next();
                foundStart = foundStart || current.equals(resourceAddress);
                if (foundStart) {
                    sb.append('{').append(current.toString()).append("}->");
                }
            }
            sb.append('{').append(resourceAddress.toString()).append('}');

            throw ElytronCommonMessages.ROOT_LOGGER.cycleDetected(sb.toString());
        }
        currentStack.addFirst(resourceAddress);
    }

    protected abstract void resolveRuntime(ModelNode model, OperationContext context) throws OperationFailedException;

    protected abstract ExceptionSupplier<T, StartException> prepareServiceSupplier(OperationContext context, CapabilityServiceBuilder<?> serviceBuilder) throws OperationFailedException;

    protected abstract T createImmediately(OperationContext foreignContext) throws OperationFailedException;

}