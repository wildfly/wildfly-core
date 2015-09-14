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

import org.jboss.modules.ClassSpec;
import org.jboss.modules.IterableResourceLoader;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.Resource;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * ResourceLoader that delegates all method calls to its delegate.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
class DelegatingResourceLoader implements ResourceLoader {

    private final IterableResourceLoader delegate;

    DelegatingResourceLoader(final IterableResourceLoader delegate) {
        this.delegate = delegate;
    }

    final IterableResourceLoader getDelegate() {
        return delegate;
    }

    @Override
    public Iterator<Resource> iterateResources(final String startPath, final boolean recursive) {
        return getDelegate().iterateResources(startPath, recursive);
    }

    @Override
    public String getRootName() {
        return getDelegate().getRootName();
    }

    @Override
    public ClassSpec getClassSpec(final String fileName) throws IOException {
        return getDelegate().getClassSpec(fileName);
    }

    @Override
    public PackageSpec getPackageSpec(final String name) throws IOException {
        return getDelegate().getPackageSpec(name);
    }

    @Override
    public Resource getResource(final String name) {
        return getDelegate().getResource(name);
    }

    @Override
    public String getLibrary(final String name) {
        return getDelegate().getLibrary(name);
    }

    @Override
    public Collection<String> getPaths() {
        return getDelegate().getPaths();
    }

    @Override
    public void close() {
        getDelegate().close();
    }

    @Override
    public ResourceLoader getParent() {
        return null;
    }

}
