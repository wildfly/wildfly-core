/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.CLASS_NAME;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.MODULE;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import jakarta.security.auth.message.config.AuthConfigFactory;
import jakarta.security.auth.message.module.ServerAuthModule;

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
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.security.auth.jaspi.Flag;
import org.wildfly.security.auth.jaspi.JaspiConfigurationBuilder;



/**
 * Resource definition for the JASPI configurations for the Servlet profile.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class JaspiDefinition {

    private static final Map<String, String> REGISTRATION_MAP = new ConcurrentHashMap<>();

    static final SimpleAttributeDefinition LAYER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.LAYER, ModelType.STRING, true)
            .setDefaultValue(new ModelNode("*"))
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition APPLICATION_CONTEXT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.APPLICATION_CONTEXT, ModelType.STRING, true)
            .setDefaultValue(new ModelNode("*"))
            .setAllowExpression(true)
            .setMinSize(1)
            .setRestartAllServices()
            .build();

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

    static final PropertiesAttributeDefinition OPTIONS = new PropertiesAttributeDefinition.Builder(ElytronDescriptionConstants.OPTIONS, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final ObjectTypeAttributeDefinition SERVER_AUTH_MODULE = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.SERVER_AUTH_MODULE, CLASS_NAME, MODULE, FLAG, OPTIONS)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final  ObjectListAttributeDefinition SERVER_AUTH_MODULES = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.SERVER_AUTH_MODULES, SERVER_AUTH_MODULE)
            .setRequired(false)
            .setRestartAllServices()
            .build();

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] { LAYER, APPLICATION_CONTEXT, DESCRIPTION, SERVER_AUTH_MODULES };

    static final JaspiAddHandler ADD = new JaspiAddHandler();

    static final AbstractRemoveStepHandler REMOVE = new AbstractRemoveStepHandler() {

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            if (context.isResourceServiceRestartAllowed()) {
                removeRegistration(context);
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
                .setPathKey(ElytronDescriptionConstants.JASPI_CONFIGURATION)
                .setAttributes(ATTRIBUTES)
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .build();
    }

    private static void removeRegistration(final OperationContext context) {
        final String registrationId = REGISTRATION_MAP.remove(context.getCurrentAddressValue());
        if (registrationId != null) {
            AuthConfigFactory authConfigFactory = AuthConfigFactory.getFactory();
            authConfigFactory.removeRegistration(registrationId);
        }
    }

    private static Supplier<ServerAuthModule> createServerAuthModuleSupplier(final String className, final String module) {
        return new Supplier<ServerAuthModule>() {

            @Override
            public ServerAuthModule get() {
                try {
                ClassLoader classLoader = ClassLoadingAttributeDefinitions.resolveClassLoader(module);
                Object sam =  classLoader.loadClass(className).newInstance();
                return ServerAuthModule.class.cast(sam);
                } catch (Exception e) {
                  throw   ROOT_LOGGER.failedToCreateServerAuthModule(className, module, e);
                }
            }
        };
    }

    static class JaspiAddHandler extends BaseAddHandler {

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            final String layer = LAYER.resolveModelAttribute(context, model).asString();
            final String applicationContext = APPLICATION_CONTEXT.resolveModelAttribute(context, model).asString();
            final String description = DESCRIPTION.resolveModelAttribute(context, model).asStringOrNull();

            final String addressValue = context.getCurrentAddressValue();
            final JaspiConfigurationBuilder builder = JaspiConfigurationBuilder.builder("*".equals(layer) ? null : layer,
                    "*".equals(applicationContext) ? null : applicationContext)
                    .setDescription(description);

            final List<ModelNode> serverAuthModules = SERVER_AUTH_MODULES.resolveModelAttribute(context, model).asList();
            for (ModelNode serverAuthModule : serverAuthModules) {
                final String className = CLASS_NAME.resolveModelAttribute(context, serverAuthModule).asString();
                final String module = MODULE.resolveModelAttribute(context, serverAuthModule).asStringOrNull();
                final Flag flag = Flag.valueOf(FLAG.resolveModelAttribute(context, serverAuthModule).asString());
                final Map<String, String> options = OPTIONS.unwrap(context, serverAuthModule);

                builder.addAuthModuleFactory(createServerAuthModuleSupplier(className, module), flag, options);
            }

            final String registrationId = builder.register();
            REGISTRATION_MAP.put(addressValue, registrationId);
        }

        @Override
        protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
            removeRegistration(context);
        }

    }



}
