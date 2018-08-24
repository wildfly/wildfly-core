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

package org.jboss.as.process.jdk;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

import org.jboss.as.process.logging.ProcessLogger;

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
    private final boolean forLaunch;
    private final boolean isModularJdk;
    private final String javaExecutable;

    static {
        final String os = SecurityUtils.getSystemProperty(OS_NAME_SYS_PROP).toLowerCase(Locale.ROOT);
        JAVA_EXECUTABLE = os.contains("win") ? "java.exe" : "java";

        final ArrayList<String> modularJavaOpts = new ArrayList<>();
        modularJavaOpts.add("--add-exports=java.base/sun.nio.ch=ALL-UNNAMED");
        modularJavaOpts.add("--add-exports=jdk.unsupported/sun.reflect=ALL-UNNAMED");
        modularJavaOpts.add("--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED");
        modularJavaOpts.add("--add-modules=java.se");
        DEFAULT_MODULAR_JDK_ARGUMENTS = Collections.unmodifiableList(modularJavaOpts);
    }

    private JdkType(final boolean forLaunch, final boolean isModularJdk, final String javaExecutable) {
        this.forLaunch = forLaunch;
        this.isModularJdk = isModularJdk;
        this.javaExecutable = javaExecutable;
    }

    public String getJavaExecutable() {
        return javaExecutable;
    }

    /**
     * Was this object created for the purpose of launching a process, or simply as a holder of data?
     * @return {@code} true if this object was created for the purpose of launching a process.
     */
    public boolean isForLaunch() {
        return forLaunch;
    }

    /**
     * Whether this JVM type is modular. This value is only reliable if {@link #isForLaunch()} returns
     * {@code true}, as otherwise checks for JVM modularity may not be performed or may not be accurate.
     * @return {@code true} if the JVM is known to be modular, {@code false} otherwise
     */
    public boolean isModularJdk() {
        return isModularJdk;
    }

    public Collection<String> getDefaultArguments() {
        return isModularJdk ? DEFAULT_MODULAR_JDK_ARGUMENTS : Collections.EMPTY_LIST;
    }

    /**
     * Create a {@code JdkType} based on the location of the root dir of the JRE/JDK installation.
     * @param javaHome the root dir of the JRE/JDK installation. Cannot be {@code null} or empty
     * @param forLaunch {@code true} if the created object will be used for launching servers; {@code false}
     *                              if it is simply a data holder. A value of {@code true} will disable
     *                              some validity checks and may disable determining if the JVM is modular
     * @return the {@code JdkType}. Will not return {@code null}
     *
     * @throws IllegalStateException if the {@code JdkType} cannot be determined.
     */
    public static JdkType createFromJavaHome(final String javaHome, boolean forLaunch) {
        if (javaHome == null || javaHome.trim().equals("")) {
            throw ProcessLogger.ROOT_LOGGER.invalidJavaHome(javaHome);
        }
        final File javaHomeDir = new File(javaHome);
        if (forLaunch && !javaHomeDir.exists()) {
            throw ProcessLogger.ROOT_LOGGER.invalidJavaHome(javaHomeDir.getAbsolutePath());
        }
        final File javaBinDir = new File(javaHomeDir, BIN_DIR);
        if (forLaunch && !javaBinDir.exists()) {
            throw ProcessLogger.ROOT_LOGGER.invalidJavaHomeBin(javaBinDir.getAbsolutePath(), javaHomeDir.getAbsolutePath());
        }
        final File javaExecutable = new File(javaBinDir, JAVA_EXECUTABLE);
        if (forLaunch && !javaExecutable.exists()) {
            throw ProcessLogger.ROOT_LOGGER.cannotFindJavaExe(javaBinDir.getAbsolutePath());
        }
        return new JdkType(forLaunch, isModularJDK(javaHomeDir, forLaunch), javaExecutable.getAbsolutePath());
    }

    private static boolean isModularJDK(File javaHomeDir, boolean forLaunch) {
        // Only do potentially failing modular vm detection if the object
        // will be used for launching a server.
        if (forLaunch) {
            // FIXME this assumes a certain structure; try to spawn a vm process instead
            return new File(javaHomeDir, JMODS_DIR).exists();
        }
        return false;
    }

    /**
     * Create a {@code JdkType} based on the location of the java executable.
     * @param javaExecutable the location of the java executable. Cannot be {@code null} or empty
     * @param forLaunch {@code true} if the created object will be used for launching servers; {@code false}
     *                              if it is simply a data holder. A value of {@code true} will disable
     *                              some validity checks and may disable determining if the JVM is modular
     * @return the {@code JdkType}. Will not return {@code null}
     */
    public static JdkType createFromJavaExecutable(final String javaExecutable, boolean forLaunch) {
        assert javaExecutable != null;

        if (javaExecutable.equals(JAVA_WIN_EXECUTABLE) || javaExecutable.equals(JAVA_UNIX_EXECUTABLE)) {
            return createFromEnvironmentVariable(forLaunch);
        } else {
            final File javaExe = new File(javaExecutable);
            if (forLaunch && !javaExe.exists()) {
                throw ProcessLogger.ROOT_LOGGER.cannotFindJavaExe(javaExecutable);
            }
            // FIXME. We should not require this structure in order to determine if a JDK is modular, e.g.
            // /lib/java is a valid value for javaExecutable but this algorithm rejects it
            final File javaBinDir = javaExe.getParentFile();
            if (javaBinDir == null) {
                // We reject this even if forLaunch==false as we'll NPE otherwise. But fixing FIXME makes this go away
                throw ProcessLogger.ROOT_LOGGER.invalidJavaHomeBin("null", "null");
            }
            if (forLaunch && !javaBinDir.exists()) {
                throw ProcessLogger.ROOT_LOGGER.invalidJavaHomeBin(javaBinDir.getAbsolutePath(), "null");
            }
            final File javaHomeDir = javaBinDir.getParentFile();
            if (javaHomeDir == null) {
                // We reject this even if forLaunch==false as we'll NPE otherwise. But fixing FIXME makes this go away
                throw ProcessLogger.ROOT_LOGGER.invalidJavaHome("null");
            }
            if (forLaunch && !javaHomeDir.exists()) {
                throw ProcessLogger.ROOT_LOGGER.invalidJavaHome(javaHomeDir.getAbsolutePath());
            }
            return createFromJavaHome(javaHomeDir.getAbsolutePath(), forLaunch);
        }
    }

    /**
     * Create a {@code JdkType} based on the location of the root dir of the JRE/JDK installation, as specified
     * by the system property {@code java.home}.
     * @param forLaunch {@code true} if the created object will be used for launching servers; {@code false}
     *                              if it is simply a data holder. A value of {@code true} will disable
     *                              some validity checks and may disable determining if the JVM is modular
     * @return the {@code JdkType}. Will not return {@code null}
     */
    public static JdkType createFromSystemProperty(boolean forLaunch) {
        return createFromJavaHome(SecurityUtils.getSystemProperty(JAVA_HOME_SYS_PROP), forLaunch);
    }

    private static JdkType createFromEnvironmentVariable(boolean forLaunch) {
        final String envJavaHome = SecurityUtils.getSystemVariable(JAVA_HOME_ENV_VAR);
        if (envJavaHome != null && !"".equals(envJavaHome.trim())) {
            return createFromJavaHome(envJavaHome, forLaunch);
        } else {
            return createFromSystemProperty(forLaunch);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JdkType jdkType = (JdkType) o;

        if (forLaunch != jdkType.forLaunch) return false;
        if (isModularJdk != jdkType.isModularJdk) return false;
        return javaExecutable.equals(jdkType.javaExecutable);
    }

    @Override
    public int hashCode() {
        int result = (forLaunch ? 1 : 0);
        result = 31 * result + (isModularJdk ? 1 : 0);
        result = 31 * result + javaExecutable.hashCode();
        return result;
    }
}
