/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.tests;

import static org.jboss.as.patching.runner.TestUtils.randomString;

import java.io.File;

import org.jboss.as.patching.metadata.Builder;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.jboss.as.patching.metadata.PatchElementBuilder;

/**
 * @author Emanuel Muckenhuber
 */
public class PatchingTestStepBuilder extends AbstractPatchTestBuilder<PatchingTestStepBuilder> implements Builder {

    private String patchId;
    private final PatchBuilder builder;
    private final PatchingTestBuilder testBuilder;
    private final File root;

    public PatchingTestStepBuilder(PatchingTestBuilder testBuilder) {
        this.testBuilder = testBuilder;
        this.builder = PatchBuilder.create();
        this.root = new File(testBuilder.getRoot(), randomString());
        this.root.mkdir();
    }

    @Override
    protected String getPatchId() {
        return patchId;
    }

    protected File getPatchDir() {
        return root;
    }

    public PatchingTestStepBuilder setPatchId(String id) {
        this.patchId = id;
        builder.setPatchId(id);
        return returnThis();
    }

    public PatchingTestStepBuilder upgradeIdentity(String version, String resultingVersion) {
        return upgradeIdentity(AbstractPatchingTest.PRODUCT_NAME, version, resultingVersion);
    }

    public PatchingTestStepBuilder upgradeIdentity(String name, String version, String resultingVersion) {
        builder.upgradeIdentity(name, version, resultingVersion);
        return returnThis();
    }

    public PatchingTestStepBuilder oneOffPatchIdentity(String version) {
        return oneOffPatchIdentity(AbstractPatchingTest.PRODUCT_NAME, version);
    }

    public PatchingTestStepBuilder oneOffPatchIdentity(String name, String version) {
        builder.oneOffPatchIdentity(name, version);
        return returnThis();
    }

    public PatchElementTestStepBuilder upgradeElement(String patchId, String layerName, boolean isAddon) {
        final PatchElementBuilder elementBuilder = builder.upgradeElement(patchId, layerName, isAddon);
        return new PatchElementTestStepBuilder(patchId, elementBuilder, this);
    }

    public PatchElementTestStepBuilder oneOffPatchElement(String patchId, String layerName, boolean isAddon) {
        final PatchElementBuilder elementBuilder = builder.oneOffPatchElement(patchId, layerName, isAddon);
        return new PatchElementTestStepBuilder(patchId, elementBuilder, this);
    }

    @Override
    protected PatchingTestStepBuilder internalAddModification(ContentModification modification) {
        builder.addContentModification(modification);
        return returnThis();
    }

    @Override
    public Patch build() {
        return builder.build();
    }

    protected PatchingTestStepBuilder returnThis() {
        return this;
    }

}
