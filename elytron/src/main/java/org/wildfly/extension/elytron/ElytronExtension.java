/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.wildfly.security.auth.client.AuthenticationContext;

/**
 * Main entry point for initialising the WildFly Elytron subsystem.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ElytronExtension implements Extension {

    /**
     * The name space used for the {@code subsystem} element
     */
    public static final String NAMESPACE = "urn:wildfly:elytron:1.0";

    /**
     * The name of our subsystem within the model.
     */
    public static final String SUBSYSTEM_NAME = "elytron";

    /**
     * The attachment key that is used for associating the authentication context with a deployment context.
     */
    public static final AttachmentKey<AuthenticationContext> AUTHENTICATION_CONTEXT_KEY = AttachmentKey.create(AuthenticationContext.class);

    static final ModelVersion ELYTRON_1_0_0 = ModelVersion.create(1);

    private static final ModelVersion ELYTRON_CURRENT = ELYTRON_1_0_0;

    static final String ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    /**
     * The parser used for parsing our subsystem
     */
    private final ElytronSubsystemParser parser = new ElytronSubsystemParser();

    protected static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME);
    private static final String RESOURCE_NAME = ElytronExtension.class.getPackage().getName() + ".LocalDescriptions";
    static final ServiceName BASE_SERVICE_NAME = ServiceName.of(SUBSYSTEM_NAME);

    static StandardResourceDescriptionResolver getResourceDescriptionResolver(final String... keyPrefixes) {
        StringBuilder sb = new StringBuilder(SUBSYSTEM_NAME);
        if (keyPrefixes != null) {
            for (String current : keyPrefixes) {
                sb.append(".").append(current);
            }
        }

        return new StandardResourceDescriptionResolver(sb.toString(), RESOURCE_NAME, ElytronExtension.class.getClassLoader(), true, false);
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE, parser);
    }

    @Override
    public void initialize(ExtensionContext context) {
        final SubsystemRegistration subsystemRegistration = context.registerSubsystem(SUBSYSTEM_NAME, ELYTRON_CURRENT);

        // Elytron is expected to be used everywhere.
        subsystemRegistration.setHostCapable();

        final ManagementResourceRegistration registration = subsystemRegistration.registerSubsystemModel(ElytronDefinition.INSTANCE);
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);

        subsystemRegistration.registerXMLElementWriter(parser);
    }

    static ModelNode createAddSubsystemOperation() {
        final ModelNode subsystem = new ModelNode();
        subsystem.get(OP).set(ADD);
        subsystem.get(OP_ADDR).add(SUBSYSTEM, SUBSYSTEM_NAME);
        return subsystem;
    }

    @SuppressWarnings("unchecked")
    static <T> ServiceController<T> getRequiredService(ServiceRegistry serviceRegistry, ServiceName serviceName, Class<T> serviceType) {
        ServiceController<?> controller = serviceRegistry.getRequiredService(serviceName);
        return (ServiceController<T>) controller;
    }

    static String asStringIfDefined(OperationContext context, AttributeDefinition attributeDefinition, ModelNode model) throws OperationFailedException {
        ModelNode value = attributeDefinition.resolveModelAttribute(context, model);
        if (value.isDefined()) {
            return value.asString();
        }

        return null;
    }

    static String[] asStringArrayIfDefined(OperationContext context, StringListAttributeDefinition attributeDefinition, ModelNode model) throws OperationFailedException {
        ModelNode resolved = attributeDefinition.resolveModelAttribute(context, model);
        if (resolved.isDefined()) {
            List<ModelNode> values = resolved.asList();
            String[] response = new String[values.size()];
            for (int i = 0; i < response.length; i++) {
                response[i] = values.get(i).asString();
            }
            return response;
        }
        return null;
    }

    static Double asDoubleIfDefined(OperationContext context, SimpleAttributeDefinition attributeDefinition, ModelNode model) throws OperationFailedException {
        ModelNode value = attributeDefinition.resolveModelAttribute(context, model);
        if (value.isDefined()) {
            return value.asDouble();
        }

        return null;
    }

    static int asIntIfDefined(OperationContext context, SimpleAttributeDefinition attributeDefinition, ModelNode model) throws OperationFailedException {
        ModelNode value = attributeDefinition.resolveModelAttribute(context, model);
        if (value.isDefined()) {
            return value.asInt();
        }

        return -1;
    }

}
