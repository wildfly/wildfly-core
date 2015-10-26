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

import org.jboss.modules.AbstractResourceLoader;
import org.jboss.modules.ClassSpec;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.PathUtils;
import org.jboss.modules.Resource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLStreamHandler;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Thomas.Diesler@jboss.com
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class JarFileResourceLoader extends AbstractResourceLoader implements ResourceLoader {
    private static final String INDEX_FILE = "META-INF/PATHS.LIST";

    private final ResourceLoader parent;
    // protected by {@code children}
    private final Map<String, ResourceLoader> children = new HashMap<>();
    // protected by {@code overlays}
    private final Map<String, File> overlays = new HashMap<>();
    private final JarFile jarFile;
    private final String rootName;
    private final URL rootUrl;
    private final String path;
    private final String relativePath;
    private final File fileOfJar;

    // protected by {@code this}
    private final Map<CodeSigners, CodeSource> codeSources = new HashMap<>();

    JarFileResourceLoader(final ResourceLoader parent, final String rootName, final JarFile jarFile, final String path) {
        this(parent, rootName, jarFile, path, null);
    }

    JarFileResourceLoader(final ResourceLoader parent, final String rootName, final JarFile jarFile, final String path, final String relativePath) {
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
        String realPath = relativePath == null ? null : PathUtils.canonicalize(relativePath);
        if (realPath != null && realPath.endsWith("/")) realPath = realPath.substring(0, realPath.length() - 1);
        this.relativePath = realPath;
        try {
            rootUrl = getJarURI(fileOfJar.toURI(), realPath).toURL();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid root file specified", e);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid root file specified", e);
        }
    }

    void addChild(final String path, final ResourceLoader loader) {
        synchronized (children) {
            if (children.get(path) != null) {
                throw new IllegalStateException("Child loader for '" + path + "' already registered"); // TODO: remove this check?
            }
            children.put(path, loader);
        }
        synchronized (overlays) {
            for (final String overlayPath : overlays.keySet()) {
                if (overlayPath.startsWith(path)) {
                    loader.addOverlay(overlayPath.substring(path.length() + 1), overlays.get(overlayPath));
                }
            }
        }
    }

    public ResourceLoader getChild(final String path) {
        synchronized (children) {
            return children.get(path);
        }
    }

    @Override
    public void addOverlay(final String resourcePath, final File content) {
        synchronized (children) {
            if (children.size() > 0) {
                for (final String path : children.keySet()) {
                    if (resourcePath.startsWith(path)) {
                        if (resourcePath.length() == path.length())
                            throw new UnsupportedOperationException(); // TODO: remove this check?
                        children.get(path).addOverlay(resourcePath.substring(path.length() + 1), content);
                        return;
                    }
                }
                return;
            }
        }
        if (resourcePath.endsWith("/")) {
            // TODO: remove this check? Shouldn't be validated in DUP?
            throw new IllegalArgumentException("Invalid overlay path: '" + resourcePath + "'");
        }
        synchronized (overlays) {
            overlays.put(resourcePath, content);
        }
    }

    @Override
    public ResourceLoader getParent() {
        return parent;
    }

    public File getRoot() {
        return fileOfJar;
    }

    public String getPath() {
        return path;
    }

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

    public String getRootName() {
        return rootName;
    }

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
                final byte[] buf = new byte[16384];
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
            codeSources.put(codeSigners, codeSource = new CodeSource(rootUrl, entryCodeSigners));
        }
        return codeSource;
    }

    private JarEntry getJarEntry(final String fileName) {
        return relativePath == null ? jarFile.getJarEntry(fileName) : jarFile.getJarEntry(relativePath + "/" + fileName);
    }

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

    public String getLibrary(final String name) {
        // JARs cannot have libraries in them
        return null;
    }

    public Resource getResource(String name) {
        if (name == null) return null;
        try {
            name = PathUtils.canonicalize(PathUtils.relativize(name));
            if (name.endsWith("/")) return null;
            final JarFile jarFile = this.jarFile;
            final JarEntry entry = getJarEntry(name);
            File overlay;
            synchronized (overlays) {
                overlay = overlays.get(name);
            }
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
            return new JarEntryResource(jarFile, entry, name, entryURL, overlay, overlayURL);
        } catch (MalformedURLException e) {
            // must be invalid...?  (todo: check this out)
            return null;
        } catch (URISyntaxException e) {
            // must be invalid...?  (todo: check this out)
            return null;
        }
    }

    public Iterator<Resource> iterateResources(String startPath, final boolean recursive) {
        final JarFile jarFile = this.jarFile;
        if (relativePath != null) startPath = startPath.equals("") ? relativePath : relativePath + "/" + startPath;
        final String startName = PathUtils.canonicalize(PathUtils.relativize(startPath));
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
                                    next = new JarEntryResource(jarFile, null, overlayPath, null, overlay, overlayURL);
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
                                        next = new JarEntryResource(jarFile, entry, name, getJarURI(new File(jarFile.getName()).toURI(), entry.getName()).toURL(), null, null);
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

    public Iterator<String> iteratePaths(String startPath, final boolean recursive) {
        if (startPath == null) throw new NullPointerException("Method parameter cannot be null");
        if (relativePath != null) startPath = startPath.equals("") ? relativePath : relativePath + "/" + startPath;
        final String startName = PathUtils.canonicalize(PathUtils.relativize(startPath));
        if (!"".equals(startName) && jarFile.getJarEntry(startName) == null) return Collections.emptyIterator();
        final Collection<String> index = new HashSet<String>();
        extractJarPaths(jarFile, startName, index, recursive);
        return index.iterator();
    }

    public Collection<String> getPaths() {
        final Collection<String> index = new HashSet<String>();
        index.add("");
        String relativePath = this.relativePath != null ? this.relativePath : "";
        // First check for an external index
        final JarFile jarFile = this.jarFile;
        final String jarFileName = jarFile.getName();
        final long jarModified = fileOfJar.lastModified();
        final File indexFile = new File(jarFileName + ".index");
        if (ResourceLoaders.USE_INDEXES) {
            if (indexFile.exists()) {
                final long indexModified = indexFile.lastModified();
                if (indexModified != 0L && jarModified != 0L && indexModified >= jarModified) try {
                    return readIndex(new FileInputStream(indexFile), index, relativePath);
                } catch (IOException e) {
                    index.clear();
                }
            }
        }
        // Next check for an internal index
        JarEntry listEntry = jarFile.getJarEntry(INDEX_FILE);
        if (listEntry != null) {
            try {
                return readIndex(jarFile.getInputStream(listEntry), index, relativePath);
            } catch (IOException e) {
                index.clear();
            }
        }
        // Next just read the JAR
        extractJarPaths(jarFile, relativePath, index, true);

        if (ResourceLoaders.WRITE_INDEXES && relativePath == null) {
            writeExternalIndex(indexFile, index);
        }
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

    static void extractJarPaths(final JarFile jarFile, String relativePath,
            final Collection<String> index, final boolean recursive) {
        relativePath = "".equals(relativePath) ? relativePath : relativePath + "/";
        final Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            final JarEntry jarEntry = entries.nextElement();
            final String name = jarEntry.getName();
            final int idx = name.lastIndexOf('/');
            if (idx == -1) continue;
            final String path = name.substring(0, idx);
            if (path.length() == 0 || path.endsWith("/")) {
                // invalid name, just skip...
                continue;
            }
            if (recursive ? PathUtils.isChild(relativePath, path) : PathUtils.isDirectChild(relativePath, path)) {
                if (relativePath.equals("")) {
                    index.add(path);
                } else {
                    index.add(path.substring(relativePath.length()));
                }
            }
        }
        if (index.size() > 0) {
            index.add("");
        }
    }

    static void writeExternalIndex(final File indexFile,
            final Collection<String> index) {
        // Now try to write it
        boolean ok = false;
        try {
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(indexFile)));
            try {
                for (String name : index) {
                    writer.write(name);
                    writer.write('\n');
                }
                writer.close();
                ok = true;
            } finally {
                IOUtils.safeClose(writer);
            }
        } catch (IOException e) {
            // failed, ignore
        } finally {
            if (! ok) {
                // well, we tried...
                indexFile.delete();
            }
        }
    }

    static Collection<String> readIndex(final InputStream stream, final Collection<String> index, final String relativePath) throws IOException {
        final BufferedReader r = new BufferedReader(new InputStreamReader(stream));
        try {
            String s;
            while ((s = r.readLine()) != null) {
                String name = s.trim();
                if (relativePath == null) {
                    index.add(name);
                } else {
                    if (name.startsWith(relativePath + "/")) {
                        index.add(name.substring(relativePath.length() + 1));
                    }
                }
            }
            return index;
        } finally {
            // if exception is thrown, undo index creation
            r.close();
        }
    }

    static void addInternalIndex(File file, boolean modify) throws IOException {
        final JarFile oldJarFile = new JarFile(file, false);
        try {
            final Collection<String> index = new TreeSet<String>();
            final File outputFile;

            outputFile = new File(file.getAbsolutePath().replace(".jar", "-indexed.jar"));

            final ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(outputFile));
            try {
                Enumeration<JarEntry> entries = oldJarFile.entries();
                while (entries.hasMoreElements()) {
                    final JarEntry entry = entries.nextElement();

                    // copy data, unless we're replacing the index
                    if (!entry.getName().equals(INDEX_FILE)) {
                        final JarEntry clone = (JarEntry) entry.clone();
                        // Compression level and format can vary across implementations
                        if (clone.getMethod() != ZipEntry.STORED)
                            clone.setCompressedSize(-1);
                        zo.putNextEntry(clone);
                        IOUtils.copy(oldJarFile.getInputStream(entry), zo);
                    }

                    // add to the index
                    final String name = entry.getName();
                    final int idx = name.lastIndexOf('/');
                    if (idx == -1) continue;
                    final String path = name.substring(0, idx);
                    if (path.length() == 0 || path.endsWith("/")) {
                        // invalid name, just skip...
                        continue;
                    }
                    index.add(path);
                }

                // write index
                zo.putNextEntry(new ZipEntry(INDEX_FILE));
                final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zo));
                try {
                    for (String name : index) {
                        writer.write(name);
                        writer.write('\n');
                    }
                    writer.close();
                } finally {
                    IOUtils.safeClose(writer);
                }
                zo.close();
                oldJarFile.close();

                if (modify) {
                    file.delete();
                    if (!outputFile.renameTo(file)) {
                        throw new IOException("failed to rename " + outputFile.getAbsolutePath() + " to " + file.getAbsolutePath());
                    }
                }
            } finally {
                IOUtils.safeClose(zo);
            }
        } finally {
            IOUtils.safeClose(oldJarFile);
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
