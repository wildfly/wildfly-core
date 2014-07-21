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
package org.jboss.as.controller.transform.description;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.Resource.ResourceEntry;
import org.jboss.as.controller.transform.ChainedTransformationTools;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.PathAddressTransformer;
import org.jboss.as.controller.transform.ResourceTransformationContext;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilderImpl.ModelVersionPair;
import org.jboss.dmr.ModelNode;

/**
 * Placeholder transformer implementation for chained transformers. It uses {@link org.jboss.as.controller.registry.OperationTransformerRegistry.PlaceholderResolver} to override how the transformers for the child resources
 * of the placeholder are resolved.
 *
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class ChainedTransformingDescription extends AbstractDescription implements TransformationDescription, ResourceTransformer, OperationTransformer {

    private final LinkedHashMap<ModelVersionPair, ChainedPlaceholderResolver> placeholderResolvers;

    ChainedTransformingDescription(PathElement pathElement, LinkedHashMap<ModelVersionPair, ChainedPlaceholderResolver> placeholderResolvers) {
        super(pathElement, PathAddressTransformer.DEFAULT, true);
        this.placeholderResolvers = placeholderResolvers;
    }

    @Override
    public TransformedOperation transformOperation(TransformationContext context, PathAddress address, ModelNode operation)
            throws OperationFailedException {
        assert context instanceof ResourceTransformationContext : "Wrong context type";
        ResourceTransformationContext currentCtx = (ResourceTransformationContext)context;

        List<TransformedOperation> delegates = new ArrayList<TransformedOperation>();
        ModelNode currentOp = operation;
        Iterator<Map.Entry<ModelVersionPair, ChainedPlaceholderResolver>> it = placeholderResolvers.entrySet().iterator();
        if (it.hasNext()) {
            ChainedPlaceholderResolver resolver = it.next().getValue();
            currentCtx = ChainedTransformationTools.initialiseChain(currentCtx, resolver);
            PathAddress addr = ChainedTransformationTools.transformAddress(PathAddress.pathAddress(currentOp.require(OP_ADDR)), currentCtx.getTarget());
            currentOp.get(OP_ADDR).set(addr.toModelNode());
            OperationTransformer transformer = currentCtx.getTarget().resolveTransformer(currentCtx, address, currentOp.require(OP).asString());
            TransformedOperation transformed = transformer.transformOperation(currentCtx, address, currentOp);
            currentOp = transformed.getTransformedOperation();
            delegates.add(transformed);
        }
        while (it.hasNext()) {
            if (currentOp == null) {
                break;
            }
            ChainedPlaceholderResolver resolver = it.next().getValue();
            currentCtx = ChainedTransformationTools.nextInChainOperation(currentCtx, resolver);
            PathAddress currentAddress = PathAddress.pathAddress(currentOp.require(OP_ADDR));
            PathAddress addr = ChainedTransformationTools.transformAddress(currentAddress, currentCtx.getTarget());
            currentOp.get(OP_ADDR).set(addr.toModelNode());
            OperationTransformer transformer = currentCtx.getTarget().resolveTransformer(currentCtx, currentAddress, currentOp.require(OP).asString());
            TransformedOperation transformed = transformer.transformOperation(currentCtx, address, currentOp);
            currentOp = transformed.getTransformedOperation();
            delegates.add(transformed);
        }

        return new ChainedTransformedOperation(currentOp, delegates.toArray(new TransformedOperation[delegates.size()]));
    }

    @Override
    public void transformResource(final ResourceTransformationContext context, final PathAddress address, final Resource resource) throws OperationFailedException {
        if (resource.isProxy() || resource.isRuntime()) {
            return;
        }

        //For now just assume we come in through the top layer
        ResourceTransformationContext current = context;
        Iterator<Map.Entry<ModelVersionPair, ChainedPlaceholderResolver>> it = placeholderResolvers.entrySet().iterator();
        if (it.hasNext()) {
            ChainedPlaceholderResolver resolver = it.next().getValue();
            current = ChainedTransformationTools.initialiseChain(current, resolver);
            resolver.getDescription().getResourceTransformer().transformResource(current, address, resource);
        }
        while (it.hasNext()) {
            ChainedPlaceholderResolver resolver = it.next().getValue();
            current = ChainedTransformationTools.nextInChainResource(current, resolver);
            try {
                Resource currentResource = current.readResourceFromRoot(address);
                resolver.getDescription().getResourceTransformer().transformResource(current, address, currentResource);
            } catch (Resource.NoSuchResourceException e) {
                //The resource was rejected/discarded
                continue;
            }
        }

        Resource transformed = current.getTransformedRoot();
        Resource originalTransformed = context.getTransformedRoot();
        copy(transformed, originalTransformed, address);
    }


    @Override
    public PathAddressTransformer getPathAddressTransformer() {
        return PathAddressTransformer.DEFAULT;
    }

    @Override
    public OperationTransformer getOperationTransformer() {
        return this;
    }

    @Override
    public ResourceTransformer getResourceTransformer() {
        return this;
    }

    @Override
    public Map<String, OperationTransformer> getOperationTransformers() {
        return Collections.emptyMap();
    }

    @Override
    public List<TransformationDescription> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public Set<String> getDiscardedOperations() {
        return Collections.emptySet();
    }

    @Override
    public boolean isPlaceHolder() {
        return true;
    }

    private void copy(Resource src, Resource dest, PathAddress address) {
        PathAddress parentAddress = address.size() > 1 ? address.subAddress(0, address.size() - 1) : PathAddress.EMPTY_ADDRESS;
        PathElement childElement = address.getLastElement();
        Resource source = src.navigate(parentAddress);
        Resource destination = dest.navigate(parentAddress);
        Resource sourceChild = source.getChild(childElement);
        if (sourceChild != null) {
            Resource destChild = Resource.Factory.create();
            destination.registerChild(childElement, destChild);
            copy(sourceChild, destChild);
        }
        //copy(src, dest);
    }

    private void copy(Resource src, Resource dest) {
        dest.getModel().set(src.getModel());
        for (String type : src.getChildTypes()) {
            for (ResourceEntry entry : src.getChildren(type)) {
                Resource added = Resource.Factory.create();
                dest.registerChild(PathElement.pathElement(type, entry.getName()), added);
                copy(entry, added);
            }
        }
    }

    private PathAddress transformAddress(final PathAddress original, final ResourceTransformationContext context) {
        return ChainedTransformationTools.transformAddress(original, context.getTarget());
    }

    private static class ChainedTransformedOperation extends TransformedOperation {

        private TransformedOperation[] delegates;
        private volatile String failure;
        private volatile boolean initialized;

        public ChainedTransformedOperation(ModelNode transformedOperation, TransformedOperation...delegates) {
            // FIXME ChainedTransformedOperation constructor
            super(transformedOperation, null);
            this.delegates = delegates;
        }

        @Override
        public ModelNode getTransformedOperation() {
            return super.getTransformedOperation();
        }

        @Override
        public OperationResultTransformer getResultTransformer() {
            return this;
        }

        @Override
        public boolean rejectOperation(ModelNode preparedResult) {
            for (TransformedOperation delegate : delegates) {
                if (delegate.rejectOperation(preparedResult)) {
                    failure = delegate.getFailureDescription();
                    initialized = true; //See comment in getFailureDescription()
                    return true;
                }
            }
            return false;
        }

        @Override
        public String getFailureDescription() {
            //In real life this will always be initialized by the transforming proxy before anyone calls this method
            //For testing we call it directly from ModelTestUtils
            if (!initialized) {
                for (TransformedOperation delegate : delegates) {
                    String failure = delegate.getFailureDescription();
                    if (failure != null) {
                        return failure;
                    }
                }
            }
            return failure;
        }

        @Override
        public ModelNode transformResult(ModelNode result) {
            ModelNode currentResult = result;
            for (int i = delegates.length - 1 ; i >= 0 ; --i) {
                currentResult = delegates[i].transformResult(currentResult);
            }
            return currentResult;
        }
    }
}
