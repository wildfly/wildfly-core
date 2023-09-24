/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.extension;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.jboss.as.controller.Extension;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.jboss.shrinkwrap.api.exporter.StreamExporter;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.xnio.IoUtils;

/**
 * Utilities for manipulating extensions in integration tests.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class ExtensionUtils {

    public static final String JAR_NAME = "test-extension.jar";

    public static void createExtensionModule(String extensionName, Class<? extends Extension> extension) throws IOException {
        createExtensionModule(getExtensionModuleRoot(extensionName), extension);
    }

    public static void createExtensionModule(String extensionName, Class<? extends Extension> extension, Package... additionalPackages) throws IOException {
        createExtensionModule(getExtensionModuleRoot(extensionName), extension, additionalPackages);
    }

    public static void createExtensionModule(File extensionModuleRoot, Class<? extends Extension> extension) throws IOException {
        createExtensionModule(extensionModuleRoot, extension, new Package[0]);
    }

    public static void createExtensionModule(File extensionModuleRoot, Class<? extends Extension> extension, Package... additionalPackages) throws IOException {
        deleteRecursively(extensionModuleRoot.toPath());

        if (extensionModuleRoot.exists() && !extensionModuleRoot.isDirectory()) {
            throw new IllegalArgumentException(extensionModuleRoot + " already exists and is not a directory");
        }
        File file = new File(extensionModuleRoot, "main");
        if (!file.mkdirs() && !file.exists()) {
            throw new IllegalArgumentException("Could not create " + file);
        }
        final InputStream is = createResourceRoot(extension, additionalPackages).exportAsInputStream();
        try {
            copyFile(new File(file, JAR_NAME), is);
        } finally {
            IoUtils.safeClose(is);
        }

        URL url = extension.getResource("module.xml");
        if (url == null) {
            throw new IllegalStateException("Could not find module.xml");
        }
        copyFile(new File(file, "module.xml"), url.openStream());
    }

    public static void deleteExtensionModule(String moduleName) {
        deleteRecursively(getExtensionModuleRoot(moduleName).toPath());
    }

    public static void deleteExtensionModule(File extensionModuleRoot) {
        deleteRecursively(extensionModuleRoot.toPath());
    }

    private static void copyFile(File target, InputStream src) throws IOException {
        final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target));
        try {
            int i = src.read();
            while (i != -1) {
                out.write(i);
                i = src.read();
            }
        } finally {
            IoUtils.safeClose(out);
        }
    }

    public static File getModulePath() {
        String modulePath = System.getProperty("module.path", null);
        if (modulePath == null) {
            String jbossHome = System.getProperty("jboss.home", null);
            if (jbossHome == null) {
                throw new IllegalStateException("Neither -Dmodule.path nor -Djboss.home were set");
            }
            modulePath = jbossHome + File.separatorChar + "modules";
        }else{
            modulePath = modulePath.split(File.pathSeparator)[0];
        }
        File moduleDir = new File(modulePath);
        if (!moduleDir.exists()) {
            throw new IllegalStateException("Determined module path does not exist");
        }
        if (!moduleDir.isDirectory()) {
            throw new IllegalStateException("Determined module path is not a dir");
        }
        return moduleDir;
    }

    private static File getExtensionModuleRoot(String extensionName) {
        File file = getModulePath();
        for (String element : extensionName.split("\\.")) {
            file = new File(file, element);
        }
        return file;
    }

    private static StreamExporter createResourceRoot(Class<? extends Extension> extension, Package... additionalPackages) throws IOException {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        storePackage(extension.getPackage(), extension.getClassLoader(), archive);
        if (additionalPackages != null) {
            for (Package pkg : additionalPackages) {
                storePackage(pkg, extension.getClassLoader(), archive);
            }
        }

        archive.addAsServiceProvider(Extension.class, extension);
        return archive.as(ZipExporter.class);
    }

    private static void storePackage(Package pkg, ClassLoader classLoader, JavaArchive archive) throws IOException {
        archive.addPackage(pkg);

        // Store misc files that shrinkwrap apparently doesn't
        String packagePath = pkg.getName().replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(packagePath);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            String urlPath = url.getFile();
            int bangIndex = urlPath.indexOf('!');
            if (bangIndex < 0) {
                storeMiscPackageContentsFromDirectory(url, packagePath, classLoader, archive);
            } else {
                String archivePath = urlPath.substring(0, bangIndex);
                storeMiscPackageContentsFromArchive(archivePath, packagePath, classLoader, archive);
            }
        }
    }

    private static void storeMiscPackageContentsFromDirectory(URL directory, String packagePath,
                                                              ClassLoader classLoader, JavaArchive archive) {
        try {
            File file = new File(directory.toURI());
            File[] children;
            if (file.isDirectory() && (children = file.listFiles()) != null) {
                for (File child : children) {
                    if (!child.isDirectory() && !child.getName().endsWith(".class")) {
                        String name = packagePath + '/' + child.getName() ;
                        archive.addAsResource(new ClassLoaderAsset(name, classLoader), name);
                    }

                }
            }
        } catch (URISyntaxException | IllegalArgumentException e) {
            // ignore; sometimes the package is also visible on the classpath inside other jars
            // resulting in a URL that cannot be used to create a file
        }

    }

    private static void storeMiscPackageContentsFromArchive(String archivePath, String packagePath,
                                                              ClassLoader classLoader, JavaArchive archive) throws IOException {

        try {
            if (archivePath.startsWith("file:")) {
                archivePath = archivePath.substring(5);
            }
            ZipFile zip = new ZipFile(archivePath);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(packagePath) && !name.endsWith(".class")
                        && !name.substring(packagePath.length() + 1).contains("/")
                        && name.charAt(name.length() - 1) != '/') {
                    archive.addAsResource(new ClassLoaderAsset(name, classLoader), name);
                }
            }
        } catch (ZipException e) {
            throw new RuntimeException("Error handling file " + archivePath, e);
        }

    }

    private static void deleteRecursively(Path path) {
        if (path == null) {
            return;
        }
        if (Files.notExists(path)){
            return ;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }

            });
        } catch (IOException e) {
            System.out.println("Could not delete file = " + e.getMessage());
        }
    }
}
