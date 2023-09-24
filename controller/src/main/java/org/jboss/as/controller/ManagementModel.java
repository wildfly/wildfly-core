/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;

/**
* Encapsulates the elements of a management model.
*
* @author Brian Stansberry (c) 2014 Red Hat Inc.
*/
public interface ManagementModel {

    /**
     * Gets the registration of resource, attribute and operation definitions and
     * operation handlers.
     *
     * @return the resource registration. Will not be {@code null}
     */
    ManagementResourceRegistration getRootResourceRegistration();

    /**
     * Gets the root resource of the concrete configuration model.
     *
     * @return the resource. Will not be {@code null}
     */
    Resource getRootResource();

    /**
     * Gets the registry of capabilities and their requirements.
     *
     * @return the registry. Will not be {@code null}
     */
    RuntimeCapabilityRegistry getCapabilityRegistry();

}
