/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.as.server.moduleservice;

import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.ModuleDependencySpecBuilder;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.ResourceLoaders;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.vfs.VFSUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Service that manages the module spec for external modules (i.e. modules that reside outside of the application server).
 *
 * @author Stuart Douglas
 *
 */
public class ExternalModuleSpecService implements Service<ModuleDefinition> {

    private static final List<String> EE_API_MODULES;

    static {
        // CRITICAL! Update this if any new EE API is added
        EE_API_MODULES = Arrays.asList(
                "javax.activation.api",
                "javax.annotation.api",
                "javax.batch.api",
                "javax.ejb.api",
                "javax.enterprise.api",
                "javax.enterprise.concurrent.api",
                "javax.inject.api",
                "javax.interceptor.api",
                "javax.json.api",
                "javax.jms.api",
                "javax.jws.api",
                "javax.mail.api",
                "javax.management.j2ee.api",
                "javax.persistence.api",
                "javax.resource.api",
                "javax.rmi.api",
                "javax.security.auth.message.api",
                "javax.security.jacc.api",
                "javax.servlet.api",
                "javax.servlet.jsp.api",
                "javax.transaction.api",
                "javax.validation.api",
                "javax.ws.rs.api",
                "javax.websocket.api",
                "javax.xml.bind.api",
                "javax.xml.soap.api",
                "javax.xml.ws.api",
                "org.glassfish.jakarta.el",
                //TODO WFLY-5966 validate the need for these and remove if not needed.
                // Prior to WFLY-5922 they were exported by javax.ejb.api
                "javax.xml.rpc.api",
                "org.omg.api",
                // This one always goes last.
                "javax.api"
        );
    }

    private final ModuleIdentifier moduleIdentifier;

    private final File file;

    private volatile ModuleDefinition moduleDefinition;

    private volatile JarFile jarFile;

    public ExternalModuleSpecService(ModuleIdentifier moduleIdentifier, File file) {
        this.moduleIdentifier = moduleIdentifier;
        this.file = file;
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        try {
            this.jarFile = new JarFile(file);
        } catch (IOException e) {
            throw new StartException(e);
        }
        final ModuleSpec.Builder specBuilder = ModuleSpec.build(moduleIdentifier);
        addResourceRoot(specBuilder, jarFile);
        //TODO: We need some way of configuring module dependencies for external archives
        addEEDependencies(specBuilder);
        specBuilder.addDependency(DependencySpec.createLocalDependencySpec());
        // TODO: external resource need some kind of dependency mechanism
        ModuleSpec moduleSpec = specBuilder.create();
        this.moduleDefinition = new ModuleDefinition(moduleIdentifier, Collections.<ModuleDependency>emptySet(), moduleSpec);


        ServiceModuleLoader.installModuleResolvedService(context.getChildTarget(), moduleIdentifier);

    }

    @Override
    public synchronized void stop(StopContext context) {
        VFSUtils.safeClose(jarFile);
        jarFile = null;
        moduleDefinition = null;
    }

    @Override
    public ModuleDefinition getValue() throws IllegalStateException, IllegalArgumentException {
        return moduleDefinition;
    }

    private static void addResourceRoot(final ModuleSpec.Builder specBuilder, final JarFile file) {
        specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(ResourceLoaders.createJarResourceLoader(
                    file.getName(), file)));
    }

    private static void addEEDependencies(ModuleSpec.Builder specBuilder) {
        /*
            Legacy code that was replaced by the full list of EE API modules
            DependencySpec javaee = new ModuleDependencySpecBuilder().setName("javaee.api").setOptional(true).build();
            specBuilder.addDependency(javaee);
         */

        for (String api : EE_API_MODULES) {
            DependencySpec dependencySpec = new ModuleDependencySpecBuilder().setName(api).setOptional(true).build();
            specBuilder.addDependency(dependencySpec);
        }
    }

}
