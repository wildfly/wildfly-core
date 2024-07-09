/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.Iterator;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationTransformerRegistry.PlaceholderResolver;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
class ResourceTransformationContextImpl implements ResourceTransformationContext {

    private final Resource root;
    private final PathAddress current;
    private final PathAddress read;
    private final OriginalModel originalModel;
    private final TransformersLogger logger;
    private final TransformerOperationAttachment transformerOperationAttachment;
    private final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry;

    static ResourceTransformationContext create(final Transformers.TransformationInputs tp, final TransformationTarget target,
                                                final PathAddress current, final PathAddress read,
                                                final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry) {
        final Resource root = Resource.Factory.create();
        final OriginalModel originalModel = new OriginalModel(tp.getRootResource(), tp.getRunningMode(), tp.getProcessType(), target, tp.getRootRegistration());
        final TransformerOperationAttachment attachment = tp.getTransformerOperationAttachment();
        return new ResourceTransformationContextImpl(root, current, read, originalModel, attachment, ignoredTransformationRegistry);
    }

    static ResourceTransformationContext create(TransformationTarget target, Resource model,
                                                ImmutableManagementResourceRegistration registration,
                                                RunningMode runningMode, ProcessType type,
                                                TransformerOperationAttachment attachment,
                                                final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry) {
        final Resource root = Resource.Factory.create();
        final OriginalModel originalModel = new OriginalModel(model, runningMode, type, target, registration);
        return new ResourceTransformationContextImpl(root, PathAddress.EMPTY_ADDRESS,
                originalModel, attachment, ignoredTransformationRegistry);
    }

    private ResourceTransformationContextImpl(Resource root, PathAddress address, final OriginalModel originalModel,
                                              TransformerOperationAttachment transformerOperationAttachment,
                                              final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry) {
        this(root, address, address, originalModel, transformerOperationAttachment, ignoredTransformationRegistry);
    }

    private ResourceTransformationContextImpl(Resource root, PathAddress address, PathAddress read,
                                              final OriginalModel originalModel,
                                              TransformerOperationAttachment transformerOperationAttachment,
                                              final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry) {
        this.root = root;
        this.current = address;
        this.read = read;
        this.originalModel = originalModel;
        this.logger = TransformersLogger.getLogger(originalModel.target);
        this.transformerOperationAttachment = transformerOperationAttachment;
        this.ignoredTransformationRegistry = ignoredTransformationRegistry;
    }

    private ResourceTransformationContextImpl(ResourceTransformationContextImpl context, OriginalModel originalModel) {
        this.root = context.root.clone();
        this.current = context.current;
        this.read = context.read;
        this.logger = context.getLogger();
        this.transformerOperationAttachment = context.transformerOperationAttachment;
        this.ignoredTransformationRegistry = context.ignoredTransformationRegistry;
        this.originalModel = originalModel;
    }


    ResourceTransformationContextImpl copy(PlaceholderResolver placeholderResolver) {
        assert originalModel.target instanceof TransformationTargetImpl : "Wrong target";
        TransformationTargetImpl tgt = (TransformationTargetImpl)originalModel.target;
        TransformationTargetImpl targetCopy = tgt.copyWithplaceholderResolver(placeholderResolver);
        OriginalModel originalModelCopy = new OriginalModel(originalModel.original, originalModel.mode, originalModel.type, targetCopy, originalModel.registration);
        return new ResourceTransformationContextImpl(this, originalModelCopy);
    }

    public ResourceTransformationContext copyAndReplaceOriginalModel(PlaceholderResolver placeholderResolver) {
        assert originalModel.target instanceof TransformationTargetImpl : "Wrong target";
        TransformationTargetImpl tgt = (TransformationTargetImpl)originalModel.target;
        TransformationTargetImpl targetCopy = tgt.copyWithplaceholderResolver(placeholderResolver);
        final OriginalModel originalModelCopy = new OriginalModel(root, originalModel.mode,
                originalModel.type, targetCopy, originalModel.registration);
        ResourceTransformationContext copy = new ResourceTransformationContextImpl(this, originalModelCopy);
        Resource root = copy.getTransformedRoot();
        if (current.size() > 0) {
            PathElement last = current.getLastElement();
            Resource parent = root;
            for (PathElement element : current) {
                if (element.equals(last)) {
                    parent.removeChild(element);
                } else {
                    parent = parent.getChild(element);
                    if (parent == null) {
                        break;
                    }
                }
            }
        }
        return copy;
    }

    @Override
    public ResourceTransformationContext addTransformedResource(PathAddress address, Resource toAdd) {
        final PathAddress absoluteAddress = this.current.append(address);
        final PathAddress read = this.read.append(address);
        return addTransformedResourceFromRoot(absoluteAddress, read, toAdd);
    }

    @Override
    public ResourceTransformationContext addTransformedResourceFromRoot(PathAddress absoluteAddress, Resource toAdd) {
        return addTransformedResourceFromRoot(absoluteAddress, absoluteAddress, toAdd);
    }

    private ResourceTransformationContext addTransformedResourceFromRoot(PathAddress absoluteAddress, PathAddress read, Resource toAdd) {
        // Only keep the mode, drop all children
        final Resource copy;
        if (toAdd != null) {
            copy = Resource.Factory.create(false, toAdd.getOrderedChildTypes());
            copy.writeModel(toAdd.getModel());
        } else {
            copy = Resource.Factory.create();
        }
        return addTransformedRecursiveResourceFromRoot(absoluteAddress, read, copy);
    }

    @Override
    public void addTransformedRecursiveResource(PathAddress relativeAddress, Resource resource) {
        final PathAddress absoluteAddress = this.current.append(relativeAddress);
        final PathAddress readAddress = this.read.append(relativeAddress);
        addTransformedRecursiveResourceFromRoot(absoluteAddress, readAddress, resource);
    }

    private ResourceTransformationContext addTransformedRecursiveResourceFromRoot(final PathAddress absoluteAddress, final PathAddress read, final Resource toAdd) {
        Resource model = this.root;
        Resource parent = null;
        if (absoluteAddress.size() > 0) {
            final Iterator<PathElement> i = absoluteAddress.iterator();
            while (i.hasNext()) {
                final PathElement element = i.next();
                if (element.isMultiTarget()) {
                    throw ControllerLogger.ROOT_LOGGER.cannotWriteTo("*");
                }
                if (!i.hasNext()) {
                    if (model.hasChild(element)) {
                        throw ControllerLogger.ROOT_LOGGER.duplicateResourceAddress(absoluteAddress);
                    } else {
                        parent = model;
                        model.registerChild(element, toAdd);
                        model = toAdd;
                        if (read.size() > 0) {
                            //We might be able to deal with this better in the future, but for now
                            //throw an error if the address was renamed and it was an ordered child type.
                            Set<String> parentOrderedChildren = parent.getOrderedChildTypes();
                            String readType = read.getLastElement().getKey();
                            if (parentOrderedChildren.contains(readType)) {
                                if (absoluteAddress.size() == 0 || !absoluteAddress.getLastElement().getKey().equals(readType)) {
                                    throw ControllerLogger.ROOT_LOGGER.orderedChildTypeRenamed(read, absoluteAddress, readType, parentOrderedChildren);
                                }
                            }
                        }
                    }
                } else {
                    model = model.getChild(element);
                    if (model == null) {
                        PathAddress ancestor = PathAddress.EMPTY_ADDRESS;
                        for (PathElement pe : absoluteAddress) {
                            ancestor = ancestor.append(pe);
                            if (element.equals(pe)) {
                                break;
                            }
                        }
                        throw ControllerLogger.ROOT_LOGGER.resourceNotFound(ancestor, absoluteAddress);
                    }
                }
            }
        } else {
            //If this was the root address, replace the resource model
            model.writeModel(toAdd.getModel());
        }
        return new ResourceTransformationContextImpl(root, absoluteAddress, read, originalModel,
                transformerOperationAttachment, ignoredTransformationRegistry);
    }

    @Override
    public Resource readTransformedResource(final PathAddress relativeAddress) {
        final PathAddress address = this.current.append(relativeAddress);
        return Resource.Tools.navigate(root, address);
    }

    public TransformerEntry resolveTransformerEntry(PathAddress address) {
        final TransformerEntry entry = originalModel.target.getTransformerEntry(this, address);
        if (entry == null) {
            return TransformerEntry.ALL_DEFAULTS;
        }
        return entry;
    }

    protected ResourceTransformer resolveTransformer(TransformerEntry entry, PathAddress address) {
        final ResourceTransformer transformer;
        try {
            transformer = entry.getResourceTransformer();
        } catch (NullPointerException e) {
            //Temp for WFCORE-1270 to get some more information
            NullPointerException npe = new NullPointerException("NPE for " + address);
            npe.setStackTrace(e.getStackTrace());
            throw npe;
        }
        if (transformer == null) {
            final ImmutableManagementResourceRegistration childReg = originalModel.getRegistration(address);
            if (childReg == null) {
                return ResourceTransformer.DISCARD;
            }
            if (childReg.isRemote() || childReg.isRuntimeOnly()) {
                return ResourceTransformer.DISCARD;
            }
            return ResourceTransformer.DEFAULT;
        }
        return transformer;
    }

    @Override
    public void processChildren(final Resource resource) throws OperationFailedException {
        final Set<String> types = resource.getChildTypes();
        for (final String type : types) {
            for (final Resource.ResourceEntry child : resource.getChildren(type)) {
                processChild(child.getPathElement(), child);
            }
        }
    }

    @Override
    public void processChild(final PathElement element, Resource child) throws OperationFailedException {
        final PathAddress childAddress = read.append(element); // read
        final TransformerEntry entry = resolveTransformerEntry(childAddress);
        final PathAddressTransformer path = entry.getPathTransformation();
        final PathAddress currentAddress = path.transform(element, new PathAddressTransformer.Builder() { // write
            @Override
            public PathAddress getOriginal() {
                return childAddress;
            }

            @Override
            public PathAddress getCurrent() {
                return current;
            }

            @Override
            public PathAddress getRemaining() {
                return PathAddress.EMPTY_ADDRESS.append(element);
            }

            @Override
            public PathAddress next(PathElement... elements) {
                return current.append(elements);
            }
        });
        final ResourceTransformer transformer = resolveTransformer(entry, childAddress);
        final ResourceTransformationContext childContext =
                new ResourceTransformationContextImpl(root, currentAddress, childAddress, originalModel,
                        transformerOperationAttachment, ignoredTransformationRegistry);
        transformer.transformResource(childContext, currentAddress, child);
    }

    @Override
    public TransformationTarget getTarget() {
        return originalModel.target;
    }

    @Override
    public ProcessType getProcessType() {
        return originalModel.type;
    }

    @Override
    public RunningMode getRunningMode() {
        return originalModel.mode;
    }

    @Override
    public ImmutableManagementResourceRegistration getResourceRegistration(PathAddress address) {
        final PathAddress a = read.append(address);
        return originalModel.getRegistration(a);
    }

    @Override
    public ImmutableManagementResourceRegistration getResourceRegistrationFromRoot(PathAddress address) {
        return originalModel.getRegistration(address);
    }

    @Override
    public Resource readResource(PathAddress address) {
        final PathAddress a = read.append(address);
        return originalModel.get(a);
    }

    @Override
    public Resource readResourceFromRoot(PathAddress address) {
        return originalModel.get(address);
    }

    @Override
    public Resource getTransformedRoot() {
        return root;
    }

    static ResourceTransformationContext createAliasContext(final PathAddress address, final ResourceTransformationContext context) {
        if (context instanceof ResourceTransformationContextImpl) {
            final ResourceTransformationContextImpl impl = (ResourceTransformationContextImpl) context;
            return new ResourceTransformationContextImpl(impl.root, address, impl.read, impl.originalModel,
                    impl.transformerOperationAttachment, impl.ignoredTransformationRegistry);
        } else {
            throw new IllegalArgumentException("wrong context type");
        }
    }

    static TransformationContext wrapForOperation(TransformationContext context, ModelNode operation) {
        if(context instanceof ResourceTransformationContextImpl) {
            final ResourceTransformationContextImpl impl = (ResourceTransformationContextImpl) context;
            return new ResourceTransformationContextImpl(impl.root,
                    PathAddress.pathAddress(operation.get(OP_ADDR)), impl.originalModel,
                    impl.transformerOperationAttachment, impl.ignoredTransformationRegistry);
        } else {
            return context;
        }
    }

    @Override
    public boolean isResourceTransformationIgnored(PathAddress address) {
        return ignoredTransformationRegistry.isResourceTransformationIgnored(address);
    }

    @Override
    public TransformersLogger getLogger() {
        return logger;
    }

    @Override
    public <V> V getAttachment(final OperationContext.AttachmentKey<V> key) {
        if (transformerOperationAttachment == null) {
            return null;
        }
        return transformerOperationAttachment.getAttachment(key);
    }

    @Override
    public <V> V attach(final OperationContext.AttachmentKey<V> key, final V value) {
        if (transformerOperationAttachment == null) {
            return null;
        }
        return transformerOperationAttachment.attach(key, value);
    }

    @Override
    public <V> V attachIfAbsent(final OperationContext.AttachmentKey<V> key, final V value) {
        if (transformerOperationAttachment == null) {
            return null;
        }
        return transformerOperationAttachment.attachIfAbsent(key, value);
    }

    @Override
    public <V> V detach(final OperationContext.AttachmentKey<V> key) {
        if (transformerOperationAttachment == null) {
            return null;
        }
        return transformerOperationAttachment.detach(key);
    }

    static class OriginalModel {

        private final Resource original;
        private final RunningMode mode;
        private final ProcessType type;
        private final TransformationTarget target;
        private final ImmutableManagementResourceRegistration registration;

        OriginalModel(Resource original, RunningMode mode, ProcessType type, TransformationTarget target, ImmutableManagementResourceRegistration registration) {
            this.original = original.clone();
            this.mode = mode;
            this.type = type;
            this.target = target;
            this.registration = registration;
        }

        Resource get(final PathAddress address) {
            return original.navigate(address);
        }

        ImmutableManagementResourceRegistration getRegistration(PathAddress address) {
            return registration.getSubModel(address);
        }

    }
}
