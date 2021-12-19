/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.server;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ExpressionResolverExtension;
import org.jboss.as.controller.ExpressionResolverImpl;
import org.jboss.as.controller.OperationClientException;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class RuntimeExpressionResolver extends ExpressionResolverImpl implements ExpressionResolver.ResolverExtensionRegistry {

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
        String expression = node.asString();
        if (context != null && expression.length() > 3) {

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
                    try {
                        result = resolveExpression(expression, iter.next(), context);
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

    private String resolveExpression(String expression, ExpressionResolverExtension resolver, OperationContext context) throws OperationFailedException {
        resolver.initialize(context);
        return resolver.resolveExpression(expression, context);
    }
}
