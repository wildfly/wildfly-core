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

package org.wildfly.loaders.deployment;

import static org.wildfly.loaders.deployment.Utils.explodeArchive;
import static org.wildfly.loaders.deployment.Utils.getResourceName;
import static org.wildfly.loaders.deployment.Utils.normalizePath;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.security.AccessController;
import java.util.Collection;
import java.util.Locale;
import java.util.jar.JarFile;

import org.jboss.modules.Resource;

/**
 * Static factory methods for various types of resource loaders.
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class ResourceLoaders {
    private static final String JBOSS_TMP_DIR_PROPERTY = "jboss.server.temp.dir";
    private static final String JVM_TMP_DIR_PROPERTY = "java.io.tmpdir";
    private static final String XML_SUFFIX = ".xml";
    private static final File TMP_ROOT;

    static {
        String configTmpDir = AccessController.doPrivileged(new PropertyReadAction(JBOSS_TMP_DIR_PROPERTY));
        if (configTmpDir == null) {
            configTmpDir = AccessController.doPrivileged(new PropertyReadAction(JVM_TMP_DIR_PROPERTY));
        }
        TMP_ROOT = new File(configTmpDir);
    }

    private ResourceLoaders() {
    }

    /**
     * Creates a new deployment resource loader.
     * @param root deployment file or directory
     * @param isDeployment indicates whether archive represents deployment
     * @return new deployment resource loader
     * @throws IOException if some I/O error occurs
     */
    public static ResourceLoader newResourceLoader(final File root, final boolean isDeployment) throws IOException {
        return newResourceLoader(root != null ? root.getName() : null, root, null, null, isDeployment);
    }

    /**
     * Creates a new deployment resource loader.
     * @param name deployment loader name
     * @param root deployment file or directory
     * @param isDeployment indicates whether archive represents deployment
     * @return new deployment resource loader
     * @throws IOException if some I/O error occurs
     */
    public static ResourceLoader newResourceLoader(final String name, final File root, final boolean isDeployment) throws IOException {
        return newResourceLoader(name, root, null, null, isDeployment);
    }

    /**
     * Creates a new deployment resource loader.
     * @param root deployment file or directory
     * @param parent parent loader
     * @param isDeployment indicates whether archive represents deployment
     * @return new deployment resource loader
     * @throws IOException if some I/O error occurs
     */
    public static ResourceLoader newResourceLoader(final File root, final String path, final ResourceLoader parent, final boolean isDeployment) throws IOException {
        return newResourceLoader(root != null ? root.getName() : null, root, path, parent, isDeployment);
    }

    /**
     * Creates a new deployment resource loader.
     * @param name deployment loader name
     * @param root deployment file or directory
     * @param parent parent loader
     * @param isDeployment indicates whether archive represents deployment
     * @return new deployment resource loader
     * @throws IOException if some I/O error occurs
     */
    public static ResourceLoader newResourceLoader(final String name, final File root, final String path, final ResourceLoader parent, final boolean isDeployment) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("Archive file cannot be null");
        }
        if (name == null || "".equals(name)) {
            throw new IllegalArgumentException("Archive name cannot be neither null nor empty string");
        }
        if (parent != null && (path == null || path.equals(""))) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        ResourceLoader loader = parent;
        while (true) {
            if (loader instanceof DelegatingResourceLoader) {
                loader = ((DelegatingResourceLoader)loader).getDelegate();
                continue;
            }
            break;
        }
        if (root.isDirectory()) {
            return new FileResourceLoader(parent, name, root, path, isDeployment, AccessController.getContext());
        } else {
            if (name.toLowerCase(Locale.ENGLISH).endsWith(XML_SUFFIX)) {
                return new SingleFileResourceLoader(name, root, path, parent, isDeployment, AccessController.getContext());
            } else {
                if (explodeArchive(name)) {
                    final File tempDir = new File(TMP_ROOT, getResourceName(name) + ".tmp" + System.currentTimeMillis());
                    IOUtils.unzip(root, tempDir);
                    final FileResourceLoader newFileLoader = new FileResourceLoader(parent, name, tempDir, path, isDeployment, AccessController.getContext());
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
                    return new JarFileResourceLoader(parent, name, new JarFile(root), path, isDeployment);
                }
            }
        }
    }

    /**
     * Creates a subdeployment filtered view of a deployment resource loader.
     * Only resources under subdeployment path will be accessible by new loader.
     *
     * @param name the name of the created loader
     * @param parent the parent loader to create filtered view from
     * @param path subresource path that will behave like the root of the archive
     * @param isDeployment indicates whether archive represents deployment
     * @return a subresource filtered view of an iterable resource loader.
     */
    public static ResourceLoader newResourceLoader(final String name, final ResourceLoader parent, final String path, final boolean isDeployment) throws IOException {
        if (name == null || parent == null) {
            throw new NullPointerException("Method parameter cannot be null");
        }
        final String normalizedPath = normalizePath(path);
        final Collection<String> paths = parent.getPaths();
        ResourceLoader loader = parent;
        while (true) {
            if (loader instanceof DelegatingResourceLoader) {
                loader = ((DelegatingResourceLoader)loader).getDelegate();
                continue;
            }
            break;
        }
        final Resource resource = parent.getResource(normalizedPath);
        if (resource != null) {
            if (loader instanceof FileResourceLoader) {
                final FileResourceLoader fileLoader = (FileResourceLoader) loader;
                final File resourceFile = new File(loader.getRoot(), normalizedPath);
                final JarFileResourceLoader newLoader = new JarFileResourceLoader(loader, name, new JarFile(resourceFile), normalizedPath, isDeployment);
                fileLoader.addChild(normalizedPath, newLoader);
                return newLoader;
            } else if (loader instanceof JarFileResourceLoader) {
                final JarFileResourceLoader jarLoader = (JarFileResourceLoader) loader;
                if (explodeArchive(name)) {
                    final File tempDir = new File(TMP_ROOT, getResourceName(normalizedPath) + ".tmp" + System.currentTimeMillis());
                    IOUtils.unzip(resource.openStream(), tempDir);
                    final FileResourceLoader newFileLoader = new FileResourceLoader(loader, name, tempDir, normalizedPath, isDeployment, AccessController.getContext());
                    jarLoader.addChild(normalizedPath, newFileLoader);
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
                    final File tempFile = new File(TMP_ROOT, getResourceName(normalizedPath) + ".tmp" + System.currentTimeMillis());
                    IOUtils.copyAndClose(resource.openStream(), new FileOutputStream(tempFile));
                    final JarFileResourceLoader newJarLoader = new JarFileResourceLoader(loader, name, new JarFile(tempFile), normalizedPath, isDeployment);
                    jarLoader.addChild(normalizedPath, newJarLoader);
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
        } else if (paths.contains(normalizedPath)) {
            if (loader instanceof FileResourceLoader) {
                final FileResourceLoader fileLoader = (FileResourceLoader) loader;
                final FileResourceLoader newFileLoader = new FileResourceLoader(fileLoader, name, new File(fileLoader.getRoot(), normalizedPath), normalizedPath, isDeployment, AccessController.getContext());
                fileLoader.addChild(normalizedPath, newFileLoader);
                return newFileLoader;
            } else if (loader instanceof JarFileResourceLoader) {
                final JarFileResourceLoader jarLoader = (JarFileResourceLoader) loader;
                if (explodeArchive(name)) {
                    final File tempDir = new File(TMP_ROOT, getResourceName(name) + ".tmp" + System.currentTimeMillis());
                    IOUtils.unzip(parent.iterateResources(normalizedPath, true), tempDir, normalizedPath.length() + 1);
                    final FileResourceLoader newFileLoader = new FileResourceLoader(parent, name, tempDir, normalizedPath, isDeployment, AccessController.getContext());
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
                    final JarFileResourceLoader newJarLoader = new JarFileResourceLoader(jarLoader, name, new JarFile(jarLoader.getRoot()), normalizedPath, normalizedPath, isDeployment);
                    jarLoader.addChild(normalizedPath, newJarLoader);
                    return newJarLoader;
                }
            } else {
                throw new UnsupportedOperationException();
            }
        } else {
            throw new IllegalArgumentException("Subresource '" + normalizedPath + "' does not exist");
        }
    }

}
