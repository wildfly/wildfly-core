/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.security.manager;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/*
 * This class implements the security manager extension.
 *
 * @author <a href="sguilhen@jboss.com">Stefan Guilhen</a>
 */
public class SecurityManagerExtension implements Extension {

    public static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(SUBSYSTEM, Constants.SUBSYSTEM_NAME);
    protected static final String RESOURCE_NAME = SecurityManagerExtension.class.getPackage().getName() + ".LocalDescriptions";

    private static final ModelVersion CURRENT_MODEL_VERSION = ModelVersion.create(4);
    static final ModelVersion DEPRECATED_SINCE = ModelVersion.create(4);

    public static StandardResourceDescriptionResolver getResolver(final String... keyPrefix) {
        StringBuilder prefix = new StringBuilder(Constants.SUBSYSTEM_NAME);
        for (String kp : keyPrefix) {
            prefix.append('.').append(kp);
        }
        return new StandardResourceDescriptionResolver(prefix.toString(), RESOURCE_NAME, SecurityManagerExtension.class.getClassLoader(), true, false);
    }

    @Override
    public void initialize(final ExtensionContext context) {
        final SubsystemRegistration subsystem = context.registerSubsystem(Constants.SUBSYSTEM_NAME, CURRENT_MODEL_VERSION);
        final ManagementResourceRegistration registration = subsystem.registerSubsystemModel(SecurityManagerRootDefinition.INSTANCE);
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE, false);
        subsystem.registerXMLElementWriter(SecurityManagerSubsystemParser_1_0::new);
    }

    @Override
    public void initializeParsers(final ExtensionParsingContext context) {
        // For the current version we don't use a Supplier as we want its description initialized
        // TODO if any new xsd versions are added, use a Supplier for the old version
        context.setSubsystemXmlMapping(Constants.SUBSYSTEM_NAME, Namespace.SECURITY_MANAGER_1_0.getUriString(), new SecurityManagerSubsystemParser_1_0());
    }


}
