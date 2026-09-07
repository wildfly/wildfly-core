/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.jar.boot;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.Policy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public final class Main {

    private static final String SYSPROP_KEY_CLASS_PATH = "java.class.path";
    private static final String SYSPROP_KEY_MODULE_PATH = "module.path";
    private static final String SYSPROP_KEY_SYSTEM_MODULES = "jboss.modules.system.pkgs";

    private static final String JBOSS_MODULES_DIR_NAME = "modules";

    private static final String MODULE_ID_JAR_RUNTIME = "org.wildfly.bootable-jar";

    private static final String BOOTABLE_JAR = "org.wildfly.core.jar.runtime.BootableJar";
    private static final String BOOTABLE_JAR_RUN_METHOD = "run";
    private static final String BOOTABLE_JAR_RUNTIME_CONFIGURATOR_CLASS = "org.wildfly.core.jar.runtime.BootableServerConfigurator";
    private static final String BOOTABLE_JAR_RUNTIME_CONFIGURATOR_CONFIGURATION_CLASS = "org.wildfly.core.jar.runtime.Configuration";
    private static final String BOOTABLE_JAR_RUNTIME_CONFIGURATOR_METHOD_NAME = "configure";
    private static final String BOOTABLE_JAR_RUNTIME_CONFIGURATOR_CLI_OPS_METHOD_NAME = "getCliOperations";
    private static final String BOOTABLE_JAR_RUNTIME_CONFIGURATOR_ARGS_METHOD_NAME = "getArguments";

    private static final String INSTALL_DIR = "--install-dir";
    private static final String SECMGR = "-secmgr";
    private static final String DISPLAY_GALLEON_CONFIG = "--display-galleon-config";

    private static final String WILDFLY_RESOURCE = "/wildfly.zip";

    private static final String PROVISIONING_RESOURCE = "/provisioning.xml";

    private static final String WILDFLY_BOOTABLE_TMP_DIR_PREFIX = "wildfly-bootable-server";

    private static final Set<PosixFilePermission> EXECUTE_PERMISSIONS = new HashSet<>();

    static {
        EXECUTE_PERMISSIONS.add(PosixFilePermission.OWNER_EXECUTE);
        EXECUTE_PERMISSIONS.add(PosixFilePermission.OWNER_WRITE);
        EXECUTE_PERMISSIONS.add(PosixFilePermission.OWNER_READ);
        EXECUTE_PERMISSIONS.add(PosixFilePermission.GROUP_EXECUTE);
        EXECUTE_PERMISSIONS.add(PosixFilePermission.GROUP_WRITE);
        EXECUTE_PERMISSIONS.add(PosixFilePermission.GROUP_READ);
        EXECUTE_PERMISSIONS.add(PosixFilePermission.OTHERS_EXECUTE);
        EXECUTE_PERMISSIONS.add(PosixFilePermission.OTHERS_READ);
    }

    public static void main(String[] args) throws Exception {

        List<String> filteredArgs = new ArrayList<>();
        Path installDir = null;
        boolean securityManager = false;
        boolean displayGalleonConfig = false;

        for (String arg : args) {
            if (arg.startsWith(INSTALL_DIR)) {
                installDir = Paths.get(getValue(arg));
            } else if (SECMGR.equals(arg)) {
                securityManager = true;
            } else if (DISPLAY_GALLEON_CONFIG.equals(arg)) {
                displayGalleonConfig = true;
            } else {
                filteredArgs.add(arg);
            }
        }

        if (displayGalleonConfig) {
            try (InputStream provisioningFile = Main.class.getResourceAsStream(PROVISIONING_RESOURCE)) {
                if (provisioningFile == null) {
                    throw new Exception("Resource " + PROVISIONING_RESOURCE + " doesn't exist, can't retrieve galleon configuration.");
                }
                try(InputStreamReader reader = new InputStreamReader(provisioningFile, StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(reader)) {
                    while(br.ready()) {
                        System.out.println(br.readLine());
                    }
                }
            }
            return;
        }

        final SecurityManager existingSecMgr = System.getSecurityManager();
        if (existingSecMgr != null) {
            throw new Exception("An existing security manager was detected.  You must use the -secmgr switch to start with a security manager.");
        }

        installDir = installDir == null ? Files.createTempDirectory(WILDFLY_BOOTABLE_TMP_DIR_PREFIX) : installDir;
        long t = System.currentTimeMillis();
        // If the same install directory is being used and a previous delete is happening we should attempt to wait for
        // the directory to be deleted before we extract our container.
        waitForDelete(installDir);

        // If the directory already exists and there is a PID file the container is already running and we should not
        // attempt to overwrite it.
        final Path pidFile = installDir.resolve(getPidFileName());
        if (Files.exists(pidFile)) {
            throw new IllegalStateException(String.format("The server has already been extracted to \"%s\" and appears " +
                    "to be running.", installDir));
        }

        try (InputStream wf = Main.class.getResourceAsStream(WILDFLY_RESOURCE)) {
            if (wf == null) {
                throw new Exception("Resource " + WILDFLY_RESOURCE + " doesn't exist, can't run.");
            }
            unzip(wf, installDir);
        }

        //Extensions are injected by the maven plugin during packaging.
        ServiceLoader<RuntimeExtension> loader = ServiceLoader.load(RuntimeExtension.class);
        for (RuntimeExtension extension : loader) {
            extension.boot(filteredArgs, installDir);
        }

        runBootableJar(installDir, filteredArgs, System.currentTimeMillis() - t, securityManager);
    }

    private static String getValue(String arg) {
        int sep = arg.indexOf("=");
        if (sep == -1 || sep == arg.length() - 1) {
            throw new RuntimeException("Invalid argument " + arg + ", no value provided");
        }
        return arg.substring(sep + 1);
    }

    private static void runBootableJar(Path jbossHome, List<String> arguments, Long unzipTime, boolean securityManager) throws Exception {
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
        URL url = bootableJarModule.getExportedResource("bootable-configurators.properties");
        Properties p = new Properties();
        try (InputStream in = url.openStream()) {
            p.load(in);
        }
        List<String> cliCmds = null;
        if (!p.isEmpty()) {
            cliCmds = new ArrayList<>();
            Class<?> configuratorClass = moduleCL.loadClass(BOOTABLE_JAR_RUNTIME_CONFIGURATOR_CLASS);
            Class<?> configurationClass = moduleCL.loadClass(BOOTABLE_JAR_RUNTIME_CONFIGURATOR_CONFIGURATION_CLASS);
            Method configure = configuratorClass.getMethod(BOOTABLE_JAR_RUNTIME_CONFIGURATOR_METHOD_NAME, List.class, Path.class);
            Method getCliOperations = configurationClass.getMethod(BOOTABLE_JAR_RUNTIME_CONFIGURATOR_CLI_OPS_METHOD_NAME);
            Method getArguments = configurationClass.getMethod(BOOTABLE_JAR_RUNTIME_CONFIGURATOR_ARGS_METHOD_NAME);
            List<String> args = Collections.unmodifiableList(arguments);
            for (String k : p.stringPropertyNames()) {
                String moduleName = p.getProperty(k);
                Module bootableConfiguratorModule = moduleLoader.loadModule(moduleName);
                ServiceLoader<?> loader = bootableConfiguratorModule.loadService(configuratorClass);
                for (Object configurator : loader) {
                    Object config = configure.invoke(configurator, args, jbossHome);
                    if (config != null) {
                        @SuppressWarnings("unchecked")
                        List<String> cliOps = (List<String>) getCliOperations.invoke(config);
                        if (cliOps != null && !cliOps.isEmpty()) {
                            cliCmds.addAll(cliOps);
                        }
                        @SuppressWarnings("unchecked")
                        List<String> extraArguments = (List<String>) getArguments.invoke(config);
                        if (extraArguments != null && !extraArguments.isEmpty()) {
                            arguments.addAll(extraArguments);
                        }
                    }
                }
            }
        }
        Method runMethod;
        try {
            runMethod = bjFactoryClass.getMethod(BOOTABLE_JAR_RUN_METHOD, Path.class, List.class, ModuleLoader.class, ModuleClassLoader.class, Long.class, List.class);
        } catch (final NoSuchMethodException nsme) {
            throw new Exception(nsme);
        }

        // Wait until the last moment and install the SecurityManager.
        if (securityManager) {
            final BootablePolicy policy = new BootablePolicy(Policy.getPolicy());
            Policy.setPolicy(policy);

            final Iterator<SecurityManager> iterator = bootableJarModule.loadService(SecurityManager.class).iterator();
            if (iterator.hasNext()) {
                System.setSecurityManager(iterator.next());
            } else {
                throw new IllegalStateException("No SecurityManager found to install.");
            }
        }

        runMethod.invoke(null, jbossHome, arguments, moduleLoader, moduleCL, unzipTime, cliCmds);
    }

    private static void unzip(InputStream wf, Path dir) throws Exception {
        boolean isWindows = isWindows();
        try (ZipInputStream zis = new ZipInputStream(wf)) {
            ZipEntry ze = zis.getNextEntry();
            while (ze != null) {
                String fileName = ze.getName();
                Path newFile = dir.resolve(fileName);
                if (!newFile.normalize().startsWith(dir.normalize())) {
                    throw new IOException("Bad zip entry");
                }
                if (ze.isDirectory()) {
                    Files.createDirectories(newFile);
                } else {
                    // Create any parent directories that may be required before the copy
                    final Path parent = newFile.getParent();
                    if (parent != null && Files.notExists(parent)) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(zis, newFile, StandardCopyOption.REPLACE_EXISTING);
                    if (!isWindows && newFile.getFileName().toString().endsWith(".sh")) {
                        Files.setPosixFilePermissions(newFile, EXECUTE_PERMISSIONS);
                    }
                }
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows");
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

    private static String getPidFileName() {
        String pidFileName = System.getProperty("org.wildfly.core.bootable.jar.pidFile");
        if (pidFileName == null) {
            pidFileName = System.getenv("JBOSS_PIDFILE");
            if (pidFileName == null) {
                pidFileName = "wildfly.pid";
            }
        }
        return pidFileName;
    }

    @SuppressWarnings("MagicNumber")
    private static void waitForDelete(final Path installDir) throws InterruptedException {
        final Path cleanupMarker = installDir.resolve("wildfly-cleanup-marker");
        if (Files.exists(cleanupMarker)) {
            long t;
            try {
                t = Long.parseLong(System.getProperty("org.wildfly.core.bootable.jar.timeout", "10"));
            } catch (NumberFormatException ignore) {
                t = 10L; // 10 seconds
            }
            long timeout = (t * 1000L);
            while (Files.exists(cleanupMarker)) {
                final long wait = 500L;
                TimeUnit.MILLISECONDS.sleep(wait);
                timeout -= wait;
                if (timeout <= 0L) {
                    if (Files.exists(cleanupMarker)) {
                        final String msg = String.format("The install directory %s may still be in the process of being deleted. " +
                                        "The marker file %s has not been deleted within %ds. Please check for a previous job running " +
                                        "and delete the marker file if it was left behind because of an error.",
                                installDir, cleanupMarker, t);
                        throw new IllegalStateException(msg);
                    }
                    break;
                }
            }
        }
    }
}
