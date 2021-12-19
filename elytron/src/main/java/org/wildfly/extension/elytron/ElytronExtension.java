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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;

import org.jboss.as.controller.ExpressionResolverExtension;
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
     * The name spaces used for the {@code subsystem} element
     */
    static final String NAMESPACE_1_0 = "urn:wildfly:elytron:1.0";
    static final String NAMESPACE_1_1 = "urn:wildfly:elytron:1.1";
    static final String NAMESPACE_1_2 = "urn:wildfly:elytron:1.2";
    static final String NAMESPACE_2_0 = "urn:wildfly:elytron:2.0";
    static final String NAMESPACE_3_0 = "urn:wildfly:elytron:3.0";
    static final String NAMESPACE_4_0 = "urn:wildfly:elytron:4.0";
    static final String NAMESPACE_5_0 = "urn:wildfly:elytron:5.0";
    static final String NAMESPACE_6_0 = "urn:wildfly:elytron:6.0";
    static final String NAMESPACE_7_0 = "urn:wildfly:elytron:7.0";
    static final String NAMESPACE_8_0 = "urn:wildfly:elytron:8.0";
    static final String NAMESPACE_9_0 = "urn:wildfly:elytron:9.0";
    static final String NAMESPACE_10_0 = "urn:wildfly:elytron:10.0";
    static final String NAMESPACE_11_0 = "urn:wildfly:elytron:11.0";
    static final String NAMESPACE_12_0 = "urn:wildfly:elytron:12.0";
    static final String NAMESPACE_13_0 = "urn:wildfly:elytron:13.0";
    static final String NAMESPACE_14_0 = "urn:wildfly:elytron:14.0";
    static final String NAMESPACE_15_0 = "urn:wildfly:elytron:15.0";

    static final String CURRENT_NAMESPACE = NAMESPACE_15_0;

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

    private static final ModelVersion ELYTRON_CURRENT = ELYTRON_15_0_0;

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
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_1_0, () -> new ElytronSubsystemParser1_0());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_1_1, () -> new ElytronSubsystemParser1_1());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_1_2, () -> new ElytronSubsystemParser1_2());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_2_0, () -> new ElytronSubsystemParser2_0());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_3_0, () -> new ElytronSubsystemParser3_0());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_4_0, () -> new ElytronSubsystemParser4_0());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_5_0, () -> new ElytronSubsystemParser5_0());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_6_0, () -> new ElytronSubsystemParser6_0());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_7_0, () -> new ElytronSubsystemParser7_0());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_8_0, () -> new ElytronSubsystemParser8_0());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_9_0, () -> new ElytronSubsystemParser9_0());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_10_0, () -> new ElytronSubsystemParser10_0());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_11_0, () -> new ElytronSubsystemParser11_0());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_12_0, () -> new ElytronSubsystemParser12_0());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_13_0, () -> new ElytronSubsystemParser13_0());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_14_0, () -> new ElytronSubsystemParser14_0());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE_15_0, () -> new ElytronSubsystemParser15_0());
    }

    @Override
    public void initialize(ExtensionContext context) {
        final SubsystemRegistration subsystemRegistration = context.registerSubsystem(SUBSYSTEM_NAME, ELYTRON_CURRENT);

        // Elytron is expected to be used everywhere.
        subsystemRegistration.setHostCapable();

        AtomicReference<ExpressionResolverExtension> resolverRef = new AtomicReference<>();
        final ManagementResourceRegistration registration = subsystemRegistration.registerSubsystemModel(new ElytronDefinition(resolverRef));
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
        subsystemRegistration.registerXMLElementWriter(() -> new ElytronSubsystemParser15_0());

        context.registerExpressionResolverExtension(resolverRef::get, ExpressionResolverResourceDefinition.INITIAL_PATTERN, false);
    }

    @SuppressWarnings("unchecked")
    static <T> ServiceController<T> getRequiredService(ServiceRegistry serviceRegistry, ServiceName serviceName, Class<T> serviceType) {
        ServiceController<?> controller = serviceRegistry.getRequiredService(serviceName);
        return (ServiceController<T>) controller;
    }

}
