/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability.registry;

import java.util.Map;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;

/**
 * Registry of {@link org.jboss.as.controller.capability.RuntimeCapability capabilities} available in the runtime.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public interface RuntimeCapabilityRegistry extends ImmutableCapabilityRegistry {

    enum RuntimeStatus {
        /**
         * Runtime services are functioning normally as per their persistent configuration
         * and the process' current {@linkplain org.jboss.as.controller.RunningMode running mode}.
         * Note that this may mean no runtime services if that is normal for the process type and running
         * mode. */
        NORMAL,
        /**
         * The process needs to be reloaded to bring runtime services for a capability in sync with
         * their persistent configuration.
         */
        RELOAD_REQUIRED,
        /**
         * The process needs to be restarted to bring runtime services for a capability in sync with
         * their persistent configuration.
         */
        RESTART_REQUIRED;
    }

    /**
     * Registers a capability with the system. Any
     * {@link org.jboss.as.controller.capability.Capability#getRequirements() requirements}
     * associated with the capability will be recorded as requirements.
     *
     * @param capability the capability. Cannot be {@code null}
     */
    void registerCapability(RuntimeCapabilityRegistration capability);

    /**
     * Registers an additional requirement a capability has beyond what it was aware of when {@code capability}
     * was passed to {@link #registerCapability(RuntimeCapabilityRegistration)}. Used for cases
     * where a capability optionally depends on another capability, and whether or not that requirement is needed is
     * not known when the capability is first registered.
     *
     * @param requirement the requirement
     * @throws java.lang.IllegalArgumentException if no matching capability is currently
     *                                            {@link #registerCapability(RuntimeCapabilityRegistration) registered} for either {@code required} or {@code dependent}
     */
    void registerAdditionalCapabilityRequirement(RuntimeRequirementRegistration requirement);

    /**
     * Remove a previously registered requirement for a capability.
     *
     * @param requirement the requirement. Cannot be {@code null}
     * @see #registerAdditionalCapabilityRequirement(org.jboss.as.controller.capability.registry.RuntimeRequirementRegistration)
     */
    void removeCapabilityRequirement(RuntimeRequirementRegistration requirement);

    /**
     * Remove a previously registered capability if all registration points for it have been removed.
     *
     * @throws java.lang.IllegalStateException if no capability with the given name is available in the given context
     * @throws java.lang.IllegalArgumentException if the capability does not provide a runtime API
     * @throws java.lang.ClassCastException if the runtime API exposed by the capability cannot be cast to type {code T}
     * @param capabilityName    the name of the capability. Cannot be {@code null}
     * @param scope           the scope in which the capability is registered. Cannot be {@code null}
     * @param registrationPoint the specific registration point that is being removed
     * @return the capability that was removed, or {@code null} if no matching capability was registered or other
     * registration points for the capability still exist
     */
    RuntimeCapabilityRegistration removeCapability(String capabilityName, CapabilityScope scope, PathAddress registrationPoint);

    /**
     * Gets the status of any capabilities associated with the given resource address.
     *
     * @param address the address. Cannot be {@code null}
     * @param resourceRegistration the registration for the resource at {@code address}. Cannot be {@code null}
     * @return a map of capability ids to their runtime status. Will not return {@code null} but may return
     *         an empty map if no capabilities are associated with the address.
     */
    Map<CapabilityId, RuntimeStatus> getRuntimeStatus(PathAddress address, ImmutableManagementResourceRegistration resourceRegistration);

    /**
     * Notification that any capabilities associated with the given address require reload in order to bring their
     * runtime services into sync with their persistent configuration.
     *
     * @param address the address. Cannot be {@code null}
     * @param resourceRegistration the registration for the resource at {@code address}. Cannot be {@code null}
     */
    void capabilityReloadRequired(PathAddress address, ImmutableManagementResourceRegistration resourceRegistration);

    /**
     * Notification that any capabilities associated with the given address require restart in order to bring their
     * runtime services into sync with their persistent configuration.
     *
     * @param address the address. Cannot be {@code null}
     * @param resourceRegistration the registration for the resource at {@code address}. Cannot be {@code null}
     */
    void capabilityRestartRequired(PathAddress address, ImmutableManagementResourceRegistration resourceRegistration);
}
