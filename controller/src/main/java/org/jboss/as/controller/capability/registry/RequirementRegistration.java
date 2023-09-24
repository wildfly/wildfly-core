/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.PathAddress;

/**
 * Encapsulates the registration information for a requirement for a capability.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class RequirementRegistration {

    private final Map<PathAddress, List<RegistrationPoint>> registrationPoints = new LinkedHashMap<>();
    private final String requiredName;
    private final CapabilityId dependentId;

    public RequirementRegistration(String requiredName, String dependentName, CapabilityScope dependentContext) {
        this(requiredName, new CapabilityId(dependentName, dependentContext));
    }

    protected RequirementRegistration(String requiredName, CapabilityId dependentId) {
        this.requiredName = requiredName;
        this.dependentId = dependentId;
    }

    /**
     * Creates a new requirement registration.
     *
     * @param requiredName      the name of the required capability
     * @param dependentName     the name of the capability that requires {@code requiredName}
     * @param dependentContext  context in which the dependent capability exists
     * @param registrationPoint point in the configuration model that triggered the requirement
     */
    protected RequirementRegistration(String requiredName, String dependentName, CapabilityScope dependentContext,
                                      RegistrationPoint registrationPoint) {
        this(requiredName, dependentName, dependentContext);
        putRegistrationPoint(registrationPoint);
    }

    /**
     * Copy constructor.
     *
     * @param toCopy the registration to copy.
     */
    public RequirementRegistration(RuntimeRequirementRegistration toCopy) {
        this(toCopy.getRequiredName(), toCopy.getDependentId());
        for (Map.Entry<PathAddress, List<RegistrationPoint>> entry : ((RequirementRegistration) toCopy).registrationPoints.entrySet()) {
            this.registrationPoints.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    public String getRequiredName() {
        return requiredName;
    }

    public String getDependentName() {
        return dependentId.getName();
    }

    public CapabilityScope getDependentContext() {
        return dependentId.getScope();
    }

    public CapabilityId getDependentId() {
        return dependentId;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || !getClass().isAssignableFrom(o.getClass())) { return false; }

        RequirementRegistration that = (RequirementRegistration) o;

        return dependentId.equals(that.dependentId)
                && requiredName.equals(that.requiredName);

    }

    @Override
    public final int hashCode() {
        int result = requiredName.hashCode();
        result = 31 * result + dependentId.hashCode();
        return result;
    }

    /**
     * Gets the registration point that been associated with the registration for the longest period.
     *
     * @return the initial registration point, or {@code null} if there are no longer any registration points
     */
    public synchronized RegistrationPoint getOldestRegistrationPoint() {
        return registrationPoints.size() == 0 ? null : registrationPoints.values().iterator().next().get(0);
    }

    /**
     * Get all registration points associated with this registration.
     *
     * @return all registration points. Will not be {@code null} but may be empty
     */
    public synchronized Set<RegistrationPoint> getRegistrationPoints() {
        Set<RegistrationPoint> result = new HashSet<>();
        for (List<RegistrationPoint> registrationPoints : registrationPoints.values()) {
            result.addAll(registrationPoints);
        }
        return Collections.unmodifiableSet(result);
    }

    public synchronized boolean addRegistrationPoint(RegistrationPoint toAdd) {
        PathAddress addedAddress = toAdd.getAddress();
        if (registrationPoints.containsKey(addedAddress)) {
            List<RegistrationPoint> registrationPoints = this.registrationPoints.get(addedAddress);
            if (registrationPoints.contains(toAdd)) {
                return false;
            }
        }
        putRegistrationPoint(toAdd);

        return true;
    }

    public synchronized boolean removeRegistrationPoint(RegistrationPoint toRemove) {
        PathAddress removedAddress = toRemove.getAddress();
        if (!registrationPoints.containsKey(removedAddress)) {
            return false;
        }
        List<RegistrationPoint> registrationPoints = this.registrationPoints.get(removedAddress);
        if (toRemove.getAttribute() == null){
            registrationPoints.clear();
        } else {
            registrationPoints.remove(toRemove);
        }
        if (registrationPoints.isEmpty()) {
            this.registrationPoints.remove(removedAddress);
        }
        return true;
    }

    public synchronized int getRegistrationPointCount() {
        int size = 0;
        for (List<RegistrationPoint> value : registrationPoints.values()){
            size=+value.size();
        }
        return size;
    }

    private void putRegistrationPoint(RegistrationPoint registrationPoint) {
        PathAddress address = registrationPoint.getAddress();
        if (this.registrationPoints.containsKey(address)) {
            this.registrationPoints.get(address).add(registrationPoint);
        } else {
            List<RegistrationPoint> registrationPoints = new ArrayList<>();
            registrationPoints.add(registrationPoint);
            this.registrationPoints.put(address, registrationPoints);
        }
    }
}
