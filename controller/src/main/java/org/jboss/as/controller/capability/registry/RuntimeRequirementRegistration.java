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

package org.jboss.as.controller.capability.registry;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.PathAddress;

/**
 * Registration information for requirement for a {@link org.jboss.as.controller.capability.RuntimeCapability}. As a runtime
 * requirement is associated with an actual management model, the registration exposes the {@link #getOldestRegistrationPoint() point in the model}
 * that triggered the requirement.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class RuntimeRequirementRegistration extends RequirementRegistration {

    private final Map<PathAddress, RegistrationPoint> registrationPoints = new LinkedHashMap<>();
    private final boolean runtimeOnly;

    /**
     * Creates a new requirement registration.
     *
     * @param requiredName the name of the required capability
     * @param dependentName the name of the capability that requires {@code requiredName}
     * @param dependentContext context in which the dependent capability exists
     * @param registrationPoint point in the configuration model that triggered the requirement
     */
    public RuntimeRequirementRegistration(String requiredName, String dependentName, CapabilityContext dependentContext,
                                          RegistrationPoint registrationPoint) {
        this(requiredName, dependentName, dependentContext, registrationPoint, false);
    }

    /**
     * Creates a new requirement registration.
     *
     * @param requiredName the name of the required capability
     * @param dependentName the name of the capability that requires {@code requiredName}
     * @param dependentContext context in which the dependent capability exists
     * @param registrationPoint point in the configuration model that triggered the requirement
     * @param runtimeOnly {@code true} if and only if the requirement is optional and runtime-only
     *                              (i.e. not mandated by the persistent configuration), and
     *                              therefore should not result in a configuration validation failure
     *                              if it is not satisfied
     */
    public RuntimeRequirementRegistration(String requiredName, String dependentName, CapabilityContext dependentContext,
                                          RegistrationPoint registrationPoint, boolean runtimeOnly) {
        super(requiredName, dependentName, dependentContext);
        this.registrationPoints.put(registrationPoint.getAddress(), registrationPoint);
        this.runtimeOnly = runtimeOnly;
    }

    /**
     * Copy constructor.
     *
     * @param toCopy the registration to copy.
     */
    public RuntimeRequirementRegistration(RuntimeRequirementRegistration toCopy) {
        super(toCopy.getRequiredName(), toCopy.getDependentId());
        this.registrationPoints.putAll(toCopy.registrationPoints);
        this.runtimeOnly = toCopy.runtimeOnly;
    }

    /**
     * Gets whether the requirement is optional and runtime-only (i.e. not mandated by the persistent configuration),
     * and therefore should not result in a configuration validation failure if it is not satisfied.
     *
     * @return {@code true} if the requirement is optional and runtime-only
     */
    public boolean isRuntimeOnly() {
        return runtimeOnly;
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
        return Collections.unmodifiableSet(new HashSet<>(registrationPoints.values()));
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
}
