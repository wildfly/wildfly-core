/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability.registry;

import org.jboss.as.controller.capability.RuntimeCapability;

/**
 * Registration information for a {@link org.jboss.as.controller.capability.RuntimeCapability}. As a runtime capability is
 * associated with an actual management model, the registration exposes the {@link #getOldestRegistrationPoint() point in the model}
 * that triggered the registration.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class RuntimeCapabilityRegistration extends CapabilityRegistration<RuntimeCapability<?>> {

    public RuntimeCapabilityRegistration(RuntimeCapability<?> capability, CapabilityScope context, RegistrationPoint registrationPoint) {
        super(capability, context, registrationPoint);
    }

    /**
     * Copy constructor.
     *
     * @param toCopy the registration to copy. Cannot be {@code null}
     */
    public RuntimeCapabilityRegistration(RuntimeCapabilityRegistration toCopy) {
        super(toCopy);
    }
}
