/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.core.jar.boot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;

/**
 * Bootable jar Main class.
 *
 * @author jdenise
 */
public final class Main {

    private static final String SYSPROP_KEY_CLASS_PATH = "java.class.path";
    private static final String SYSPROP_KEY_MODULE_PATH = "module.path";
    private static final String SYSPROP_KEY_SYSTEM_MODULES = "jboss.modules.system.pkgs";

    private static final String JBOSS_MODULES_DIR_NAME = "modules";

    private static final String MODULE_ID_JAR_RUNTIME = "org.wildfly.bootable-jar";

    private static final String BOOTABLE_JAR = "org.wildfly.core.jar.runtime.BootableJar";
    private static final String BOOTABLE_JAR_RUN_METHOD = "run";

    private static final String INSTALL_DIR = "--install-dir";

    private static final String WILDFLY_RESOURCE = "/wildfly.zip";
    private static final String WILDFLY_BOOTABLE_TMP_DIR_PREFIX = "wildfly-bootable-server";

    public static void main(String[] args) throws Exception {

        List<String> filteredArgs = new ArrayList<>();
        Path installDir = null;
        for (String arg : args) {
            if (arg.startsWith(INSTALL_DIR)) {
                installDir = Paths.get(getValue(arg));
            } else {
                filteredArgs.add(arg);
            }
        }

        installDir = installDir == null ? Files.createTempDirectory(WILDFLY_BOOTABLE_TMP_DIR_PREFIX) : installDir;
        long t = System.currentTimeMillis();
        try (InputStream wf = Main.class.getResourceAsStream(WILDFLY_RESOURCE)) {
            if (wf == null) {
                throw new Exception("Resource " + WILDFLY_RESOURCE + " doesn't exist, can't run.");
            }
            unzip(wf, installDir.toFile());
        }
        runBootableJar(installDir, filteredArgs, System.currentTimeMillis() - t);
    }

    private static String getValue(String arg) {
        int sep = arg.indexOf("=");
        if (sep == -1 || sep == arg.length() - 1) {
            throw new RuntimeException("Invalid argument " + arg + ", no value provided");
        }
        return arg.substring(sep + 1);
    }

    private static void runBootableJar(Path jbossHome, List<String> arguments, Long unzipTime) throws Exception {
        final String modulePath = jbossHome.resolve(JBOSS_MODULES_DIR_NAME).toAbsolutePath().toString();
        ModuleLoader moduleLoader = setupModuleLoader(modulePath);
        final Module bootableJarModule;
        try {
            bootableJarModule = moduleLoader.loadModule(MODULE_ID_JAR_RUNTIME);
        } catch (final ModuleLoadException mle) {
            throw new Exception(mle);
        }

        final ModuleClassLoader moduleCL = bootableJarModule.getClassLoader();
        final Class<?> bjFactoryClass;
        try {
            bjFactoryClass = moduleCL.loadClass(BOOTABLE_JAR);
        } catch (final ClassNotFoundException cnfe) {
            throw new Exception(cnfe);
        }
        Method runMethod;
        try {
            runMethod = bjFactoryClass.getMethod(BOOTABLE_JAR_RUN_METHOD, Path.class, List.class, ModuleLoader.class, ModuleClassLoader.class, Long.class);
        } catch (final NoSuchMethodException nsme) {
            throw new Exception(nsme);
        }
        runMethod.invoke(null, jbossHome, arguments, moduleLoader, moduleCL, unzipTime);
    }

    private static void unzip(InputStream wf, File dir) throws Exception {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(wf)) {
            ZipEntry ze = zis.getNextEntry();
            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(dir, fileName);
                if (fileName.endsWith("/")) {
                    newFile.mkdirs();
                    zis.closeEntry();
                    ze = zis.getNextEntry();
                    continue;
                }
                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
        }
    }

    private static String trimPathToModulesDir(String modulePath) {
        int index = modulePath.indexOf(File.pathSeparator);
        return index == -1 ? modulePath : modulePath.substring(0, index);
    }

    // Copied from Embedded, lightly updated.
    private static ModuleLoader setupModuleLoader(final String modulePath) {
        assert modulePath != null : "modulePath not null";

        // verify the first element of the supplied modules path exists, and if it does not, stop and allow the user to correct.
        // Once modules are initialized and loaded we can't change Module.BOOT_MODULE_LOADER (yet).
        final Path moduleDir = Paths.get(trimPathToModulesDir(modulePath));
        if (Files.notExists(moduleDir) || !Files.isDirectory(moduleDir)) {
            throw new RuntimeException("The first directory of the specified module path " + modulePath + " is invalid or does not exist.");
        }

        final String classPath = System.getProperty(SYSPROP_KEY_CLASS_PATH);
        try {
            // Set up sysprop env
            System.clearProperty(SYSPROP_KEY_CLASS_PATH);
            System.setProperty(SYSPROP_KEY_MODULE_PATH, modulePath);

            final StringBuilder packages = new StringBuilder("org.jboss.modules");
            String custompackages = System.getProperty(SYSPROP_KEY_SYSTEM_MODULES);
            if (custompackages != null) {
                packages.append(",").append(custompackages);
            }
            System.setProperty(SYSPROP_KEY_SYSTEM_MODULES, packages.toString());

            // Get the module loader
            return Module.getBootModuleLoader();
        } finally {
            // Return to previous state for classpath prop
            if (classPath != null) {
                System.setProperty(SYSPROP_KEY_CLASS_PATH, classPath);
            }
        }
    }
}
