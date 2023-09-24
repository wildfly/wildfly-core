/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.operations;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONTROLLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOTE;
import static org.jboss.dmr.ModelType.STRING;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.as.host.controller.model.host.AdminOnlyDomainConfigPolicy;
import org.jboss.as.remoting.Protocol;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.security.auth.client.AuthenticationContext;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class RemoteDomainControllerAddHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "write-remote-domain-controller";

    public static final SimpleAttributeDefinition PORT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PORT, ModelType.INT)
            .setRequired(false)
            .setAllowExpression(true)
            .setValidator(new IntRangeValidator(1, 65535, true, true))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setRequires(ModelDescriptionConstants.HOST)
            .build();

    public static final SimpleAttributeDefinition HOST = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.HOST, ModelType.STRING)
            .setRequired(false)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setRequires(ModelDescriptionConstants.PORT)
            .build();

    public static final SimpleAttributeDefinition PROTOCOL = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PROTOCOL, ModelType.STRING)
            .setRequired(false)
            .setAllowExpression(true)
            .setValidator(EnumValidator.create(Protocol.class))
            .setDefaultValue(Protocol.REMOTE.toModelNode())
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setRequires(ModelDescriptionConstants.HOST, ModelDescriptionConstants.PORT)
            .build();

    public static final SimpleAttributeDefinition AUTHENTICATION_CONTEXT =
            new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.AUTHENTICATION_CONTEXT,  ModelType.STRING,  true)
                    .setCapabilityReference("org.wildfly.security.authentication-context", "org.wildfly.host.controller")
                    .setAlternatives(ModelDescriptionConstants.SECURITY_REALM, ModelDescriptionConstants.USERNAME)
                    .build();

    public static final SimpleAttributeDefinition USERNAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.USERNAME, STRING, true)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .setFlags(AttributeAccess.Flag.RESTART_JVM)
            .setDeprecated(ModelVersion.create(5))
            .build();

    public static final SimpleAttributeDefinition IGNORE_UNUSED_CONFIG = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.IGNORE_UNUSED_CONFIG, ModelType.BOOLEAN, true)
            .setRequired(false)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_JVM)
            .build();

    public static final SimpleAttributeDefinition ADMIN_ONLY_POLICY = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ADMIN_ONLY_POLICY, ModelType.STRING, true)
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_JVM)
            .setValidator(EnumValidator.create(AdminOnlyDomainConfigPolicy.class))
            .setAllowedValues(AdminOnlyDomainConfigPolicy.ALLOW_NO_CONFIG.toString(), AdminOnlyDomainConfigPolicy.FETCH_FROM_DOMAIN_CONTROLLER.toString(), AdminOnlyDomainConfigPolicy.REQUIRE_LOCAL_CONFIG.toString())
            .setDefaultValue(new ModelNode(AdminOnlyDomainConfigPolicy.ALLOW_NO_CONFIG.toString()))
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, HostResolver.getResolver("host"))
            .setParameters(PROTOCOL, PORT, HOST, AUTHENTICATION_CONTEXT, USERNAME, IGNORE_UNUSED_CONFIG, ADMIN_ONLY_POLICY)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.DOMAIN_CONTROLLER)
            .setDeprecated(ModelVersion.create(5, 0, 0))
            .build();

    private final LocalHostControllerInfoImpl hostControllerInfo;
    private final DomainControllerWriteAttributeHandler writeAttributeHandler;

    public RemoteDomainControllerAddHandler(final LocalHostControllerInfoImpl hostControllerInfo, DomainControllerWriteAttributeHandler writeAttributeHandler) {
        this.hostControllerInfo = hostControllerInfo;
        this.writeAttributeHandler = writeAttributeHandler;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        final ModelNode model = resource.getModel();
        ModelNode dc = model.get(DOMAIN_CONTROLLER);
        ModelNode remoteDC = dc.get(REMOTE);

        PROTOCOL.validateAndSet(operation, remoteDC);
        PORT.validateAndSet(operation, remoteDC);
        HOST.validateAndSet(operation, remoteDC);
        USERNAME.validateAndSet(operation, remoteDC);
        IGNORE_UNUSED_CONFIG.validateAndSet(operation, remoteDC);
        ADMIN_ONLY_POLICY.validateAndSet(operation, remoteDC);

        if (remoteDC.hasDefined(ADMIN_ONLY_POLICY.getName())) {
            ModelNode current = ADMIN_ONLY_POLICY.resolveModelAttribute(context, remoteDC);
            if (current.asString().equals(AdminOnlyDomainConfigPolicy.LEGACY_FETCH_FROM_DOMAIN_CONTROLLER.toString())) {
                ControllerLogger.ROOT_LOGGER.adminOnlyPolicyDeprecatedValue();
                remoteDC.get(ADMIN_ONLY_POLICY.getName()).set(AdminOnlyDomainConfigPolicy.FETCH_FROM_DOMAIN_CONTROLLER.toString());
            }
        }

        if (operation.has(AUTHENTICATION_CONTEXT.getName())) {
            AUTHENTICATION_CONTEXT.validateAndSet(operation, remoteDC);
            final String authenticationContext = AUTHENTICATION_CONTEXT.resolveModelAttribute(context, operation).asString();
            context.addStep(new OperationStepHandler() {

                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    hostControllerInfo.setAuthenticationContext(context.getCapabilityServiceName(
                            "org.wildfly.security.authentication-context", authenticationContext, AuthenticationContext.class));
                }
            }, Stage.RUNTIME);
        } else {
            remoteDC.get(AUTHENTICATION_CONTEXT.getName()).clear();
        }

        if (dc.has(LOCAL)) {
            dc.remove(LOCAL);
        }

        if (context.isBooting()) {
            writeAttributeHandler.initializeRemoteDomain(context, remoteDC);
        } else {
            context.reloadRequired();
        }

        context.completeStep(new OperationContext.RollbackHandler() {
            @Override
            public void handleRollback(OperationContext context, ModelNode operation) {
                if (!context.isBooting()) {
                    context.revertReloadRequired();
                }
            }
        });
    }
}
