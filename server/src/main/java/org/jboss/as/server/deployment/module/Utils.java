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
package org.jboss.as.server.deployment.module;

import org.jboss.modules.Resource;
import org.jboss.as.server.loaders.ResourceLoader;
import org.jboss.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.StringTokenizer;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Package utility methods.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class Utils {

    private Utils() {
        // forbidden instantiation
    }

    static Manifest getManifest(final ResourceRoot resourceRoot) throws IOException {
        final ResourceLoader loader = resourceRoot.getLoader();
        if (loader != null) {
            return readManifest(loader.getResource(JarFile.MANIFEST_NAME));
        } else {
            return readManifest(resourceRoot.getRoot().getChild(JarFile.MANIFEST_NAME));
        }
    }

    static String getPathForClassPathEntry(final String canonPath, final ResourceLoader loader) {
        if (loader == null) return null;
        if (!canonPath.contains("../")) return canonPath;

        final int upperLevelCount = canonPath.lastIndexOf("../") / 3 + 1;
        if (upperLevelCount == 0) {
            return canonPath;
        } else {
            final int loaderLevelCount = loader.getPath().split("/").length - 1;
            if (loaderLevelCount > upperLevelCount) {
                final String[] loaderPathItems = loader.getPath().split("/");
                final StringBuilder sb = new StringBuilder();
                for (int i = 0; i < loaderPathItems.length - upperLevelCount; i++) {
                    sb.append(loaderPathItems[i]).append("/");
                }
                sb.append(canonPath.substring(upperLevelCount * 3));
                return sb.toString();
            } else if (loaderLevelCount == 0) {
                return getPathForClassPathEntry(canonPath.substring(3), loader.getParent());
            } else {
                return getPathForClassPathEntry(canonPath.substring(loaderLevelCount * 3), loader.getParent());
            }
        }
    }

    static ResourceLoader getLoaderForClassPathEntry(final String canonPath, final ResourceLoader loader) {
        if (loader == null) return null;
        if (!canonPath.contains("../")) {
            if (loader != null && loader.getPath() != null) {
                if (loader.getPath().equals("WEB-INF/classes")) {
                    if (loader.getParent() != null) {
                        return loader.getParent().getParent(); // special case
                    }
                }
            }
            return loader.getParent();
        }

        final int upperLevelCount = canonPath.lastIndexOf("../") / 3 + 1;
        if (upperLevelCount == 0) {
            return loader.getParent();
        } else {
            final int loaderLevelCount = loader.getPath().split("/").length - 1;
            if (loaderLevelCount >= upperLevelCount || loaderLevelCount == 0) {
                return loader.getParent();
            } else {
                return getLoaderForClassPathEntry(canonPath.substring(loaderLevelCount * 3), loader.getParent());
            }
        }
    }

    static boolean isValidClassPathEntry(final String canonPath) {
        return !canonPath.endsWith("../") && !"".equals(canonPath);
    }

    static String getLoaderPath(final ResourceLoader loader) {
        if (loader == null) return null;
        ResourceLoader currentLoader = loader;
        ResourceLoader parentLoader;
        String fullPath = currentLoader.getPath();
        while (currentLoader != null) {
            parentLoader = currentLoader.getParent();
            if (parentLoader == null || parentLoader.getPath() == null || parentLoader.getPath().equals("")) break;
            fullPath = parentLoader.getPath() + "/" + fullPath;
            currentLoader = parentLoader;
        }
        return fullPath;
    }

    static String canonicalizeClassPathEntry(final String path) {
        if (path == null || path.equals("")) return path;
        final StringTokenizer st = new StringTokenizer(path, "\\/" + File.separator);
        final int length = st.countTokens();
        final String[] pathItems = new String[length];
        final boolean[] pathItemValid = new boolean[length];

        for (int i = 0; i < length; i++) {
            pathItems[i] = st.nextToken();
            if (pathItems[i].equals(".")) {
                pathItemValid[i] = false;
                continue;
            }
            if (pathItems[i].equals("..") && i > 0) {
                for (int j = i - 1; j >= 0; j--) {
                    if (pathItemValid[j] && !pathItems[j].equals("..")) {
                        pathItemValid[j] = false;
                        pathItemValid[i] = false;
                        break;
                    } else {
                        pathItemValid[i] = true;
                    }
                }
            } else {
                pathItemValid[i] = true;
            }
        }
        final StringBuilder retVal = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (pathItemValid[i]) {
                retVal.append(pathItems[i]);
                if (i != (length - 1)) retVal.append("/");
                if (i == (length - 1) && pathItems[i].equals(".."))  retVal.append("/");
            }
        }
        return retVal.toString();
    }

    private static Manifest readManifest(final VirtualFile manifest) throws IOException {
        if (manifest == null || !manifest.exists()) return null;
        final InputStream stream = new PaddedManifestStream(manifest.openStream());
        try {
            return new Manifest(stream);
        } finally {
            if (stream != null) try { stream.close(); } catch (final Throwable ignored) {}
        }
    }

    private static Manifest readManifest(final Resource manifestResource) throws IOException {
        if (manifestResource == null) return null;
        final InputStream stream = new PaddedManifestStream(manifestResource.openStream());
        try {
            return new Manifest(stream);
        } finally {
            if (stream != null) try { stream.close(); } catch (final Throwable ignored) {}
        }
    }

}
