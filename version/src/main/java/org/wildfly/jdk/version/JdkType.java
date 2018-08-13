/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
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

package org.wildfly.jdk.version;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

/**
 * Jdk type detection utility.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class JdkType {

    private static final String BIN_DIR = "bin";
    private static final String JMODS_DIR = "jmods";
    private static final String JAVA_HOME_SYS_PROP = "java.home";
    private static final String JAVA_HOME_ENV_VAR = "JAVA_HOME";
    private static final String OS_NAME_SYS_PROP = "os.name";
    private static final Collection<String> DEFAULT_MODULAR_JDK_ARGUMENTS;
    private static final String JAVA_EXECUTABLE;
    private static final String JAVA_UNIX_EXECUTABLE = "java";
    private static final String JAVA_WIN_EXECUTABLE = "java.exe";
    private final boolean isModularJdk;
    private final String javaHome;
    private final String javaExecutable;

    static {
        final String os = SecurityUtils.getSystemProperty(OS_NAME_SYS_PROP).toLowerCase(Locale.ROOT);
        JAVA_EXECUTABLE = os.contains("win") ? "java.exe" : "java";

        final ArrayList<String> modularJavaOpts = new ArrayList<>();
        modularJavaOpts.add("--add-exports=java.base/sun.nio.ch=ALL-UNNAMED");
        modularJavaOpts.add("--add-exports=jdk.unsupported/sun.reflect=ALL-UNNAMED");
        modularJavaOpts.add("--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED");
        modularJavaOpts.add("--illegal-access=permit");
        modularJavaOpts.add("--add-modules=java.se");
        DEFAULT_MODULAR_JDK_ARGUMENTS = Collections.unmodifiableList(modularJavaOpts);
    }

    private JdkType(final boolean isModularJdk, final String javaHome, final String javaExecutable) {
        this.isModularJdk = isModularJdk;
        this.javaHome = javaHome;
        this.javaExecutable = javaExecutable;
    }

    public String getJavaHome() {
        return javaHome;
    }

    public String getJavaExecutable() {
        return javaExecutable;
    }

    public Collection<String> getDefaultArguments() {
        return isModularJdk ? DEFAULT_MODULAR_JDK_ARGUMENTS : Collections.EMPTY_LIST;
    }

    public static JdkType createFromJavaHome(final File javaHome) {
        return javaHome == null ? createFromJavaHome((String) null) : createFromJavaHome(javaHome.getAbsolutePath());
    }

    public static JdkType createFromJavaHome(final String javaHome) {
        if (javaHome == null || javaHome.trim().equals("")) {
            throw JdkVersionLogger.ROOT_LOGGER.invalidJavaHome(javaHome);
        }
        final File javaHomeDir = new File(javaHome);
        if (!javaHomeDir.exists()) {
            throw JdkVersionLogger.ROOT_LOGGER.invalidJavaHome(javaHomeDir.getAbsolutePath());
        }
        final File javaBinDir = new File(javaHomeDir, BIN_DIR);
        if (!javaBinDir.exists()) {
            throw JdkVersionLogger.ROOT_LOGGER.invalidJavaHomeBin(javaBinDir.getAbsolutePath(), javaHomeDir.getAbsolutePath());
        }
        final File javaExecutable = new File(javaBinDir, JAVA_EXECUTABLE);
        if (!javaExecutable.exists()) {
            throw JdkVersionLogger.ROOT_LOGGER.cannotFindJavaExe(javaBinDir.getAbsolutePath());
        }
        return new JdkType(new File(javaHomeDir, JMODS_DIR).exists(), javaHomeDir.getAbsolutePath(), javaExecutable.getAbsolutePath());
    }

    public static JdkType createFromJavaExecutable(final String javaExecutable) {
        if (javaExecutable == null) return createFromSystemProperty();

        if (javaExecutable.equals(JAVA_WIN_EXECUTABLE) || javaExecutable.equals(JAVA_UNIX_EXECUTABLE)) {
            return createFromEnvironmentVariable();
        } else {
            final File javaExe = new File(javaExecutable);
            if (!javaExe.exists()) {
                throw JdkVersionLogger.ROOT_LOGGER.cannotFindJavaExe(javaExecutable);
            }
            final File javaBinDir = javaExe.getParentFile();
            if (javaBinDir == null) {
                throw JdkVersionLogger.ROOT_LOGGER.invalidJavaHomeBin("null", "null");
            }
            if (!javaBinDir.exists()) {
                throw JdkVersionLogger.ROOT_LOGGER.invalidJavaHomeBin(javaBinDir.getAbsolutePath(), "null");
            }
            final File javaHomeDir = javaBinDir.getParentFile();
            if (javaHomeDir == null) {
                throw JdkVersionLogger.ROOT_LOGGER.invalidJavaHome("null");
            }
            if (!javaHomeDir.exists()) {
                throw JdkVersionLogger.ROOT_LOGGER.invalidJavaHome(javaHomeDir.getAbsolutePath());
            }
            return createFromJavaHome(javaHomeDir);
        }
    }

    public static JdkType createFromSystemProperty() {
        return createFromJavaHome(SecurityUtils.getSystemProperty(JAVA_HOME_SYS_PROP));
    }

    public static JdkType createFromEnvironmentVariable() {
        final String envJavaHome = SecurityUtils.getSystemVariable(JAVA_HOME_ENV_VAR);
        if (envJavaHome != null && !"".equals(envJavaHome.trim())) {
            return createFromJavaHome(envJavaHome);
        } else {
            return createFromSystemProperty();
        }
    }

}
