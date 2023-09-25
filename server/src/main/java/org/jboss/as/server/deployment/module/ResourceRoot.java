/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.SimpleAttachable;
import org.jboss.vfs.VirtualFile;

/**
 * @author John E. Bailey
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ResourceRoot extends SimpleAttachable {


    private final String rootName;
    private final VirtualFile root;
    private final MountHandle mountHandle;
    private final List<FilterSpecification> exportFilters = new ArrayList<FilterSpecification>();
    private boolean usePhysicalCodeSource;

    public ResourceRoot(final VirtualFile root, final MountHandle mountHandle) {
        this(root.getName(), root, mountHandle);
    }

    public ResourceRoot(final String rootName, final VirtualFile root, final MountHandle mountHandle) {
        this.rootName = rootName;
        this.root = root;
        this.mountHandle = mountHandle;
    }

    public String getRootName() {
        return rootName;
    }

    public VirtualFile getRoot() {
        return root;
    }

    public MountHandle getMountHandle() {
        return mountHandle;
    }

    public List<FilterSpecification> getExportFilters() {
        return exportFilters;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ResourceRoot [");
        if (root != null)
            builder.append("root=").append(root);
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
        if(!additionalResourceRoot.getRoot().equals(root)) {
            throw ServerLogger.ROOT_LOGGER.cannotMergeResourceRoot(root, additionalResourceRoot.getRoot());
        }
        usePhysicalCodeSource = additionalResourceRoot.usePhysicalCodeSource;
        if(additionalResourceRoot.getExportFilters().isEmpty()) {
            //new root has no filters, so we don't want our existing filters to break anything
            //see WFLY-1527
            this.exportFilters.clear();
        } else {
            this.exportFilters.addAll(additionalResourceRoot.getExportFilters());
        }
    }
}
