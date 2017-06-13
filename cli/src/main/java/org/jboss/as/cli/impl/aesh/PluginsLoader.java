/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli.impl.aesh;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ServiceLoader;
import java.util.jar.JarFile;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.LocalModuleFinder;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ModuleSpec.Builder;
import org.jboss.modules.ResourceLoader;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.ResourceLoaders;

/**
 *
 * @author jdenise@redhat.com
 */
public final class PluginsLoader {

    public interface Loader {
        <T> Iterable loadPlugins(File modulePath, String name, Class<T> clazz) throws ModuleLoadException;
    }

    private static class ModularPluginsLoader implements Loader {

        private static final String AESH_MODULE = "org.aesh";

        private class NakedJarFinder implements ModuleFinder {

            private final File jar;

            private NakedJarFinder(File jar) {
                this.jar = jar;
            }

            @Override
            public ModuleSpec findModule(String name, ModuleLoader delegateLoader) throws ModuleLoadException {
                try {
                    if (name.equals(jar.getName())) {
                        Builder builder = ModuleSpec.build(name);
                        JarFile jf = new JarFile(jar);
                        ResourceLoader jarLoader = ResourceLoaders.createJarResourceLoader("", jf);
                        ResourceLoaderSpec jarFile = ResourceLoaderSpec.createResourceLoaderSpec(jarLoader);
                        DependencySpec dep = DependencySpec.createModuleDependencySpec(AESH_MODULE);
                        DependencySpec local = DependencySpec.createLocalDependencySpec();
                        return builder.addResourceRoot(jarFile).addDependency(local).
                                addDependency(dep).create();
                    }
                } catch (IOException ex) {
                    throw new ModuleLoadException(ex);
                }
                return null;
            }
        }

        private class PlugLoader extends ModuleLoader {

            PlugLoader(ModuleFinder[] finders) {
                super(finders);
            }

            @Override
            protected Module preloadModule(String identifier) throws ModuleLoadException {
                Module module = null;
                try {
                    module = super.preloadModule(identifier);
                } catch (Exception ex) {
                    // XXX OK, will try with CLI loader.
                }
                if (module == null) {
                    ModuleClassLoader cl = (ModuleClassLoader) Thread.currentThread().getContextClassLoader();
                    module = cl.getModule().getModuleLoader().loadModule(identifier);
                }

                return module;
            }
        }

        @Override
        public <T> Iterable loadPlugins(File modulePath, String name, Class<T> clazz) throws ModuleLoadException {
            if (modulePath == null && name == null) {
                throw new IllegalArgumentException("modulePath and names can't be null");
            }
            if (modulePath == null) {
                // load from CLI loader.
                return loadModuleExtensions(name, clazz);
            }
            if (name == null) {
                // a jar file.
                return loadJarExtensions(modulePath, clazz);
            }
            return loadModuleExtensions(modulePath, name, clazz);
        }

        private <T> Iterable loadJarExtensions(File jar, Class<T> clazz) throws ModuleLoadException {
            ModuleFinder[] finders = {new NakedJarFinder(jar)};
            ModuleLoader loader = new ModularPluginsLoader.PlugLoader(finders);
            Module module = loader.loadModule(jar.getName());
            return module.loadService(clazz);
        }

        private <T> Iterable loadModuleExtensions(String name, Class<T> clazz) throws ModuleLoadException {
            ModuleClassLoader cl = (ModuleClassLoader) Thread.currentThread().getContextClassLoader();
            Module module = cl.getModule().getModuleLoader().loadModule(name);
            return module.loadService(clazz);
        }

        private <T> Iterable loadModuleExtensions(File modulePath, String name, Class<T> clazz) throws ModuleLoadException {
            File[] files = {modulePath};
            ModuleFinder[] finders = {new LocalModuleFinder(files)};

            ModuleLoader loader = new ModularPluginsLoader.PlugLoader(finders);
            Module module = loader.loadModule(name);
            return module.loadService(clazz);
        }
    }

    private static class ClassPathExtensionsLoader implements Loader {

        @Override
        public <T> Iterable loadPlugins(File modulePath, String name, Class<T> clazz) throws ModuleLoadException {
            if (name != null) {
                throw new ModuleLoadException("No name is expected " + name);
            }
            if (modulePath == null) {
                throw new ModuleLoadException("Jar file is expected.");
            }
            try {
                URL[] urls = {modulePath.toURI().toURL()};
                ClassLoader loader = new URLClassLoader(urls);
                return ServiceLoader.load(clazz, loader);
            } catch (MalformedURLException ex) {
                throw new ModuleLoadException(ex);
            }
        }

    }

    public static boolean isModular() {
        return (Thread.currentThread().getContextClassLoader() instanceof ModuleClassLoader);
    }

    public static Loader newPluginsLoader() {
        if (isModular()) {
            return new ModularPluginsLoader();
        } else {
            return new ClassPathExtensionsLoader();
        }
    }
}
