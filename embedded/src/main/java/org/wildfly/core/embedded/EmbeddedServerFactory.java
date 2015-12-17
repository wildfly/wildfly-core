/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.core.embedded;

import java.io.File;

import org.jboss.modules.ModuleLoader;

/**
 * <p>
 * Factory that sets up an embedded standalone server using modular classloading.
 * </p>
 * <p>
 * If a clean run is wanted, you can specify <code>${jboss.embedded.root}</code> to an existing directory
 * which will copy the contents of the data and configuration directories under a temporary folder. This
 * has the effect of this run not polluting later runs of the embedded server.
 * </p>
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Thomas.Diesler@jboss.com
 *
 * @deprecated use {@link EmbeddedProcessFactory}
 */
@Deprecated
public class EmbeddedServerFactory {

    /**
     * Create an embedded standalone server.
     *
     * @param jbossHomePath the location of the root of server installation. Cannot be {@code null} or empty.
     * @param modulePath the location of the root of the module repository. May be {@code null} if the standard
     *                   location under {@code jbossHomePath} should be used
     * @param systemPackages names of any packages that must be treated as system packages, with the same classes
     *                       visible to the caller's classloader visible to server-side classes loaded from
     *                       the server's modular classloader
     * @return the server. Will not be {@code null}
     */

    public static StandaloneServer create(String jbossHomePath, String modulePath, String... systemPackages) {
       return createStandalone(jbossHomePath, modulePath, systemPackages, new String[0]);
    }

    /**
     * Create an embedded standalone server.
     *
     * @param jbossHomePath the location of the root of server installation. Cannot be {@code null} or empty.
     * @param modulePath the location of the root of the module repository. May be {@code null} if the standard
     *                   location under {@code jbossHomePath} should be used
     * @param systemPackages names of any packages that must be treated as system packages, with the same classes
     *                       visible to the caller's classloader visible to server-side classes loaded from
     *                       the server's modular classloader
     * @param cmdargs any additional arguments to pass to the embedded server (e.g. -b=192.168.100.10)
     *
     * @return the server. Will not be {@code null}
     */
    public static StandaloneServer create(String jbossHomePath, String modulePath, String[] systemPackages, String[] cmdargs) {
        return createStandalone(jbossHomePath, modulePath, systemPackages, cmdargs);
    }

    @SuppressWarnings("deprecation")
    public static EmbeddedServerReference createStandalone(String jbossHomePath, String modulePath, String[] systemPackages, String[] cmdargs) {
        EmbeddedManagedProcess emp = EmbeddedProcessFactory.createStandaloneServer(jbossHomePath, modulePath, systemPackages, cmdargs);
        return new EmbeddedServerReference(emp, false);
    }

    /**
     * Create an embedded standalone server with an already established module loader.
     *
     * @param moduleLoader the module loader. Cannot be {@code null}
     * @param jbossHomeDir the location of the root of server installation. Cannot be {@code null} or empty.
     * @return the server. Will not be {@code null}
     */
    public static StandaloneServer create(ModuleLoader moduleLoader, File jbossHomeDir) {
        return createStandalone(moduleLoader, jbossHomeDir, new String[0]);
    }

    /**
     * Create an embedded standalone server with an already established module loader.
     *
     * @param moduleLoader the module loader. Cannot be {@code null}
     * @param jbossHomeDir the location of the root of server installation. Cannot be {@code null} or empty.
     * @param cmdargs any additional arguments to pass to the embedded server (e.g. -b=192.168.100.10)
     * @return the server. Will not be {@code null}
     */
    public static StandaloneServer create(ModuleLoader moduleLoader, File jbossHomeDir, String[] cmdargs) {
        return createStandalone(moduleLoader, jbossHomeDir, cmdargs);
    }

    @SuppressWarnings("deprecation")
    public static EmbeddedServerReference createStandalone(ModuleLoader moduleLoader, File jbossHomeDir, String[] cmdargs) {
        EmbeddedManagedProcess emp = EmbeddedProcessFactory.createStandaloneServer(moduleLoader, jbossHomeDir, cmdargs);
        return new EmbeddedServerReference(emp, false);
    }

    @SuppressWarnings("deprecation")
    public static EmbeddedServerReference createHostController(String jbossHomePath, String modulePath, String[] systemPackages, String[] cmdargs) {
        EmbeddedManagedProcess emp =  EmbeddedProcessFactory.createHostController(jbossHomePath, modulePath, systemPackages, cmdargs);
        return new EmbeddedServerReference(emp, true);
    }

    /**
     * Create an embedded host controller with an already established module loader.
     *
     * @param moduleLoader the module loader. Cannot be {@code null}
     * @param jbossHomeDir the location of the root of server installation. Cannot be {@code null} or empty.
     * @param cmdargs      any additional arguments to pass to the embedded server (e.g. -b=192.168.100.10)
     * @return the running host controller Will not be {@code null}
     */
    @SuppressWarnings("deprecation")
    public static EmbeddedServerReference createHostController(ModuleLoader moduleLoader, File jbossHomeDir, String[] cmdargs) {
        EmbeddedManagedProcess emp = EmbeddedProcessFactory.createHostController(moduleLoader, jbossHomeDir, cmdargs);
        return new EmbeddedServerReference(emp, true);
    }

}
