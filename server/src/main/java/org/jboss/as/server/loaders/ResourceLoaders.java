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

import static org.jboss.as.server.loaders.Utils.getResourceName;
import static org.jboss.as.server.loaders.Utils.explodeArchive;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.security.AccessController;
import java.util.Collection;
import java.util.Locale;
import java.util.jar.JarFile;

import org.jboss.modules.PathUtils;
import org.jboss.modules.Resource;
import org.jboss.modules.filter.PathFilter;

/**
 * Static factory methods for various types of resource loaders.
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class ResourceLoaders {
    static final boolean USE_INDEXES;
    static final boolean WRITE_INDEXES;
    private static final String JBOSS_TMP_DIR_PROPERTY = "jboss.server.temp.dir";
    private static final String JVM_TMP_DIR_PROPERTY = "java.io.tmpdir";
    private static final String XML_SUFFIX = ".xml";

    static final File TMP_ROOT;

    static {
        USE_INDEXES = Boolean.parseBoolean(AccessController.doPrivileged(new PropertyReadAction("jboss.modules.use-indexes", "false")));
        WRITE_INDEXES = USE_INDEXES && Boolean.parseBoolean(AccessController.doPrivileged(new PropertyReadAction("jboss.modules.write-indexes", "false")));
        String configTmpDir = AccessController.doPrivileged(new PropertyReadAction(JBOSS_TMP_DIR_PROPERTY));
        if (configTmpDir == null)
            configTmpDir = AccessController.doPrivileged(new PropertyReadAction(JVM_TMP_DIR_PROPERTY));
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
        return newResourceLoader(root != null ? root.getName() : null, root, null);
    }

    /**
     * Creates a new deployment resource loader.
     * @param name deployment loader name
     * @param root deployment file or directory
     * @return new deployment resource loader
     * @throws IOException if some I/O error occurs
     */
    public static ResourceLoader newResourceLoader(final String name, final File root) throws IOException {
        return newResourceLoader(name, root, null);
    }

    /**
     * Creates a new deployment resource loader.
     * @param root deployment file or directory
     * @param parent parent loader
     * @return new deployment resource loader
     * @throws IOException if some I/O error occurs
     */
    public static ResourceLoader newResourceLoader(final File root, final ResourceLoader parent) throws IOException {
        return newResourceLoader(root != null ? root.getName() : null, root, parent);
    }

    /**
     * Creates a new deployment resource loader.
     * @param name deployment loader name
     * @param root deployment file or directory
     * @param parent parent loader
     * @return new deployment resource loader
     * @throws IOException if some I/O error occurs
     */
    public static ResourceLoader newResourceLoader(final String name, final File root, final ResourceLoader parent) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("Archive file cannot be null");
        }
        if (name == null || "".equals(name)) {
            throw new IllegalArgumentException("Archive name cannot be neither null nor empty string");
        }

        if (root.isDirectory()) {
            return new FileResourceLoader(parent, name, root, AccessController.getContext());
        } else {
            if (root.getName().toLowerCase(Locale.ENGLISH).endsWith(XML_SUFFIX)) {
                return new SingleFileResourceLoader(name, root, parent, AccessController.getContext());
            } else {
                if (explodeArchive(name)) {
                    final File tempDir = new File(TMP_ROOT, getResourceName(name) + ".tmp" + System.currentTimeMillis());
                    IOUtils.unzip(root, tempDir);
                    final FileResourceLoader newFileLoader = new FileResourceLoader(parent, name, tempDir, AccessController.getContext());
                    return new DelegatingResourceLoader(newFileLoader) {
                        @Override
                        public void close() {
                            try {
                                super.close();
                            } finally {
                                IOUtils.delete(tempDir);
                            }
                        }
                    };
                } else {
                    return new JarFileResourceLoader(parent, name, new JarFile(root));
                }
            }
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
    // TODO: expose to public? Will it be needed? If not eliminate FilteredResourceLoader from API completely.
    private static ResourceLoader newFilteredResourceLoader(final PathFilter pathFilter, final ResourceLoader originalLoader) {
        return new FilteredResourceLoader(pathFilter, originalLoader);
    }

    /**
     * Creates a subdeployment filtered view of a deployment resource loader.
     * Only resources under subdeployment path will be accessible by new loader.
     *
     * @param name the name of the resource root
     * @param parent the parent loader to create filtered view from
     * @param subresourcePath subresource path that will behave like the root of the archive
     * @return a subresource filtered view of an iterable resource loader.
     */
    // TODO: eliminate explode
    public static ResourceLoader newResourceLoader(final String name, final ResourceLoader parent, final String subresourcePath) throws IOException {
        if (name == null || parent == null || subresourcePath == null) {
            throw new NullPointerException("Method parameter cannot be null");
        }
        final String subResPath = PathUtils.relativize(PathUtils.canonicalize(subresourcePath));
        if (subResPath.equals("")) {
            throw new IllegalArgumentException("Cannot create subresource loader for archive root");
        }
        if (subResPath.endsWith("/")) {
            throw new IllegalArgumentException("Subresource path parameter cannot end with '/' character");
        }
        final Collection<String> paths = parent.getPaths();
        ResourceLoader loader = parent;
        while (true) {
            if (loader instanceof FilteredResourceLoader) {
                loader = ((FilteredResourceLoader)loader).getLoader();
                continue;
            }
            if (loader instanceof DelegatingResourceLoader) {
                loader = ((DelegatingResourceLoader)loader).getDelegate();
                continue;
            }
            break;
        }
        if (paths.contains(subResPath)) {
            if (loader instanceof FileResourceLoader) {
                final FileResourceLoader fileLoader = (FileResourceLoader) loader;
                final FileResourceLoader newFileLoader = new FileResourceLoader(fileLoader, name, new File(fileLoader.getRoot(), subResPath), AccessController.getContext());
                fileLoader.addChild(subResPath, newFileLoader);
                return newFileLoader;
            } else if (loader instanceof JarFileResourceLoader) {
                final JarFileResourceLoader jarLoader = (JarFileResourceLoader) loader;
                if (explodeArchive(name)) {
                    final File tempDir = new File(TMP_ROOT, getResourceName(name) + ".tmp" + System.currentTimeMillis());
                    IOUtils.unzip(parent.iterateResources(subresourcePath, true), tempDir, subresourcePath.length() + 1);
                    final FileResourceLoader newFileLoader = new FileResourceLoader(parent, name, tempDir, AccessController.getContext());
                    return new DelegatingResourceLoader(newFileLoader) {
                        @Override
                        public void close() {
                            try {
                                super.close();
                            } finally {
                                IOUtils.delete(tempDir);
                            }
                        }
                    };
                } else {
                    final JarFileResourceLoader newJarLoader = new JarFileResourceLoader(jarLoader, name, new JarFile(jarLoader.getFile()), subResPath);
                    jarLoader.addChild(subResPath, newJarLoader);
                    return newJarLoader;
                }
            } else {
                throw new UnsupportedOperationException();
            }
        } else {
            final Resource resource = parent.getResource(subResPath);
            if (resource == null) {
                throw new IllegalArgumentException("Subresource '" + subResPath + "' does not exist");
            }
            if (loader instanceof FileResourceLoader) {
                final FileResourceLoader fileLoader = (FileResourceLoader) loader;
                final JarFileResourceLoader newLoader = new JarFileResourceLoader(loader, name, new JarFile(resource.getURL().getFile()));
                fileLoader.addChild(subResPath, newLoader);
                return newLoader;
            } else if (loader instanceof JarFileResourceLoader) {
                if (explodeArchive(name)) {
                    final File tempDir = new File(TMP_ROOT, getResourceName(subResPath) + ".tmp" + System.currentTimeMillis());
                    IOUtils.unzip(resource.openStream(), tempDir);
                    final JarFileResourceLoader jarLoader = (JarFileResourceLoader) loader;
                    final FileResourceLoader newFileLoader = new FileResourceLoader(loader, name, tempDir, AccessController.getContext());
                    jarLoader.addChild(subResPath, newFileLoader);
                    return new DelegatingResourceLoader(newFileLoader) {
                        @Override
                        public void close() {
                            try {
                                super.close();
                            } finally {
                                IOUtils.delete(tempDir);
                            }
                        }
                    };
                } else {
                    final File tempFile = new File(TMP_ROOT, getResourceName(subResPath) + ".tmp" + System.currentTimeMillis());
                    IOUtils.copyAndClose(resource.openStream(), new FileOutputStream(tempFile));
                    final JarFileResourceLoader jarLoader = (JarFileResourceLoader) loader;
                    final JarFileResourceLoader newJarLoader = new JarFileResourceLoader(loader, name, new JarFile(tempFile));
                    jarLoader.addChild(subResPath, newJarLoader);
                    return new DelegatingResourceLoader(newJarLoader) {
                        @Override
                        public void close() {
                            try {
                                super.close();
                            } finally {
                                IOUtils.delete(tempFile);
                            }
                        }
                    };
                }
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

}
