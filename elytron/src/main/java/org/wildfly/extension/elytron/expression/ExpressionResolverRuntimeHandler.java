/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron.expression;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;

/**
 * Utility to allow the add handler for the /subsystem=elytron/expression=encryption resource
 * to initialize the {@link ElytronExpressionResolver}.
 */
public final class ExpressionResolverRuntimeHandler {

    public static void initializeResolver(OperationContext context) throws OperationFailedException {
        ElytronExpressionResolver resolver = context.getCapabilityRuntimeAPI("org.wildfly.security.expression-resolver", ElytronExpressionResolver.class);
        resolver.ensureInitialised(null, context);
    }
}
