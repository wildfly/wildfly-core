/*
 * Copyright 2023 Red Hat, Inc.
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
package org.wildfly.extension.elytron.common;

import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

/**
 * A base subsystem definition with common components from WildFly Elytron.
 *
 * @author <a href="mailto:carodrig@redhat.com">Cameron Rodriguez</a>
 */
public class ElytronCommonDefinitions extends SimpleResourceDefinition {

    public static final String ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    public ElytronCommonDefinitions(Parameters parameters) {
        super(parameters);
    }

    /**
     * Dynamically generates {@link ResourceDefinition} for a given subsystem.
     *
     * @param extensionClass Class object for the subsystem {@link org.jboss.as.controller.Extension Extension} (ex.
     *                       {@code org.wildfly.extension.elytron-oauth2.ElytronOAuth2Extension})
     * @param serverOrHostController whether the given resource registration is for a server, or if not, is not for a
     *                               resource in the {@code profile} resource tree. See
     *                               {@link #isServerOrHostController(ImmutableManagementResourceRegistration)}
     * @return An array of definitions for TLS components in Elytron
     *
     * @implSpec The last element of the subsystem package MUST match the capability name of the subsystem. For example,
     * a subsystem using the capability name {@code org.wildfly.security.elytron-oauth2} would need to use a package like
     * {@code org.wildfly.extension.elytron-oauth2}
     */
    public static ResourceDefinition[] getElytronCommonTLSDefinitions(final Class<?> extensionClass,
                                                                      boolean serverOrHostController) {

        return new ResourceDefinition[]{
                AdvancedModifiableKeyStoreDecorator.wrap(extensionClass,
                        KeyStoreDefinition.configure(extensionClass)),
                ModifiableKeyStoreDecorator.wrap(extensionClass,
                        LdapKeyStoreDefinition.configure(extensionClass)),
                ModifiableKeyStoreDecorator.wrap(extensionClass,
                        FilteringKeyStoreDefinition.configure(extensionClass)),

                SSLDefinitions.getKeyManagerDefinition(extensionClass),
                SSLDefinitions.getTrustManagerDefinition(extensionClass),
                SSLDefinitions.getServerSSLContextDefinition(extensionClass, serverOrHostController),
                SSLDefinitions.getClientSSLContextDefinition(extensionClass, serverOrHostController),
                SSLDefinitions.getServerSNISSLContextDefinition(extensionClass),

                CertificateAuthorityDefinition.configure(extensionClass),
                CertificateAuthorityAccountDefinition.configure(extensionClass)
        };
    }

    public static <T> ServiceBuilder<T> commonRequirements(final Class<?> extensionClass, ServiceBuilder<T> serviceBuilder) {
        return commonRequirements(ServiceName.of(getSubsystemName(extensionClass)), serviceBuilder);
    }

    public static <T> ServiceBuilder<T> commonRequirements(ServiceName subsystemName, ServiceBuilder<T> serviceBuilder) {
        return commonRequirements(subsystemName, serviceBuilder, true, true);
    }

    public static <T> ServiceBuilder<T> commonRequirements(final Class<?> extensionClass, ServiceBuilder<T> serviceBuilder, boolean dependOnProperties, boolean dependOnProviderRegistration) {
        return commonRequirements(ServiceName.of(getSubsystemName(extensionClass)), serviceBuilder, dependOnProperties, dependOnProviderRegistration);
    }

    public static <T> ServiceBuilder<T> commonRequirements(ServiceName subsystemName, ServiceBuilder<T> serviceBuilder, boolean dependOnProperties, boolean dependOnProviderRegistration) {
        if (dependOnProperties) serviceBuilder.requires(SecurityPropertyService.serviceName(subsystemName));
        if (dependOnProviderRegistration) serviceBuilder.requires(ProviderRegistrationService.serviceName(subsystemName));
        return serviceBuilder;
    }

    /* Extension methods */

    /**
     * Extracts the subsystem name from the last element of the {@link org.jboss.as.controller.Extension} package name.
     *
     * @param extensionClass Class object for the subsystem {@link org.jboss.as.controller.Extension Extension}
     * @return the last element of the package name. Ex. {@code org.wildfly.extension.elytron} returns {@code elytron}.
     */
    static String getSubsystemName(final Class<?> extensionClass) {
        String[] packageSegments = extensionClass.getPackage().getName().split("\\.");
        return packageSegments[packageSegments.length - 1];
    }

    /**
     * Generates a subsystem capability, with prefix {@code org.wildfly.security}
     *
     * @param extensionClass Class object for the subsystem {@link org.jboss.as.controller.Extension Extension}
     * @return a subsystem capability name
     */
    static String getSubsystemCapability(final Class<?> extensionClass) {
        return ElytronCommonCapabilities.CAPABILITY_BASE + getSubsystemName(extensionClass);
    }

    public static StandardResourceDescriptionResolver getResourceDescriptionResolver(final Class<?> extensionClass,
                                                                              final String... keyPrefixes) {

        String resourceName = extensionClass.getPackage().getName() + ".LocalDescriptions";

        StringBuilder sb = new StringBuilder(getSubsystemName(extensionClass));
        if (keyPrefixes != null) {
            for (String current : keyPrefixes) {
                sb.append(".").append(current);
            }
        }

        return new StandardResourceDescriptionResolver(sb.toString(), resourceName, extensionClass.getClassLoader(), true, false);
    }

    /**
     * Gets whether the given {@code resourceRegistration} is for a server, or if not,
     * is not for a resource in the {@code profile} resource tree.
     */
    public static boolean isServerOrHostController(ImmutableManagementResourceRegistration resourceRegistration) {
        return resourceRegistration.getProcessType().isServer() || !ModelDescriptionConstants.PROFILE.equals(resourceRegistration.getPathAddress().getElement(0).getKey());
    }

    @SuppressWarnings("unchecked")
    public static <T> ServiceController<T> getRequiredService(ServiceRegistry serviceRegistry, ServiceName serviceName, Class<T> serviceType) {
        ServiceController<?> controller = serviceRegistry.getRequiredService(serviceName);
        return (ServiceController<T>) controller;
    }
}
