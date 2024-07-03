/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.security.manager.logging;

import java.lang.invoke.MethodHandles;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.Param;

/**
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@MessageLogger(projectCode = "WFLYSM", length = 4)
public interface SecurityManagerLogger extends BasicLogger {

    SecurityManagerLogger ROOT_LOGGER = Logger.getMessageLogger(MethodHandles.lookup(), SecurityManagerLogger.class, "org.wildfly.extension.security.manager");

//    @LogMessage(level = INFO)
//    @Message(id = 1, value = "Installing the WildFly Security Manager")
//    void installingWildFlySecurityManager();

    /**
     * Creates a {@link javax.xml.stream.XMLStreamException} to indicate an invalid version was found in the permissions element.
     *
     * @param found the version that was found in the element.
     * @param expected the expected version.
     * @return the constructed {@link javax.xml.stream.XMLStreamException}
     */
    @Message(id = 2, value = "Invalid version found in the permissions element. Found %s, expected %s")
    XMLStreamException invalidPermissionsXMLVersion(String found, String expected);

    /**
     * Creates a {@link org.jboss.as.controller.OperationFailedException} to indicate that the security manager subsystem
     * was incorrectly configured. As a rule the minimum-set permissions must be implied by the maximum-set permissions.
     *
     * @param failedPermissions a list of the permissions in the minimum-set that are not implied by the maximum-set.
     * @return the constructed {@link org.jboss.as.controller.OperationFailedException}
     */
    @Message(id = 3, value = "Subsystem configuration error: the following permissions are not implied by the maximum permissions set %s")
    OperationFailedException invalidSubsystemConfiguration(StringBuilder failedPermissions);

    /**
     * Creates a {@link org.jboss.as.server.deployment.DeploymentUnitProcessingException} to indicate that the deployment
     * was incorrectly configured. As a rule the permissions specified in the deployment descriptors (permissions.xml or
     * jboss-permissions.xml) must be implied by the subsystem maximum-set.
     *
     * @param failedPermissions a list of the permissions in deployment descriptors that are not implied by the maximum-set.
     * @return the constructed {@link org.jboss.as.server.deployment.DeploymentUnitProcessingException}
     */
    @Message(id = 4, value = "Deployment configuration error: the following permissions are not implied by the maximum permissions set %s")
    DeploymentUnitProcessingException invalidDeploymentConfiguration(StringBuilder failedPermissions);

    /**
     * Creates a message indicating that empty maximum sets are not understood in the target model version and must thus
     * be rejected.
     *
     * @return the constructed {@link String} message.
     */
    @Message(id = 5, value = "Empty maximum sets are not understood in the target model version and must be rejected")
    String rejectedEmptyMaximumSet();

    /**
     * Creates a {@link javax.xml.stream.XMLStreamException} to indicate an unexpected element was found in the permissions.xml file.
     * @param name the unexpected element name.
     * @param location the location of the error.
     * @return the constructed {@link javax.xml.stream.XMLStreamException}.
     */
    @Message(id = 6, value = "Unexpected element '%s' encountered")
    XMLStreamException unexpectedElement(QName name, @Param Location location);

    /**
     * Creates a {@link javax.xml.stream.XMLStreamException} to indicate an unexpected attribute was found in the permissions.xml file.
     * @param name the unexpected attribute name.
     * @param location the location of the error.
     * @return the constructed {@link javax.xml.stream.XMLStreamException}.
     */
    @Message(id = 7, value = "Unexpected attribute '%s' encountered")
    XMLStreamException unexpectedAttribute(QName name, @Param Location location);

    /**
     * Create a {@link javax.xml.stream.XMLStreamException} to indicate an unexpected end of document.
     *
     * @param location the location of the error.
     * @return the constructed {@link javax.xml.stream.XMLStreamException}.
     */
    @Message(id = 8, value = "Unexpected end of document")
    XMLStreamException unexpectedEndOfDocument(@Param Location location);

    /**
     * Creates a {@link javax.xml.stream.XMLStreamException} indicating there are missing required attribute(s).
     *
     * @param sb the missing attributes.
     * @param location the location of the error.
     *
     * @return the constructed {@link javax.xml.stream.XMLStreamException}.
     */
    @Message(id = 9, value = "Missing required attribute(s): %s")
    XMLStreamException missingRequiredAttributes(StringBuilder sb, @Param Location location);

    /**
     * Creates a {@link javax.xml.stream.XMLStreamException} indicating there are missing required element(s).
     *
     * @param sb the missing elements.
     * @param location the location of the error.
     *
     * @return the constructed {@link javax.xml.stream.XMLStreamException}.
     */
    @Message(id = 10, value = "Missing required element(s): %s")
    XMLStreamException missingRequiredElements(StringBuilder sb, @Param Location location);

    /**
     * Creates a {@link javax.xml.stream.XMLStreamException} indicating the presence of an unexpected content type.
     *
     * @param type the unexpected type.
     * @param location the location of the error.
     * @return the constructed {@link javax.xml.stream.XMLStreamException}.
     */
    @Message(id = 11, value = "Unexpected content of type %s")
    XMLStreamException unexpectedContentType(String type, @Param Location location);

    /**
     * Log message to warn that a permission returned null and was invalid. The permission
     * will be ignored in the passed list (maximum-set, minimum-set or deployment).
     *
     * @param type The type of the permission (maximum-set, minimum-set or deployment)
     * @param permissionClass The permission class
     * @param permissionName The permission name
     * @param permissionActions The permission actions
     */
    @LogMessage(level = Level.WARN)
    @Message(id = 12, value = "The following permission could not be constructed and will be ignored in the %s: (class=\"%s\" name=\"%s\" actions=\"%s\")")
    void ignoredPermission(String type, String permissionClass, String permissionName, String permissionActions);
}
