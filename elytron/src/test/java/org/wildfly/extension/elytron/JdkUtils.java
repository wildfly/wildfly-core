/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class JdkUtils {

    private static final String javaSpecVersion = System.getProperty("java.specification.version");
    private static final String javaVendor = System.getProperty("java.vendor");

    private JdkUtils() {}

    static int getJavaSpecVersion() {
        return Integer.parseInt(javaSpecVersion);
    }

    static boolean isIbmJdk() {
        return javaVendor.startsWith("IBM");
    }

}
