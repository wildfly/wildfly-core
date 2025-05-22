/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.moduleservice;

import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;

/**
 * Allows callers to add external modules that can be used as dependencies for a DeploymentUnit.
 * <p>
 * A module is considered external when it resides outside ot the default "modules" directory from which JBoss Modules looks to find modules.
 * Adding an external module consists of creating a Module Identifier string and <code>ExternalModuleSpecService</code>
 * that manages the life cycle of the module specification that defines the module.
 * To be created it is necessary to specify a path to its location. That path could be a jar file or a directory, in which case the directory
 * will be scanned searching for jar files in it.
 * <p>
 *
 * @author <a href="mailto:yborgess@redhat.com">Yeray Borges</a>
 */
public interface ExternalModule {

    /**
     * Prefix name used for the new installed module identifier.
     */
    String EXTERNAL_MODULE_PREFIX = ServiceModuleLoader.MODULE_PREFIX + "external.";

    /**
     * Prepares and install a new Module Identifier string and <code>ExternalModuleSpecService</code> to manage this module.
     * This method prevents the creation of duplicate external module spec services for the given module name looking at the
     * service registry, so it can be called multiple times for the same module name.
     *
     * @param moduleName      The module name to add.
     * @param path            An absolute path pointing out to a directory or jar file that will be used as source for the resources backed
     *                        by the given module name we want to install. Cannot be {@code null}
     * @param serviceRegistry The MSC service registry which will be used to find if there is already an <code>ExternalModuleSpecService</code>
     *                        created for the given module name. Cannot be {@code null}.
     * @param serviceTarget   The {@link ServiceTarget} to use to install the service. Cannot be {@code null}.
     * @return The Module Identifier as a String of the module created for the given path.
     */
    String addExternalModuleAsString(String moduleName, String path, ServiceRegistry serviceRegistry, ServiceTarget serviceTarget);

    /**
     * Prepares and install a new module and <code>ExternalModuleSpecService</code> to manage this module.
     * <p>
     * This method does not use a specific module name, instead the module name is derived from the given path.
     * This method prevents the creation of duplicate external module spec services for the given path looking at the service registry,
     * so it can be called multiple times using the same path.
     *
     * @param path            An absolute path pointing out to a directory or jar file that will be used as source for the resources backed by
     *                        the given module name we want to install. Cannot be {@code null}
     * @param serviceRegistry The MSC service registry which will be used to find if there is already an <code>ExternalModuleSpecService</code>
     *                        created for the given path. Cannot be {@code null}.
     * @param serviceTarget   The {@link ServiceTarget} to use to install the service. Cannot be {@code null}.
     * @return The string representing the Module Identifier created for the given path.
     */
    String addExternalModuleAsString(String path, ServiceRegistry serviceRegistry, ServiceTarget serviceTarget);

    /**
     * Checks if the path argument refers to a file and the file is valid to be used as a class-path entry.
     *
     * @param path value to test.
     * @return <code>true</code> if and only if the path refers to a file and the file is considered valid as a class-path entry,
     * otherwise returns <code>false</code>.
     */
    boolean isValidFile(String path);
}
