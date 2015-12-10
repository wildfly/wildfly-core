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

import org.jboss.modules.ClassSpec;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.Resource;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.AccessControlContext;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class SingleFileResourceLoader implements ResourceLoader {

    private final String name;
    private final String path;
    private final String fullPath;
    private final File root;
    private final ResourceLoader parent;
    private final AccessControlContext context;
    private volatile boolean isDeployment;

    SingleFileResourceLoader(final String name, final File root, final String path, final ResourceLoader parent, final boolean isDeployment, final AccessControlContext context) {
        this.name = name;
        this.root = root;
        this.parent = parent;
        this.path = path == null ? "" : path;
        this.context = context;
        this.fullPath = parent != null ? parent.getFullPath() + "/" + path : name;
    }

    @Override
    public void setUsePhysicalCodeSource(final boolean usePhysicalCodeSource) {
        this.isDeployment = !usePhysicalCodeSource;
    }

    @Override
    public ResourceLoader getParent() {
        return parent;
    }

    @Override
    public ResourceLoader getChild(final String path) {
        return null;
    }

    @Override
    public Iterator<String> iteratePaths(final String startPath, final boolean recursive) {
        return Collections.emptyIterator();
    }

    @Override
    public void addOverlay(final String path, final File content) {
        // unsupported
    }

    @Override
    public Iterator<Resource> iterateResources(final String startPath, final boolean recursive) {
        if (!"".equals(startPath)) return Collections.emptyIterator();
        // one shot iterator
        return new Iterator<Resource>() {
            boolean done;

            @Override
            public boolean hasNext() {
                return !done;
            }

            @Override
            public Resource next() {
                try {
                    return new FileEntryResource(name, root, isDeployment ? fullPath : null, context);
                } finally {
                    done = true;
                }
            }
        };
    }

    @Override
    public String getRootName() {
        return name;
    }

    @Override
    public ClassSpec getClassSpec(final String fileName) throws IOException {
        return null;
    }

    @Override
    public PackageSpec getPackageSpec(final String name) throws IOException {
        return null;
    }

    @Override
    public Resource getResource(final String name) {
        return "".equals(name) ? new FileEntryResource(name, root, isDeployment ? fullPath : null, context) : null;
    }

    @Override
    public String getLibrary(final String name) {
        return null;
    }

    @Override
    public Collection<String> getPaths() {
        return Collections.emptyList();
    }

    @Override
    public void close() {
        // does nothing
    }

    @Override
    public File getRoot() {
        return null;
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
        return null;
    }

}
