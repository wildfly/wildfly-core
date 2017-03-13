/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.elytron;

import java.util.Collection;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;

/**
 * Extends the {@link AbstractWriteAttributeHandler} overriding the {@link #requiresRuntime(OperationContext)}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class ElytronWriteAttributeHandler<V> extends AbstractWriteAttributeHandler<V> implements ElytronOperationStepHandler {
    protected ElytronWriteAttributeHandler(final AttributeDefinition... definitions) {
        super(definitions);
    }

    protected ElytronWriteAttributeHandler(final Collection<AttributeDefinition> definitions) {
        super(definitions);
    }

    @Override
    protected boolean requiresRuntime(final OperationContext context) {
        return isServerOrHostController(context);
    }
}
