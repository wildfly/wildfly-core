/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.controller.operations;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
class ServerGroupReferenceValidationHandler implements OperationStepHandler {

    private final ModelReference[] references;

    static ModelReference create(final String key, final AttributeDefinition definition) {
        return new ModelReference(key, PathAddress.EMPTY_ADDRESS, definition);
    }

    ServerGroupReferenceValidationHandler(ModelReference... references) {
        this.references = references;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final Resource resource;
        try {
             resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        } catch (Exception e) {
            // The resource got removed before we could validate - this only happens in some edge cases for composite operations
            context.stepCompleted();
            return;
        }

        final ModelNode model = resource.getModel();
        for (final ModelReference reference : references) {
            final String value = reference.definition.resolveModelAttribute(context, model).asString();
            final PathElement element = PathElement.pathElement(reference.key, value);
            context.readResourceFromRoot(reference.address.append(element));
            context.stepCompleted();
        }
    }

    static class ModelReference {

        private final String key;
        private final PathAddress address;
        private final AttributeDefinition definition;

        ModelReference(String key, PathAddress address, AttributeDefinition definition) {
            this.key = key;
            this.address = address;
            this.definition = definition;
        }
    }

}
