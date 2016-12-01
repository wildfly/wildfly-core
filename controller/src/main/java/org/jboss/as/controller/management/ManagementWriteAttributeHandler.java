/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.management;

import java.util.function.Consumer;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.registry.Resource;

/**
 * An extension of {@link ReloadRequiredWriteAttributeHandler} that takes into account that management interfaces run in all
 * modes.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ManagementWriteAttributeHandler extends ReloadRequiredWriteAttributeHandler {

    private final Consumer<OperationContext> contextConsumer;

    ManagementWriteAttributeHandler(AttributeDefinition[] attributes) {
        super(attributes);
        contextConsumer = null;
    }

    ManagementWriteAttributeHandler(AttributeDefinition[] attributes, Consumer<OperationContext> contextConsumer) {
        super(attributes);
        this.contextConsumer = contextConsumer;
    }

    @Override
    protected void validateUpdatedModel(OperationContext context, Resource model) throws OperationFailedException {
        if (contextConsumer != null) {
            contextConsumer.accept(context);
        }
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        // Management interfaces run in all modes including ADMIN_ONLY
        return !context.isBooting();
    }

}
