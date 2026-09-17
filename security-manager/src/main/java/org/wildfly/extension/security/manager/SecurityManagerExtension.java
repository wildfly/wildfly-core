/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.security.manager;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.extension.AbstractLegacyExtension;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/*
 * This class implements the security manager extension.
 *
 * @author <a href="sguilhen@jboss.com">Stefan Guilhen</a>
 *
 * @deprecated the code module containing this extension only exists to support use of particular resource definition
 * classes in other subsystems.
 */
@Deprecated(forRemoval = true, since = "34.0")
public class SecurityManagerExtension extends AbstractLegacyExtension {

    public static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(SUBSYSTEM, Constants.SUBSYSTEM_NAME);
    protected static final String RESOURCE_NAME = SecurityManagerExtension.class.getPackage().getName() + ".LocalDescriptions";

    private static final ModelVersion CURRENT_MODEL_VERSION = ModelVersion.create(4);
    static final ModelVersion DEPRECATED_SINCE = ModelVersion.create(4);

    public SecurityManagerExtension() {
        super("org.wildfly.extension.security.manager", Constants.SUBSYSTEM_NAME);
    }

    public static StandardResourceDescriptionResolver getResolver(final String... keyPrefix) {
        StringBuilder prefix = new StringBuilder(Constants.SUBSYSTEM_NAME);
        for (String kp : keyPrefix) {
            prefix.append('.').append(kp);
        }
        return new StandardResourceDescriptionResolver(prefix.toString(), RESOURCE_NAME, SecurityManagerExtension.class.getClassLoader(), true, false);
    }

    @Override
    protected Set<ManagementResourceRegistration> initializeLegacyModel(final ExtensionContext context) {
        final SubsystemRegistration subsystem = context.registerSubsystem(Constants.SUBSYSTEM_NAME, CURRENT_MODEL_VERSION);
        final ManagementResourceRegistration registration = subsystem.registerSubsystemModel(SecurityManagerRootDefinition.INSTANCE);
        subsystem.registerXMLElementWriter(SecurityManagerSubsystemParser_1_0::new);
        return Collections.singleton(registration);
    }

    @Override
    protected void initializeLegacyParsers(final ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(Constants.SUBSYSTEM_NAME, Namespace.SECURITY_MANAGER_1_0.getUriString(), new SecurityManagerSubsystemParser_1_0());
    }
}
