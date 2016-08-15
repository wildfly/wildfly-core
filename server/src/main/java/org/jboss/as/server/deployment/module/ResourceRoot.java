/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.SimpleAttachable;
import org.wildfly.loaders.ResourceLoader;

/**
 * @author John E. Bailey
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class ResourceRoot extends SimpleAttachable {

    private final ResourceLoader loader;
    private final List<FilterSpecification> exportFilters = new ArrayList<FilterSpecification>();
    private boolean usePhysicalCodeSource;

    public ResourceRoot(final ResourceLoader loader) {
        if (loader == null) throw new NullPointerException();
        this.loader = loader;
    }

    public ResourceLoader getLoader() {
        return loader;
    }

    public List<FilterSpecification> getExportFilters() {
        return exportFilters;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ResourceRoot [");
        builder.append("loader=").append(loader.getRootName());
        builder.append("]");
        return builder.toString();
    }

    public void setUsePhysicalCodeSource(final boolean usePhysicalCodeSource) {
        this.usePhysicalCodeSource = usePhysicalCodeSource;
    }

    public boolean isUsePhysicalCodeSource() {
        return usePhysicalCodeSource;
    }

    /**
     * Merges information from the resource root into this resource root
     *
     * @param additionalResourceRoot The root to merge
     */
    public void merge(final ResourceRoot additionalResourceRoot) {
        if (!additionalResourceRoot.getLoader().getPath().equals(loader.getPath())) {
            throw ServerLogger.ROOT_LOGGER.cannotMergeResourceRoot(loader.getPath(), additionalResourceRoot.getLoader().getPath());
        }
        usePhysicalCodeSource = additionalResourceRoot.usePhysicalCodeSource;
        if (additionalResourceRoot.getExportFilters().isEmpty()) {
            //new root has no filters, so we don't want our existing filters to break anything
            //see WFLY-1527
            this.exportFilters.clear();
        } else {
            this.exportFilters.addAll(additionalResourceRoot.getExportFilters());
        }
    }
}
