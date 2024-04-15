/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;

import org.jboss.as.controller.PersistentResourceXMLDescriptionWriter;
import org.jboss.as.controller.extension.ExpressionResolverExtension;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.server.deployment.AttachmentKey;
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
     * The current name space used for the {@code subsystem} element
     */
    static final String NAMESPACE_18_0 = "urn:wildfly:elytron:18.0";

    static final String CURRENT_NAMESPACE = NAMESPACE_18_0;

    /**
     * The name of our subsystem within the model.
     */
    public static final String SUBSYSTEM_NAME = "elytron";

    /**
     * The attachment key that is used for associating the authentication context with a deployment context.
     */
    public static final AttachmentKey<AuthenticationContext> AUTHENTICATION_CONTEXT_KEY = AttachmentKey.create(AuthenticationContext.class);
    public static final AttachmentKey<SSLContext> SSL_CONTEXT_KEY = AttachmentKey.create(SSLContext.class);

    static final ModelVersion ELYTRON_1_2_0 = ModelVersion.create(1, 2);
    static final ModelVersion ELYTRON_2_0_0 = ModelVersion.create(2);
    static final ModelVersion ELYTRON_3_0_0 = ModelVersion.create(3);
    static final ModelVersion ELYTRON_4_0_0 = ModelVersion.create(4);
    static final ModelVersion ELYTRON_5_0_0 = ModelVersion.create(5);
    static final ModelVersion ELYTRON_6_0_0 = ModelVersion.create(6);
    static final ModelVersion ELYTRON_7_0_0 = ModelVersion.create(7);
    static final ModelVersion ELYTRON_8_0_0 = ModelVersion.create(8);
    static final ModelVersion ELYTRON_9_0_0 = ModelVersion.create(9);
    static final ModelVersion ELYTRON_10_0_0 = ModelVersion.create(10);
    static final ModelVersion ELYTRON_11_0_0 = ModelVersion.create(11);
    static final ModelVersion ELYTRON_12_0_0 = ModelVersion.create(12);
    static final ModelVersion ELYTRON_13_0_0 = ModelVersion.create(13);
    static final ModelVersion ELYTRON_14_0_0 = ModelVersion.create(14);
    static final ModelVersion ELYTRON_15_0_0 = ModelVersion.create(15);
    static final ModelVersion ELYTRON_15_1_0 = ModelVersion.create(15, 1);
    static final ModelVersion ELYTRON_16_0_0 = ModelVersion.create(16);
    static final ModelVersion ELYTRON_17_0_0 = ModelVersion.create(17);
    static final ModelVersion ELYTRON_18_0_0 = ModelVersion.create(18);
    static final ModelVersion ELYTRON_19_0_0 = ModelVersion.create(19);

    private static final ModelVersion ELYTRON_CURRENT = ELYTRON_19_0_0;

    static final String ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";


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

    /**
     * Gets whether the given {@code resourceRegistration} is for a server, or if not,
     * is not for a resource in the {@code profile} resource tree.
     */
    static boolean isServerOrHostController(ImmutableManagementResourceRegistration resourceRegistration) {
        return resourceRegistration.getProcessType().isServer() || !ModelDescriptionConstants.PROFILE.equals(resourceRegistration.getPathAddress().getElement(0).getKey());
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMappings(SUBSYSTEM_NAME, EnumSet.allOf(ElytronSubsystemSchema.class));
    }

    @Override
    public void initialize(ExtensionContext context) {
        final SubsystemRegistration subsystemRegistration = context.registerSubsystem(SUBSYSTEM_NAME, ELYTRON_CURRENT);

        // Elytron is expected to be used everywhere.
        subsystemRegistration.setHostCapable();

        AtomicReference<ExpressionResolverExtension> resolverRef = new AtomicReference<>();
        final ManagementResourceRegistration registration = subsystemRegistration.registerSubsystemModel(new ElytronDefinition(resolverRef));
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
        subsystemRegistration.registerXMLElementWriter(new PersistentResourceXMLDescriptionWriter(ElytronSubsystemSchema.CURRENT.get(context.getStability())));

        context.registerExpressionResolverExtension(resolverRef::get, ExpressionResolverResourceDefinition.INITIAL_PATTERN, false);
    }

    @SuppressWarnings("unchecked")
    static <T> ServiceController<T> getRequiredService(ServiceRegistry serviceRegistry, ServiceName serviceName, Class<T> serviceType) {
        ServiceController<?> controller = serviceRegistry.getRequiredService(serviceName);
        return (ServiceController<T>) controller;
    }

}
