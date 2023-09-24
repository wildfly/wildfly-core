/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD_INDEX;

/**
 * Base class for {@link OperationStepHandler} implementations that add managed resource.
 *
 * @author John Bailey
 */
public class AbstractAddStepHandler implements OperationStepHandler, OperationDescriptor {

    protected final Collection<? extends AttributeDefinition> attributes;

    /**
     * Constructs an add handler.
     */
    public AbstractAddStepHandler() { //default constructor to preserve backward compatibility
        this.attributes = List.of();
    }

    /**
     * Constructs an add handler
     * @param attributes attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     */
    @SuppressWarnings("unchecked")
    public AbstractAddStepHandler(Collection<? extends AttributeDefinition> attributes) {
        // Create defensive copy, if collection was not already immutable
        this.attributes = (attributes instanceof Set) ? Set.copyOf((Set<AttributeDefinition>) attributes) : List.copyOf(attributes);
    }

    /**
     * Constructs an add handler
     *
     * @param attributes attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     */
    public AbstractAddStepHandler(AttributeDefinition... attributes) {
        this(List.of(attributes));
    }

    public AbstractAddStepHandler(Parameters parameters) {
        this.attributes = parameters.attributes;
    }

    @Override
    public Collection<? extends AttributeDefinition> getAttributes() {
        return this.attributes;
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final Resource resource = createResource(context, operation);
        populateModel(context, operation, resource);
        recordCapabilitiesAndRequirements(context, operation, resource);
        //verify model for alternatives & requires
        if (requiresRuntime(context)) {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                    performRuntime(context, operation, resource);

                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            rollbackRuntime(context, operation, resource);
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    /**
     * Create the {@link Resource} that the {@link AbstractAddStepHandler#execute(OperationContext, ModelNode)}
     * method operates on. This method is invoked during {@link org.jboss.as.controller.OperationContext.Stage#MODEL}.
     * <p>
     * This default implementation uses the {@link org.jboss.as.controller.OperationContext#createResource(PathAddress)
     * default resource creation facility exposed by the context}. Subclasses wishing to create a custom resource
     * type can override this method.
     *
     * @param context the operation context
     * @param operation the operation
     */
    protected Resource createResource(final OperationContext context, final ModelNode operation) {
        ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
        if (registration != null) {
            Set<String> orderedChildTypes = registration.getOrderedChildTypes();
            boolean orderedChildResource = registration.isOrderedChildResource();
            if (orderedChildResource || !orderedChildTypes.isEmpty()) {
                return new OrderedResourceCreator(orderedChildResource, orderedChildTypes).createResource(context, operation);
            }
        }
        return createResource(context);
    }

    /**
     * Create the {@link Resource} that the {@link AbstractAddStepHandler#execute(OperationContext, ModelNode)}
     * method operates on. This method is invoked during {@link org.jboss.as.controller.OperationContext.Stage#MODEL}.
     * <p>
     * This default implementation uses the {@link org.jboss.as.controller.OperationContext#createResource(PathAddress)
     * default resource creation facility exposed by the context}. Subclasses wishing to create a custom resource
     * type can override this method.
     *
     * @param context the operation context
     */
    protected Resource createResource(final OperationContext context) {
        return context.createResource(PathAddress.EMPTY_ADDRESS);
    }

    /**
     * Populate the given resource in the persistent configuration model based on the values in the given operation.
     * This method isinvoked during {@link org.jboss.as.controller.OperationContext.Stage#MODEL}.
     * <p>
     * This default implementation simply calls {@link #populateModel(ModelNode, org.jboss.as.controller.registry.Resource)}.
     *
     * @param context the operation context
     * @param operation the operation
     * @param resource the resource that corresponds to the address of {@code operation}
     *
     * @throws OperationFailedException if {@code operation} is invalid or populating the model otherwise fails
     */
    protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws  OperationFailedException {
        populateModel(operation, resource);
    }

    /**
     * Populate the given resource in the persistent configuration model based on the values in the given operation.
     * This method is invoked during {@link org.jboss.as.controller.OperationContext.Stage#MODEL}.
     * <p>
     * This default implementation simply calls {@link #populateModel(ModelNode, org.jboss.dmr.ModelNode)}.
     *
     * @param operation the operation
     * @param resource the resource that corresponds to the address of {@code operation}
     *
     * @throws OperationFailedException if {@code operation} is invalid or populating the model otherwise fails
     */
    protected void populateModel(final ModelNode operation, final Resource resource) throws  OperationFailedException {
        populateModel(operation, resource.getModel());
    }

    /**
     * Populate the given node in the persistent configuration model based on the values in the given operation.
     * This method is invoked during {@link org.jboss.as.controller.OperationContext.Stage#MODEL}.
     * <p>
     * This default implementation invokes {@link org.jboss.as.controller.AttributeDefinition#validateAndSet(org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)}
     * on any attributes passed to the constructor.
     *
     * @param operation the operation
     * @param model persistent configuration model node that corresponds to the address of {@code operation}
     *
     * @throws OperationFailedException if {@code operation} is invalid or populating the model otherwise fails
     */
    protected void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
        for (AttributeDefinition attr : attributes) {
            attr.validateAndSet(operation, model);
        }
    }

    /**
     * Record any new {@link org.jboss.as.controller.capability.RuntimeCapability capabilities} that are available as
     * a result of this operation, as well as any requirements for other capabilities that now exist. This method is
     * invoked during {@link org.jboss.as.controller.OperationContext.Stage#MODEL}.
     * <p>
     * Any changes made by this method will automatically be discarded if the operation rolls back.
     * </p>
     * <p>
     * This default implementation registers any capabilities provided to the constructor and asks any
     * {@code AttributeDefinition} provided to the constructor to
     * {@link AttributeDefinition#addCapabilityRequirements(OperationContext, Resource, ModelNode) add capability requirements}.
     * </p>
     *
     * @param context the context. Will not be {@code null}
     * @param operation the operation that is executing Will not be {@code null}
     * @param resource the resource that has been added. Will reflect any updates made by
     * {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}. Will
     *                 not be {@code null}
     */
    protected void recordCapabilitiesAndRequirements(final OperationContext context, final ModelNode operation, Resource resource) throws OperationFailedException {

        for (RuntimeCapability<?> capability : context.getResourceRegistration().getCapabilities()) {
            if (capability.isDynamicallyNamed()) {
                context.registerCapability(capability.fromBaseCapability(context.getCurrentAddress()));
            } else {
                context.registerCapability(capability);
            }
        }

        ModelNode model = resource.getModel();
        for (AttributeDefinition ad : attributes) {
            if (model.hasDefined(ad.getName()) || ad.hasCapabilityRequirements()) {
                ad.addCapabilityRequirements(context, resource, model.get(ad.getName()));
            }
        }
        ImmutableManagementResourceRegistration mrr = context.getResourceRegistration();
        assert mrr.getRequirements() != null;
        for (CapabilityReferenceRecorder recorder : mrr.getRequirements()) {
            recorder.addCapabilityRequirements(context, resource, null);
        }
    }

    /**
     * Gets whether a {@link org.jboss.as.controller.OperationContext.Stage#RUNTIME} step should be added to call
     * {@link #performRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}}.
     * This default implementation will return {@code true} for a normal server running in normal (non admin-only) mode.
     * If running on a host controller, it will return {@code true} if it is the active copy of the host controller subsystem.
     * Subclasses that perform no runtime update could override and return {@code false}. This method is
     * invoked during {@link org.jboss.as.controller.OperationContext.Stage#MODEL}.
     *
     * @param context operation context
     * @return {@code true} if {@code performRuntime} should be invoked; {@code false} otherwise.
     */
    protected boolean requiresRuntime(OperationContext context) {
        return context.isDefaultRequiresRuntime();
    }

    /**
     * Make any runtime changes necessary to effect the changes indicated by the given {@code operation}. Executes
     * after {@link #populateModel(org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)}, so the given {@code resource}
     * parameter will reflect any changes made in that method. This method is
     * invoked during {@link org.jboss.as.controller.OperationContext.Stage#RUNTIME}. Subclasses that wish to make
     * changes to runtime services should override either this method or the
     * {@link #performRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)} variant. Override
     * this one if you wish to make use of the {@code resource} parameter beyond simply
     * {@link org.jboss.as.controller.registry.Resource#getModel() accessing its model property}.
     * <p>
     * This default implementation simply calls the
     * {@link #performRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)} variant.
     * <strong>Subclasses that override this method should not call {@code super.performRuntime(...)}.</strong>
     * </p>
     *
     * @param context  the operation context
     * @param operation the operation being executed
     * @param resource persistent configuration resource that corresponds to the address of {@code operation}
     *
     * @throws OperationFailedException if {@code operation} is invalid or updating the runtime otherwise fails
     */
    protected void performRuntime(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
        performRuntime(context, operation, resource.getModel());
    }

    /**
     * Make any runtime changes necessary to effect the changes indicated by the given {@code operation}. Executes
     * after {@link #populateModel(org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)}, so the given {@code resource}
     * parameter will reflect any changes made in that method. This method is
     * invoked during {@link org.jboss.as.controller.OperationContext.Stage#RUNTIME}. Subclasses that wish to make
     * changes to runtime services should override this method or the
     * {@link #performRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)} variant.
     *
     * @param context  the operation context
     * @param operation the operation being executed
     * @param model persistent configuration model from the resource that corresponds to the address of {@code operation}
     *
     * @throws OperationFailedException if {@code operation} is invalid or updating the runtime otherwise fails
     */
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
    }

    /**
     * Rollback runtime changes made in {@link #performRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}.
     * Any services that were added in {@link org.jboss.as.controller.OperationContext.Stage#RUNTIME} will be automatically removed after this
     * method executes. Called from the {@link org.jboss.as.controller.OperationContext.ResultHandler} or
     * {@link org.jboss.as.controller.OperationContext.RollbackHandler} passed to {@code OperationContext.completeStep(...)}.
     *
     * @param context the operation context
     * @param operation the operation being executed
     * @param resource persistent configuration model node that corresponds to the address of {@code operation}
     */
    protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
        // no-op
    }

    /**
     * Interface to handle custom resource creation
     */
    private interface ResourceCreator {
        /**
         * Create a resource
         *
         * @param context the operation context
         * @param operation the operation
         */
        Resource createResource(OperationContext context, ModelNode operation);
    }

    /**
     * A resource creator that deals with creating parent resources which have ordered children,
     * and putting the ordered children in the correct place in the parent
     *
     */
    private static class OrderedResourceCreator implements ResourceCreator {
        private final Set<String> orderedChildTypes;
        private final boolean indexedAdd;

        /**
         * Constructor
         *
         * @param indexedAdd if ({@code true} this is the child of a parent with ordered children,
         * and this child will be added at the {@code add-index} of the {@code add} operation in the
         * parent's list of children of this type. If {@code false} this is a normal child, i.e. the
         * insert will always happen at the end of the list as normal.
         * @param orderedChildTypes if not {@code null} or empty, this indicates that this is a parent
         * resource with ordered children, and the entries here are the type names of children which
         * are ordered.
         */
        public OrderedResourceCreator(boolean indexedAdd, Set<String> orderedChildTypes) {
            this.indexedAdd = indexedAdd;
            this.orderedChildTypes = orderedChildTypes == null ? Collections.emptySet() : orderedChildTypes;
        }

        /**
         * Constructor
         *
         * @param indexedAdd if ({@code true} this is the child of a parent with ordered children,
         * and this child will be added at the {@code add-index} of the {@code add} operation in the
         * parent's list of children of this type. If {@code false} this is a normal child, i.e. the
         * insert will always happen at the end of the list as normal.
         * @param orderedChildTypes if not {@code null} or empty, this indicates that this is a parent
         * resource with ordered children, and the entries here are the type names of children which
         * are ordered.
         */
        public OrderedResourceCreator(boolean indexedAdd, String... orderedChildTypes) {
            this.indexedAdd = indexedAdd;
            Set<String> set = new HashSet<>(orderedChildTypes.length);
            set.addAll(Arrays.asList(orderedChildTypes));
            this.orderedChildTypes = set;
        }

        @Override
        public Resource createResource(OperationContext context, ModelNode operation) {
            // Creates a parent with ordered children (if set is not empty)
            Resource resource = Resource.Factory.create(false, orderedChildTypes);

            // Attempts to do the insert if indexedAdd is true.
            int index = -1;
            if (indexedAdd && operation.hasDefined(ADD_INDEX)) {
                index = operation.get(ADD_INDEX).asInt();
            }
            if (index >= 0) {
                context.addResource(PathAddress.EMPTY_ADDRESS, operation.get(ADD_INDEX).asInt(), resource);
            } else {
                context.addResource(PathAddress.EMPTY_ADDRESS, resource);
            }
            return resource;
        }
    }

    public static class Parameters {
        // Set is not the ideal data structure, but since this is a protected field, we are stuck with it
        protected Set<AttributeDefinition> attributes = Set.of();

        public Parameters() {
        }

        public Parameters addAttribute(AttributeDefinition... attributes) {
            return this.addAttribute(List.of(attributes));
        }

        public Parameters addAttribute(Collection<? extends AttributeDefinition> attributes) {
            if (this.attributes.isEmpty()) {
                // Create defensive copy, if collection was not already immutable
                this.attributes = (attributes instanceof List) ? new ImmutableListSet<>(List.copyOf(attributes)) : Set.copyOf(attributes);
            } else {
                // Use copy-on-write semantics
                // We expect most users to bulk-add attributes
                List<AttributeDefinition> newAttributes = new ArrayList<>(this.attributes.size() + attributes.size());
                newAttributes.addAll(this.attributes);
                newAttributes.addAll(attributes);
                this.attributes = new ImmutableListSet<>(newAttributes);
            }
            return this;
        }
    }

    // Wraps a list as an immutable set
    private static class ImmutableListSet<T> extends AbstractSet<T> {
        private final List<T> list;

        ImmutableListSet(List<T> list) {
            this.list = list;
        }

        @Override
        public int size() {
            return this.list.size();
        }

        @Override
        public Iterator<T> iterator() {
            return this.list.iterator();
        }

        @Override
        public boolean add(T e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(Collection<? extends T> c) {
            throw new UnsupportedOperationException();
        }
    }
}
