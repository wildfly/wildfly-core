/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.wildfly.core.launcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Compares 2 different version strings.
 * <p/>
 * The version strings must be valid integers separated by {@code .} (dots).
 * <p/>
 * Date: 09.11.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class VersionComparator implements Comparator<String> {

    public static VersionComparator INSTANCE = new VersionComparator();

    private VersionComparator() {
    }

    /**
     * Compares the first version to the second version and returns, 0 if they are equal, a value less than 0 if the
     * first version is less than the second version or a value greater than 0 if the first version is greater than
     * the second version.
     *
     * @param version1 the first version to compare.
     * @param version2 the second version to compare.
     *
     * @return a value of 0 if the versions are equal, less than 0 if {@code version1} is less than {@code version2},
     *         a value greater than 0 if {@code version1} is greater than {@code version2}.
     */
    public static int compareVersion(final String version1, final String version2) {
        return INSTANCE.compare(version1, version2);
    }

    @Override
    public int compare(final String o1, final String o2) {
        final String[] vs1 = o1.split("\\.");
        final String[] vs2 = o2.split("\\.");
        final int len = (vs1.length > vs2.length ? vs1.length : vs2.length);
        final List<Integer> v1 = convert(vs1, len);
        final List<Integer> v2 = convert(vs2, len);
        int result = 0;
        for (int i = 0; i < len; i++) {
            final Integer vi1 = v1.get(i);
            final Integer vi2 = v2.get(i);
            result = vi1.compareTo(vi2);
            if (result != 0) {
                break;
            }
        }
        return result;
    }

    private static List<Integer> convert(final String[] version, final int len) {
        final List<Integer> result = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            if (i < version.length) {
                final String s = version[i];
                if ("x".equalsIgnoreCase(s)) {
                    result.add(0);
                } else {
                    try {
                        result.add(Integer.valueOf(s));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(String.format("Version part %s is not a valid integer", s), e);
                    }
                }
            } else {
                result.add(0);
            }
        }
        return result;
    }
}
