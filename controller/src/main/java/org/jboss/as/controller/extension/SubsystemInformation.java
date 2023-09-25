/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.extension;

import java.util.List;

import org.jboss.as.controller.ModelVersion;

/**
* @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
*/
public interface SubsystemInformation {

    /**
     * Gets the URIs of the XML namespaces the subsystem can parse.
     *
     * @return list of XML namespace URIs. Will not return {@code null}
     */
    List<String> getXMLNamespaces();

    /**
     * Gets the major version of the subsystem's management interface, if available.
     *
     * @return the major interface version, or {@code null} if the subsystem does not have a versioned interface
     */
    Integer getManagementInterfaceMajorVersion();

    /**
     * Gets the minor version of the subsystem's management interface, if available.
     *
     * @return the minor interface version, or {@code null} if the subsystem does not have a versioned interface
     */
    Integer getManagementInterfaceMinorVersion();

    /**
     * Gets the micro version of the subsystem's management interface, if available.
     *
     * @return the micro interface version, or {@code null} if the subsystem does not have a versioned interface
     */
    Integer getManagementInterfaceMicroVersion();


    /**
     * Gets full version of the subsystem
     * @return full {@link ModelVersion}
     */
    default ModelVersion getManagementInterfaceVersion(){
        return ModelVersion.create(getManagementInterfaceMajorVersion() != null ? getManagementInterfaceMajorVersion() : 0,
                getManagementInterfaceMinorVersion() != null ? getManagementInterfaceMinorVersion() : 0,
                getManagementInterfaceMicroVersion() != null ? getManagementInterfaceMicroVersion() : 0
        );
    }

}
