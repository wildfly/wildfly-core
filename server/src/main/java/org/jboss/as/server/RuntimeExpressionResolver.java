/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.extension.ExpressionResolverExtension;
import org.jboss.as.controller.ExpressionResolverImpl;
import org.jboss.as.controller.OperationClientException;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.extension.ResolverExtensionRegistry;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class RuntimeExpressionResolver extends ExpressionResolverImpl implements ResolverExtensionRegistry {

    private static final Logger log = Logger.getLogger(RuntimeExpressionResolver.class);

    // guarded by this
    private final Set<ExpressionResolverExtension> extensions = new HashSet<>();

    @Override
    public synchronized void addResolverExtension(ExpressionResolverExtension extension) {
        extensions.add(extension);
    }

    @Override
    public synchronized void removeResolverExtension(ExpressionResolverExtension extension) {
        extensions.remove(extension);
    }

    @Override
    protected void resolvePluggableExpression(ModelNode node, OperationContext context) throws OperationFailedException {
        resolvePluggableExpression(node, context, null);
    }

    @Override
    protected void resolvePluggableExpression(ModelNode node, CapabilityServiceSupport capabilitySupport) {
        try {
            resolvePluggableExpression(node, null, capabilitySupport);
        } catch (OperationFailedException e) {
            // OFE should only be thrown if 'context' is not null
            throw new IllegalStateException(e);
        }
    }

    private void resolvePluggableExpression(ModelNode node, OperationContext context, CapabilityServiceSupport capabilitySupport) throws OperationFailedException {
        String expression = node.asString();
        if ((context != null || capabilitySupport != null) && expression.length() > 3) {

            // Cycle through all registered extensions until one returns a result.
            // Cache any exceptions (first of each type) so we can propagate them
            // if none return a result
            String result = null;
            OperationFailedException ofe = null;
            RuntimeException operationClientException = null;
            RuntimeException otherRe = null;

            synchronized (extensions) {
                Iterator<ExpressionResolverExtension> iter = extensions.iterator();
                while (result == null && iter.hasNext()) {
                    ExpressionResolverExtension extension = iter.next();
                    try {
                        if (capabilitySupport != null) {
                            result = extension.resolveExpression(expression, capabilitySupport);
                        } else {
                            result = resolveExpressionWithContext(expression, extension, context);
                        }
                    } catch (OperationFailedException oe) {
                        if (ofe == null) {
                            ofe = oe;
                        }
                    } catch (RuntimeException re) {
                        if (re instanceof OperationClientException) {
                            if (operationClientException == null) {
                                operationClientException = re;
                            }
                        } else if (otherRe == null) {
                            otherRe = re;
                        }
                    }
                }
            }

            // If we have a result use it, otherwise throw an exception caught,
            // preferring OFEs, then other OperationClientExceptions, then server faults.
            // If no exceptions and no results, the caller will just carry on.
            if (result != null) {
                node.set(result);
            } else if (ofe != null) {
                throw ofe;
            } else if (operationClientException != null) {
                throw operationClientException;
            } else if (otherRe != null) {
                throw otherRe;
            }
        }
    }

    private String resolveExpressionWithContext(String expression, ExpressionResolverExtension resolver, OperationContext context) throws OperationFailedException {
        if (context != null) {
            resolver.initialize(context);
            return resolver.resolveExpression(expression, context);
        }
        return null;
    }

}
