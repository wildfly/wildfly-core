/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.common.Assert.checkNotNullParam;
import static org.wildfly.extension.elytron.DoohickeySimultaneity.withLock;
import static org.wildfly.extension.elytron.DoohickeySimultaneity.withLockForService;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathResolver;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.io.File;

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
import org.wildfly.extension.elytron.FileAttributeDefinitions.PathResolver;

/**
 * The {@code Doohickey} is the central point for resource initialisation allowing a resource to be
 * initialised for it's runtime API, as a service or a combination of them both.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
abstract class ElytronDoohickey<T> implements ExceptionFunction<OperationContext, T, OperationFailedException> {

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
            withLock(resourceAddress, () -> {
                if (value == null) {
                    if (foreignContext == null) {
                        // If a caller that can't provide an OperationContext needs to initialize,
                        // there's a programming bug as this object should be initialized
                        // before any call paths are executed that don't come through
                        // the OperationStepHandlers that provide a context.
                        throw ROOT_LOGGER.illegalNonManagementInitialization(getClass());
                    }
                    resolveRuntime(foreignContext, true);
                    value = createImmediately(foreignContext);
                }
                return null;
            });
        }

        return value;
    }

    public final T get() throws StartException {
        // The get method is assumed to be called as part of the MSC lifecycle.
        if (value == null) {
            try {
                withLockForService(resourceAddress, () -> {
                    if (value == null) {
                        value = serviceValueSupplier.get();
                    }
                    return null;
                });
            } catch (OperationFailedException e) {
                throw new StartException(e);
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

    private void resolveRuntime(final OperationContext foreignContext, final boolean skipCycleCheck) throws OperationFailedException {
        // The OperationContext may not be foreign but treat it as though it is.
        if (modelResolved == false) {
            if (value == null) {
                if (skipCycleCheck) {
                    // Already holding the lock and already pushed our address — just resolve.
                    if (modelResolved == false) {
                        ModelNode model = foreignContext.readResourceFromRoot(resourceAddress).getModel();
                        resolveRuntime(model, foreignContext);
                        modelResolved = true;
                    }
                } else {
                    withLock(resourceAddress, () -> {
                        if (modelResolved == false) {
                            ModelNode model = foreignContext.readResourceFromRoot(resourceAddress).getModel();
                            resolveRuntime(model, foreignContext);
                            modelResolved = true;
                        }
                        return null;
                    });
                }
            }
        }
    }

    protected File resolveRelativeToImmediately(String path, String relativeTo, OperationContext foreignContext) {
        PathResolver pathResolver = pathResolver();
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

    /**
     * Returns {@code true} if this doohickey already holds an initialised value, {@code false} otherwise.
     * Package-private so that service implementations can check whether early-access initialisation has
     * already run before deciding whether to skip their own load.
     */
    boolean hasValue() {
        return value != null;
    }

    /**
     * Updates the cached value held by this doohickey.  Package-private so that a service implementation
     * that creates the canonical resource instance (e.g. the unmodifiable wrapper around an
     * {@code AtomicLoadKeyStore}) can ensure the API and the service return the same object once the
     * service has started.
     *
     * @param canonicalValue the canonical instance to cache; must not be {@code null}
     */
    void setValue(T canonicalValue) {
        this.value = canonicalValue;
    }

    /**
     * Returns the currently cached value without triggering initialization.
     * Returns {@code null} if the value has not yet been set.
     * Package-private; symmetric with {@link #hasValue()}.
     */
    T cachedValue() {
        return value;
    }

    /**
     * Clears the cached value so that the next call to {@link #get()} re-invokes the service-path supplier.
     * Package-private so that the {@code init} operation can reset the value when stop+start is used
     * to re-initialise the service (e.g. after the underlying key-store has been reloaded).
     */
    void reset() {
        this.value = null;
    }

    /**
     * Returns a {@link TrivialService.ValueSupplier} whose {@code get()} delegates to {@link #get()}
     * and whose {@code dispose()} calls {@link #reset()}.  Used by {@link DoohickeyAddHandler} so that
     * a normal MSC dependency restart (stop then start) invalidates the cached value and forces the
     * next start to rebuild from the wired MSC suppliers rather than returning stale state.
     */
    TrivialService.ValueSupplier<T> asValueSupplier() {
        return new TrivialService.ValueSupplier<T>() {
            @Override
            public T get() throws StartException {
                return ElytronDoohickey.this.get();
            }

            @Override
            public void dispose() {
                ElytronDoohickey.this.reset();
            }
        };
    }

    protected abstract void resolveRuntime(ModelNode model, OperationContext context) throws OperationFailedException;

    protected abstract ExceptionSupplier<T, StartException> prepareServiceSupplier(OperationContext context, CapabilityServiceBuilder<?> serviceBuilder) throws OperationFailedException;

    protected abstract T createImmediately(OperationContext foreignContext) throws OperationFailedException;

}
