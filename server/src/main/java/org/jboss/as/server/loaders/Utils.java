/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.loaders;

import org.jboss.modules.PathUtils;
import org.jboss.modules.Resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.StringTokenizer;

/**
 * Modules utility methods.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class Utils {

    private static final String PATH_SEPARATOR = "/";
    static final String WAR_EXTENSION = ".war";
    static final String WAB_EXTENSION = ".wab";
    static final String RAR_EXTENSION = ".rar";

    private Utils() {
        // forbidden instantiation
    }

    /**
     * Returns true if specified resource or path exists, false otherwise.
     * @param loader to inspect
     * @param resourceOrPath resource or path to check
     * @return true if specified resource or path exists, false otherwise
     */
    public static boolean resourceOrPathExists(final ResourceLoader loader, final String resourceOrPath) {
        return loader.getResource(resourceOrPath) != null || loader.getPaths().contains(resourceOrPath);
    }

    /**
     * Obtains resource name from its path. It's the last String after '/' path separator char.
     * @param resourcePath to get name from
     * @return resource name
     */
    public static String getResourceName(final String resourcePath) {
        if (resourcePath == null) {
            throw new IllegalArgumentException();
        }
        String canonPath = PathUtils.relativize(PathUtils.canonicalize(resourcePath));
        canonPath = canonPath.endsWith("/") ? canonPath.substring(0, canonPath.length() - 1) : canonPath;
        if (canonPath.equals("")) {
            throw new IllegalArgumentException();
        }
        final int separatorIndex = canonPath.lastIndexOf(PATH_SEPARATOR);
        return separatorIndex != -1 ? canonPath.substring(separatorIndex + 1) : canonPath;
    }

    /**
     * Inspects for child archives. Archives can be either exploded or packaged.
     * @param loader loader to inspect
     * @param recursive whether should recurse into subdirectories
     * @param suffixes non empty list of suffixes to be matched - case is ignored
     * @return collection of child archives
     */
    public static Collection<String> getChildArchives(final ResourceLoader loader, final boolean recursive, final String... suffixes) {
        return getChildArchives(loader, null, recursive, suffixes);
    }

    /**
     * Whether given archive should be exploded or not.
     * @param archive name to detect explosion policy.
     * @return true if archive should be exploded, false otherwise.
     */
    static boolean explodeArchive(final String archive) {
        final String archiveName = getResourceName(archive).toLowerCase(Locale.ENGLISH);
        return archiveName.endsWith(WAR_EXTENSION) || archiveName.endsWith(WAB_EXTENSION) || archiveName.endsWith(RAR_EXTENSION);
    }

    /**
     * Inspects for child archives. Archives can be either exploded or packaged.
     * @param loader loader to inspect
     * @param startPath path to start search from
     * @param recursive whether should recurse into subdirectories
     * @param suffixes non empty list of suffixes to be matched - case is ignored
     * @return collection of child archives
     */
    public static Collection<String> getChildArchives(final ResourceLoader loader, final String startPath, final boolean recursive, final String... suffixes) {
        if (loader == null || suffixes == null || suffixes.length == 0) {
            throw new IllegalArgumentException();
        }
        final Collection<String> retVal = new ArrayList<>();
        // search for exploded archives
        final String canonStartPath = startPath != null ? PathUtils.relativize(PathUtils.canonicalize(startPath)) : "";
        final Iterator<String> paths = loader.iteratePaths(canonStartPath, recursive);
        String candidate;
        while (paths.hasNext()) {
            candidate = paths.next();
            if (matches(retVal, candidate, suffixes)) {
                retVal.add(candidate);
            }
        }
        // search for packaged archives
        final Iterator<Resource> resources = loader.iterateResources(canonStartPath, recursive);
        while (resources.hasNext()) {
            candidate = resources.next().getName();
            if (matches(retVal, candidate, suffixes)) {
                retVal.add(candidate);
            }
        }
        return retVal;
    }

    private static boolean matches(final Collection<String> matches, final String candidate, final String... suffixes) {
        final StringTokenizer st = new StringTokenizer(candidate, PATH_SEPARATOR);
        String candidateToken;
        while (st.hasMoreTokens()) {
            candidateToken = st.nextToken().toLowerCase(Locale.ENGLISH);
            for (final String suffix : suffixes) {
                if (candidateToken.endsWith(suffix)) {
                    for (final String match : matches) {
                        if (candidate.startsWith(match)) {
                            return false;
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

}
