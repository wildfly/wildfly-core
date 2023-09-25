/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.patching.runner;

import java.io.File;
import java.util.Objects;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class FailedFileRenaming {

    private final String sourceFile;
    private final String targetFile;
    private final String patchId;

    public FailedFileRenaming(final File sourceFile, final File targetFile, final String applyPatchId) {
        this.sourceFile = sourceFile.getAbsolutePath();
        this.targetFile = targetFile.getAbsolutePath();
        this.patchId = applyPatchId;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public String getTargetFile() {
        return targetFile;
    }

    public String getPatchId() {
        return patchId;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Objects.hashCode(this.sourceFile);
        hash = 29 * hash + Objects.hashCode(this.targetFile);
        hash = 29 * hash + Objects.hashCode(this.patchId);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FailedFileRenaming other = (FailedFileRenaming) obj;
        if (!Objects.equals(this.sourceFile, other.sourceFile)) {
            return false;
        }
        if (!Objects.equals(this.targetFile, other.targetFile)) {
            return false;
        }
        if (!Objects.equals(this.patchId, other.patchId)) {
            return false;
        }
        return true;
    }

}
