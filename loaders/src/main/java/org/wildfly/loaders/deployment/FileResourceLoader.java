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

import static org.wildfly.loaders.deployment.Utils.isEmptyPath;
import static org.wildfly.loaders.deployment.Utils.normalizePath;

import org.jboss.modules.ClassSpec;
import org.jboss.modules.AbstractResourceLoader;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.PathUtils;
import org.jboss.modules.Resource;

import static java.security.AccessController.doPrivileged;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessControlContext;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.NoSuchElementException;
import java.util.jar.Manifest;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class FileResourceLoader extends AbstractResourceLoader implements ResourceLoader {

    private final File root;
    private final String path;
    private final String fullPath;
    private volatile boolean isDeployment;
    private final ResourceLoader parent;
    private final String rootName;
    private final Manifest manifest;
    private final AccessControlContext context;
    // protected by {@code children}
    private final Map<String, ResourceLoader> children = new HashMap<>();
    // protected by {@code overlays}
    private final Map<String, File> overlays = new HashMap<>();
    private final URL rootUrl;
    private final URL deploymentUrl;

    FileResourceLoader(final ResourceLoader parent, final String rootName, final File root, final String path, final boolean isDeployment, final AccessControlContext context) {
        if (root == null) {
            throw new IllegalArgumentException("root is null");
        }
        if (rootName == null) {
            throw new IllegalArgumentException("rootName is null");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is null");
        }
        if (parent != null && (path == null || path.equals(""))) {
            throw new IllegalArgumentException("path cannot be null");
        }
        this.parent = parent;
        this.root = root;
        this.path = path == null ? "" : path;
        this.rootName = rootName;
        this.fullPath = parent != null ? parent.getFullPath() + "/" + path : rootName;
        this.isDeployment = isDeployment;
        final File manifestFile = new File(root, "META-INF" + File.separatorChar + "MANIFEST.MF");
        manifest = readManifestFile(manifestFile);
        final SecurityManager sm = System.getSecurityManager();
        try {
            deploymentUrl = new URL("deployment", null, 0, fullPath, new DeploymentURLStreamHandler());
            if (sm != null) {
                rootUrl = doPrivileged(new PrivilegedAction<URL>() {
                    public URL run() {
                        try {
                            return root.getAbsoluteFile().toURI().toURL();
                        } catch (MalformedURLException e) {
                            throw new IllegalArgumentException("Invalid root file specified", e);
                        }
                    }
                }, context);
            } else {
                rootUrl = root.getAbsoluteFile().toURI().toURL();
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid root file specified", e);
        }
        this.context = context;
    }

    void addChild(final String path, final ResourceLoader loader) {
        final String normalizedPath = normalizePath(path);
        synchronized (children) {
            if (children.get(normalizedPath) != null) {
                throw new IllegalStateException("Child loader for '" + normalizedPath + "' already registered");
            }
            children.put(normalizedPath, loader);
        }
        synchronized (overlays) {
            for (final String overlayPath : overlays.keySet()) {
                if (overlayPath.startsWith(normalizedPath) && !overlayPath.equals(normalizedPath)) {
                    // propagate overlays up in the loaders hierarchy
                    loader.addOverlay(overlayPath.substring(normalizedPath.length() + 1), overlays.get(overlayPath));
                }
            }
        }
    }

    @Override
    public void setUsePhysicalCodeSource(final boolean usePhysicalCodeSource) {
        this.isDeployment = !usePhysicalCodeSource;
    }

    @Override
    public ResourceLoader getChild(final String path) {
        synchronized (children) {
            return children.get(path);
        }
    }

    @Override
    public void addOverlay(final String path, final File content) {
        final String normalizedPath = normalizePath(path);
        synchronized (children) {
            if (children.size() > 0) {
                for (final String childPath : children.keySet()) {
                    if (normalizedPath.startsWith(childPath)) {
                        children.get(childPath).addOverlay(normalizedPath.substring(childPath.length() + 1), content);
                        return;
                    }
                }
                return;
            }
        }
        synchronized (overlays) {
            overlays.put(normalizedPath, content);
        }
    }

    @Override
    public File getRoot() {
        return root;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getFullPath() {
        return fullPath;
    }

    @Override
    public URL getRootURL() {
        try {
            return getRoot().toURI().toURL();
        } catch (final MalformedURLException ignored) {
            return null; // should never happen
        }
    }

    @Override
    public ResourceLoader getParent() {
        return parent;
    }

    @Override
    public String getRootName() {
        return rootName;
    }

    private static Manifest readManifestFile(final File manifestFile) {
        try {
            return manifestFile.exists() ? new Manifest(new FileInputStream(manifestFile)) : null;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public ClassSpec getClassSpec(final String fileName) throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            try {
                return doPrivileged(new PrivilegedExceptionAction<ClassSpec>() {
                    public ClassSpec run() throws IOException {
                        return doGetClassSpec(fileName);
                    }
                }, context);
            } catch (PrivilegedActionException e) {
                try {
                    throw e.getException();
                } catch (IOException e1) {
                    throw e1;
                } catch (RuntimeException e1) {
                    throw e1;
                } catch (Exception e1) {
                    throw new UndeclaredThrowableException(e1);
                }
            }
        } else {
            return doGetClassSpec(fileName);
        }
    }

    private ClassSpec doGetClassSpec(final String fileName) throws IOException {
        final Resource resource = getResource(fileName);
        if (resource == null) {
            return null;
        }
        final long size = resource.getSize();
        final ClassSpec spec = new ClassSpec();
        spec.setCodeSource(new CodeSource(isDeployment ? deploymentUrl : rootUrl, (CodeSigner[])null));
        final InputStream is = resource.openStream();
        try {
            if (size <= (long) Integer.MAX_VALUE) {
                final int castSize = (int) size;
                byte[] bytes = new byte[castSize];
                int a = 0, res;
                while ((res = is.read(bytes, a, castSize - a)) > 0) {
                    a += res;
                }
                // done
                is.close();
                spec.setBytes(bytes);
                return spec;
            } else {
                throw new IOException("Resource is too large to be a valid class file");
            }
        } finally {
            IOUtils.safeClose(is);
        }
    }

    @Override
    public PackageSpec getPackageSpec(final String name) throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            try {
                return doPrivileged(new PrivilegedExceptionAction<PackageSpec>() {
                    public PackageSpec run() throws IOException {
                        return getPackageSpec(name, manifest, root.toURI().toURL());
                    }
                }, context);
            } catch (PrivilegedActionException e) {
                try {
                    throw e.getException();
                } catch (IOException e1) {
                    throw e1;
                } catch (RuntimeException e1) {
                    throw e1;
                } catch (Exception e1) {
                    throw new UndeclaredThrowableException(e1);
                }
            }
        } else {
            return getPackageSpec(name, manifest, root.toURI().toURL());
        }
    }

    @Override
    public Resource getResource(final String name) {
        if (isEmptyPath(name)) return null;
        final String normalizedPath = normalizePath(name);
        final File overlay = overlays.get(normalizedPath);
        final File file = overlay != null ? overlay : new File(root, normalizedPath);
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            return doPrivileged(new PrivilegedAction<Resource>() {
                public Resource run() {
                    if (!file.exists() || file.isDirectory()) {
                        return null;
                    } else {
                        return new FileEntryResource(normalizedPath, file, isDeployment ? fullPath : null, context);
                    }
                }
            }, context);
        } else if (! file.exists() || file.isDirectory()) {
            return null;
        } else {
            return new FileEntryResource(normalizedPath, file, isDeployment ? fullPath : null, context);
        }
    }

    class Itr implements Iterator<Resource> {
        private final String startName;
        private final String base;
        private final String[] names;
        private final boolean recursive;
        private int i = 0;
        private Itr nested;
        private Resource next;
        private Iterator<String> overlayPaths;

        Itr(final String startName, final String base, final String[] names, final Iterator<String> overlayPaths, final boolean recursive) {
            this.startName = startName;
            this.base = base;
            this.names = names;
            this.overlayPaths = overlayPaths;
            this.recursive = recursive;
        }

        public boolean hasNext() {
            if (next != null) {
                return true;
            }
            String overlayPath;
            while (overlayPaths != null && overlayPaths.hasNext()) {
                overlayPath = overlayPaths.next();
                if ((recursive ? PathUtils.isChild(startName, overlayPath) : PathUtils.isDirectChild(startName, overlayPath))) {
                    try {
                        final File overlay = overlays.get(overlayPath);
                        next = new FileEntryResource(overlayPath, overlay, FileResourceLoader.this.isDeployment ? fullPath : null, context);
                        return true;
                    } catch (Exception ignored) {
                    }
                }
            }
            final String base = this.base;
            final String[] names = this.names;
            while (names != null && i < names.length) {
                final String current = names[i];
                final String full = base.isEmpty() ? current : base + "/" + current;
                if (FileResourceLoader.this.overlays.containsKey(full)) {
                    i++;
                    continue;
                }
                final File file = new File(root, full);
                if (recursive && nested == null) {
                    final String[] children = file.list();
                    if (children != null && children.length > 0) {
                        nested = new Itr(startName, full, children, null, recursive);
                    }
                }
                if (nested != null) {
                    if (nested.hasNext()) {
                        next = nested.next();
                        return true;
                    }
                    nested = null;
                }
                i++;
                if (file.isFile()) {
                    next = new FileEntryResource(full, file, FileResourceLoader.this.isDeployment ? fullPath : null, context);
                    return true;
                }
            }
            return false;
        }

        public Resource next() {
            if (! hasNext()) {
                throw new NoSuchElementException();
            }
            try {
                return next;
            } finally {
                next = null;
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Iterator<String> iteratePaths(final String startPath, final boolean recursive) {
        final String normalizedPath = "".equals(startPath) ? "" : normalizePath(startPath);
        final File start = new File(root, normalizedPath);
        if (!start.exists() || !start.isDirectory()) return Collections.emptyIterator();
        final List<String> index = new ArrayList<>();
        index.add(normalizedPath);
        buildIndex(index, start, normalizedPath, recursive);
        return index.iterator();
    }

    @Override
    public Iterator<Resource> iterateResources(final String startPath, final boolean recursive) {
        final String normalizedPath = "".equals(startPath) ? "" : normalizePath(startPath);
        final File start = new File(root, normalizedPath);
        final String[] children = start.list();
        final Set<String> overlayPaths;
        synchronized (overlays) {
            overlayPaths = overlays.keySet();
        }
        if (overlayPaths.size() == 0 && (children == null || children.length == 0)) {
            return Collections.<Resource>emptySet().iterator();
        }
        return new Itr(startPath, normalizedPath, children, overlayPaths.iterator(), recursive);
    }

    @Override
    public Collection<String> getPaths() {
        final List<String> index = new ArrayList<>();
        index.add("");
        buildIndex(index, root, "", true);
        return index;
    }

    private void buildIndex(final List<String> index, final File root, final String pathBase, boolean recursive) {
        File[] files = root.listFiles();
        if (files != null) for (File file : files) {
            if (file.isDirectory()) {
                if (pathBase.equals("")) {
                    index.add(file.getName());
                    if (recursive) buildIndex(index, file, file.getName(), recursive);
                } else {
                    index.add(pathBase + "/" + file.getName());
                    if (recursive) buildIndex(index, file, pathBase + "/" + file.getName(), recursive);
                }
            }
        }
    }
}
