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

import org.jboss.as.controller.ExpressionResolver;
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
public class RuntimeExpressionResolver extends ExpressionResolverImpl {

    private static final Logger log = Logger.getLogger(RuntimeExpressionResolver.class);

    private static final String EXPRESSION_RESOLVER_CAPABILITY = "org.wildfly.controller.expression-resolver";

    @Override
    protected void resolvePluggableExpression(ModelNode node, OperationContext context) throws OperationFailedException {
        String expression = node.asString();
        if (expression.length() > 3) {
            String expressionValue = expression.substring(2, expression.length() -1);

            /*
             * Step 1 - Use ExpressionResolver capability if available.
             */

            if (context != null) {
                try {
                    ExpressionResolver expressionResolver = context.getCapabilityRuntimeAPI(EXPRESSION_RESOLVER_CAPABILITY, ExpressionResolver.class);
                    ModelNode result = expressionResolver.resolveExpressions(node, context);
                    if (result != null) {
                        node.set(result.asString());
                    }
                } catch (IllegalStateException e) {
                    // We can't cache this state as it could be added in a later operation.
                    log.tracef("Not resolving %s -- runtime capability not available.", expressionValue);
                }
            }
        }
    }
}
