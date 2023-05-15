/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service;

import org.jboss.as.controller.OperationContext;
import org.jboss.msc.service.ServiceController;

/**
 * Installs a service into the target associated with an {@link OperationContext}.
 * @author Paul Ferraro
 */
public interface ResourceServiceInstaller {
    /**
     * Installs a service into the target associated with the specified operation context.
     * @param context an operation context
     * @return the controller of the installed service
     */
    ServiceController<?> install(OperationContext context);
}
