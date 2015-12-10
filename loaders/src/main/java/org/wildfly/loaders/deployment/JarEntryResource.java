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

import org.jboss.modules.Resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class JarEntryResource implements Resource {
    private final JarFile jarFile;
    private final JarEntry entry;
    private final String entryName;
    private final URL resourceURL;
    private final File overlay;
    private final URL overlayURL;
    private final String loaderFullPath;

    JarEntryResource(final JarFile jarFile, final JarEntry entry, final String entryName, final URL resourceURL, final File overlay, final URL overlayURL, final String loaderFullPath) {
        this.jarFile = jarFile;
        this.entry = entry;
        this.entryName = entryName;
        this.resourceURL = resourceURL;
        this.overlay = overlay;
        this.overlayURL = overlayURL;
        this.loaderFullPath = loaderFullPath;
    }

    public String getName() {
        return entryName;
    }

    public URL getURL() {
        if (loaderFullPath != null) {
            try {
                return new URL("deployment", null, 0, loaderFullPath + "/" + entryName, new DeploymentURLStreamHandler(this));
            } catch (final MalformedURLException ignored) {
                throw new IllegalStateException(); // should never happen
            }
        } else {
            return overlayURL != null ? overlayURL : resourceURL;
        }
    }

    public InputStream openStream() throws IOException {
        return overlay != null ? new FileInputStream(overlay) : jarFile.getInputStream(entry);
    }

    public long getSize() {
        final long size = overlay != null ? overlay.length() : entry.getSize();
        return size == -1 ? 0 : size;
    }

    JarEntry getEntry() {
        return entry;
    }
}
