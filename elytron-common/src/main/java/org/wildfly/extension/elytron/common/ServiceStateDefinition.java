/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron.common;

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
public class ServiceStateDefinition {

    public static final SimpleAttributeDefinition STATE = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.STATE, ModelType.STRING)
        .setStorageRuntime()
        .setAllowedValues(allowedValues(State.values()))
        .build();

    /**
     * Populate the supplied response {@link ModelNode} with information about the supplied {@link ServiceController}
     *
     * @param response the response to populate.
     * @param serviceController the {@link ServiceController} to use when populating the response.
     */
    public static void populateResponse(final ModelNode response, final ServiceController<?> serviceController) {
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
