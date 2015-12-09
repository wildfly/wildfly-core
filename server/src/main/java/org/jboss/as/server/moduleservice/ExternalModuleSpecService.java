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
import org.jboss.as.server.Utils;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.loaders.ResourceLoader;
import org.wildfly.loaders.ResourceLoaders;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * Service that manages the module spec for external modules (i.e. modules that reside outside of the application server).
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ExternalModuleSpecService implements Service<ModuleDefinition> {

    private final ModuleIdentifier moduleIdentifier;

    private final File file;

    private volatile ModuleDefinition moduleDefinition;

    private volatile ResourceLoader loader;

    public ExternalModuleSpecService(ModuleIdentifier moduleIdentifier, File file) {
        this.moduleIdentifier = moduleIdentifier;
        this.file = file;
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        final ModuleSpec.Builder specBuilder = ModuleSpec.build(moduleIdentifier);
        try {
            loader = ResourceLoaders.newResourceLoader(file, false);
            specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(loader));
        } catch (IOException e) {
            throw new StartException(e);
        }
        //TODO: We need some way of configuring module dependencies for external archives
        ModuleIdentifier javaee = ModuleIdentifier.create("javaee.api");
        specBuilder.addDependency(DependencySpec.createModuleDependencySpec(javaee));
        specBuilder.addDependency(DependencySpec.createLocalDependencySpec());
        // TODO: external resource need some kind of dependency mechanism
        ModuleSpec moduleSpec = specBuilder.create();
        this.moduleDefinition = new ModuleDefinition(moduleIdentifier, Collections.<ModuleDependency>emptySet(), moduleSpec);


        ServiceModuleLoader.installModuleResolvedService(context.getChildTarget(), moduleIdentifier);

    }

    @Override
    public synchronized void stop(StopContext context) {
        Utils.safeClose(loader);
        loader = null;
        moduleDefinition = null;
    }

    @Override
    public ModuleDefinition getValue() throws IllegalStateException, IllegalArgumentException {
        return moduleDefinition;
    }

}
