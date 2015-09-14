/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
package org.jboss.as.server.deployment.module;

import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;
import org.jboss.vfs.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Package utility methods.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class Utils {

    private Utils() {
        // forbidden instantiation
    }

    static Manifest getManifest(final ResourceRoot resourceRoot) throws IOException {
        final ResourceLoader loader = resourceRoot.getLoader();
        if (loader != null) {
            return readManifest(loader.getResource(JarFile.MANIFEST_NAME));
        } else {
            return readManifest(resourceRoot.getRoot().getChild(JarFile.MANIFEST_NAME));
        }
    }

    private static Manifest readManifest(final VirtualFile manifest) throws IOException {
        if (manifest == null || !manifest.exists()) return null;
        final InputStream stream = new PaddedManifestStream(manifest.openStream());
        try {
            return new Manifest(stream);
        } finally {
            if (stream != null) try { stream.close(); } catch (final Throwable ignored) {}
        }
    }

    private static Manifest readManifest(final Resource manifestResource) throws IOException {
        if (manifestResource == null) return null;
        final InputStream stream = new PaddedManifestStream(manifestResource.openStream());
        try {
            return new Manifest(stream);
        } finally {
            if (stream != null) try { stream.close(); } catch (final Throwable ignored) {}
        }
    }

}
