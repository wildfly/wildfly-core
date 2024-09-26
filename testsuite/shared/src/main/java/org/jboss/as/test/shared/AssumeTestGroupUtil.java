/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.shared;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.Supplier;

import org.junit.Assume;

/**
 * Helper methods which help to skip tests that are not appropriate for execution in particular tests environments.
 * Call these methods directly in a particular test method or, if you want to skip whole test class,
 * put the method call into method annotated with {@link org.junit.BeforeClass}.
 *
 */
public final class AssumeTestGroupUtil {

    /**
     * Assume for tests that fail when the security manager is enabled. This should be used sparingly and issues should
     * be filed for failing tests so a proper fix can be done.
     * <p>
     * Note that this checks the {@code security.manager} system property and <strong>not</strong> that the
     * {@link System#getSecurityManager()} is {@code null}. The property is checked so that the assumption check can be
     * done in a {@link org.junit.Before @Before} or {@link org.junit.BeforeClass @BeforeClass} method.
     * </p>
     */
    public static void assumeSecurityManagerDisabled() {
        assumeCondition("Tests failing if the security manager is enabled.", AssumeTestGroupUtil::isSecurityManagerDisabled);
    }

    /**
     * Check if the JDK Security Manager is <strong>not</strong> enabled.
     * <p>
     * Note that this checks the {@code security.manager} system property and <strong>not</strong> that the
     * {@link System#getSecurityManager()} is {@code null}. The property is checked so that the assumption check can be
     * done in a {@link org.junit.Before @Before} or {@link org.junit.BeforeClass @BeforeClass} method.
     * </p>
     *
     * @return {@code true} if the {@code security.manager} system property is null.
     */
    public static boolean isSecurityManagerDisabled() {
        return System.getProperty("security.manager") == null;
    }

    /**
     * Assume for tests that fail when the JVM version is too low. This should be used sparingly.
     *
     * @param javaSpecificationVersion the JDK specification version. Use 11 for JDK 11. Must be 11 or higher.
     */
    public static void assumeJDKVersionAfter(int javaSpecificationVersion) {
        assert javaSpecificationVersion >= 11; // we only support 11 or later
        assumeCondition("Tests failing if the JDK in use is before " + javaSpecificationVersion + ".",
                () -> isJDKVersionAfter(javaSpecificationVersion));
    }

    /**
     * Assume for tests that fail when the JVM version is too high. This should be used sparingly.
     *
     * @param javaSpecificationVersion the JDK specification version. Must be 11 or higher.
     */
    public static void assumeJDKVersionBefore(int javaSpecificationVersion) {
        assert javaSpecificationVersion > 11; // we only support 11 or later so no reason to call this for 11
        assumeCondition("Tests failing if the JDK in use is after " + javaSpecificationVersion + ".",
                () -> isJDKVersionBefore(javaSpecificationVersion));
    }

    /**
     * Check if the current JDK specification version is greater than the given value.
     *
     * @param javaSpecificationVersion the JDK specification version. Use 11 for JDK 11.
     */
    public static boolean isJDKVersionAfter(int javaSpecificationVersion) {
        return getJavaSpecificationVersion() > javaSpecificationVersion;
    }

    /**
     * Check if the current JDK specification version is less than the given value.
     *
     * @param javaSpecificationVersion the JDK specification version. Use 11 for JDK 11.
     */
    public static boolean isJDKVersionBefore(int javaSpecificationVersion) {
        return getJavaSpecificationVersion() < javaSpecificationVersion;
    }

    private static int getJavaSpecificationVersion() {
        return Runtime.version().feature();
    }

    private static void assumeCondition(final String message, final Supplier<Boolean> assumeTrueCondition) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                Assume.assumeTrue(message, assumeTrueCondition.get());
                return null;
            }
        });
    }

    private AssumeTestGroupUtil() {
        // prevent instantiation
    }

    public static void assumeNotBootableJar() {
        assumeCondition("Some tests cannot run in Bootable JAR packaging",
                () -> !isBootableJar());
    }

    public static boolean isBootableJar() {
        return System.getProperty("ts.bootable") != null;
    }
}
