/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tests;

import java.io.File;

import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.PatchElementBuilder;

/**
 * @author Emanuel Muckenhuber
 */
public class PatchElementTestStepBuilder extends AbstractPatchTestBuilder<PatchElementTestStepBuilder> {

    private final String patchId;
    private final PatchElementBuilder builder;
    private final PatchingTestStepBuilder parent;

    public PatchElementTestStepBuilder(String patchId, PatchElementBuilder builder, PatchingTestStepBuilder parent) {
        this.patchId = patchId;
        this.builder = builder;
        this.parent = parent;
    }

    @Override
    protected String getPatchId() {
        return patchId;
    }

    @Override
    protected File getPatchDir() {
        return parent.getPatchDir();
    }

    public PatchingTestStepBuilder getParent() {
        return parent;
    }

    @Override
    protected PatchElementTestStepBuilder internalAddModification(ContentModification modification) {
        builder.addContentModification(modification);
        return returnThis();
    }

    @Override
    protected PatchElementTestStepBuilder returnThis() {
        return this;
    }

}
