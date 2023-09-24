/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.services.path;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ONLY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELATIVE_TO;

import org.jboss.dmr.ModelNode;

/**
 * Represents a path entry
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class PathEntry {
    private final String name;
    private volatile String path;
    private volatile String relativeTo;
    private final boolean readOnly;
    private volatile PathResolver resolver;

    PathEntry(String name, String path, String relativeTo, boolean readOnly, PathResolver resolver) {
        this.name = name;
        this.path = path;
        this.relativeTo = relativeTo;
        this.readOnly = readOnly;
        this.resolver = resolver;
    }

    public PathEntry(PathEntry pathEntry) {
        this.name = pathEntry.name;
        this.path = pathEntry.path;
        this.relativeTo = pathEntry.relativeTo;
        this.readOnly = pathEntry.readOnly;
        this.resolver = pathEntry.resolver;
    }

    /**
     * Gets the name of the path within the model
     *
     * @return the name of the path
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the fully resolved path
     *
     * @return the name of the path this path is relative to. If {@code null} this is an absolute path
     */
    public String resolvePath() {
        return resolver.resolvePath(name, path, relativeTo, resolver);
    }

    /**
     * Gets the value of the path within the model. This is either the absolute path
     * or the relative path.
     *
     * @return the value of the path
     */
    String getPath() {
        return path;
    }

    /**
     * Gets the name of the logical path this path is relative to, if any.
     *
     * @return the name of the logical path. If {@code null} this is an absolute path
     */
    String getRelativeTo() {
        return relativeTo;
    }

    /**
     * Gets whether the path is immutable, and cannot be removed or modified via a management operation.
     *
     * @return {@code true} if the path is immutable
     */
    boolean isReadOnly() {
        return readOnly;
    }

    void setPath(String path) {
        this.path = path;
    }

    void setRelativeTo(String relativeTo) {
        this.relativeTo = relativeTo;
    }

    void setPathResolver(PathResolver resolver) {
        this.resolver = resolver;
    }

    boolean isResolved() {
        return resolver.isResolved(relativeTo);
    }

    ModelNode toModel() {
        ModelNode model = new ModelNode();
        model.get(NAME).set(name);
        model.get(PATH).set(path);
        if (relativeTo != null) {
            model.get(RELATIVE_TO).set(relativeTo);
        } else {
            model.get(RELATIVE_TO);
        }
        if (readOnly) {
            model.get(READ_ONLY).set(readOnly);
        }
        return model;
    }

    interface PathResolver {
        String resolvePath(String name, String path, String relativeTo, PathResolver resolver);
        boolean isResolved(String relativeTo);
    }
}
