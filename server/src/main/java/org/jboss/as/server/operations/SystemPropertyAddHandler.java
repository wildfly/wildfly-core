/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.server.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.server.controller.resources.SystemPropertyResourceDefinition.BOOT_TIME;
import static org.jboss.as.server.controller.resources.SystemPropertyResourceDefinition.VALUE;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationClientException;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.ProcessEnvironmentSystemPropertyUpdater;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Operation handler for adding domain/host and server system properties.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SystemPropertyAddHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = ADD;

    public static ModelNode getOperation(ModelNode address, String value) {
        return getOperation(address, value, null);
    }

    public static ModelNode getOperation(ModelNode address, String value, Boolean boottime) {
        ModelNode op = Util.getEmptyOperation(OPERATION_NAME, address);
        if (value == null) {
            op.get(VALUE.getName()).set(new ModelNode());
        } else {
            op.get(VALUE.getName()).set(value);
        }
        if (boottime != null) {
            op.get(BOOT_TIME.getName()).set(boottime);
        }
        return op;
    }


    private final ProcessEnvironmentSystemPropertyUpdater systemPropertyUpdater;
    private final AttributeDefinition[] attributes;

    /**
     * Create the SystemPropertyAddHandler
     *
     * @param systemPropertyUpdater the local process environment system property updater, or {@code null} if interaction with the process
     *                           environment is not required
     */
    public SystemPropertyAddHandler(ProcessEnvironmentSystemPropertyUpdater systemPropertyUpdater, AttributeDefinition[] attributes) {
        this.systemPropertyUpdater = systemPropertyUpdater;
        this.attributes = attributes;
    }


    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws  OperationFailedException {
        final ModelNode model = context.createResource(PathAddress.EMPTY_ADDRESS).getModel();
        for (AttributeDefinition attr : attributes) {
            attr.validateAndSet(operation, model);
        }

        final String name = context.getCurrentAddressValue();
        final ModelNode valueNode = model.get(VALUE.getName());
        final String value = valueNode.asStringOrNull();
        final boolean applyToRuntime = systemPropertyUpdater != null && systemPropertyUpdater.isRuntimeSystemPropertyUpdateAllowed(name, value, context.isBooting());
        final boolean reload = !applyToRuntime && context.getProcessType().isServer();

        final AtomicReference<String> currentValue = new AtomicReference<>();
        if (applyToRuntime) {
            // Cache the current value for use in rollback
            currentValue.set(WildFlySecurityManager.getPropertyPrivileged(name, null));

            String setValue = null;
            boolean setIt = false;
            try {
                setValue = value != null ? VALUE.resolveModelAttribute(context, model).asStringOrNull() : null;
                setIt = true;
            } catch (Exception resolutionFailure) {
                handleDeferredResolution(context, model, name, resolutionFailure);
            }

            if (setIt) {
                setProperty(name, setValue, systemPropertyUpdater);
            }

        } else if (reload) {
            context.reloadRequired();
        }

        context.completeStep(new OperationContext.RollbackHandler() {
            @Override
            public void handleRollback(OperationContext context, ModelNode operation) {
                if (reload) {
                    context.revertReloadRequired();
                }
                if (applyToRuntime) {
                    setProperty(name, currentValue.get(), systemPropertyUpdater);
                }
            }
        });
    }

    private static void setProperty(String name, String value, ProcessEnvironmentSystemPropertyUpdater systemPropertyUpdater) {
        if (value != null) {
            WildFlySecurityManager.setPropertyPrivileged(name, value);
        } else {
            WildFlySecurityManager.clearPropertyPrivileged(name);
        }
        if (systemPropertyUpdater != null) {
            systemPropertyUpdater.systemPropertyUpdated(name, value);
        }
    }

    private void handleDeferredResolution(OperationContext context, ModelNode model,
                                         final String propertyName, final Exception resolutionFailure) {

        assert resolutionFailure == null
                || resolutionFailure instanceof RuntimeException
                || resolutionFailure instanceof OperationFailedException
                : "invalid resolutionFailure type " + resolutionFailure.getClass();

        DeferredProcessor deferredResolver = (DeferredProcessor) context.getAttachment(SystemPropertyDeferredProcessor.ATTACHMENT_KEY);
        if (deferredResolver == null) {
            deferredResolver = new DeferredProcessor(systemPropertyUpdater);
            context.attach(SystemPropertyDeferredProcessor.ATTACHMENT_KEY, deferredResolver);
        }
        deferredResolver.addDeferredProcessee(propertyName, model, resolutionFailure);

        // Try again to resolve in Stage.RUNTIME, when runtime-only ExpressionResolverExtensions may be available
        // If that fails it will add a step to see in Stage.VERIFY if some other OSH resolved it
        final DeferredProcessor finalDeferredResolver = deferredResolver;
        context.addStep((context1, operation) -> {
            // See if the property can be processed now
            if (!finalDeferredResolver.processDeferredPropertyAtRuntime(propertyName, context1)) {

                // It's possible some other OSH(s) will call processDeferredProperties,
                // so add a Stage.VERIFY step to see if one has successfully resolved our prop,
                // and to fail the overall op if not.
                context.addStep((context2, operation2) -> {
                    DeferredProcesee procesee = finalDeferredResolver.getUnresolved(propertyName);
                    if (procesee != null) {
                        // If there is an OFE cached, throw it in preference to any cached RuntimeException
                        if (procesee.ofe != null) {
                            context2.setRollbackOnly();
                            throw procesee.ofe;
                        } else if (procesee.re != null) {
                            context2.setRollbackOnly();
                            throw procesee.re;
                        } //
                    }
                }, OperationContext.Stage.VERIFY);

            }
        }, OperationContext.Stage.RUNTIME);
    }

    static final class DeferredProcessor implements SystemPropertyDeferredProcessor {

        private final Map<String, DeferredProcesee> unresolved = new HashMap<>();
        private final ProcessEnvironmentSystemPropertyUpdater systemPropertyUpdater;

        DeferredProcessor(ProcessEnvironmentSystemPropertyUpdater systemPropertyUpdater) {
            this.systemPropertyUpdater = systemPropertyUpdater;
        }

        synchronized void addDeferredProcessee(String propertyName, ModelNode model, Exception resolutionFailure) {
            unresolved.put(propertyName, new DeferredProcesee(model, resolutionFailure));
        }

        /**
         * Try to resolve a single property.
         * @param property the name of the property. Cannot be {@code null}
         * @param resolver the resolver to use to attempt resolution. Cannot be {@code null}
         * @return {@code true} if the property had already been resolved or if this call set the property; {@code false} otherwise
         */
        synchronized boolean processDeferredPropertyAtRuntime(String property, ExpressionResolver resolver) {
            DeferredProcesee procesee = unresolved.get(property);
            if (procesee != null) {
                try {
                    final String setValue = VALUE.resolveModelAttribute(resolver, procesee.model).asString();
                    setProperty(property, setValue, systemPropertyUpdater);
                    unresolved.remove(property);
                } catch (RuntimeException | OperationFailedException resolutionFailure) {
                    procesee.setDeferredProcessingException(resolutionFailure);
                    return false;
                }
            }
            return true;
        }

        @Override
        public synchronized void processDeferredProperties(ExpressionResolver resolver) {
            for (Iterator<Map.Entry<String, DeferredProcesee>> it = unresolved.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, DeferredProcesee> entry = it.next();
                try {
                    DeferredProcesee procesee = entry.getValue();
                    final String setValue = VALUE.resolveModelAttribute(resolver, procesee.model).asString();
                    setProperty(entry.getKey(), setValue, systemPropertyUpdater);
                    it.remove();
                } catch (RuntimeException | OperationFailedException resolutionFailure) {
                    entry.getValue().setDeferredProcessingException(resolutionFailure);
                }
            }
        }

        // For unit tests
        synchronized DeferredProcesee getUnresolved(String propertyName) {
            return unresolved.get(propertyName);
        }
    }

    static final class DeferredProcesee {
        private final ModelNode model;
        private OperationFailedException ofe;
        private RuntimeException re;

        private DeferredProcesee(ModelNode model, Exception resolutionFailure) {
            this.model = model;
            if (resolutionFailure != null) {
                setDeferredProcessingException(resolutionFailure);
            }
        }

        private void setDeferredProcessingException(Exception e) {
            assert  e instanceof RuntimeException || e instanceof OperationFailedException;
            if (e instanceof OperationFailedException) {
                this.ofe = (OperationFailedException) e;
                this.re = null;
            } else {
                this.re = (RuntimeException) e;
                // Don't discard any cached OFE unless this RuntimeException is an OperationClientException
                // If we fail we prefer to throw OFE as it indicates a client failure
                if (e instanceof OperationClientException) {
                    this.ofe = null;
                }
            }
        }

        // Expose internals to unit tests

        OperationFailedException getOperationFailedException() {
            return ofe;
        }

        RuntimeException getRuntimeException() {
            return re;
        }
    }
}
