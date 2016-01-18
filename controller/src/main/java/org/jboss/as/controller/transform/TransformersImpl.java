/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller.transform;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.Iterator;
import java.util.List;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.AliasEntry;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 * @author Tomaz Cerar
 */
public class TransformersImpl implements Transformers {
    private final TransformationTarget target;

    TransformersImpl(TransformationTarget target) {
        assert target != null;
        this.target = target;
    }

    @Override
    public TransformationTarget getTarget() {
        return target;
    }

    @Override
    public OperationTransformer.TransformedOperation transformOperation(final TransformationContext context, final ModelNode operation) throws OperationFailedException {

        final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
        //If this is an alias, get the real address before transforming
        ImmutableManagementResourceRegistration reg = context.getResourceRegistration(address);
        final PathAddress useAddress;
        if (reg != null && reg.isAlias()) {
            useAddress = reg.getAliasEntry().convertToTargetAddress(address, AliasEntry.AliasContext.create(operation, context));
        } else {
            useAddress = address;
        }
        final String operationName = operation.require(OP).asString();

        TransformationContext opCtx = ResourceTransformationContextImpl.wrapForOperation(context, operation);
        final OperationTransformer transformer = target.resolveTransformer(opCtx, useAddress, operationName);
        if (transformer == null) {
            ControllerLogger.ROOT_LOGGER.tracef("operation %s does not need transformation", operation);
            return new OperationTransformer.TransformedOperation(operation, OperationResultTransformer.ORIGINAL_RESULT);
        }
        // Transform the path address
        final PathAddress transformed = transformAddress(useAddress, target);
        // Update the operation using the new path address
        operation.get(OP_ADDR).set(transformed.toModelNode()); // TODO should this happen by default?

        OperationTransformer.TransformedOperation res = transformer.transformOperation(opCtx, transformed, operation);
        context.getLogger().flushLogQueue();
        return res;
    }

    @Override
    public OperationTransformer.TransformedOperation transformOperation(final TransformationInputs transformationInputs, final ModelNode operation) throws OperationFailedException {

        final PathAddress original = PathAddress.pathAddress(operation.require(OP_ADDR));
        //If this is an alias, get the real address before transforming
        ImmutableManagementResourceRegistration reg = transformationInputs.getRootRegistration().getSubModel(original);
        final PathAddress useAddress;
        if (reg != null && reg.isAlias()) {
            useAddress = reg.getAliasEntry().convertToTargetAddress(original, AliasEntry.AliasContext.create(operation, transformationInputs));
        } else {
            useAddress = original;
        }

        final String operationName = operation.require(OP).asString();

        // Transform the path address
        final PathAddress transformed = transformAddress(useAddress, target);
        // Update the operation using the new path address
        operation.get(OP_ADDR).set(transformed.toModelNode()); // TODO should this happen by default?

        final TransformationContext context = ResourceTransformationContextImpl.create(transformationInputs, target, transformed, original, Transformers.DEFAULT);
        final OperationTransformer transformer = target.resolveTransformer(context, useAddress, operationName);
        if (transformer == null) {
            ControllerLogger.ROOT_LOGGER.tracef("operation %s does not need transformation", operation);
            return new OperationTransformer.TransformedOperation(operation, OperationResultTransformer.ORIGINAL_RESULT);
        }
        final OperationTransformer.TransformedOperation op = transformer.transformOperation(context, transformed, operation);
        context.getLogger().flushLogQueue();
        return op;
    }

    @Override
    public Resource transformRootResource(TransformationInputs transformationInputs, Resource resource) throws OperationFailedException {
        return transformRootResource(transformationInputs, resource, Transformers.DEFAULT);
    }

    @Override
    public Resource transformRootResource(TransformationInputs transformationInputs, Resource resource, ResourceIgnoredTransformationRegistry ignoredTransformationRegistry) throws OperationFailedException {
        // Transform the path address
        final PathAddress original = PathAddress.EMPTY_ADDRESS;
        final PathAddress transformed = transformAddress(original, target);
        final ResourceTransformationContext context = ResourceTransformationContextImpl.create(transformationInputs, target, transformed, original, ignoredTransformationRegistry);
        final ResourceTransformer transformer = target.resolveTransformer(context, original);
        if(transformer == null) {

            ControllerLogger.ROOT_LOGGER.tracef("resource %s does not need transformation", resource);
            return resource;
        }
        transformer.transformResource(context, transformed, resource);
        context.getLogger().flushLogQueue();
        return context.getTransformedRoot();
    }

    @Override
    public Resource transformResource(final ResourceTransformationContext context, Resource resource) throws OperationFailedException {

        final ResourceTransformer transformer = target.resolveTransformer(context, PathAddress.EMPTY_ADDRESS);
        if (transformer == null) {
            ControllerLogger.ROOT_LOGGER.tracef("resource %s does not need transformation", resource);
            return resource;
        }
        transformer.transformResource(context, PathAddress.EMPTY_ADDRESS, resource);
        context.getLogger().flushLogQueue();
        return context.getTransformedRoot();
    }

    /**
     * Transform a path address.
     *
     * @param original the path address to be transformed
     * @param target the transformation target
     * @return the transformed path address
     */
    protected static PathAddress transformAddress(final PathAddress original, final TransformationTarget target) {
        final List<PathAddressTransformer> transformers = target.getPathTransformation(original);
        final Iterator<PathAddressTransformer> transformations = transformers.iterator();
        final PathAddressTransformer.BuilderImpl builder = new PathAddressTransformer.BuilderImpl(transformations, original);
        return builder.start();
    }
}
