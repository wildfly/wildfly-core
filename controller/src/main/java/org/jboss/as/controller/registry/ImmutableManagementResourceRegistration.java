/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.registry;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;

import java.security.Permission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.as.controller.CapabilityReferenceRecorder;
import java.util.StringJoiner;

import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.FeatureRegistry;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.security.ControllerPermission;

/**
 * Read-only view of a {@link ManagementResourceRegistration}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public interface ImmutableManagementResourceRegistration extends FeatureRegistry {

    /**
     * A {@link org.jboss.as.controller.security.ControllerPermission} needed to create a {@link ImmutableManagementResourceRegistration} or invoke one
     * of its methods. The name of the permission is "{@code canAccessImmutableManagementResourceRegistration}."
     */
    Permission ACCESS_PERMISSION = ControllerPermission.CAN_ACCESS_IMMUTABLE_MANAGEMENT_RESOURCE_REGISTRATION;

    /**
     * Gets the address under which we are registered.
     *
     * @return the address. Will not be {@code null}
     */
    PathAddress getPathAddress();

    /**
     * Gets the type of process in which this management resource type is registered.
     * @return the process type. Will not return {@code null}
     */
    ProcessType getProcessType();

    /**
     * Gets the registration for this resource type's parent, if there is one.
     * @return the parent, or {@code null} if {@link #getPathAddress()} returns an address with a
     *         {@link PathAddress#size() size} of {@code 0}
     */
    ImmutableManagementResourceRegistration getParent();

    /**
     * Gets the maximum number of times a resource of the type described by this registration
     * can occur under its parent resource (or, for a root resource, the minimum number of times it can
     * occur at all.)
     *
     * @return the minimum number of occurrences
     */
    default int getMaxOccurs() {
        PathAddress pa = getPathAddress();
        return pa.size() == 0 || !pa.getLastElement().isWildcard() ? 1 : Integer.MAX_VALUE;
    }

    /**
     * Gets the minimum number of times a resource of the type described by this registration
     * can occur under its parent resource (or, for a root resource, the number of times it can
     * occur at all.)
     *
     * @return the minimum number of occurrences
     */
    default int getMinOccurs() {
        return getPathAddress().size() == 0 ? 1 : 0;
    }


    default boolean isFeature() {
        return !PathAddress.EMPTY_ADDRESS.equals(getPathAddress());
    }

    default String getFeature() {
        if(PathAddress.EMPTY_ADDRESS.equals(getPathAddress())) {
            if(getProcessType().isServer()) {
                return "server-root";
            }
            return "";
        }
        StringJoiner joiner = new StringJoiner(".");
        final PathAddress pathAddress = getPathAddress();
        final String initialKey = pathAddress.getElement(0).getKey();
        if (getProcessType().isManagedDomain() && !HOST.equals(initialKey) && !PROFILE.equals(initialKey)) {
            joiner.add(DOMAIN);
        }
        for (int i = 0; i < pathAddress.size(); i++) {
            PathElement elt = pathAddress.getElement(i);
            joiner.add(elt.getKey());
            if (!elt.isWildcard() && (i > 0 || !HOST.equals(elt.getKey()))) {
                joiner.add(elt.getValue());
            }
        }
        return joiner.toString();
    }

    /**
     * Gets whether this model node only exists in the runtime and has no representation in the
     * persistent configuration model.
     *
     * @return {@code true} if the model node has no representation in the
     * persistent configuration model; {@code false} otherwise
     *
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    boolean isRuntimeOnly();

    /**
     * Gets whether operations against the resource represented by this registration will be proxied to
     * a remote process.
     *
     * @return {@code true} if this registration represents a remote resource; {@code false} otherwise
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    boolean isRemote();

    /**
     * Gets whether this resource registration is an alias to another resource.
     *
     * @return {@code true} if this registration represents an alias; {@code false} otherwise
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    boolean isAlias();

    /**
     * Gets the alias entry for this registration if it is an alias
     *
     * @return the alias entry if this registration represents an aliased resource; {@code null} otherwise
     * @throws IllegalStateException if {@link #isAlias()} returns {@code false}
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    AliasEntry getAliasEntry();

    /**
     * Get the operation handler at the given address, or {@code null} if none exists.
     *
     * @param address the address, relative to this node
     * @param operationName the operation name
     * @return the operation handler
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    OperationStepHandler getOperationHandler(PathAddress address, String operationName);

    /**
     * Get the operation description at the given address, or {@code null} if none exists.
     *
     * @param address the address, relative to this node
     * @param operationName the operation name
     * @return the operation description
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    DescriptionProvider getOperationDescription(PathAddress address, String operationName);

    /**
     * Get the special characteristic flags for the operation at the given address, or {@code null} if none exist.
     *
     * @param address the address, relative to this node
     * @param operationName the operation name
     * @return the operation entry flags or {@code null}
     *
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    Set<OperationEntry.Flag> getOperationFlags(PathAddress address, String operationName);

    /**
     * Get the entry representing an operation registered with the given name at the given address, or {@code null} if none exists.
     *
     * @param address the address, relative to this node
     * @param operationName the operation name
     * @return the operation entry or {@code null}
     *
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    OperationEntry getOperationEntry(PathAddress address, String operationName);

    /**
     * Get the names of the attributes for a node
     *
     * @param address the address, relative to this node
     * @return the attribute names. If there are none an empty set is returned
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    Set<String> getAttributeNames(PathAddress address);

    /**
     * Get the information about all attributes for a node.
     * @param address the address, relative to this node
     * @return the attribute information, in a map keyed by attribute name. If there are none an empty map is returned
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    Map<String, AttributeAccess> getAttributes(final PathAddress address);

    /**
     * Gets the information on how to read from or write to the given attribute.
     *
     * @param address the address of the resource
     * @param attributeName the name of the attribute
     *
     * @return the handling information, or {@code null} if the attribute or address is unknown
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    AttributeAccess getAttributeAccess(PathAddress address, String attributeName);

    /**
     * Get a map of descriptions of all notifications emitted by the resources at an address.
     *
     * @param address the address
     * @param inherited true to include inherited notifications
     * @return the notifications map
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    Map<String, NotificationEntry> getNotificationDescriptions(PathAddress address, boolean inherited);

    /**
     * Get the names of the types of children for a node
     *
     * @param address the address, relative to this node
     * @return the child type names. If there are none an empty set is returned
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    Set<String> getChildNames(PathAddress address);

    /**
     * Gets the set of direct child address elements under the node at the passed in PathAddress
     *
     * @param address the address we want to find children for
     * @return the set of direct child elements
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    Set<PathElement> getChildAddresses(PathAddress address);

    /**
     * Get the model description at the given address, or {@code null} if none exists.
     *
     * @param address the address, relative to this node
     * @return the model description
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    DescriptionProvider getModelDescription(PathAddress address);

    /**
     * Get a map of descriptions of all operations available at an address.
     *
     * @param address the address
     * @param inherited true to include inherited operations
     * @return the operation map
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    Map<String, OperationEntry> getOperationDescriptions(PathAddress address, boolean inherited);

    /**
     * If there is a proxy controller registered under any part of the registered address it will be returned.
     * E.g. if the address passed in is <code>[a=b,c=d,e=f]</code> and there is a proxy registered under
     * <code>[a=b,c=d]</code> that proxy will be returned.
     *
     * @param address the address to look for a proxy under
     * @return the found proxy controller, or <code>null</code> if there is none
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    ProxyController getProxyController(PathAddress address);

    /**
     * Finds all proxy controllers registered at the passed in address, or at lower levels.
     * <p/>
     * E.g. if the address passed in is <code>a=b</code> and there are proxies registered at
     * <code>[a=b,c=d]</code>, <code>[a=b,e=f]</code> and <code>[g-h]</code>, the proxies for
     * <code>[a=b,c=d]</code> and <code>[a=b,e=f]</code> will be returned.
     *
     * @param address the address to start looking for proxies under
     * @return the found proxy controllers, or an empty set if there are none
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    Set<ProxyController> getProxyControllers(PathAddress address);

    /**
     * Get a sub model registration.
     *
     * @param address the address, relative to this node
     * @return the node registration, <code>null</code> if there is none
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    ImmutableManagementResourceRegistration getSubModel(PathAddress address);

    List<AccessConstraintDefinition> getAccessConstraints();


    /**
     * Return @code true} if a child resource registration was registered using
     * {@link ManagementResourceRegistration#registerSubModel(ResourceDefinition)}, and {@code false} otherwise
     *
     * @return whether this is an ordered child or not
     */
    boolean isOrderedChildResource();

    /**
     * Return the names of the child types registered to be ordered.
     *
     * @return the set of ordered child types, and and empty set if there are none
     */
    Set<String> getOrderedChildTypes();

    /**
     * Returns all capabilities provided by this resource. This will only include capabilities for which
     * this resource controls the registration of the capability. If any children of this resource are involved
     * in providing the capability, the registration for the children must not include the capability in the
     * value they return from this method.
     *
     * @return Set of capabilities if any registered otherwise an empty set
     *
     * @see #getIncorporatingCapabilities()
     */
    Set<RuntimeCapability> getCapabilities();

    /**
     * Returns all capabilities provided by parents of this resource, to which this resource contributes. This will
     * only include capabilities for which this resource <strong>does not</strong> control the registration of the
     * capability. Any capabilities registered by this resource will instead be included in the return value for
     * {@link #getCapabilities()}.
     * <p>
     * Often, this method will return {@code null}, which has a special meaning. A {@code null} value means
     * this resource contributes to any capabilities provided by resources higher in its branch of the resource tree,
     * with the search for such capabilities continuing through ancestor resources until:
     * <ol>
     *     <li>The ancestor has registered a capability; i.e. once a capability is identified, higher levels
     *     are not searched</li>
     *     <li>The ancestor returns a non-null value from this method; i.e. once an ancestor declares an incorporating
     *     capability or that there are no incorporating capabilities, higher levels are not searched</li>
     *     <li>The ancestor is a root resource. Child resources do not contribute to root capabilities unless
     *     they specifically declare they do so</li>
     *     <li>The ancestor has single element address whose key is {@code host}. Child resources do not contribute
     *     to host root capabilities unless they specifically declare they do so</li>
     *     <li>For subsystem resources, the ancestor resource is not provided by the subsystem. Subsystem resources
     *     do not contribute to capabilities provided by the kernel</li>
     * </ol>
     * <p>
     * A non-{@code null} value indicates no search of parent resources for capabilities should be performed, and
     * only those capabilities included in the return set should be considered as incorporating this resource
     * (or none at all if the return set is empty.)
     * <p>
     * An instance of this interface that returns a non-empty set from {@link #getCapabilities()}
     * <strong>must not</strong> return {@code null} from this method. If a resource itself provides a capability but
     * also contributes to a different capability provided by a parent, that relationship must be specifically noted
     * in the return value from this method.
     * <p>Note that providing a capability that is in turn a requirement of a parent resource's capability is not
     * the kind of "contributing" to the parent resource's capability that is being considered here. The relationship
     * between a capability and its requirements is separately tracked by the {@link RuntimeCapability} itself.  A
     * typical "contributing" resource would be one that represents a chunk of configuration directly used by the parent
     * resource's capability.
     *
     * @return set of capabilities, or {@code null} if default resolution of capabilities to which this resource
     *         contributes should be used; an empty set can be used to indicate this resource does not contribute
     *         to capabilities provided by its parent. Will not return {@code null} if {@link #getCapabilities()}
     *         returns a non-empty set.
     *
     * @see #getCapabilities()
     */
    Set<RuntimeCapability> getIncorporatingCapabilities();

    /**
     * Returns all capability reference recorders provided by this resource.
     *
     * @return Set of CapabilityReferenceRecorder if any registered otherwise an empty set.
     */
    Set<CapabilityReferenceRecorder> getRequirements();

    /**
     * Returns additional packages provided by this resource.
     *
     * @return Set of
     * {@link org.jboss.as.controller.registry.RuntimePackageDependency} if any
     * registered otherwise an empty set.
     */
    Set<RuntimePackageDependency> getAdditionalRuntimePackages();
}
