/*
 * Copyright 2018 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.CLASS_NAME;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.MODULE;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.security.auth.jaspi.Flag;

/**
 * Resource definition for the JASPI configurations for the Servlet profile.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class JaspiDefinition {

    static final SimpleAttributeDefinition DESCRIPTION = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DESCRIPTION, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition FLAG = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FLAG, ModelType.STRING, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(ElytronDescriptionConstants.REQUIRED))
            .setAllowedValues(ElytronDescriptionConstants.REQUIRED, ElytronDescriptionConstants.REQUISITE, ElytronDescriptionConstants.SUFFICIENT, ElytronDescriptionConstants.OPTIONAL)
            .setValidator(EnumValidator.create(Flag.class, Flag.values()))
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final PropertiesAttributeDefinition PROPERTIES = new PropertiesAttributeDefinition.Builder(ElytronDescriptionConstants.PROPERTIES, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final ObjectTypeAttributeDefinition SERVER_AUTH_MODULE = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.SERVER_AUTH_MODULE, CLASS_NAME, MODULE, FLAG, PROPERTIES)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setXmlName(ElytronDescriptionConstants.SERVER_AUTH_MODULE)
            .build();

    static final  ObjectListAttributeDefinition SERVER_AUTH_MODULES = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.SERVER_AUTH_MODULES, SERVER_AUTH_MODULE)
            .setRequired(false)
            .setRestartAllServices()
            .setXmlName(ElytronDescriptionConstants.SERVER_AUTH_MODULES)
            .build();

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { DESCRIPTION, SERVER_AUTH_MODULES };

    static final JaspiAddHandler ADD = new JaspiAddHandler();

    static final AbstractRemoveStepHandler REMOVE = new AbstractRemoveStepHandler() {

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            if (context.isResourceServiceRestartAllowed()) {
                System.out.println("Lets remove this thing " + model.toString());
            } else {
                context.reloadRequired();
            }
        }

        @Override
        protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            if (context.isResourceServiceRestartAllowed()) {
                ADD.performRuntime(context, operation, model);
            } else {
                context.revertReloadRequired();
            }
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return ADD.requiresRuntime(context);
        }

    };

    static ResourceDefinition getJaspiServletConfigurationDefinition() {
        return TrivialResourceDefinition.builder()
                .setPathKey(ElytronDescriptionConstants.JASPI_SERVLET_CONFIGURATION)
                .setAttributes(ATTRIBUTES)
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .build();
    }

    static class JaspiAddHandler extends BaseAddHandler {

        JaspiAddHandler() {
            super(ATTRIBUTES);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            System.out.println("Lets add this thing " + model.toString());
        }

    };

}
