/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability.registry;

/**
 * Registration information for requirement for a {@link org.jboss.as.controller.capability.RuntimeCapability}. As a runtime
 * requirement is associated with an actual management model, the registration exposes the {@link #getOldestRegistrationPoint() point in the model}
 * that triggered the requirement.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class RuntimeRequirementRegistration extends RequirementRegistration {

    private final boolean runtimeOnly;

    /**
     * Creates a new requirement registration.
     *
     * @param requiredName      the name of the required capability
     * @param dependentName     the name of the capability that requires {@code requiredName}
     * @param dependentScope    scope in which the dependent capability exists
     * @param registrationPoint point in the configuration model that triggered the requirement
     */
    public RuntimeRequirementRegistration(String requiredName, String dependentName, CapabilityScope dependentScope,
                                          RegistrationPoint registrationPoint) {
        this(requiredName, dependentName, dependentScope, registrationPoint, false);
    }

    /**
     * Creates a new requirement registration.
     *
     * @param requiredName      the name of the required capability
     * @param dependentName     the name of the capability that requires {@code requiredName}
     * @param dependentScope    scope in which the dependent capability exists
     * @param registrationPoint point in the configuration model that triggered the requirement
     * @param runtimeOnly       {@code true} if and only if the requirement is optional and runtime-only
     *                          (i.e. not mandated by the persistent configuration), and
     *                          therefore should not result in a configuration validation failure
     *                          if it is not satisfied
     */
    public RuntimeRequirementRegistration(String requiredName, String dependentName, CapabilityScope dependentScope,
                                          RegistrationPoint registrationPoint, boolean runtimeOnly) {
        super(requiredName, dependentName, dependentScope, registrationPoint);
        this.runtimeOnly = runtimeOnly;
    }

    /**
     * Copy constructor.
     *
     * @param toCopy the registration to copy.
     */
    public RuntimeRequirementRegistration(RuntimeRequirementRegistration toCopy) {
        super(toCopy);
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

}
