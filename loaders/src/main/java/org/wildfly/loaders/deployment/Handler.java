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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class Handler extends URLStreamHandler {

    static final String DEPLOYMENT_PROTOCOL = "deployment";

    @Override
    protected URLConnection openConnection(final URL url) throws IOException {
        if (!DEPLOYMENT_PROTOCOL.equals(url.getProtocol())) {
            throw new IllegalArgumentException("Wrong protocol: " + url.getProtocol());
        }
        final Resource urlResource = ResourceLoaders.getResourceFor(url);
        if (urlResource == null) throw new IllegalArgumentException("Unknown resource: " + url);
        return new URLConnection(url) {
            @Override
            public void connect() throws IOException {
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return urlResource.openStream();
            }
        };
    }

}
