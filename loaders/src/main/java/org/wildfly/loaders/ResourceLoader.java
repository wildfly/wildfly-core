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

package org.wildfly.loaders;

import org.jboss.modules.IterableResourceLoader;

import java.io.File;
import java.net.URL;
import java.util.Iterator;

/**
 * Resource loader. Resource laoder can be associated with either deployment, subdeployment, library inside the deployment
 * or standalone archives that are not representing deployments or any parts of it.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface ResourceLoader extends IterableResourceLoader {

    /**
     * Returns loader's root file. It can be either directory or packaged jar archive.
     * @return loaders's root file
     */
    File getRoot();

    /**
     * Returns loader's root URL. The URL protocol is either 'jar' or 'file'.
     * @return root URL
     */
    URL getRootURL();

    /**
     * Returns loader path. It is either empty string if the loader is associated with top level archive,
     * or it is loader's relative path to its parent
     * @return loader path
     */
    String getPath();

    /**
     * Returns loader full path. It is never empty string. If parent has loader then its
     * full path consists of parent's full path plus current loader path.
     * @return full loader path
     */
    String getFullPath();

    /**
     * Returns parent loader of this loader. It returns null if loader is top level loader.
     * @return parent loader of this loader
     */
    ResourceLoader getParent();

    /**
     * Returns child loader of this loader associated with given path.
     * @param path to lookup loader for
     * @return child loader associated with the path
     */
    ResourceLoader getChild(String path);

    /**
     * Iterates sub paths under given start path.
     * @param startPath to search for sub paths
     * @param recursive whether search is recursive or not
     * @return sub paths of given path
     */
    Iterator<String> iteratePaths(String startPath, boolean recursive);

    /**
     * Registers overlay with this loader. The overlay is automatically propagated to child loaders.
     * @param path to register overlay for
     * @param content the given overlay
     */
    void addOverlay(String path, File content);

}
