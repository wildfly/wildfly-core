/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Configures a service for a given resource.
 * @author Paul Ferraro
 */
public interface ResourceServiceConfigurator {

    /**
     * Configures a service using the specified operation context and model.
     * @param context an operation context, used to resolve capabilities and expressions
     * @param model the resource model
     * @return a service installer
     * @throws OperationFailedException if there was a failure reading the model or resolving expressions/capabilities
     */
    ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException;
}
