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
        assumeCondition("Tests failing if the security manager is enabled.", () -> System.getProperty("security.manager") == null);
    }

    /**
     * Assume for tests that fail when the JVM version is too low. This should be used sparingly.
     *
     * @param javaSpecificationVersion the JDK specification version. Use 8 for JDK 8. Must be 8 or higher.
     */
    public static void assumeJDKVersionAfter(int javaSpecificationVersion) {
        assert javaSpecificationVersion >= 8; // we only support 8 or later so no reason to call this for 8
        assumeCondition("Tests failing if the JDK in use is before " + javaSpecificationVersion + ".",
                () -> getJavaSpecificationVersion() > javaSpecificationVersion);
    }

    /**
     * Assume for tests that fail when the JVM version is too high. This should be used sparingly.
     *
     * @param javaSpecificationVersion the JDK specification version. Must be 9 or higher.
     */
    public static void assumeJDKVersionBefore(int javaSpecificationVersion) {
        assert javaSpecificationVersion >= 9; // we only support 8 or later so no reason to call this for 8
        assumeCondition("Tests failing if the JDK in use is after " + javaSpecificationVersion + ".",
                () -> getJavaSpecificationVersion() < javaSpecificationVersion);
    }

    // BES 2020/05/18 I added this along with assumeJDKVersionAfter/assumeJDKVersionBefore but commented it
    // out because using it seems like bad practice. If there's a legit need some day, well, here's the code...
//    /**
//     * Assume for tests that fail when the JVM version is something. This should be used sparingly and issues should
//     * be filed for failing tests so a proper fix can be done, as it's inappropriate to limit a test to a single version.
//     *
//     * @param javaSpecificationVersion the JDK specification version. Use 8 for JDK 8. Must be 8 or higher.
//     */
//    public static void assumeJDKVersionEquals(int javaSpecificationVersion) {
//        assert javaSpecificationVersion >= 8; // we only support 8 or later
//        assumeCondition("Tests failing if the JDK in use is other than " + javaSpecificationVersion + ".",
//                () -> getJavaSpecificationVersion() == javaSpecificationVersion);
//    }

    private static int getJavaSpecificationVersion() {
        String versionString = System.getProperty("java.specification.version");
        versionString = versionString.startsWith("1.") ? versionString.substring(2) : versionString;
        return Integer.parseInt(versionString);
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
}
