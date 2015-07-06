/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD_INDEX;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

/**
 * Base class for {@link OperationStepHandler} implementations that add managed resource.
 *
 * @author John Bailey
 */
public class AbstractAddStepHandler implements OperationStepHandler {

    static final Set<RuntimeCapability> NULL_CAPABILITIES = Collections.emptySet();
    private static final Set<? extends AttributeDefinition> NULL_ATTRIBUTES = Collections.emptySet();

    private final Set<RuntimeCapability> capabilities;
    protected final Collection<? extends AttributeDefinition> attributes;

    /**
     * Constructs an add handler.
     */
    public AbstractAddStepHandler() { //default constructor to preserve backward compatibility
        this.attributes = NULL_ATTRIBUTES;
        this.capabilities = NULL_CAPABILITIES;
    }

    /**
     * Constructs an add handler
     * @param attributes attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}.attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     */
    public AbstractAddStepHandler(Collection<? extends AttributeDefinition> attributes) {
        this(NULL_CAPABILITIES, attributes );
    }

    /**
     * Constructs an add handler
     * @param capability capability to register in {@link #recordCapabilitiesAndRequirements(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     *                     {@code null} is allowed
     * @param attributes attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}.attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     */
    public AbstractAddStepHandler(RuntimeCapability capability, Collection<? extends AttributeDefinition> attributes) {
        this(capability == null ? NULL_CAPABILITIES : Collections.singleton(capability), attributes );
    }

    /**
     * Constructs an add handler.
     *
     * @param capabilities capabilities to register in {@link #recordCapabilitiesAndRequirements(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     *                     {@code null} is allowed
     * @param attributes   attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     */
    public AbstractAddStepHandler(Set<RuntimeCapability> capabilities, Collection<? extends AttributeDefinition> attributes) {
        //Please don't add more constructors, instead use the Parameters variety
        this.attributes = attributes == null ? NULL_ATTRIBUTES : attributes;
        this.capabilities = capabilities == null ? NULL_CAPABILITIES : capabilities;
    }

    /**
     * Constructs an add handler
     *
     * @param capability capability to register in {@link #recordCapabilitiesAndRequirements(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     *                     {@code null} is allowed
     * @param attributes attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     */
    public AbstractAddStepHandler(RuntimeCapability capability, AttributeDefinition... attributes) {
        this(capability == null ? NULL_CAPABILITIES : Collections.singleton(capability), attributes);
    }

    /**
     * Constructs an add handler
     *
     * @param attributes attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     */
    public AbstractAddStepHandler(AttributeDefinition... attributes) {
        this(NULL_CAPABILITIES, attributes);
    }

    /**
     * Constructs an add handler
     *
     * @param capabilities capabilities to register in {@link #recordCapabilitiesAndRequirements(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     *                     {@code null} is allowed
     * @param attributes attributes to use in {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     */
    public AbstractAddStepHandler(Set<RuntimeCapability> capabilities, AttributeDefinition... attributes) {
        this(capabilities, attributes.length > 0 ? Arrays.asList(attributes) : NULL_ATTRIBUTES);
    }

    public AbstractAddStepHandler(Parameters parameters) {
        if (parameters.capabilities == null) {
            capabilities = NULL_CAPABILITIES;
        } else if (parameters.capabilities.size() == 1) {
            capabilities = Collections.singleton(parameters.capabilities.iterator().next());
        } else {
            capabilities = Collections.unmodifiableSet(parameters.capabilities);
        }

        if (parameters.attributes == null) {
            attributes = NULL_ATTRIBUTES;
        } else if (parameters.attributes.size() == 1) {
            attributes = Collections.singleton(parameters.attributes.iterator().next());
        } else {
            attributes = Collections.unmodifiableSet(parameters.attributes);
        }
    }

    /** {@inheritDoc */
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final Resource resource = createResource(context, operation);
        populateModel(context, operation, resource);
        recordCapabilitiesAndRequirements(context, operation, resource);
        //verify model for alternatives & requires
        if (requiresRuntime(context)) {
            context.addStep(new OperationStepHandler() {
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
            if (orderedChildResource || orderedChildTypes.size() > 0) {
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
     * {@link AttributeDefinition#addCapabilityRequirements(OperationContext, ModelNode) add capability requirements}.
     * </p>
     *
     * @param context the context. Will not be {@code null}
     * @param operation the operation that is executing Will not be {@code null}
     * @param resource the resource that has been added. Will reflect any updates made by
     * {@link #populateModel(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}. Will
     *                 not be {@code null}
     */
    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        for (RuntimeCapability capability : capabilities) {
            if (capability.isDynamicallyNamed()) {
                context.registerCapability(capability.fromBaseCapability(context.getCurrentAddressValue()), null);
            } else {
                context.registerCapability(capability, null);
            }
        }
        ModelNode model = resource.getModel();
        for (AttributeDefinition ad : attributes) {
            ad.addCapabilityRequirements(context, model.get(ad.getName()));
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
     * <strong>Deprecated</strong>. Has no effect unless a subclass somehow makes use of it.
     *
     * @return  {@code true}
     *
     * @deprecated has no effect
     */
    @Deprecated
    protected boolean requiresRuntimeVerification() {
        return true;
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
     * <p>
     * To provide compatible behavior with previous releases, this default implementation calls the deprecated
     * {@link #performRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode, ServiceVerificationHandler, java.util.List)}
     * method. It then does nothing with the objects referenced by the {@code verificationHandler} and
     * {@code controllers} parameters passed to that method. Subclasses that overrode that method are encouraged to
     * instead override this one or the {@link #performRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     * variant. <strong>Subclasses that override this method should not call{@code super.performRuntime(...)}.</strong>
     *
     * @param context  the operation context
     * @param operation the operation being executed
     * @param model persistent configuration model from the resource that corresponds to the address of {@code operation}
     *
     * @throws OperationFailedException if {@code operation} is invalid or updating the runtime otherwise fails
     */
    @SuppressWarnings("deprecation")
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        performRuntime(context, operation, model, ServiceVerificationHandler.INSTANCE, new ArrayList<ServiceController<?>>());

        // Don't bother adding the SVH, as it does nothing.
        // Just invoke requiresRuntimeVerification() on the extremely remote chance some subclass
        // is somehow expecting the call
        requiresRuntimeVerification();
    }

    /**
     * <strong>Deprecated</strong>. Subclasses should instead override
     * {@link #performRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     * or {@link #performRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)}.
     * <p>
     * This default implementation does nothing.
     *
     * @param context  the operation context
     * @param operation the operation being executed
     * @param model persistent configuration model node that corresponds to the address of {@code operation}
     * @param verificationHandler not used; service verification is performed automatically
     * @param newControllers not used; removal of added services during rollback is performed automatically.
     * @throws OperationFailedException if {@code operation} is invalid or updating the runtime otherwise fails
     *
     * @deprecated instead override one of the non-deprecated overloaded variants
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model,
                                  final ServiceVerificationHandler verificationHandler, final List<ServiceController<?>> newControllers) throws OperationFailedException {
    }

    /**
     * Rollback runtime changes made in {@link #performRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}.
     * Any services that were added in {@link org.jboss.as.controller.OperationContext.Stage#RUNTIME} will be automatically removed after this
     * method executes. Called from the {@link org.jboss.as.controller.OperationContext.ResultHandler} or
     * {@link org.jboss.as.controller.OperationContext.RollbackHandler} passed to {@code OperationContext.completeStep(...)}.
     * <p>
     * To provide compatible behavior with previous releases, this default implementation calls the deprecated
     * {@link #rollbackRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode, java.util.List)}
     * variant, passing in an empty list for the {@code controllers} parameter. Subclasses that overrode that method are
     * encouraged to instead override this one. <strong>Subclasses that override this method should not call
     * {@code super.rollbackRuntime(...).}</strong>
     *
     * @param context the operation context
     * @param operation the operation being executed
     * @param resource persistent configuration model node that corresponds to the address of {@code operation}
     */
    @SuppressWarnings("deprecation")
    protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
        rollbackRuntime(context, operation, resource.getModel(), new ArrayList<ServiceController<?>>(0));
    }

    /**
     * <strong>Deprecated</strong>. Subclasses wishing for custom rollback behavior should instead override
     * {@link #rollbackRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}.
     * <p>
     * This default implementation does nothing. <strong>Subclasses that override this method should not call
     * {@code super.performRuntime(...)}.</strong>
     * </p>
     * </p>
     * @param context the operation context
     * @param operation the operation being executed
     * @param model persistent configuration model node that corresponds to the address of {@code operation}
     * @param controllers  will always be an empty list
     *
     * @deprecated instead override {@link #rollbackRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}
     */
    @Deprecated
    protected void rollbackRuntime(OperationContext context, final ModelNode operation, final ModelNode model, List<ServiceController<?>> controllers) {
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
    private class OrderedResourceCreator implements ResourceCreator {
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
            this.orderedChildTypes = orderedChildTypes == null ? Collections.<String>emptySet() : orderedChildTypes;
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
            Set<String> set = new HashSet<String>(orderedChildTypes.length);
            for (String type : orderedChildTypes) {
                set.add(type);
            }
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
        private Set<RuntimeCapability> capabilities = null;
        protected Set<AttributeDefinition> attributes = null;

        public Parameters() {
        }

        public Parameters addRuntimeCapability(RuntimeCapability...capabilities) {
            Set<RuntimeCapability> capabilitySet = getOrCreateCapabilities();
            for (RuntimeCapability capability : capabilities) {
                capabilitySet.add(capability);
            }
            return this;
        }

        public Parameters addRuntimeCapability(Set<RuntimeCapability> capabilities) {
            getOrCreateCapabilities().addAll(capabilities);
            return this;
        }

        public Parameters addAttribute(AttributeDefinition... attributeDefinitions) {
            Set<AttributeDefinition> attributeSet = getOrCreateAttributes();
            for (AttributeDefinition def : attributeDefinitions) {
                attributeSet.add(def);
            }
            return this;
        }

        public Parameters addAttribute(Collection<AttributeDefinition> attributeDefinitions) {
            getOrCreateAttributes().addAll(attributeDefinitions);
            return this;
        }

        private Set<RuntimeCapability> getOrCreateCapabilities() {
            if (capabilities == null) {
                capabilities = new HashSet<>();
            }
            return capabilities;
        }

        private Set<AttributeDefinition> getOrCreateAttributes() {
            if (attributes == null) {
                attributes = new HashSet<>();
            }
            return attributes;
        }
    }
}
