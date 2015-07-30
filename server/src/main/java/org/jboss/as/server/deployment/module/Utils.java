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

    /**
     * Get a manifest from a virtual file, assuming the virtual file is the root of an archive
     *
     * @param archive the root the archive
     * @return the manifest or null if not found
     * @throws IOException if there is an error reading the manifest or the virtual file is closed
     */
    static Manifest getManifest(final VirtualFile archive) throws IOException {
        if (archive == null) {
            return null;
        }
        final VirtualFile manifest = archive.getChild(JarFile.MANIFEST_NAME);
        if (manifest == null || !manifest.exists()) {
            return null;
        }
        return readManifest(manifest);
    }

    /**
     * Read the manifest from given manifest VirtualFile.
     *
     * @param manifest the VF to read from
     * @return JAR's manifest
     * @throws IOException if problems while opening VF stream occur
     */
    private static Manifest readManifest(final VirtualFile manifest) throws IOException {
        final InputStream stream = new PaddedManifestStream(manifest.openStream());
        try {
            return new Manifest(stream);
        } finally {
            if (stream != null) try { stream.close(); } catch (final Throwable ignored) {}
        }
    }

}
