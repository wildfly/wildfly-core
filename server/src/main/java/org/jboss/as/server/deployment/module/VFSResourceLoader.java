/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import static java.security.AccessController.doPrivileged;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.modules.AbstractResourceLoader;
import org.jboss.modules.ClassSpec;
import org.jboss.modules.IterableResourceLoader;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.PathUtils;
import org.jboss.modules.Resource;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VFSUtils;
import org.jboss.vfs.VirtualFile;
import org.jboss.vfs.VirtualFileFilter;
import org.jboss.vfs.VirtualFilePermission;
import org.jboss.vfs.VisitorAttributes;
import org.jboss.vfs.util.FilterVirtualFileVisitor;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Resource loader capable of loading resources from VFS archives.
 *
 * @author John Bailey
 * @author Thomas.Diesler@jboss.com
 */
public class VFSResourceLoader extends AbstractResourceLoader implements IterableResourceLoader {

    private static final String MR_PREFIX = "META-INF/versions/";
    private static final int RELEASE = Runtime.version().feature();
    private static final Attributes.Name MULTI_RELEASE_NAME = new Attributes.Name("Multi-Release");

    private final VirtualFile root;
    private final String rootName;
    private final Manifest manifest;
    private final URL rootUrl;
    private final boolean multiRelease;

    // protected by {@code this}
    private final Map<CodeSigners, CodeSource> codeSources = new HashMap<>();

    /**
     * Construct new instance.
     *
     * @param rootName The module root name
     * @param root The root virtual file
     * @throws IOException if the manifest could not be read or the root URL is invalid
     */
    public VFSResourceLoader(final String rootName, final VirtualFile root) throws IOException {
        this(rootName, root, false);
    }

    /**
     * Construct new instance.
     *
     * @param rootName The module root name
     * @param root The root virtual file
     * @param usePhysicalCodeSource {@code true} to use the physical root URL for code sources, {@code false} to use the VFS URL
     * @throws IOException if the manifest could not be read or the root URL is invalid
     */
    public VFSResourceLoader(final String rootName, final VirtualFile root, final boolean usePhysicalCodeSource) throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        final boolean checking = WildFlySecurityManager.isChecking();
        if (checking) {
            sm.checkPermission(new VirtualFilePermission(root.getPathName(), "read"));
        }
        this.root = root;
        this.rootName = rootName;
        try {
            manifest = checking ? doPrivileged(new PrivilegedExceptionAction<Manifest>() {
                public Manifest run() throws IOException {
                    return VFSUtils.getManifest(root);
                }
            }) : VFSUtils.getManifest(root);
        } catch (PrivilegedActionException pe) {
            try {
                throw pe.getException();
            } catch (IOException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }
        }
        // A multi-release jar file is a jar file that contains a manifest with a main attribute named "Multi-Release"
        // with value true
        multiRelease = manifest != null && Boolean.parseBoolean(manifest.getMainAttributes().getValue(MULTI_RELEASE_NAME));
        rootUrl = usePhysicalCodeSource ? VFSUtils.getRootURL(root) : root.asFileURL();
    }

    VirtualFile getExistentVirtualFile(final String name) {
        VirtualFile file;
        int version = RELEASE;
        if (multiRelease) while (version >= 9) {
            file = root.getChild(MR_PREFIX + version + "/" + name);
            if (file.exists()) {
                return file;
            }
            version --;
        }
        file = root.getChild(name);
        return file.exists() ? file : null;
    }

    /**
     * Determine if this resource root is a multi-release root.
     *
     * @return {@code true} if it is a multi-release root, {@code false} otherwise
     */
    public boolean isMultiRelease() {
        return multiRelease;
    }

    /** {@inheritDoc} */
    public ClassSpec getClassSpec(final String name) throws IOException {
        try {
            return doPrivileged(new PrivilegedExceptionAction<ClassSpec>() {
                public ClassSpec run() throws Exception {
                    final VirtualFile file = getExistentVirtualFile(name);
                    if (file == null) return null;
                    final long size = file.getSize();
                    final ClassSpec spec = new ClassSpec();
                    synchronized (VFSResourceLoader.this) {
                        final InputStream is = file.openStream();
                        try {
                            if (size <= Integer.MAX_VALUE) {
                                final int castSize = (int) size;
                                byte[] bytes = new byte[castSize];
                                int a = 0, res;
                                while ((res = is.read(bytes, a, castSize - a)) > 0) {
                                    a += res;
                                }
                                // consume remainder so that cert check doesn't fail in case of wonky JARs
                                while (is.read() != -1) {}
                                // done
                                is.close();
                                spec.setBytes(bytes);
                                final CodeSigner[] entryCodeSigners = file.getCodeSigners();
                                final CodeSigners codeSigners = entryCodeSigners == null || entryCodeSigners.length == 0 ? EMPTY_CODE_SIGNERS : new CodeSigners(entryCodeSigners);
                                CodeSource codeSource = codeSources.get(codeSigners);
                                if (codeSource == null) {
                                    codeSources.put(codeSigners, codeSource = new CodeSource(rootUrl, entryCodeSigners));
                                }
                                spec.setCodeSource(codeSource);
                                return spec;
                            } else {
                                throw ServerLogger.ROOT_LOGGER.resourceTooLarge();
                            }
                        } finally {
                            VFSUtils.safeClose(is);
                        }
                    }
                }
            });
        } catch (PrivilegedActionException pe) {
            try {
                throw pe.getException();
            } catch (IOException | Error | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }
        }
    }

    /** {@inheritDoc} */
    public PackageSpec getPackageSpec(final String name) throws IOException {
        return getPackageSpec(name, this.manifest, this.rootUrl);
    }

    /** {@inheritDoc} */
    public String getLibrary(final String name) {
        return null;
    }

    /** {@inheritDoc} */
    public String getRootName() {
        return rootName;
    }

    /** {@inheritDoc} */
    public PathFilter getExportFilter() {
        return PathFilters.acceptAll();
    }

    /** {@inheritDoc} */
    public Resource getResource(final String name) {
        return doPrivileged(new PrivilegedAction<Resource>() {
            public Resource run() {
                try {
                    final VirtualFile file = getExistentVirtualFile(PathUtils.canonicalize(name));
                    if (file == null) {
                        return null;
                    }
                    return new VFSEntryResource(file.getPathNameRelativeTo(root), file, file.toURL());
                } catch (MalformedURLException e) {
                    // must be invalid...?  (todo: check this out)
                    return null;
                }
            }
        });
    }

    /** {@inheritDoc} */
    public Collection<String> getPaths() {
        final List<String> index = new ArrayList<String>();
        // First check for an index file
        final VirtualFile indexFile = VFS.getChild(root.getPathName() + ".index");
        if (indexFile.exists()) {
            try {
                final BufferedReader r = new BufferedReader(new InputStreamReader(indexFile.openStream(), StandardCharsets.UTF_8));
                try {
                    String s;
                    while ((s = r.readLine()) != null) {
                        index.add(s.trim());
                    }
                    return index;
                } finally {
                    // if exception is thrown, undo index creation
                    r.close();
                }
            } catch (IOException e) {
                index.clear();
            }
        }

        FilterVirtualFileVisitor visitor = new FilterVirtualFileVisitor(new VirtualFileFilter() {
            @Override
            public boolean accepts(VirtualFile file) {
                return file.isDirectory();
            }
        }, VisitorAttributes.RECURSE);
        try {
            root.visit(visitor);
        } catch (IOException e) {
            index.clear();
        }

        index.add("");
        for (VirtualFile dir : visitor.getMatched()) {
            index.add(dir.getPathNameRelativeTo(root));
        }

        return index;
    }

    @Override
    public Iterator<Resource> iterateResources(String startPath, boolean recursive) {
        VirtualFile child = root.getChild(startPath);
        if (startPath.length() > 1 && child == root) {
            return Collections.<Resource>emptySet().iterator();
        }
        VirtualFileFilter filter = new VirtualFileFilter() {
            @Override
            public boolean accepts(VirtualFile file) {
                return file.isFile();
            }
        };
        final Iterator<VirtualFile> children;
        try {
            children = (recursive ? child.getChildrenRecursively(filter) : child.getChildren(filter)).iterator();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return new Iterator<Resource>() {

            @Override
            public boolean hasNext() {
                return children.hasNext();
            }

            @Override
            public Resource next() {
                VirtualFile file = children.next();
                URL fileURL;
                try {
                    fileURL = file.toURL();
                } catch (MalformedURLException ex) {
                    throw new IllegalStateException(ex);
                }
                return new VFSEntryResource(file.getPathNameRelativeTo(root), file, fileURL);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    static class VFSEntryResource implements Resource {
        private final String name;
        private final VirtualFile entry;
        private final URL resourceURL;

        VFSEntryResource(final String name, final VirtualFile entry, final URL resourceURL) {
            this.name = name;
            this.entry = entry;
            this.resourceURL = resourceURL;
        }

        public String getName() {
            return name;
        }

        public URL getURL() {
            return resourceURL;
        }

        public InputStream openStream() throws IOException {
            return entry.openStream();
        }

        public long getSize() {
            final long size = entry.getSize();
            return size == -1 ? 0 : size;
        }
    }

    static final CodeSigners EMPTY_CODE_SIGNERS = new CodeSigners(new CodeSigner[0]);

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
