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
import static java.security.AccessController.doPrivileged;

import org.jboss.modules.AbstractResourceLoader;
import org.jboss.modules.ClassSpec;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.PathUtils;
import org.jboss.modules.Resource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLStreamHandler;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Thomas.Diesler@jboss.com
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class JarFileResourceLoader extends AbstractResourceLoader implements ResourceLoader {
    private final ResourceLoader parent;
    // protected by {@code children}
    private final Map<String, ResourceLoader> children = new HashMap<>();
    // protected by {@code overlays}
    private final Map<String, File> overlays = new HashMap<>();
    // protected by {@code this}
    private final Map<CodeSigners, CodeSource> codeSources = new HashMap<>();
    private final JarFile jarFile;
    private final String rootName;
    private final URL rootUrl;
    private final String path;
    private final String fullPath;
    private final String relativePath;
    private final File fileOfJar;
    private volatile boolean isDeployment;

    JarFileResourceLoader(final ResourceLoader parent, final String rootName, final JarFile jarFile, final String path, final boolean isDeployment) {
        this(parent, rootName, jarFile, path, null, isDeployment);
    }

    JarFileResourceLoader(final ResourceLoader parent, final String rootName, final JarFile jarFile, final String path, final String relativePath, final boolean isDeployment) {
        if (jarFile == null) {
            throw new IllegalArgumentException("jarFile cannot be null");
        }
        if (rootName == null) {
            throw new IllegalArgumentException("rootName cannot be null");
        }
        if (parent != null && (path == null || path.equals(""))) {
            throw new IllegalArgumentException("path cannot be null");
        }
        fileOfJar = new File(jarFile.getName());
        this.parent = parent;
        this.jarFile = jarFile;
        this.path = path == null ? "" : path;
        this.rootName = rootName;
        this.relativePath = isEmptyPath(relativePath) ? null : normalizePath(relativePath);
        this.fullPath = parent != null ? parent.getFullPath() + "/" + path : "/" + rootName;
        this.isDeployment = isDeployment;
        try {
            rootUrl = getJarURI(fileOfJar.toURI(), this.relativePath).toURL();
        } catch (URISyntaxException|MalformedURLException e) {
            throw new IllegalArgumentException("Invalid root file specified", e);
        }
    }

    @Override
    public void setUsePhysicalCodeSource(final boolean usePhysicalCodeSource) {
        this.isDeployment = !usePhysicalCodeSource;
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
    public ResourceLoader getChild(final String path) {
        synchronized (children) {
            return children.get(path);
        }
    }

    @Override
    public Iterator<ResourceLoader> iterateChildren() {
        final Set<ResourceLoader> retVal = new HashSet<>();
        synchronized (children) {
            retVal.addAll(children.values());
        }
        return retVal.iterator();
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
    public ResourceLoader getParent() {
        return parent;
    }

    @Override
    public File getRoot() {
        return fileOfJar;
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
            if (relativePath == null || "".equals(relativePath)) {
                return getRoot().toURI().toURL();
            } else {
                return new URL("jar:" + getRoot().toURI().toURL().toString() + "!/" + relativePath);
            }
        } catch (final MalformedURLException ignored) {
            return null;
        }
    }

    private static URI getJarURI(final URI original, final String nestedPath) throws URISyntaxException {
        final StringBuilder b = new StringBuilder();
        b.append("file:");
        assert original.getScheme().equals("file");
        final String path = original.getPath();
        assert path != null;
        final String host = original.getHost();
        if (host != null) {
            final String userInfo = original.getRawUserInfo();
            b.append("//");
            if (userInfo != null) {
                b.append(userInfo).append('@');
            }
            b.append(host);
        }
        b.append(path).append("!/");
        if (nestedPath != null) {
            b.append(nestedPath);
        }
        return new URI("jar", b.toString(), null);
    }

    @Override
    public String getRootName() {
        return rootName;
    }

    @Override
    public synchronized ClassSpec getClassSpec(final String fileName) throws IOException {
        final ClassSpec spec = new ClassSpec();
        final JarEntryResource resource = (JarEntryResource) getResource(fileName);
        if (resource == null) {
            // no such entry
            return null;
        }
        final long size = resource.getSize();
        final InputStream is = resource.openStream();
        try {
            if (size == 0) {
                // size unknown
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final byte[] buf = new byte[1024];
                int res;
                while ((res = is.read(buf)) > 0) {
                    baos.write(buf, 0, res);
                }
                // done
                CodeSource codeSource = createCodeSource(resource.getEntry());
                baos.close();
                is.close();
                spec.setBytes(baos.toByteArray());
                spec.setCodeSource(codeSource);
                return spec;
            } else if (size <= (long) Integer.MAX_VALUE) {
                final int castSize = (int) size;
                byte[] bytes = new byte[castSize];
                int a = 0, res;
                while ((res = is.read(bytes, a, castSize - a)) > 0) {
                    a += res;
                }
                // consume remainder so that cert check doesn't fail in case of wonky JARs
                while (is.read() != -1) {}
                // done
                CodeSource codeSource = createCodeSource(resource.getEntry());
                is.close();
                spec.setBytes(bytes);
                spec.setCodeSource(codeSource);
                return spec;
            } else {
                throw new IOException("Resource is too large to be a valid class file");
            }
        } finally {
            IOUtils.safeClose(is);
        }
    }

    // this MUST only be called after the input stream is fully read (see MODULES-201)
    private CodeSource createCodeSource(final JarEntry entry) {
        final CodeSigner[] entryCodeSigners = entry != null ? entry.getCodeSigners() : null;
        final CodeSigners codeSigners = entryCodeSigners == null || entryCodeSigners.length == 0 ? EMPTY_CODE_SIGNERS : new CodeSigners(entryCodeSigners);
        CodeSource codeSource = codeSources.get(codeSigners);
        if (codeSource == null) {
            URL deploymentUrl = doPrivileged(new DeploymentURLCreateAction(fullPath));
            codeSources.put(codeSigners, codeSource = new CodeSource(isDeployment ? deploymentUrl : rootUrl, entryCodeSigners));
        }
        return codeSource;
    }

    private JarEntry getJarEntry(final String fileName) {
        return relativePath == null ? jarFile.getJarEntry(fileName) : jarFile.getJarEntry(relativePath + "/" + fileName);
    }

    @Override
    public PackageSpec getPackageSpec(final String name) throws IOException {
        final Manifest manifest;
        File overlay;
        synchronized (overlays) {
            overlay = overlays.get(JarFile.MANIFEST_NAME);
        }
        if (overlay != null) {
            InputStream inputStream = new FileInputStream(overlay);
            try {
                manifest = new Manifest(inputStream);
            } finally {
                IOUtils.safeClose(inputStream);
            }
        } else {
            JarEntry jarEntry = getJarEntry(JarFile.MANIFEST_NAME);
            if (jarEntry == null) {
                manifest = null;
            } else {
                InputStream inputStream = jarFile.getInputStream(jarEntry);
                try {
                    manifest = new Manifest(inputStream);
                } finally {
                    IOUtils.safeClose(inputStream);
                }
            }
        }
        return getPackageSpec(name, manifest, rootUrl);
    }

    @Override
    public String getLibrary(final String name) {
        // JARs cannot have libraries in them
        return null;
    }

    @Override
    public Resource getResource(String name) {
        if (isEmptyPath(name)) return null;
        try {
            name = normalizePath(name);
            File overlay;
            synchronized (overlays) {
                overlay = overlays.get(name);
            }
            if (overlay == null && getJarEntry(name + "/") != null) return null;
            final JarFile jarFile = this.jarFile;
            final JarEntry entry = getJarEntry(name);
            if (entry == null && overlay == null) {
                return null;
            }
            final URI uri;
            try {
                File absoluteFile = new File(jarFile.getName()).getAbsoluteFile();
                String path = absoluteFile.getPath();
                path = PathUtils.canonicalize(path);
                if (File.separatorChar != '/') {
                    // optimizes away on platforms with /
                    path = path.replace(File.separatorChar, '/');
                }
                if (PathUtils.isRelative(path)) {
                    // should not be possible, but the JDK thinks this might happen sometimes..?
                    path = "/" + path;
                }
                if (path.startsWith("//")) {
                    // UNC path URIs have loads of leading slashes
                    path = "//" + path;
                }
                uri = new URI("file", null, path, null);
            } catch (URISyntaxException x) {
                throw new IllegalStateException(x);
            }
            final URL overlayURL = overlay != null ? overlay.toURI().toURL() : null;
            final URL entryURL = entry != null ? new URL(null, getJarURI(uri, entry.getName()).toString(), (URLStreamHandler) null) : null;
            return new JarEntryResource(jarFile, entry, name, entryURL, overlay, overlayURL, isDeployment ? fullPath : null);
        } catch (MalformedURLException e) {
            // must be invalid...?
            return null;
        } catch (URISyntaxException e) {
            // must be invalid...?
            return null;
        }
    }

    @Override
    public Iterator<Resource> iterateResources(String startPath, final boolean recursive) {
        final JarFile jarFile = this.jarFile;
        if (relativePath != null) startPath = startPath.equals("") ? relativePath : relativePath + "/" + startPath;
        final String startName = "".equals(startPath) ? "" : normalizePath(startPath);
        final Enumeration<JarEntry> entries = jarFile.entries();
        return new Iterator<Resource>() {
            private Resource next;
            private Iterator<String> overlayPaths;
            public boolean hasNext() {
                synchronized (overlays) {
                    if (overlayPaths == null) {
                        overlayPaths = overlays.keySet().iterator();
                    }
                    while (next == null) {
                        if (!entries.hasMoreElements() && !overlayPaths.hasNext()) {
                            return false;
                        }
                        if (overlayPaths.hasNext()) {
                            final String overlayPath = overlayPaths.next();
                            if ((recursive ? PathUtils.isChild(startName, overlayPath) : PathUtils.isDirectChild(startName, overlayPath))) {
                                try {
                                    final File overlay = overlays.get(overlayPath);
                                    final URL overlayURL = overlay != null ? overlay.toURI().toURL() : null;
                                    next = new JarEntryResource(jarFile, null, overlayPath, null, overlay, overlayURL, isDeployment ? fullPath : null);
                                } catch (Exception ignored) {
                                }
                            }
                        } else if (entries.hasMoreElements()) {
                            final JarEntry entry = entries.nextElement();
                            final String name = (relativePath != null && entry.getName().startsWith(relativePath)) ? entry.getName().substring(relativePath.length() + 1) : entry.getName();
                            final boolean isOverlayed = overlays.containsKey(name);
                            if (!isOverlayed && (recursive ? PathUtils.isChild(startName, entry.getName()) : PathUtils.isDirectChild(startName, entry.getName()))) {
                                if (!entry.isDirectory()) {
                                    try {
                                        next = new JarEntryResource(jarFile, entry, name, getJarURI(new File(jarFile.getName()).toURI(), entry.getName()).toURL(), null, null, isDeployment ? fullPath : null);
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                        }
                    }
                    return true;
                }
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
        };
    }

    @Override
    public Iterator<String> iteratePaths(final String startPath, final boolean recursive) {
        final String startName = "".equals(startPath) ? "" : normalizePath(startPath);
        final Collection<String> index = new HashSet<>();
        extractJarPaths(jarFile, startName, index, recursive);
        return index.iterator();
    }

    @Override
    public Collection<String> getPaths() {
        final Collection<String> index = new HashSet<>();
        index.add("");
        extractJarPaths(jarFile, "", index, true);
        return index;
    }

    @Override
    public void close() {
        try {
            super.close();
        } finally {
            try {
                jarFile.close();
            } catch (Throwable t) {
                // ignored
            }
        }
    }

    private void extractJarPaths(final JarFile jarFile, final String startPath,
            final Collection<String> index, final boolean recursive) {
        String canonPath = "".equals(startPath) ? "" : normalizePath(startPath);
        if (relativePath != null) canonPath = relativePath + "/" + canonPath;
        final Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            final JarEntry jarEntry = entries.nextElement();
            final String name = jarEntry.getName();
            final int idx = name.lastIndexOf('/');
            if (idx == -1) continue;
            final String path = name.substring(0, idx);
            if (recursive ? PathUtils.isChild(canonPath, path) : PathUtils.isDirectChild(canonPath, path)) {
                if (relativePath == null) {
                    index.add(path);
                } else {
                    index.add(path.substring(relativePath.length() + 1));
                }
            }
        }
        if (index.size() > 0) {
            if (relativePath == null) {
                index.add(canonPath);
            } else {
                index.add(canonPath.substring(relativePath.length() + 1));
            }
        }
    }

    private static final CodeSigners EMPTY_CODE_SIGNERS = new CodeSigners(new CodeSigner[0]);

    static final class CodeSigners {

        private final CodeSigner[] codeSigners;
        private final int hashCode;

        public CodeSigners(final CodeSigner[] codeSigners) {
            this.codeSigners = codeSigners;
            hashCode = Arrays.hashCode(codeSigners);
        }

        public boolean equals(final Object obj) {
            return obj instanceof CodeSigners && equals((CodeSigners) obj);
        }

        private boolean equals(final CodeSigners other) {
            return Arrays.equals(codeSigners, other.codeSigners);
        }

        public int hashCode() {
            return hashCode;
        }
    }
}
