/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.moduleservice;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.modules.Module;

/**
 * Utility class for read a composite index from a system module.
 *
 * @author Stuart Douglas
 */
public class ModuleIndexBuilder {

    public static final String INDEX_LOCATION = "META-INF/jandex.idx";

    public static CompositeIndex buildCompositeIndex(Module module) {
        try {
            final Enumeration<URL> resources = module.getClassLoader().getResources(INDEX_LOCATION);
            if (!resources.hasMoreElements()) {
                return null;
            }
            final Set<Index> indexes = new HashSet<Index>();
            while (resources.hasMoreElements()) {
                final URL url = resources.nextElement();
                InputStream stream = url.openStream();
                try {
                    IndexReader reader = new IndexReader(stream);
                    indexes.add(reader.read());
                } finally {
                    stream.close();
                }
            }
            return new CompositeIndex(indexes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private ModuleIndexBuilder() {

    }

}
