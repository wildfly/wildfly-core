/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.http.server;

/**
 * A parsed version string.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
class ConsoleVersion implements Comparable<ConsoleVersion> {

    private final String name;
    private final int major;
    private final int minor;
    private final int micro;

    ConsoleVersion(final String name) {
        this.name = name;
        String[] parts = name.split("\\.");
        int maj = -1;
        int min = 0, mic = 0;
        if (parts.length < 4) {
            maj = getInt(parts[0]);
            if (maj > -1) {
                if (parts.length > 1) {
                    min = getInt(parts[1]);
                    if (min > -1) {
                        if (parts.length > 2) {
                            mic = getInt(parts[2]);
                            if (mic < 0) {
                                maj = -1;
                            }
                        }
                    } else {
                        maj = -1;
                    }
                }
            }
        }
        this.major = maj;
        this.minor = min;
        this.micro = mic;
    }

    private static int getInt(String part) {
        try {
            return Integer.valueOf(part);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ConsoleVersion && name.equals(((ConsoleVersion) obj).name);
    }

    @Override
    public int compareTo(ConsoleVersion o) {
        if (this.equals(o)) {
            return 0;
        } else if ("main".equals(name)) {
            return 1;
        } else if (major == -1) {
            return o.major == -1 ? name.compareTo(o.name) : 1;
        }

        int majorDiff = o.major - major;
        if (majorDiff != 0) {
            return majorDiff;
        }
        int minorDiff = o.minor - minor;
        if (minorDiff != 0) {
            return minorDiff;
        }
        return o.micro - micro;
    }
}
