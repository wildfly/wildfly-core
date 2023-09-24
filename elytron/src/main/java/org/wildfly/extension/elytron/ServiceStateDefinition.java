/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;

/**
 * Class to contain the attribute definition for the runtime representation of a services state.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class ServiceStateDefinition {

    static final SimpleAttributeDefinition STATE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.STATE, ModelType.STRING)
        .setStorageRuntime()
        .setAllowedValues(allowedValues(State.values()))
        .build();

    /**
     * Populate the supplied response {@link ModelNode} with information about the supplied {@link ServiceController}
     *
     * @param response the response to populate.
     * @param serviceController the {@link ServiceController} to use when populating the response.
     */
    static void populateResponse(final ModelNode response, final ServiceController<?> serviceController) {
        response.set(serviceController.getState().toString());
    }

    private static String[] allowedValues(Object[] allowedValues) {
        String[] allowedValuesResponse = new String[allowedValues.length];
        for (int i = 0; i < allowedValues.length; i++) {
            allowedValuesResponse[i] = String.valueOf(allowedValues[i]);
        }

        return allowedValuesResponse;
    }
}
