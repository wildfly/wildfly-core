/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.INCLUDE_ALIASES;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.INCLUDE_DEFAULTS;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.INCLUDE_RUNTIME;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.INCLUDE_UNDEFINED_METRIC_VALUES;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.PROXIES;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.RECURSIVE;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.RECURSIVE_DEPTH;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.AttachmentKey;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.UnauthorizedException;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.ResourceNotAddressableException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.validation.ParametersValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.OperationStepHandler} reading a part of the model. The result will only contain the current attributes of a node by default,
 * excluding all addressable children and runtime attributes. Setting the request parameter "recursive" to "true" will recursively include
 * all children and configuration attributes. Queries can include runtime attributes by setting the request parameter
 * "include-runtime" to "true".
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ReadResourceHandler extends GlobalOperationHandlers.AbstractMultiTargetHandler {

    private static final SimpleAttributeDefinition ATTRIBUTES_ONLY = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ATTRIBUTES_ONLY, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(READ_RESOURCE_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(RECURSIVE, RECURSIVE_DEPTH, PROXIES, INCLUDE_RUNTIME, INCLUDE_DEFAULTS, ATTRIBUTES_ONLY, INCLUDE_ALIASES, INCLUDE_UNDEFINED_METRIC_VALUES)
            .setReadOnly()
            .setReplyType(ModelType.OBJECT)
            .build();

    public static final OperationStepHandler INSTANCE = new ReadResourceHandler();

    private static final SimpleAttributeDefinition RESOLVE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.RESOLVE_EXPRESSIONS, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    public static final OperationDefinition RESOLVE_DEFINITION = new SimpleOperationDefinitionBuilder(READ_RESOURCE_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(RESOLVE, RECURSIVE, RECURSIVE_DEPTH, PROXIES, INCLUDE_RUNTIME, INCLUDE_DEFAULTS, ATTRIBUTES_ONLY, INCLUDE_ALIASES)
            .setReadOnly()
            .setReplyType(ModelType.OBJECT)
            .build();

    public static final OperationStepHandler RESOLVE_INSTANCE = new ReadResourceHandler(true);

    public static final AttachmentKey<ModelNode> ROLLBACKED_FAILURE_DESC = AttachmentKey.create(ModelNode.class);

    private final ParametersValidator validator;

    private final OperationStepHandler overrideHandler;
    private final boolean resolvable;

    public ReadResourceHandler() {
        this(null, null, false, false);
    }

    public ReadResourceHandler(boolean resolvable){
        this(null,null,resolvable, false);
    }

    ReadResourceHandler(final FilteredData filteredData, OperationStepHandler overrideHandler, boolean resolvable) {
        this(filteredData, overrideHandler, resolvable, true);
    }

    private ReadResourceHandler(final FilteredData filteredData, OperationStepHandler overrideHandler,
                                boolean resolvable, boolean ignoreMissingResource) {
        super(filteredData, ignoreMissingResource);
        this.overrideHandler = overrideHandler;
        this.resolvable = resolvable;
        this.validator = resolvable ? Validator.RESOLVABLE : Validator.NON_RESOLVABLE;
    }



    @Override
    void doExecute(OperationContext context, ModelNode operation, FilteredData filteredData, boolean ignoreMissingResource) throws OperationFailedException {

        if (filteredData == null) {
            doExecuteInternal(context, operation, ignoreMissingResource);
        } else {
            try {
                if (overrideHandler == null) {
                    doExecuteInternal(context, operation, ignoreMissingResource);
                } else if (overrideHandler instanceof FilteredDataReadHandler filteredDataReadHandler) {
                    filteredDataReadHandler.execute(context, operation, filteredData);
                } else {
                    ControllerLogger.ROOT_LOGGER.tracef("read-resource override handler %s does not implement %s. It should be updated",
                            overrideHandler.getClass().getName(), FilteredDataReadHandler.class.getSimpleName());
                    overrideHandler.execute(context, operation);
                }
            } catch (ResourceNotAddressableException rnae) {
                // Just report the failure to the filter and complete normally
                reportInaccesible(context, operation, filteredData);
                ControllerLogger.MGMT_OP_LOGGER.tracef("Caught ResourceNotAddressableException in %s", this);
            } catch (Resource.NoSuchResourceException nsre) {
                // It's possible this is a remote failure, in which case we
                // don't get ResourceNotAddressableException. So see if
                // it was due to any authorization denial
                AuthorizationResult.Decision decision = context.authorize(operation, EnumSet.of(Action.ActionEffect.ADDRESS)).getDecision();
                ControllerLogger.MGMT_OP_LOGGER.tracef("Caught NoSuchResourceException in %s. Authorization decision is %s", this ,decision);
                if (decision == AuthorizationResult.Decision.DENY) {
                    // Just report the failure to the filter and complete normally
                    reportInaccesible(context, operation, filteredData);
                } else if (!ignoreMissingResource) {
                    throw nsre;
                }
            } catch (UnauthorizedException ue) {
                // Just report the failure to the filter and complete normally
                PathAddress pa = PathAddress.pathAddress(operation.get(OP_ADDR));
                filteredData.addReadRestrictedResource(pa);
                context.getResult().set(new ModelNode());
                ControllerLogger.MGMT_OP_LOGGER.tracef("Caught UnauthorizedException in %s", this);

            }
        }
    }

    private void reportInaccesible(OperationContext context, ModelNode operation, FilteredData filteredData) {
        PathAddress pa = PathAddress.pathAddress(operation.get(OP_ADDR));
        filteredData.addAccessRestrictedResource(pa);
        context.getResult().set(new ModelNode());

    }

    private void doExecuteInternal(OperationContext context, ModelNode operation, boolean ignoreMissingResource) throws OperationFailedException {

        validator.validate(operation);

        final String opName = operation.require(OP).asString();
        final PathAddress address = context.getCurrentAddress();
        // WFCORE-76
        final boolean recursive = GlobalOperationHandlers.getRecursive(context, operation);
        final boolean queryRuntime = operation.get(ModelDescriptionConstants.INCLUDE_RUNTIME).asBoolean(false);
        final boolean proxies = operation.get(ModelDescriptionConstants.PROXIES).asBoolean(false);
        final boolean aliases = operation.get(ModelDescriptionConstants.INCLUDE_ALIASES).asBoolean(false);
        final boolean defaults = operation.get(ModelDescriptionConstants.INCLUDE_DEFAULTS).asBoolean(true);
        final boolean includeUndefinedMetricValues = operation.get(ModelDescriptionConstants.INCLUDE_UNDEFINED_METRIC_VALUES).asBoolean(false);
        final boolean attributesOnly = operation.get(ModelDescriptionConstants.ATTRIBUTES_ONLY).asBoolean(false);
        final boolean resolve = RESOLVE.resolveModelAttribute(context, operation).asBoolean();

        // Child types with no actual children
        final Set<String> nonExistentChildTypes = new HashSet<String>();
        // Children names read directly from the model where we didn't call read-resource to gather data
        // We wouldn't call read-resource if the recursive=false
        final Map<String, ModelNode> directChildren = new HashMap<String, ModelNode>();
        // Attributes of AccessType.METRIC
        final Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> metrics = queryRuntime
                ? new HashMap<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse>()
                : Collections.<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse>emptyMap();
        // Non-AccessType.METRIC attributes
        final Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> otherAttributes = new HashMap<>();
        // Child resources recursively read
        final Map<PathElement, ModelNode> childResources = recursive ? new LinkedHashMap<PathElement, ModelNode>() : Collections.<PathElement, ModelNode>emptyMap();

        // If we were not configured with a FilteredData, we are handling the top
        // resource being read, otherwise we are a child resource
        FilteredData fd = getFilteredData();
        final FilteredData localFilteredData = (fd == null) ? new FilteredData(address): fd;
        // We only want our assembly handler to report filtered data if we just created
        // the FilteredData for the overall op
        boolean reportFilteredData = fd == null;

        // We're going to add a bunch of steps that should immediately follow this one. We are going to add them
        // in reverse order of how they should execute, as that is the way adding a Stage.IMMEDIATE step works

        // Last to execute is the handler that assembles the overall response from the pieces created by all the other steps
        final ReadResourceAssemblyHandler assemblyHandler = new ReadResourceAssemblyHandler(address, metrics,
                otherAttributes, directChildren, childResources, nonExistentChildTypes, reportFilteredData, localFilteredData, ignoreMissingResource);
        context.addStep(assemblyHandler, queryRuntime ? OperationContext.Stage.VERIFY : OperationContext.Stage.MODEL, true);
        final ImmutableManagementResourceRegistration registry = context.getResourceRegistration();

        // Get the model for this resource.
        final Resource resource = nullSafeReadResource(context, registry);

        final Map<String, Set<String>> childrenByType = registry != null ? GlobalOperationHandlers.getChildAddresses(context, address, registry, resource, null) : Collections.<String, Set<String>>emptyMap();
        if (!attributesOnly) {
            // Next, process child resources
            for (Map.Entry<String, Set<String>> entry : childrenByType.entrySet()) {

                String childType = entry.getKey();

                // child type has no children until we add one
                nonExistentChildTypes.add(childType);
                if(!aliases && (entry.getValue() == null || entry.getValue().isEmpty())) {
                    if(isGlobalAlias(registry, childType)) {
                        nonExistentChildTypes.remove(childType);
                    }
                }

                for (String child : entry.getValue()) {
                    PathElement childPE = PathElement.pathElement(childType, child);
                    PathAddress absoluteChildAddr = address.append(childPE);

                    ModelNode rrOp = Util.createEmptyOperation(READ_RESOURCE_OPERATION, absoluteChildAddr);
                    PathAddress relativeAddr = PathAddress.pathAddress(childPE);

                    ImmutableManagementResourceRegistration childReg = registry == null ? null : registry.getSubModel(relativeAddr);

                    if (recursive) {
                        boolean getChild = false;
                        if (childReg != null) {
                            // Decide if we want to invoke on this child resource
                            boolean proxy = childReg.isRemote();
                            boolean runtimeResource = childReg.isRuntimeOnly();
                            getChild = !runtimeResource || (queryRuntime && !proxy) || (proxies && proxy);
                            if (!aliases && childReg.isAlias()) {
                                nonExistentChildTypes.remove(childType);
                                getChild = false;
                            }
                        } else {
                            ControllerLogger.MGMT_OP_LOGGER.tracef("ManagementResourceRegistration for address %s has been removed", absoluteChildAddr);
                        }
                        if (getChild) {
                            nonExistentChildTypes.remove(childType);
                            // WFCORE-76
                            GlobalOperationHandlers.setNextRecursive(context, operation, rrOp);
                            rrOp.get(ModelDescriptionConstants.PROXIES).set(proxies);
                            rrOp.get(ModelDescriptionConstants.INCLUDE_RUNTIME).set(queryRuntime);
                            rrOp.get(ModelDescriptionConstants.INCLUDE_ALIASES).set(aliases);
                            rrOp.get(ModelDescriptionConstants.INCLUDE_DEFAULTS).set(defaults);
                            rrOp.get(ModelDescriptionConstants.RESOLVE_EXPRESSIONS).set(resolve);
                            ModelNode rrRsp = new ModelNode();
                            childResources.put(childPE, rrRsp);

                            // See if there was an override registered for the standard :read-resource handling (unlikely!!!)
                            OperationStepHandler overrideHandler = childReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, opName);
                            if (overrideHandler != null && overrideHandler.getClass() == getClass()) {
                                // not an override
                                overrideHandler = null;
                            }
                            OperationStepHandler rrHandler = new ReadResourceHandler(localFilteredData, overrideHandler, resolvable);

                            context.addStep(rrRsp, rrOp, rrHandler, OperationContext.Stage.MODEL, true);
                        }
                    } else {
                        // Non-recursive. Just output the names of the children
                        // But filter inaccessible children
                        AuthorizationResult ar = context.authorize(rrOp, EnumSet.of(Action.ActionEffect.ADDRESS));
                        if (ar.getDecision() == AuthorizationResult.Decision.DENY) {
                            localFilteredData.addAccessRestrictedResource(absoluteChildAddr);
                        } else {
                            ModelNode childMap = directChildren.get(childType);
                            if (childMap == null) {
                                nonExistentChildTypes.remove(childType);
                                childMap = new ModelNode();
                                childMap.setEmptyObject();
                                directChildren.put(childType, childMap);
                            }
                            // In case of runtime resources adds '=> undefined' if there's no include-runtime parameter,
                            // in read-resource operation, otherwise adds '{"child" => undefined}'
                            if (queryRuntime || (childReg != null && (!childReg.isRuntimeOnly() || childReg.isRemote()))) {
                                childMap.get(child);
                            }
                        }
                    }
                }
            }
        }

        // Handle registered attributes
        final Set<String> attributeNames = registry != null ? registry.getAttributeNames(PathAddress.EMPTY_ADDRESS) : Collections.<String>emptySet();
        for (final String attributeName : attributeNames) {

            final AttributeAccess access = registry.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName);
            if ((aliases || !access.getFlags().contains(AttributeAccess.Flag.ALIAS))
                    && (queryRuntime || access.getStorageType() == AttributeAccess.Storage.CONFIGURATION)) {

                Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> responseMap = access.getAccessType() == AttributeAccess.AccessType.METRIC ? metrics : otherAttributes;

                AttributeDefinition ad = access.getAttributeDefinition();
                AttributeDefinition.NameAndGroup nag = ad == null ? new AttributeDefinition.NameAndGroup(attributeName) : new AttributeDefinition.NameAndGroup(ad);
                addReadAttributeStep(context, address, defaults, resolve, includeUndefinedMetricValues, localFilteredData, registry, nag, responseMap);

            }
        }
    }

    private boolean isSingletonResource(final ImmutableManagementResourceRegistration registry, final String key) {
        return registry.getSubModel(PathAddress.pathAddress(PathElement.pathElement(key))) == null;
    }

    private boolean isGlobalAlias(final ImmutableManagementResourceRegistration registry, final String childName) {
        if(isSingletonResource(registry, childName)) {
            Set<PathElement> childrenPath = registry.getChildAddresses(PathAddress.EMPTY_ADDRESS);
            boolean found = false;
            boolean alias = true;
            for(PathElement childPath : childrenPath) {
                if(childPath.getKey().equals(childName)) {
                    found = true;
                    ImmutableManagementResourceRegistration squatterRegistration = registry.getSubModel(PathAddress.pathAddress(childPath));
                    alias = alias && (squatterRegistration != null && squatterRegistration.isAlias());
                }
            }
            return (found && alias);
        }
        return registry.getSubModel(PathAddress.pathAddress(PathElement.pathElement(childName))).isAlias();
    }

    private void addReadAttributeStep(OperationContext context, PathAddress address, boolean defaults, boolean resolve, boolean includeUndefinedMetricValues, FilteredData localFilteredData,
                                      ImmutableManagementResourceRegistration registry,
                                      AttributeDefinition.NameAndGroup attributeKey, Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> responseMap) {
        // See if there was an override registered for the standard :read-attribute handling (unlikely!!!)
        OperationStepHandler overrideHandler = registry.getOperationHandler(PathAddress.EMPTY_ADDRESS, READ_ATTRIBUTE_OPERATION);
        if (overrideHandler != null &&
                (overrideHandler == ReadAttributeHandler.INSTANCE || overrideHandler == ReadAttributeHandler.RESOLVE_INSTANCE)) {
            // not an override
            overrideHandler = null;
        }

        OperationStepHandler readAttributeHandler = new ReadAttributeHandler(localFilteredData, overrideHandler, (resolve && resolvable));

        final ModelNode attributeOperation = Util.getReadAttributeOperation(address, attributeKey.getName());
        attributeOperation.get(ModelDescriptionConstants.INCLUDE_DEFAULTS).set(defaults);
        attributeOperation.get(ModelDescriptionConstants.RESOLVE_EXPRESSIONS).set(resolve);
        attributeOperation.get(ModelDescriptionConstants.INCLUDE_UNDEFINED_METRIC_VALUES).set(includeUndefinedMetricValues);

        final ModelNode attrResponse = new ModelNode();
        GlobalOperationHandlers.AvailableResponse availableResponse = new GlobalOperationHandlers.AvailableResponse(attrResponse);
        responseMap.put(attributeKey, availableResponse);

        GlobalOperationHandlers.AvailableResponseWrapper wrapper = new GlobalOperationHandlers.AvailableResponseWrapper(readAttributeHandler, availableResponse);

        context.addStep(attrResponse, attributeOperation, wrapper, OperationContext.Stage.MODEL, true);
    }

    /**
     * Provides a resource for the current step, either from the context, if the context doesn't have one
     * and {@code registry} is runtime-only, it creates a dummy resource.
     */
    private static Resource nullSafeReadResource(final OperationContext context, final ImmutableManagementResourceRegistration registry) {

        Resource result;
        if (registry != null && registry.isRemote()) {
            try {
                // BES 2015/02/12 (WFCORE-539) -- following comment and use of 'true' I can't understand,
                // as the only use of 'resource' is to get the model or the children names,
                // neither of which needs the cloning behavior in OperationContextImpl.readResourceFromRoot

                //TODO check that having changed this from false to true does not break anything
                //If it does, consider adding a Resource.alwaysClone() method that can be used in
                //OperationContextImpl.readResourceFromRoot(final PathAddress address, final boolean recursive)
                //instead of the recursive check
                //result = context.readResource(PathAddress.EMPTY_ADDRESS, true);

                // BES 2015/02/12 -- So, back to 'false'
                result = context.readResource(PathAddress.EMPTY_ADDRESS, false);
            } catch (RuntimeException e) {
                result = PlaceholderResource.INSTANCE;
            }
        } else {
            // BES 2015/02/12 (WFCORE-539) -- following comment and use of 'true' I can't understand,
            // as the only use of 'resource' is to get the model or the children names,
            // neither of which needs the cloning behavior in OperationContextImpl.readResourceFromRoot

            //TODO check that having changed this from false to true does not break anything
            //If it does, consider adding a Resource.alwaysClone() method that can be used in
            //OperationContextImpl.readResourceFromRoot(final PathAddress address, final boolean recursive)
            //instead of the recursive check

            // BES 2015/02/12 -- So, back to 'false'
            result = context.readResource(PathAddress.EMPTY_ADDRESS, false);
        }
        return result;
    }

    public static final class Validator extends ParametersValidator {

        public static final Validator RESOLVABLE = new Validator(true);
        public static final Validator NON_RESOLVABLE = new Validator(false);

        private final boolean resolvable;

        private Validator(boolean resolvable) {
            this.resolvable = resolvable;
        }

        @Override
        public void validate(ModelNode operation) throws OperationFailedException {
            super.validate(operation);
            for (AttributeDefinition def : DEFINITION.getParameters()) {
                def.validateOperation(operation);
            }
            if (operation.hasDefined(ModelDescriptionConstants.ATTRIBUTES_ONLY)) {
                if (operation.hasDefined(ModelDescriptionConstants.RECURSIVE)) {
                    throw ControllerLogger.ROOT_LOGGER.cannotHaveBothParameters(ModelDescriptionConstants.ATTRIBUTES_ONLY, ModelDescriptionConstants.RECURSIVE);
                }
                if (operation.hasDefined(ModelDescriptionConstants.RECURSIVE_DEPTH)) {
                    throw ControllerLogger.ROOT_LOGGER.cannotHaveBothParameters(ModelDescriptionConstants.ATTRIBUTES_ONLY, ModelDescriptionConstants.RECURSIVE_DEPTH);
                }
            }
            if( operation.hasDefined(ModelDescriptionConstants.RESOLVE_EXPRESSIONS)){
                if(operation.get(ModelDescriptionConstants.RESOLVE_EXPRESSIONS).asBoolean(false) && !resolvable){
                    throw ControllerLogger.ROOT_LOGGER.unableToResolveExpressions();
                }
            }
        }
    }
}
