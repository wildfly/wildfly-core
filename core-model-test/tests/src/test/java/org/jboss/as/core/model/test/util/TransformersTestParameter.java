/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.util;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.core.model.test.ClassloaderParameter;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.version.Version;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TransformersTestParameter extends ClassloaderParameter {

    private final ModelVersion modelVersion;
    private final ModelTestControllerVersion testControllerVersion;

    public TransformersTestParameter(ModelVersion modelVersion, ModelTestControllerVersion testControllerVersion) {
        this.modelVersion = modelVersion;
        this.testControllerVersion = testControllerVersion;
    }

    protected TransformersTestParameter(TransformersTestParameter delegate) {
        this(delegate.getModelVersion(), delegate.getTestControllerVersion());
    }

    public ModelVersion getModelVersion() {
        return modelVersion;
    }

    public ModelTestControllerVersion getTestControllerVersion() {
        return testControllerVersion;
    }

    public static List<TransformersTestParameter> setupVersions(){
        List<TransformersTestParameter> data = new ArrayList<TransformersTestParameter>();
        data.add(new TransformersTestParameter(ModelVersion.create(Version.MANAGEMENT_MAJOR_VERSION, Version.MANAGEMENT_MINOR_VERSION, Version.MANAGEMENT_MICRO_VERSION)
                , ModelTestControllerVersion.MASTER));

        //we only test EAP 7.4 and newer
        data.add(new TransformersTestParameter(ModelVersion.create(16, 0, 0), ModelTestControllerVersion.EAP_7_4_0));
        data.add(new TransformersTestParameter(ModelVersion.create(22, 0, 0), ModelTestControllerVersion.EAP_8_0_0));
        data.add(new TransformersTestParameter(ModelVersion.create(24, 0, 0), ModelTestControllerVersion.WILDFLY_31_0_0));
        return data;
    }

    @Override
    public String toString() {
        return "TransformersTestParameters={modelVersion=" + modelVersion + "; testControllerVersion=" + testControllerVersion + "}";
    }


}
