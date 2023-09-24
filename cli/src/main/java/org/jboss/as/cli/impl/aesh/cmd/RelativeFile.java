/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd;

import java.io.File;

/**
 * Use this type for file option that are bound to a relative-to option.
 *
 * @author jdenise@redhat.com
 */
public class RelativeFile extends File {

    private final String originalPath;

    public RelativeFile(String originalPath, File absolutePath) {
        super(absolutePath.getAbsolutePath());
        this.originalPath = originalPath;
    }

    public String getOriginalPath() {
        return originalPath;
    }
}
