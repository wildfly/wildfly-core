/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.module.util;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

/**
 * A simple utility to create a module.
 * <p>
 * This will create a JAR based on the classes and generate a module.xml file.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class ModuleBuilder {

    private final String name;
    private final JavaArchive jar;
    private final Collection<ModuleDependency> dependencies;

    private ModuleBuilder(final String name, final String archiveName) {
        this.name = name;
        final String resourceName = archiveName == null ? "test-module.jar" : archiveName;
        jar = ShrinkWrap.create(JavaArchive.class, resourceName);
        dependencies = new ArrayList<>();
    }

    /**
     * Creates a new module builder with an archive name of test-module.jar.
     *
     * @param moduleName the name for the module
     *
     * @return a new module builder
     */
    public static ModuleBuilder of(final String moduleName) {
        return of(moduleName, null);
    }

    /**
     * Creates a new module builder.
     *
     * @param moduleName  the name for the module
     * @param archiveName the name for the archive
     *
     * @return a new module builder
     */
    public static ModuleBuilder of(final String moduleName, final String archiveName) {
        return new ModuleBuilder(moduleName, archiveName);
    }

    /**
     * Adds a class to the module to be generated.
     *
     * @param c the class to add
     *
     * @return this builder
     */
    public ModuleBuilder addClass(final Class<?> c) {
        jar.addClass(c);
        return this;
    }

    /**
     * Adds the classes to the module to be generated.
     *
     * @param classes the classes to add
     *
     * @return this builder
     */
    public ModuleBuilder addClasses(final Class<?>... classes) {
        jar.addClasses(classes);
        return this;
    }

    /**
     * Adds a dependency for the module.xml file.
     *
     * @param dependency the dependency to add
     *
     * @return this builder
     */
    public ModuleBuilder addDependency(final String dependency) {
        this.dependencies.add(ModuleDependency.of(dependency));
        return this;
    }

    /**
     * Adds the dependencies for the module.xml file.
     *
     * @param dependencies the dependencies to add
     *
     * @return this builder
     */
    public ModuleBuilder addDependencies(final String... dependencies) {
        for (String dependency : dependencies) {
            addDependency(dependency);
        }
        return this;
    }

    /**
     * Adds a dependency for the module.xml file.
     *
     * @param dependency the dependency to add
     *
     * @return this builder
     */
    public ModuleBuilder addDependency(final ModuleDependency dependency) {
        this.dependencies.add(dependency);
        return this;
    }

    /**
     * Adds the dependencies for the module.xml file.
     *
     * @param dependencies the dependencies to add
     *
     * @return this builder
     */
    public ModuleBuilder addDependencies(final ModuleDependency... dependencies) {
        Collections.addAll(this.dependencies, dependencies);
        return this;
    }

    /**
     * Creates a {@code META-INF/services} file for the interface with the implementations provied.
     *
     * @param intf            the interface to crate the services file for
     * @param implementations the implemenations
     *
     * @return this builder
     */
    public ModuleBuilder addServiceProvider(final Class<?> intf, final Class<?>... implementations) {
        validate(intf, implementations);
        jar.addAsServiceProvider(intf, implementations);
        return this;
    }

    /**
     * Adds all the classes in the {@linkplain Package package} to the generated module.
     *
     * @param p the package to add
     *
     * @return this builder
     */
    public ModuleBuilder addPackage(final String p) {
        jar.addPackage(p);
        return this;
    }

    /**
     * Adds all the classes in the {@linkplain Package package} to the generated module.
     *
     * @param p the package to add
     *
     * @return this builder
     */
    public ModuleBuilder addPackage(final Package p) {
        jar.addPackage(p);
        return this;
    }

    /**
     * Creates the module by:
     * <ul>
     *     <li>Creating the module directory based on the modules name</li>
     *     <li>Generating a JAR file for the resource</li>
     *     <li>Generating a module.xml file</li>
     * </ul>
     *
     * @return a task to clean up the module
     */
    public Runnable build() {
        try {
            final Path mp = TestModule.getModulesDirectory(true).toPath();
            final Path moduleDir = mp.resolve(name.replace('.', File.separatorChar)).resolve("main");
            if (Files.notExists(moduleDir)) {
                Files.createDirectories(moduleDir);
            }
            final Path fullPathToDelete = moduleDir.subpath(0, mp.getNameCount() + 1);
            createModule(moduleDir);
            return new Runnable() {
                @Override
                public void run() {
                    try {
                        delete(moduleDir, true);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }

                private void delete(final Path dir, final boolean deleteFiles) throws IOException {
                    if (deleteFiles) {
                        for (Path path : Files.newDirectoryStream(dir)) {
                            if (!Files.isDirectory(path)) {
                                Files.delete(path);
                            }
                        }
                    }
                    final Path parent = dir.getParent();
                    if (isDirectoryEmpty(dir)) {
                        Files.delete(dir);
                    } else {
                        return;
                    }
                    if (parent != null && !parent.equals(mp)) {
                        delete(parent, false);
                    }
                }

                private boolean isDirectoryEmpty(final Path dir) throws IOException {
                    return Files.list(dir).count() == 0;
                }
            };
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void createModule(final Path moduleDir) throws IOException {
        Files.createDirectories(moduleDir);
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(moduleDir.resolve("module.xml")))) {
            out.println("<module xmlns=\"urn:jboss:module:1.9\" name=\"" + name + "\">");
            out.println("    <resources>");
            out.println("        <resource-root path=\"" + jar.getName() + "\"/>");
            out.println("    </resources>");
            if (!dependencies.isEmpty()) {
                out.println("    <dependencies>");
                for (ModuleDependency dependency : dependencies) {
                    out.print("        <module name=\"" + dependency.getName() + "\"");
                    if (dependency.isExport()) {
                        out.print(" export=\"true\"");
                    }
                    if (dependency.isOptional()) {
                        out.print(" optional=\"true\"");
                    }
                    if (dependency.getServices() != null) {
                        out.print(" services=\"" + dependency.getServices() + "\"");
                    }
                    out.println("/>");
                }
                out.println("    </dependencies>");
            }
            out.println("</module>");
        }

        // Create the JAR
        try (OutputStream out = Files.newOutputStream(moduleDir.resolve(jar.getName()), StandardOpenOption.CREATE_NEW)) {
            jar.as(ZipExporter.class).exportTo(out);
        }
    }

    private static void validate(final Class<?> type, final Class<?>... subtypes) {
        final Set<Class<?>> invalidTypes = new LinkedHashSet<>();
        for (Class<?> subtype : subtypes) {
            if (!type.isAssignableFrom(subtype)) {
                invalidTypes.add(subtype);
            }
        }
        if (!invalidTypes.isEmpty()) {
            final StringBuilder msg = new StringBuilder("The following types are not subtypes of ")
                    .append(type.getCanonicalName())
                    .append(" : ");
            final Iterator<Class<?>> iter = invalidTypes.iterator();
            while (iter.hasNext()) {
                msg.append(iter.next().getCanonicalName());
                if (iter.hasNext()) {
                    msg.append(", ");
                }
            }
            throw new IllegalArgumentException(msg.toString());
        }
    }
}
