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

    private static final String EXPRESSION_RESOLVER_CAPABILITY = "org.wildfly.controller.expression-resolver";

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
            String result = null;

            // First try registered plugins
            synchronized (extensions) {
                Iterator<ExpressionResolverExtension> iter = extensions.iterator();
                while (result == null && iter.hasNext()) {
                    result = resolveExpression(expression, iter.next(), context);
                }
            }

            // Due to the concurrent nature of boot, some subsystems may need to resolve extension expressions before
            // the subsystem that provides an RegistryExtension reaches the point in OperationContext.Stage#RUNTIME
            // where it can access the org.wildfly.management.expression-resolver-extension-registry capability
            // and register its extension.
            // It's also possible that a composite op that adds resources in the subsystem that registers
            // the resolver extension will include expressions that need resolution along with a step adding
            // the resource that provides the resolver extension. In that case resolution will be needed
            // before the resolver extension is registered.
            // To handles these cases, fall back onto the ability for a single extension to register in
            // OperationContext.Stage.MODEL a capability that we can use.
            // TODO update capability handling to support multiple runtime API registrations under the same capability
            // in order that this fallback can work for more than one resolver extension.
            if (result == null && context.getCurrentStage() != OperationContext.Stage.MODEL) {
                ExpressionResolverExtension expressionResolver = null;
                try {
                    expressionResolver = context.getCapabilityRuntimeAPI(EXPRESSION_RESOLVER_CAPABILITY, ExpressionResolverExtension.class);
                    result = expressionResolver.resolveExpression(expression, context);
                } catch (IllegalStateException ise) {
                    // No fallback extension is available.
                    // We can't cache this state as it could be added in a later operation.
                    log.tracef("Not resolving %s -- runtime capability not available.", expression.substring(2, expression.length() -1));
                }
                if (expressionResolver != null) {
                    result = resolveExpression(expression, expressionResolver, context);
                }
            }

            if (result != null) {
                node.set(result);
            }
        }
    }

    private String resolveExpression(String expression, ExpressionResolverExtension resolver, OperationContext context) throws OperationFailedException {
        resolver.initialize(context);
        return resolver.resolveExpression(expression, context);
    }
}
