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

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.DefaultResourceAddDescriptionProvider;
import org.jboss.as.controller.descriptions.DefaultResourceDescriptionProvider;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.wildfly.common.Assert;

/**
 * Basic implementation of {@link ResourceDefinition}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SimpleResourceDefinition implements ResourceDefinition {

    private static final EnumSet<OperationEntry.Flag> RESTART_FLAGS = EnumSet.of(OperationEntry.Flag.RESTART_NONE,
            OperationEntry.Flag.RESTART_RESOURCE_SERVICES, OperationEntry.Flag.RESTART_ALL_SERVICES, OperationEntry.Flag.RESTART_JVM);

    private static final RuntimeCapability[] NO_CAPABILITIES = new RuntimeCapability[0];

    private final PathElement pathElement;
    private final ResourceDescriptionResolver descriptionResolver;
    private final DescriptionProvider descriptionProvider;
    private final OperationStepHandler addHandler;
    private final OperationStepHandler removeHandler;
    private final OperationEntry.Flag addRestartLevel;
    private final OperationEntry.Flag removeRestartLevel;
    private final boolean runtime;
    private volatile DeprecationData deprecationData;
    private final boolean orderedChild;
    private final RuntimeCapability[] capabilities;
    private final Set<RuntimeCapability> incorporatingCapabilities;
    private final Set<CapabilityReferenceRecorder> requirements;
    private final List<AccessConstraintDefinition> accessConstraints;
    private final int minOccurs;
    private final int maxOccurs;
    private boolean feature;

    /**
     * {@link ResourceDefinition} that uses the given {code descriptionProvider} to describe the resource.
     *
     * @param pathElement         the path. Can be {@code null}.
     * @param descriptionProvider the description provider. Cannot be {@code null}
     * @throws IllegalArgumentException if {@code descriptionProvider} is {@code null}.
     * @deprecated Use {@link #SimpleResourceDefinition(Parameters)}
     */
    @Deprecated
    public SimpleResourceDefinition(final PathElement pathElement, final DescriptionProvider descriptionProvider) {
        //Can be removed when we get to 3.0.0
        Assert.checkNotNullParam("descriptionProvider", descriptionProvider);
        this.pathElement = pathElement;
        this.descriptionResolver = null;
        this.descriptionProvider = descriptionProvider;
        this.addHandler = null;
        this.removeHandler = null;
        this.addRestartLevel = null;
        this.removeRestartLevel = null;
        this.deprecationData = null;
        this.runtime = false;
        this.orderedChild = false;
        this.capabilities = NO_CAPABILITIES;
        this.incorporatingCapabilities = null;
        this.accessConstraints = Collections.emptyList();
        this.requirements = Collections.emptySet();
        this.minOccurs = 0;
        this.maxOccurs = Integer.MAX_VALUE;
        this.feature = true;
    }

    /**
     * {@link ResourceDefinition} that uses the given {code descriptionResolver} to configure a
     * {@link DefaultResourceDescriptionProvider} to describe the resource.
     *
     * @param pathElement         the path. Cannot be {@code null}.
     * @param descriptionResolver the description resolver to use in the description provider. Cannot be {@code null}
     * @throws IllegalArgumentException if any parameter is {@code null}.
     */
    public SimpleResourceDefinition(final PathElement pathElement, final ResourceDescriptionResolver descriptionResolver) {
        this(pathElement, descriptionResolver, null, null, null, null, null);
    }

    /**
     * {@link ResourceDefinition} that uses the given {code descriptionResolver} to configure a
     * {@link DefaultResourceDescriptionProvider} to describe the resource.
     *
     * @param pathElement         the path. Cannot be {@code null}.
     * @param descriptionResolver the description resolver to use in the description provider. Cannot be {@code null}
     * @param isRuntime tells if resource is runtime
     * @throws IllegalArgumentException if any parameter is {@code null}.
     * @deprecated Use {@link #SimpleResourceDefinition(Parameters)}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public SimpleResourceDefinition(final PathElement pathElement, final ResourceDescriptionResolver descriptionResolver, boolean isRuntime) {
        //Can be removed when we get to 3.0.0
        this(pathElement, descriptionResolver, null, null, null, null, null, isRuntime);
    }

    /**
     * {@link ResourceDefinition} that uses the given {code descriptionResolver} to configure a
     * {@link DefaultResourceDescriptionProvider} to describe the resource.
     *
     * @param pathElement         the path. Cannot be {@code null}.
     * @param descriptionResolver the description resolver to use in the description provider. Cannot be {@code null}      *
     * @param addHandler          a handler to {@link #registerOperations(ManagementResourceRegistration) register} for the resource "add" operation.
     *                            Can be {null}
     * @param removeHandler       a handler to {@link #registerOperations(ManagementResourceRegistration) register} for the resource "remove" operation.
     *                            Can be {null}
     * @throws IllegalArgumentException if any parameter is {@code null}
     */
    public SimpleResourceDefinition(final PathElement pathElement, final ResourceDescriptionResolver descriptionResolver,
                                    final OperationStepHandler addHandler, final OperationStepHandler removeHandler) {
        this(pathElement, descriptionResolver, addHandler, removeHandler, null, null, null);
    }

    /**
     * {@link ResourceDefinition} that uses the given {code descriptionResolver} to configure a
     * {@link DefaultResourceDescriptionProvider} to describe the resource.
     *
     * @param pathElement         the path. Cannot be {@code null}.
     * @param descriptionResolver the description resolver to use in the description provider. Cannot be {@code null}      *
     * @param addHandler          a handler to {@link #registerOperations(ManagementResourceRegistration) register} for the resource "add" operation.
     *                            Can be {null}
     * @param removeHandler       a handler to {@link #registerOperations(ManagementResourceRegistration) register} for the resource "remove" operation.
     *                            Can be {null}
     * @param isRuntime tells is resources is runtime or not
     * @throws IllegalArgumentException if any parameter is {@code null}
     * @deprecated Use {@link #SimpleResourceDefinition(Parameters)}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public SimpleResourceDefinition(final PathElement pathElement, final ResourceDescriptionResolver descriptionResolver,
                                    final OperationStepHandler addHandler, final OperationStepHandler removeHandler, boolean isRuntime) {
        //Can be removed when we get to 3.0.0
        this(pathElement, descriptionResolver, addHandler, removeHandler, null, null, null, isRuntime);
    }

    /**
     * {@link ResourceDefinition} that uses the given {code descriptionResolver} to configure a
     * {@link DefaultResourceDescriptionProvider} to describe the resource.
     *
     * @param pathElement         the path. Cannot be {@code null}.
     * @param descriptionResolver the description resolver to use in the description provider. Cannot be {@code null}      *
     * @param addHandler          a handler to {@link #registerOperations(ManagementResourceRegistration) register} for the resource "add" operation.
     *                            Can be {null}
     * @param removeHandler       a handler to {@link #registerOperations(ManagementResourceRegistration) register} for the resource "remove" operation.
     *                            Can be {null}
     * @param deprecationData     Information describing deprecation of this resource. Can be {@code null} if the resource isn't deprecated.
     * @throws IllegalArgumentException if any parameter is {@code null}
     * @deprecated Use {@link #SimpleResourceDefinition(Parameters)}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public SimpleResourceDefinition(final PathElement pathElement, final ResourceDescriptionResolver descriptionResolver,
                                    final OperationStepHandler addHandler, final OperationStepHandler removeHandler,
                                    final DeprecationData deprecationData) {
        //Can be removed when we get to 3.0.0
        this(pathElement, descriptionResolver, addHandler, removeHandler, null, null, deprecationData);
    }


    /**
     * {@link ResourceDefinition} that uses the given {code descriptionResolver} to configure a
     * {@link DefaultResourceDescriptionProvider} to describe the resource.
     *
     * @param pathElement         the path. Can be {@code null}.
     * @param descriptionResolver the description resolver to use in the description provider. Cannot be {@code null}      *
     * @param addHandler          a handler to {@link #registerOperations(ManagementResourceRegistration) register} for the resource "add" operation.
     *                            Can be {null}
     * @param removeHandler       a handler to {@link #registerOperations(ManagementResourceRegistration) register} for the resource "remove" operation.
     *                            Can be {null}
     * @throws IllegalArgumentException if {@code descriptionResolver} is {@code null}.
     * @deprecated Use {@link #SimpleResourceDefinition(Parameters)}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public SimpleResourceDefinition(final PathElement pathElement, final ResourceDescriptionResolver descriptionResolver,
                                    final OperationStepHandler addHandler, final OperationStepHandler removeHandler,
                                    final OperationEntry.Flag addRestartLevel, final OperationEntry.Flag removeRestartLevel) {
        //Can be removed when we get to 3.0.0
        this(pathElement, descriptionResolver, addHandler, removeHandler, addRestartLevel, removeRestartLevel, null);
    }


    /**
     * {@link ResourceDefinition} that uses the given {code descriptionResolver} to configure a
     * {@link DefaultResourceDescriptionProvider} to describe the resource.
     *
     * @param pathElement         the path. Can be {@code null}.
     * @param descriptionResolver the description resolver to use in the description provider. Cannot be {@code null}      *
     * @param addHandler          a handler to {@link #registerOperations(ManagementResourceRegistration) register} for the resource "add" operation.
     *                            Can be {null}
     * @param removeHandler       a handler to {@link #registerOperations(ManagementResourceRegistration) register} for the resource "remove" operation.
     *                            Can be {null}
     * @param deprecationData     Information describing deprecation of this resource. Can be {@code null} if the resource isn't deprecated.
     * @throws IllegalArgumentException if {@code descriptionResolver} is {@code null}.
     * @deprecated Use {@link #SimpleResourceDefinition(Parameters)}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public SimpleResourceDefinition(final PathElement pathElement, final ResourceDescriptionResolver descriptionResolver,
                                    final OperationStepHandler addHandler, final OperationStepHandler removeHandler,
                                    final OperationEntry.Flag addRestartLevel, final OperationEntry.Flag removeRestartLevel,
                                    final DeprecationData deprecationData) {
        //Can be removed when we get to 3.0.0
        this(pathElement, descriptionResolver, addHandler, removeHandler, addRestartLevel, removeRestartLevel, deprecationData, false);
    }



    /**
     * {@link ResourceDefinition} that uses the given {code descriptionResolver} to configure a
     * {@link DefaultResourceDescriptionProvider} to describe the resource.
     *
     * @param pathElement         the path. Can be {@code null}.
     * @param descriptionResolver the description resolver to use in the description provider. Cannot be {@code null}      *
     * @param addHandler          a handler to {@link #registerOperations(ManagementResourceRegistration) register} for the resource "add" operation.
     *                            Can be {null}
     * @param removeHandler       a handler to {@link #registerOperations(ManagementResourceRegistration) register} for the resource "remove" operation.
     *                            Can be {null}
     * @param deprecationData     Information describing deprecation of this resource. Can be {@code null} if the resource isn't deprecated.
     * @param runtime             Whether this is a runtime resource
     * @throws IllegalArgumentException if {@code descriptionResolver} is {@code null}.
     * @deprecated Use {@link #SimpleResourceDefinition(Parameters)}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public SimpleResourceDefinition(final PathElement pathElement, final ResourceDescriptionResolver descriptionResolver,
                                    final OperationStepHandler addHandler, final OperationStepHandler removeHandler,
                                    final OperationEntry.Flag addRestartLevel, final OperationEntry.Flag removeRestartLevel,
                                    final DeprecationData deprecationData, final boolean runtime) {
        //Don't add new constructor variants!
        //Use the Parameters variety

        //Can be removed when we get to 3.0.0
        this(new Parameters(pathElement, descriptionResolver)
                .setAddHandler(addHandler)
                .setAddRestartLevel(addRestartLevel == null ? restartLevelForAdd(addHandler) : addRestartLevel)
                .setRemoveHandler(removeHandler)
                .setRemoveRestartLevel(removeRestartLevel == null ? restartLevelForRemove(removeHandler) : removeRestartLevel)
                .setDeprecationData(deprecationData)
                .setRuntime(runtime));
    }

    /**
     * Constructs a {@link ResourceDefinition} using the passed in parameters object.
     *
     * @param parameters {@link SimpleResourceDefinition.Parameters} to configure this ResourceDefinition
     * @throws IllegalStateException if the parameters object is not valid.
     */
    public SimpleResourceDefinition(Parameters parameters) {
        this.pathElement = parameters.pathElement;
        this.descriptionResolver = parameters.descriptionResolver;
        this.addHandler = parameters.addHandler;
        this.removeHandler = parameters.removeHandler;
        this.addRestartLevel = parameters.addRestartLevel;
        this.removeRestartLevel = parameters.removeRestartLevel;
        this.deprecationData = parameters.deprecationData;
        this.runtime = parameters.runtime;
        this.orderedChild = parameters.orderedChildResource;
        this.descriptionProvider = null;
        this.capabilities = parameters.capabilities != null ? parameters.capabilities : NO_CAPABILITIES ;
        this.incorporatingCapabilities = parameters.incorporatingCapabilities;
        if (parameters.accessConstraints != null) {
            this.accessConstraints = Arrays.asList(parameters.accessConstraints);
        } else {
            this.accessConstraints = Collections.emptyList();
        }
        this.requirements = new HashSet<>(parameters.requirements);
        this.minOccurs = parameters.minOccurs;
        this.maxOccurs = parameters.maxOccurs;
        this.feature = parameters.feature;
    }

    private static OperationEntry.Flag restartLevelForAdd(OperationStepHandler addHandler) {
        return (addHandler instanceof AbstractBoottimeAddStepHandler || addHandler instanceof ReloadRequiredAddStepHandler)
                ? OperationEntry.Flag.RESTART_ALL_SERVICES : OperationEntry.Flag.RESTART_NONE;
    }

    private static OperationEntry.Flag restartLevelForRemove(OperationStepHandler removeHandler) {
        return removeHandler instanceof ReloadRequiredRemoveStepHandler
                ? OperationEntry.Flag.RESTART_ALL_SERVICES : OperationEntry.Flag.RESTART_RESOURCE_SERVICES;
    }


    @Override
    public PathElement getPathElement() {
        return pathElement;
    }

    @Override
    public DescriptionProvider getDescriptionProvider(ImmutableManagementResourceRegistration resourceRegistration) {
        return descriptionProvider == null
                ? new DefaultResourceDescriptionProvider(resourceRegistration, descriptionResolver, getDeprecationData())
                : descriptionProvider;
    }

    /**
     * {@inheritDoc}
     * Registers an add operation handler or a remove operation handler if one was provided to the constructor.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        if (addHandler != null) {
            registerAddOperation(resourceRegistration, addHandler, addRestartLevel);
        }
        if (removeHandler != null) {
            registerRemoveOperation(resourceRegistration, removeHandler, removeRestartLevel);
        }
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        // no-op
    }

    @Override
    public void registerNotifications(ManagementResourceRegistration resourceRegistration) {
        // no-op
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        // no-op
    }

    @Override
    public void registerCapabilities(ManagementResourceRegistration resourceRegistration) {
        if (capabilities!=null) {
            for (RuntimeCapability c : capabilities) {
                resourceRegistration.registerCapability(c);
            }
        }
        if (incorporatingCapabilities != null) {
            resourceRegistration.registerIncorporatingCapabilities(incorporatingCapabilities);
        }
        assert requirements != null;
        resourceRegistration.registerRequirements(requirements);
    }

    /**
     * Gets the {@link ResourceDescriptionResolver} used by this resource definition, or {@code null}
     * if a {@code ResourceDescriptionResolver} is not used.
     *
     * @return the resource description resolver, or {@code null}
     */
    public ResourceDescriptionResolver getResourceDescriptionResolver() {
        return descriptionResolver;
    }

    /**
     * Registers add operation
     *
     * @param registration resource on which to register
     * @param handler      operation handler to register
     * @param flags        with flags
     * @deprecated use {@link #registerAddOperation(org.jboss.as.controller.registry.ManagementResourceRegistration, AbstractAddStepHandler, org.jboss.as.controller.registry.OperationEntry.Flag...)}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    protected void registerAddOperation(final ManagementResourceRegistration registration, final OperationStepHandler handler, OperationEntry.Flag... flags) {
        if (handler instanceof DescriptionProvider) {
            registration.registerOperationHandler(getOperationDefinition(ModelDescriptionConstants.ADD,
                                (DescriptionProvider) handler, OperationEntry.EntryType.PUBLIC,flags)
                               , handler);

        } else {
            registration.registerOperationHandler(getOperationDefinition(ModelDescriptionConstants.ADD,
                    new DefaultResourceAddDescriptionProvider(registration, descriptionResolver, orderedChild),
                    OperationEntry.EntryType.PUBLIC,
                    flags)
                    , handler);
        }
    }

    private OperationDefinition getOperationDefinition(String operationName, DescriptionProvider descriptionProvider, OperationEntry.EntryType entryType, OperationEntry.Flag... flags){
        return new SimpleOperationDefinitionBuilder(operationName, descriptionResolver)
                .withFlags(flags)
                .setEntryType(entryType)
                .setDescriptionProvider(descriptionProvider)
                .build();

    }

    /**
     * Registers add operation
     * <p/>
     * Registers add operation
     *
     * @param registration resource on which to register
     * @param handler      operation handler to register
     * @param flags        with flags
     */
    protected void registerAddOperation(final ManagementResourceRegistration registration, final AbstractAddStepHandler handler,
                                        OperationEntry.Flag... flags) {
        registration.registerOperationHandler(getOperationDefinition(ModelDescriptionConstants.ADD,
                new DefaultResourceAddDescriptionProvider(registration, descriptionResolver, orderedChild), OperationEntry.EntryType.PUBLIC, flags)
                , handler);
    }

    @Deprecated
    @SuppressWarnings("deprecation")
    protected void registerRemoveOperation(final ManagementResourceRegistration registration, final OperationStepHandler handler,
                                           OperationEntry.Flag... flags) {
        if (handler instanceof DescriptionProvider) {
            registration.registerOperationHandler(getOperationDefinition(ModelDescriptionConstants.REMOVE,
                                            (DescriptionProvider) handler, OperationEntry.EntryType.PUBLIC,flags)
                                           , handler);
        } else {
            OperationDefinition opDef = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.REMOVE, descriptionResolver)
                    .withFlags(flags)
                    .build();
            registration.registerOperationHandler(opDef, handler);
        }
    }

    @SuppressWarnings("deprecation")
    protected void registerRemoveOperation(final ManagementResourceRegistration registration, final AbstractRemoveStepHandler handler,
                                           OperationEntry.Flag... flags) {
        registerRemoveOperation(registration, (OperationStepHandler) handler, flags);
    }

    private static OperationEntry.Flag validateRestartLevel(String paramName, OperationEntry.Flag flag) {
        if (flag != null && !RESTART_FLAGS.contains(flag)) {
            throw ControllerLogger.ROOT_LOGGER.invalidParameterValue(flag, paramName, RESTART_FLAGS);
        }
        return flag;
    }

    protected static EnumSet<OperationEntry.Flag> getFlagsSet(OperationEntry.Flag... vararg) {
        return SimpleOperationDefinitionBuilder.getFlagsSet(vararg);
    }

    /**
     * {@inheritDoc}
     *
     * @return this default implementation simply returns an empty list.
     */
    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        return this.accessConstraints;
    }

    protected void setDeprecated(ModelVersion since) {
        this.deprecationData = new DeprecationData(since);
    }

    protected DeprecationData getDeprecationData(){
        return this.deprecationData;
    }

    @Override
    public boolean isRuntime() {
        return runtime;
    }

   /**
   * {@inheritDoc}
   */
    @Override
    public int getMinOccurs() {
        if (minOccurs == 0) {
            return ResourceDefinition.super.getMinOccurs();
        }
        return minOccurs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxOccurs() {
        if (maxOccurs == Integer.MAX_VALUE) {
            return ResourceDefinition.super.getMaxOccurs();
        }
        return maxOccurs;
    }

    /**
     * Whether this resource registration is ordered in the parent. The automatically generated 'add' operation will
     * get the {@code add-index} parameter added. Also, it will get registered as an ordered child in the parent's
     * management resource registration.
     *
     * @return whether this is an ordered child resource
     */
    @SuppressWarnings("deprecation")
    public boolean isOrderedChild() {
        return orderedChild;
    }

    @Override
    public boolean isFeature() {
        return feature;
    }

    /**
     * Parameters object for the SimpleResourceDefinition constructor
     */
    public static class Parameters{
        private final PathElement pathElement;
        private ResourceDescriptionResolver descriptionResolver;
        private OperationStepHandler addHandler;
        private OperationStepHandler removeHandler;
        private OperationEntry.Flag addRestartLevel = OperationEntry.Flag.RESTART_NONE;
        private OperationEntry.Flag removeRestartLevel = OperationEntry.Flag.RESTART_ALL_SERVICES;
        private boolean runtime;
        private DeprecationData deprecationData;
        private boolean orderedChildResource;
        private RuntimeCapability[] capabilities;
        private Set<RuntimeCapability> incorporatingCapabilities;
        private Set<CapabilityReferenceRecorder> requirements = new HashSet<>();
        private AccessConstraintDefinition[] accessConstraints;
        private boolean feature = true;
        private int minOccurs = 0;
        private int maxOccurs = Integer.MAX_VALUE;
        /**
         * Creates a Parameters object
         * @param pathElement the path element of the created ResourceDefinition. Cannot be {@code null}
         * @param descriptionResolver the description resolver. Cannot be {@code null}
         */
        public Parameters(PathElement pathElement, ResourceDescriptionResolver descriptionResolver) {
            Assert.checkNotNullParam("descriptionResolver", descriptionResolver);
            this.pathElement = pathElement;
            this.descriptionResolver = descriptionResolver;
        }

        /**
         * Sets the description resolver to use
         *
         * @param descriptionResolver the description resolver. Cannot be {@code null}
         * @return this Parameters object
         */
        public Parameters setDescriptionResolver(ResourceDescriptionResolver descriptionResolver) {
            Assert.checkNotNullParam("descriptionResolver", descriptionResolver);
            this.descriptionResolver = descriptionResolver;
            return this;
        }

        /**
         * Sets the add handler. This can also be added by overriding
         * {@link SimpleResourceDefinition#registerOperations(ManagementResourceRegistration)}
         *
         * @param addHandler the add handler to use.
         * @return this Parameters object
         */
        public Parameters setAddHandler(OperationStepHandler addHandler) {
            this.addHandler = addHandler;
            if (this.addRestartLevel == null) {
                this.addRestartLevel = restartLevelForAdd(addHandler);
            }
            return this;
        }

        /**
         * Sets the remove handler. This can also be added by overriding
         * {@link SimpleResourceDefinition#registerOperations(ManagementResourceRegistration)}
         *
         * @param removeHandler the add handler to use.
         * @return this Parameters object
         */
        public Parameters setRemoveHandler(OperationStepHandler removeHandler) {
            this.removeHandler = removeHandler;
            if (this.removeRestartLevel == null) {
                this.removeRestartLevel = restartLevelForRemove(removeHandler);
            }
            return this;
        }

        /**
         * Sets the add restart level. The default is {@link OperationEntry.Flag#RESTART_NONE}
         *
         * @param addRestartLevel the restart level
         * @return this Parameters object
         * @throws IllegalArgumentException if {@code addRestartLevel} is {@code null} or a flag that does not pertain to restarts
         */
        public Parameters setAddRestartLevel(OperationEntry.Flag addRestartLevel) {
            Assert.checkNotNullParam("addRestartLevel", addRestartLevel);
            this.addRestartLevel = validateRestartLevel("addRestartLevel", addRestartLevel);
            return this;
        }

        /**
         * Sets the remove restart level. The default is {@link OperationEntry.Flag#RESTART_ALL_SERVICES}
         * @param removeRestartLevel the restart level
         * @return this Parameters object
         * @throws IllegalArgumentException if {@code addRestartLevel} is {@code null} or a flag that does not pertain to restarts
         */
        public Parameters setRemoveRestartLevel(OperationEntry.Flag removeRestartLevel) {
            Assert.checkNotNullParam("removeRestartLevel", removeRestartLevel);
            this.removeRestartLevel = validateRestartLevel("removeRestartLevel", removeRestartLevel);
            return this;
        }

        /**
         * Call to indicate that a resource is runtime-only. If not called, the default is {@code false}
         *
         * @return this Parameters object
         */
        public Parameters setRuntime() {
            this.runtime = true;
            return this;
        }


        /**
         * Call to indicate that a resource is runtime-only. If not called, the default is {@code false}
         *
         * @return this Parameters object
         */
        public Parameters setRuntime(boolean isRuntime) {
            this.runtime = isRuntime;
            return this;
        }

        /**
         * Call to deprecate the resource
         *
         * @param deprecationData Information describing deprecation of this resource.
         * @return this Parameters object
         * @throws IllegalStateException if the {@code deprecationData} is null
         */
        public Parameters setDeprecationData(DeprecationData deprecationData) {
            this.deprecationData = deprecationData;
            return this;
        }

        /**
         * Call to deprecate the resource
         *
         * @param deprecatedSince version in which model was deprecated
         * @return this Parameters object
         * @throws IllegalStateException if the {@code deprecationData} is null
         */
        public Parameters setDeprecatedSince(ModelVersion deprecatedSince) {
            Assert.checkNotNullParam("deprecatedSince", deprecatedSince);

            this.deprecationData = new DeprecationData(deprecatedSince);
            return this;
        }

        /**
         * Call to indicate that a resource is of a type where ordering matters amongst the siblings of the same type.
         * If not called, the default is {@code false}.
         *
         * @return this Parameters object
         */

        public Parameters setOrderedChild() {
            this.orderedChildResource = true;
            return this;
        }

        /**
         * set possible capabilities that this resource exposes
         * @param capabilities capabilities to register
         * @return Parameters object
         */
        public Parameters setCapabilities(RuntimeCapability ... capabilities){
            this.capabilities = capabilities;
            return this;
        }

        /**
         * Add possible capabilities for this resource to any that are already set.
         * @param capabilities capabilities to register
         * @return Parameters object
         */
        public Parameters addCapabilities(RuntimeCapability ... capabilities) {
            if (this.capabilities == null) {
                setCapabilities(capabilities);
            } else if (capabilities != null && capabilities.length > 0) {
                RuntimeCapability[] combo = Arrays.copyOf(this.capabilities, this.capabilities.length + capabilities.length);
                System.arraycopy(capabilities, 0, combo, this.capabilities.length, capabilities.length);
                setCapabilities(combo);
            }
            return this;
        }

        /**
         * Set access constraint definitions for this resource
         * @param accessConstraints access constraint definitions for this resource
         * @return Parameters object
         */
        public Parameters setAccessConstraints(AccessConstraintDefinition ... accessConstraints){
            this.accessConstraints = accessConstraints;
            return this;
        }

        /**
         * Add access constraint definitions for this resource to any that are already set.
         * @param accessConstraints access constraint definitions for this resource
         * @return Parameters object
         */
        public Parameters addAccessConstraints(AccessConstraintDefinition ... accessConstraints) {
            if (this.accessConstraints == null) {
                setAccessConstraints(accessConstraints);
            } else if (accessConstraints != null && accessConstraints.length > 0) {
                AccessConstraintDefinition[] combo = Arrays.copyOf(this.accessConstraints, this.accessConstraints.length + accessConstraints.length);
                System.arraycopy(accessConstraints, 0, combo, this.accessConstraints.length, accessConstraints.length);
                setAccessConstraints(combo);
            }
            return this;
        }

        /**
         * set the maximum number of occurrences for this resource
         * @param maxOccurs the maximum number of times this resource can occur
         * @return Parameters object
         */
        public Parameters setMaxOccurs(final int maxOccurs){
            this.maxOccurs = maxOccurs;
            return this;
        }

        /**
         * set the minimum number of occurrences for this resource
         * @param minOccurs the minimum number of times this resource must occur
         * @return Parameters object
         */
        public Parameters setMinOccurs(final int minOccurs){
            this.minOccurs = minOccurs;
            return this;
        }
/**
         * set the feature nature of this resource
         * @param feature true if this resource is a feature
         * @return Parameters object
         */
        public Parameters setFeature(final boolean feature){
            this.feature = feature;
            return this;
        }
        /**
         * Registers a set of capabilities that this resource does not directly provide but to which it contributes. This
         * will only include capabilities for which this resource <strong>does not</strong> control the
         * {@link ManagementResourceRegistration#registerCapability(RuntimeCapability) registration of the capability}.
         * Any capabilities registered by this resource should instead be declared using {@link #setCapabilities(RuntimeCapability[])}.
         * <p>
         * Use of this method is only necessary if the caller wishes to specifically record capability incorporation,
         * instead of relying on the default resolution mechanism detailed in
         * {@link ManagementResourceRegistration#getIncorporatingCapabilities()}, or
         * if it wishes disable the default resolution mechanism and specifically declare that this resource does not
         * contribute to parent capabilities. It does the latter by passing an empty set as the {@code capabilities}
         * parameter. Passing an empty set is not necessary if this resource itself directly
         * {@link #setCapabilities(RuntimeCapability[]) provides a capability}, as it is the contract of
         * {@link ManagementResourceRegistration#getIncorporatingCapabilities()} that in that case it must return an empty set.
         *
         * @param  incorporatingCapabilities set of capabilities, or {@code null} if default resolution of capabilities to which this
         *                      resource contributes should be used; an empty set can be used to indicate this resource
         *                      does not contribute to capabilities provided by its parent
         *
         * @return Parameters object
         */
        public Parameters setIncorporatingCapabilities(Set<RuntimeCapability> incorporatingCapabilities) {
            this.incorporatingCapabilities = incorporatingCapabilities;
            return this;
        }

        /**
         * Add a required capability at the resource level, using the resource registration address and the nameMappers
         * to resolve the required and dependant capabilities.
         * @param baseDependentName the dependent capability base name.
         * @param dependentDynamicNameMapper the dependent capability name mapper.
         * @param baseRequirementName the required capability base name.
         * @param requirementDynamicNameMapper  the required capability name mapper.
         * @return Parameters object.
         */
        public Parameters addRequirement(String baseDependentName,
                Function<PathAddress, String[]> dependentDynamicNameMapper,
                String baseRequirementName, Function<PathAddress, String[]> requirementDynamicNameMapper) {
            this.requirements.add(new CapabilityReferenceRecorder.ResourceCapabilityReferenceRecorder(
                    dependentDynamicNameMapper, baseDependentName, requirementDynamicNameMapper, baseRequirementName));
            return this;
        }

        /**
         * Adds incorporating capabilities to any that have already been set.
         * @param incorporatingCapabilities capabilities to add
         * @return Parameters object
         */
        public Parameters addIncorporatingCapabilities(Set<RuntimeCapability> incorporatingCapabilities) {
            if (this.incorporatingCapabilities == null) {
                setIncorporatingCapabilities(incorporatingCapabilities);
            } else if (incorporatingCapabilities != null && !incorporatingCapabilities.isEmpty()) {
                Set<RuntimeCapability> combo = new HashSet<>();
                combo.addAll(this.incorporatingCapabilities);
                combo.addAll(incorporatingCapabilities);
                setIncorporatingCapabilities(combo);
            }
            return this;
        }
    }
}
