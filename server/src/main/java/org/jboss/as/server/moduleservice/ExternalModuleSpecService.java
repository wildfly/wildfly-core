/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.moduleservice;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.jboss.as.server.controller.resources.ServerRootResourceDefinition;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.logging.Logger;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.ModuleDependencySpecBuilder;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.ResourceLoaders;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.vfs.VFSUtils;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Service that manages the module spec for external modules (i.e. modules that reside outside of the application server).
 *
 * @author Stuart Douglas
 */
public class ExternalModuleSpecService implements Service<ModuleDefinition> {
    public static final int MAX_NUMBER_OF_JAR_RESOURCES = Integer.parseInt(WildFlySecurityManager.getPropertyPrivileged("org.jboss.as.server.max_number_of_jar_resources", "256"));
    private static final Logger log = Logger.getLogger(ExternalModuleSpecService.class);

    private final String moduleIdentifier;

    private final File file;

    private volatile ModuleDefinition moduleDefinition;

    private List<JarFile> jarFiles;

    public ExternalModuleSpecService(String moduleIdentifier, File file) {
        this.moduleIdentifier = moduleIdentifier;
        this.file = file;
        this.jarFiles = new ArrayList<>();
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        final ModuleSpec.Builder specBuilder;
        String currentName = "";
        try {
            if (!file.isDirectory()) {
                currentName = file.toString();
                this.jarFiles.add(new JarFile(file));
                specBuilder = ModuleSpec.build(moduleIdentifier);
                addResourceRoot(specBuilder, jarFiles.get(0));
                log.debugf("Added %s jar file as resource root for %s module identifier", file.getAbsolutePath(), moduleIdentifier);
            } else {
                specBuilder = ModuleSpec.build(moduleIdentifier);
                final Path rootPath = file.toPath();

                //This path resource root added here pointing to the rootPath will be able to find other file resources in any rootPath subdirectories.
                //We do not need to add a addPathResourceRoot for each subdirectory
                addPathResourceRoot(specBuilder, rootPath);
                log.debugf("Added %s directory as resource root for %s module identifier", file.getAbsolutePath(), moduleIdentifier);

                //scan any jar in this directory and subdirectories
                final List<Path> processedPaths = new ArrayList<>();
                try (Stream<Path> pathStream = Files.walk(rootPath).filter(path -> !Files.isDirectory(path) && path.getFileName().toString().endsWith(".jar"))) {
                    pathStream.forEach(p -> {
                        processedPaths.add(p);
                        if (processedPaths.size() > MAX_NUMBER_OF_JAR_RESOURCES)
                            throw new RuntimeException(ServerLogger.ROOT_LOGGER.maximumNumberOfJarResources(specBuilder.getName(), MAX_NUMBER_OF_JAR_RESOURCES));
                    });
                }

                if (! processedPaths.isEmpty()) {
                    final TreeSet<Path> jars = new TreeSet<>(new PathComparator());
                    jars.addAll(processedPaths);
                    for (Path jar : jars) {
                        currentName = jar.toString();
                        JarFile jarFile = new JarFile(jar.toFile());
                        this.jarFiles.add(jarFile);
                        addResourceRoot(specBuilder, jarFile);
                        log.debugf("Added %s jar file as resource root for %s module identifier", jar.toString(), moduleIdentifier);
                    }
                }
            }
        } catch (IOException e) {
            throw ServerLogger.ROOT_LOGGER.errorOpeningZipFile(currentName, e);
        } catch (Exception e) {
            throw new StartException(e);
        }

        //TODO: We need some way of configuring module dependencies for external archives
        addEEDependencies(specBuilder);
        specBuilder.addDependency(DependencySpec.createLocalDependencySpec());
        // TODO: external resource need some kind of dependency mechanism
        ModuleSpec moduleSpec = specBuilder.create();
        this.moduleDefinition = new ModuleDefinition(moduleIdentifier, Collections.emptySet(), moduleSpec);


        ServiceModuleLoader.installModuleResolvedService(context.getChildTarget(), moduleIdentifier.toString());
    }

    @Override
    public synchronized void stop(StopContext context) {
        for (JarFile jarFile : jarFiles) {
            log.debugf("Closing %s jar file which was added as resource root for %s module identifier", jarFile.getName(), moduleIdentifier);
            VFSUtils.safeClose(jarFile);
        }
        jarFiles.clear();
        jarFiles = null;
        moduleDefinition = null;
    }

    @Override
    public ModuleDefinition getValue() throws IllegalStateException, IllegalArgumentException {
        return moduleDefinition;
    }

    private static void addResourceRoot(final ModuleSpec.Builder specBuilder, final JarFile file) {
        specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(ResourceLoaders.createJarResourceLoader(file)));
    }

    private static void addPathResourceRoot(final ModuleSpec.Builder specBuilder, Path path) {
        specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(ResourceLoaders.createPathResourceLoader(path)));
    }

    private static void addEEDependencies(ModuleSpec.Builder specBuilder) {
        DependencySpec dependencySpec = new ModuleDependencySpecBuilder()
                .setName(ServerRootResourceDefinition.WILDFLY_EE_API)
                .setOptional(true)
                .build();

        specBuilder.addDependency(dependencySpec);
    }

    /**
     * Custom path comparator that will allow comparisons based on path names at same level of directory.
     *
     * e.g. give the following paths:
     *
     * /C/E/file.txt
     * /A/B/F/file.txt
     * /A/A/file.txt
     * /Z/file.txt
     * /
     *
     * The final sort will be:
     *
     * /
     * /A/A/file.txt
     * /A/B/F/file.txt
     * /C/E/file.txt
     * /Z/file.txt
     *
     */
    static class PathComparator implements Comparator<Path> {

        @Override
        public int compare(Path path1, Path path2) {
            if (path1 == null && path2 != null) return -1;
            if (path1 != null && path2 == null) return 1;
            if (path1 == null && path2 == null) return 0;

            Path parentPath1 = path1.getParent();
            Path parentPath2 = path2.getParent();

            if (parentPath1 == null && parentPath2 != null) return -1;
            if (parentPath1 != null && parentPath2 == null) return 1;
            if (parentPath1 == null && parentPath2 == null) return 0;

            int path1Count = parentPath1.getNameCount();
            int path2Count = parentPath2.getNameCount();

            if (path1Count < path2Count) {
                if (path1Count == 0) return -1;
                Path sameLevel = parentPath2.getRoot().resolve(parentPath2.subpath(0, path1Count));
                int comparison = compareBySameLevel(parentPath1, sameLevel);
                return comparison == 0 ? -1 : comparison;
            }

            if (path2Count < path1Count) {
                if (path2Count == 0) return 1;
                Path sameLevel = parentPath1.getRoot().resolve(parentPath1.subpath(0, path2Count));
                int comparison = compareBySameLevel(sameLevel, parentPath2);
                return comparison == 0 ? 1 : comparison;
            }

            if (path2Count == path1Count) {
                int comparison = compareBySameLevel(parentPath1, parentPath2);
                if (comparison != 0) {
                    return comparison;
                }
            }

            return path1.getFileName().compareTo(path2.getFileName());
        }

        private int compareBySameLevel(Path path1, Path path2) {
            int i = 0;
            int comparison = 0;
            for (Iterator<Path> iterPath1 = path1.iterator(); iterPath1.hasNext(); i++) {
                Path levelPath1 = iterPath1.next();
                Path levelPath2 = path2.getName(i);
                comparison = levelPath1.compareTo(levelPath2);
                if (comparison!=0) {
                    break;
                }
            }
            return comparison;
        }
    }
}
