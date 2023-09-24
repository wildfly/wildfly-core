/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.deployments.resources;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.server.deployment.Services;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.PropertyConfigurable;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class LoggingConfigurationReadStepHandler implements OperationStepHandler {

    @Override
    public void execute(final OperationContext context, final ModelNode operation) {
        LogContextConfiguration configuration = null;
        // Lookup the service
        final ServiceController<?> controller = context.getServiceRegistry(false).getService(getServiceName(context));
        if (controller != null) {
            configuration = (LogContextConfiguration) controller.getValue();
        }
        // Some deployments may not have a logging configuration associated, e.g. log4j configured deployments
        if (configuration != null) {
            // Attempt to resolve the resource to ensure it exists before continuing, this can be removed when
            // WFCORE-1800 is resolved
            context.readResource(PathAddress.EMPTY_ADDRESS, false);
            final String name = context.getCurrentAddressValue();
            final ModelNode result = context.getResult();
            updateModel(configuration, name, result);
        }
    }

    /**
     * Update the model for a resource.
     *
     * @param logContextConfiguration the configuration to use
     * @param name                    the name of the resource
     * @param model                   the model to update
     */
    protected abstract void updateModel(LogContextConfiguration logContextConfiguration, String name, ModelNode model);

    /**
     * Adds properties to the model in key/value pairs.
     *
     * @param configuration the configuration to get the properties from
     * @param model         the model to update
     */
    static void addProperties(final PropertyConfigurable configuration, final ModelNode model) {
        for (String name : configuration.getPropertyNames()) {
            setModelValue(model.get(name), configuration.getPropertyValueString(name));
        }
    }

    /**
     * Sets the value of the model if the value is not {@code null}.
     *
     * @param model the model to update
     * @param value the value for the model
     */
    static void setModelValue(final ModelNode model, final String value) {
        if (value != null) {
            model.set(value);
        }
    }

    /**
     * Sets the value of the model if the value is not {@code null}.
     *
     * @param model the model to update
     * @param value the value for the model
     */
    static void setModelValue(final ModelNode model, final Boolean value) {
        if (value != null) {
            model.set(value);
        }
    }

    private static ServiceName getServiceName(final OperationContext context) {
        String deploymentName = null;
        String subdeploymentName = null;
        final PathAddress address = context.getCurrentAddress();
        for (PathElement element : address) {
            if (ModelDescriptionConstants.DEPLOYMENT.equals(element.getKey())) {
                deploymentName = getRuntimeName(context, element);
                //deploymentName = element.getValue();
            } else if (ModelDescriptionConstants.SUBDEPLOYMENT.endsWith(element.getKey())) {
                subdeploymentName = element.getValue();
            }
        }
        if (deploymentName == null) {
            throw LoggingLogger.ROOT_LOGGER.deploymentNameNotFound(address);
        }
        final ServiceName result;
        if (subdeploymentName == null) {
            result = Services.deploymentUnitName(deploymentName);
        } else {
            result = Services.deploymentUnitName(deploymentName, subdeploymentName);
        }
        return result.append("logging", "configuration");
    }

    private static String getRuntimeName(final OperationContext context, final PathElement element) {
        final ModelNode deploymentModel = context.readResourceFromRoot(PathAddress.pathAddress(element), false).getModel();
        if (!deploymentModel.hasDefined(ModelDescriptionConstants.RUNTIME_NAME)) {
            throw LoggingLogger.ROOT_LOGGER.deploymentNameNotFound(context.getCurrentAddress());
        }
        return deploymentModel.get(ModelDescriptionConstants.RUNTIME_NAME).asString();
    }
}
