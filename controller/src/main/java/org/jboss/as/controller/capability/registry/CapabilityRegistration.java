/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.Capability;

/**
 * Encapsulates the registration information for a {@link org.jboss.as.controller.capability.Capability capability}.
 *
 * @param <C> the specific type of capability associated with this registration
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class CapabilityRegistration<C extends Capability> implements Comparable<CapabilityRegistration<C>> {

    private final Map<PathAddress, RegistrationPoint> registrationPoints = new LinkedHashMap<>();
    private final C capability;
    private final CapabilityId id;

    public CapabilityRegistration(C capability, CapabilityScope scope) {
        this.capability = capability;
        this.id = new CapabilityId(capability.getName(), scope);
    }

    public CapabilityRegistration(C capability, CapabilityScope scope, RegistrationPoint registrationPoint) {
        this(capability, scope);
        this.registrationPoints.put(registrationPoint.getAddress(), registrationPoint);
    }

    /**
     * Copy constructor.
     *
     * @param toCopy the registration to copy. Cannot be {@code null}
     */
    public CapabilityRegistration(CapabilityRegistration<C> toCopy) {
        this(toCopy.getCapability(), toCopy.getCapabilityScope());
        this.registrationPoints.putAll(toCopy.registrationPoints);
    }

    /**
     * Gets the capability.
     * @return the capability. Will not return {@code null}
     */
    public C getCapability() {
        return capability;
    }

    /**
     * Gets the scope in which the capability is registered.
     *
     * @return the capability scope. Will not return {@code null}
     */
    public CapabilityScope getCapabilityScope() {
        return id.getScope();
    }

    public String getCapabilityName() {
        return id.getName();
    }

    public CapabilityId getCapabilityId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CapabilityRegistration that = (CapabilityRegistration) o;

        return id.equals(that.id);

    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Gets the registration point that been associated with the registration for the longest period.
     * @return the initial registration point, or {@code null} if there are no longer any registration points
     */
    public synchronized RegistrationPoint getOldestRegistrationPoint() {
        return registrationPoints.size() == 0 ? null : registrationPoints.values().iterator().next();
    }

    /**
     * Get all registration points associated with this registration.
     *
     * @return all registration points. Will not be {@code null} but may be empty
     */
    public synchronized Set<RegistrationPoint> getRegistrationPoints() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(registrationPoints.values()));
    }

    public synchronized boolean addRegistrationPoint(RegistrationPoint toAdd) {
        PathAddress addedAddress = toAdd.getAddress();
        if (registrationPoints.containsKey(addedAddress)) {
            return false;
        }
        registrationPoints.put(addedAddress, toAdd);
        return true;
    }

    public synchronized boolean removeRegistrationPoint(RegistrationPoint toAdd) {
        PathAddress addedAddress = toAdd.getAddress();
        if (!registrationPoints.containsKey(addedAddress)) {
            return false;
        }
        registrationPoints.remove(addedAddress);
        return true;
    }

    public synchronized int getRegistrationPointCount() {
        return registrationPoints.size();
    }

    @Override
    public int compareTo(CapabilityRegistration o) {
        return id.compareTo(o.id);
    }
}
