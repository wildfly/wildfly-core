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

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import org.jboss.modules.ClassSpec;
import org.jboss.modules.IterableResourceLoader;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.PathUtils;
import org.jboss.modules.Resource;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class FilteredResourceLoader implements ResourceLoader {

    private final PathFilter filter;
    private final ResourceLoader loader;

    FilteredResourceLoader(final PathFilter filter, final ResourceLoader loader) {
        this.filter = filter;
        this.loader = loader;
    }

    public String getRootName() {
        return loader.getRootName();
    }

    public ClassSpec getClassSpec(final String fileName) throws IOException {
        final String canonicalFileName = PathUtils.canonicalize(PathUtils.relativize(fileName));
        return filter.accept(canonicalFileName) ? loader.getClassSpec(canonicalFileName) : null;
    }

    public PackageSpec getPackageSpec(final String name) throws IOException {
        return loader.getPackageSpec(PathUtils.canonicalize(PathUtils.relativize(name)));
    }

    public Resource getResource(final String name) {
        final String canonicalFileName = PathUtils.canonicalize(PathUtils.relativize(name));
        return filter.accept(canonicalFileName) ? loader.getResource(canonicalFileName) : null;
    }

    public String getLibrary(final String name) {
        return loader.getLibrary(PathUtils.canonicalize(PathUtils.relativize(name)));
    }

    public Collection<String> getPaths() {
        return loader.getPaths();
    }

    public Iterator<Resource> iterateResources(final String startPath, final boolean recursive) {
        return PathFilters.filtered(filter, loader.iterateResources(PathUtils.relativize(PathUtils.canonicalize(startPath)), recursive));
    }

    public void close() {
        loader.close();
    }

    public ResourceLoader getParent() {
        return loader.getParent();
    }

    IterableResourceLoader getLoader() {
        return loader;
    }

}
