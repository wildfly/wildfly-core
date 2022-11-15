/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.host.controller.resources;

import static org.jboss.as.host.controller.logging.HostControllerLogger.ROOT_LOGGER;

import java.util.function.Consumer;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.management.BaseHttpInterfaceResourceDefinition;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.RuntimePackageDependency;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.HostModelUtil;
import org.jboss.as.host.controller.operations.HttpManagementAddHandler;
import org.jboss.as.host.controller.operations.HttpManagementRemoveHandler;
import org.jboss.as.host.controller.operations.LocalHostControllerInfoImpl;
import org.jboss.as.server.mgmt.UndertowHttpManagementService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for the HTTP management interface resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class HttpManagementResourceDefinition extends BaseHttpInterfaceResourceDefinition {

    public static final SimpleAttributeDefinition INTERFACE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.INTERFACE, ModelType.STRING, false)
            // expressions only allowed due to compatibility. allowing this was a mistake
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, true))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_CONFIG)
            .setCapabilityReference("org.wildfly.network.interface", HTTP_MANAGEMENT_RUNTIME_CAPABILITY)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition HTTP_PORT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PORT, ModelType.INT, true)
            .setAllowExpression(true).setValidator(new IntRangeValidator(0, 65535, true, true))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_CONFIG)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition HTTPS_PORT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SECURE_PORT, ModelType.INT, true)
            .setAllowExpression(true).setValidator(new IntRangeValidator(0, 65535, true, true))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_CONFIG)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition SECURE_INTERFACE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SECURE_INTERFACE, ModelType.STRING, true)
            // SECURE_INTERFACE does not allow expressions. INTERFACE only does due to compatibility; otherwise it shouldn't
            .setAllowExpression(false)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, false))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_CONFIG)
            .setCapabilityReference("org.wildfly.network.interface", HTTP_MANAGEMENT_RUNTIME_CAPABILITY)
            .setRestartAllServices()
            .build();

    public static final AttributeDefinition[] ATTRIBUTE_DEFINITIONS = combine(COMMON_ATTRIBUTES, INTERFACE, HTTP_PORT, HTTPS_PORT, SECURE_INTERFACE);

    private HttpManagementResourceDefinition(OperationStepHandler add, OperationStepHandler remove) {
        super(new Parameters(RESOURCE_PATH, HostModelUtil.getResourceDescriptionResolver("core", "management", "http-interface"))
            .setAddHandler(add)
            .setRemoveHandler(remove)
            .setAddRestartLevel(OperationEntry.Flag.RESTART_NONE)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilities(UndertowHttpManagementService.EXTENSIBLE_HTTP_MANAGEMENT_CAPABILITY)
            .setAdditionalPackages(RuntimePackageDependency.required("org.jboss.as.domain-http-error-context"))
        );
    }

    public static HttpManagementResourceDefinition create(final LocalHostControllerInfoImpl hostControllerInfo,
            final HostControllerEnvironment environment) {
        HttpManagementAddHandler add = new HttpManagementAddHandler(hostControllerInfo, environment);
        HttpManagementRemoveHandler remove = HttpManagementRemoveHandler.INSTANCE;

        return new HttpManagementResourceDefinition(add, remove);
    }

    @Override
    protected AttributeDefinition[] getAttributeDefinitions() {
        return ATTRIBUTE_DEFINITIONS;
    }

    @Override
    protected Consumer<OperationContext> getValidationConsumer() {
        return HttpManagementResourceDefinition::addAttributeValidator;
    }

    public static void addAttributeValidator(OperationContext context) {
        context.addStep(new OperationStepHandler() {

            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
                ModelNode httpsPort = HTTPS_PORT.resolveModelAttribute(context, model);
                ModelNode secureInterface = SECURE_INTERFACE.resolveModelAttribute(context, model);
                if (httpsPort.isDefined() || secureInterface.isDefined()) {
                    if (SSL_CONTEXT.resolveModelAttribute(context, model).isDefined()) {
                        return;
                    }
                    throw ROOT_LOGGER.attributeRequiresSSLContext(httpsPort.isDefined() ? HTTPS_PORT.getName() : SECURE_INTERFACE.getName());
                }
            }
        }, Stage.MODEL);
    }

}
