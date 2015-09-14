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

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.security.AccessController;
import java.util.StringTokenizer;
import java.util.jar.JarFile;

import org.jboss.modules.IterableResourceLoader;
import org.jboss.modules.PathUtils;
import org.jboss.modules.Resource;
import org.jboss.modules.filter.PathFilter;

/**
 * Static factory methods for various types of resource loaders.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class ResourceLoaders {
    static final boolean USE_INDEXES;
    static final boolean WRITE_INDEXES;
    private static final String JBOSS_TMP_DIR_PROPERTY = "jboss.server.temp.dir";
    private static final String JVM_TMP_DIR_PROPERTY = "java.io.tmpdir";
    static final File TMP_ROOT;

    static {
        USE_INDEXES = Boolean.parseBoolean(AccessController.doPrivileged(new PropertyReadAction("jboss.modules.use-indexes", "false")));
        WRITE_INDEXES = USE_INDEXES && Boolean.parseBoolean(AccessController.doPrivileged(new PropertyReadAction("jboss.modules.write-indexes", "false")));
        String configTmpDir = AccessController.doPrivileged(new PropertyReadAction(JBOSS_TMP_DIR_PROPERTY));
        if (configTmpDir == null)  configTmpDir = AccessController.doPrivileged(new PropertyReadAction(JVM_TMP_DIR_PROPERTY));
        TMP_ROOT = new File(configTmpDir);
    }

    private ResourceLoaders() {
    }

    /**
     * Creates a new deployment resource loader.
     * @param root deployment file or directory
     * @return new deployment resource loader
     * @throws IOException if some I/O error occurs
     */
    public static ResourceLoader newResourceLoader(final File root) throws IOException {
        return newResourceLoader(root.getName(), root);
    }

    /**
     * Creates a new deployment resource loader.
     * @param name deployment loader name
     * @param root deployment file or directory
     * @return new deployment resource loader
     * @throws IOException if some I/O error occurs
     */
    public static ResourceLoader newResourceLoader(final String name, final File root) throws IOException {
        if (root.isDirectory()) {
            return new FileResourceLoader(null, name, root, AccessController.getContext());
        } else {
            return new JarFileResourceLoader(null, name, new JarFile(root));
        }
    }

    /**
     * Create a filtered view of an iterable resource loader, which allows classes to be included or excluded on a name basis.
     * The given filter is matched against the actual class or resource name, not the directory name.
     *
     * @param pathFilter the path filter to apply
     * @param originalLoader the original loader to apply to
     * @return the filtered resource loader
     */
    public static ResourceLoader newFilteredResourceLoader(final PathFilter pathFilter, final IterableResourceLoader originalLoader) {
        return new FilteredIterableResourceLoader(pathFilter, originalLoader);
    }

    /**
     * Creates a subdeployment filtered view of a deployment resource loader.
     * Only resources under subdeployment path will be accessible by new loader.
     *
     * @param name the name of the resource root
     * @param parentLoader the parent loader to create filtered view from
     * @param subresourcePath subresource path that will behave like the root of the archive
     * @return a subresource filtered view of an iterable resource loader.
     */
    public static ResourceLoader newResourceLoader(final String name, final IterableResourceLoader parentLoader, final String subresourcePath) throws IOException {
        if (name == null || parentLoader == null || subresourcePath == null) {
            throw new NullPointerException("Method parameter cannot be null");
        }
        final String subResPath = PathUtils.relativize(PathUtils.canonicalize(subresourcePath));
        if (subResPath.equals("")) {
            throw new IllegalArgumentException("Cannot create subresource loader for archive root");
        }
        final Resource resource = parentLoader.getResource(subResPath);
        if (resource == null) {
            throw new IllegalArgumentException("Subresource '" + subResPath + "' does not exist");
        }
        IterableResourceLoader loader = parentLoader;
        while (true) {
            if (loader instanceof FilteredIterableResourceLoader) {
                loader = ((FilteredIterableResourceLoader)loader).getLoader();
                continue;
            }
            if (loader instanceof DelegatingResourceLoader) {
                loader = ((DelegatingResourceLoader)loader).getDelegate();
                continue;
            }
            break;
        }
        if (subResPath.endsWith("/")) {
            if (loader instanceof FileResourceLoader) {
                return new FileResourceLoader((ResourceLoader)loader, name, new File(resource.getURL().getFile()), AccessController.getContext());
            } else if (loader instanceof JarFileResourceLoader) {
                return new JarFileResourceLoader((ResourceLoader)loader, name, new JarFile(((JarFileResourceLoader) loader).getFile()), subResPath);
            } else {
                throw new UnsupportedOperationException();
            }
        } else {
            if (loader instanceof FileResourceLoader) {
                return new JarFileResourceLoader((ResourceLoader)loader, name, new JarFile(resource.getURL().getFile()));
            } else if (loader instanceof JarFileResourceLoader) {
                final File tempFile = new File(TMP_ROOT, getLastToken(subResPath) + ".tmp" + System.currentTimeMillis());
                IOUtils.copyAndClose(resource.openStream(), new FileOutputStream(tempFile));
                return new DelegatingResourceLoader(new JarFileResourceLoader((ResourceLoader)loader, name, new JarFile(tempFile))) {
                    @Override
                    public void close() {
                        try {
                            super.close();
                        } finally {
                            tempFile.delete();
                        }
                    }
                };
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    private static String getLastToken(final String path) {
        final StringTokenizer st = new StringTokenizer(path, "/");
        String lastToken = st.nextToken();
        while (st.hasMoreTokens()) {
            lastToken = st.nextToken();
        }
        return lastToken;
    }

}
