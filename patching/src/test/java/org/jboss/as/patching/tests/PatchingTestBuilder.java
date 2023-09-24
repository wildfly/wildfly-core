/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tests;

import java.io.File;

/**
 * @author Emanuel Muckenhuber
 */
public class PatchingTestBuilder {

    private final File root;
    public PatchingTestBuilder(File file) {
        this.root = file;
    }

    File getRoot() {
        return root;
    }

    public PatchingTestStepBuilder createStepBuilder() {
        return new PatchingTestStepBuilder(this);
    }

    public File getFile(String... segments) {
        File dir = new File(root, AbstractPatchingTest.JBOSS_INSTALLATION);
        for (String segment : segments) {
            dir = new File(dir, segment);
        }
        return dir;
    }

    public boolean hasFile(String... segments) {
        return getFile(segments).exists();
    }

}
