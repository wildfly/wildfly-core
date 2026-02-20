/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.transformers;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.transform.TransformerRegistry;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilderFactory;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.version.Stability;
import org.jboss.as.version.Version;

/**
 * Represents as an enum the kernel management API versions to which we transform.
 *
 * @author Brian Stansberry
 */
public enum KernelAPIVersion {

    // EAP 6.2.0
    VERSION_1_5(1, 5, 0),
    // EAP 6.3.0
    VERSION_1_6(1, 6, 0),
    // EAP 6.4.0
    VERSION_1_7(1, 7, 0),
    // EAP 6.4.0 CP07
    VERSION_1_8(1, 8, 0),
    //WF 8.0.0.Final
    VERSION_2_0(2, 0, 0),
    //WF 8.1.0.Final
    VERSION_2_1(2, 1, 0),
    //WF 9.0.0 and 9.0.1
    VERSION_3_0(3, 0, 0),
    //WF 10.0.0
    VERSION_4_0(4, 0, 0),
    // EAP 7.0.0
    VERSION_4_1(4, 1, 0),
    // WildFly 10.1.0
    VERSION_4_2(4, 2, 0),
    // WF 11.0.0, EAP 7.1.0
    VERSION_5_0(5, 0, 0),
    // WF 12.0.0
    VERSION_6_0(6, 0, 0),
    // WF 13.0.0
    VERSION_7_0(7, 0, 0),
    // WF 14.0.0
    VERSION_8_0(8, 0, 0),
    // WF 15.0.0
    VERSION_9_0(9, 0, 0),
    // WF 16.0.0-18.0.0
    VERSION_10_0(10, 0, 0),
    // WildFly 19.0.0
    VERSION_12_0(12, 0, 0),
    // WildFly 20.0.0
    VERSION_13_0(13, 0, 0),
    // WildFly 21.0.0
    VERSION_14_0(14, 0, 0),
    // WildFly 22.0.0
    VERSION_15_0(15, 0, 0),
    // WildFly 23.0.0
    VERSION_16_0(16, 0, 0),
    // WildFly 24.0.0
    VERSION_17_0(17, 0, 0),
    // WildFly 25.0.0
    VERSION_18_0(18, 0, 0),
    // WildFLy 26.0.0
    VERSION_19_0(19, 0, 0),
    // WildFLy 27.0.0
    VERSION_20_0(20, 0, 0),
    // WildFLy 28.0.0
    VERSION_21_0(21, 0, 0),
    // WildFLy 29.0.0
    VERSION_22_0(22, 0, 0),
    // WildFly 30.0.0
    VERSION_23_0(23, 0, 0),
    // WildFly 31.0.0
    VERSION_24_0(24, 0, 0),
    // WildFly 32.0.0
    VERSION_25_0(25, 0, 0),
    // WildFly 33.0.0
    VERSION_26_0(26, 0, 0),
    // WildFly 34.0.0
    VERSION_27_0(27, 0, 0),
    // WildFly 35.0.0
    VERSION_28_0(28, 0, 0),
    // WildFly 36.0.0
    VERSION_29_0(29, 0, 0),
    // WildFly 37.0.0
    VERSION_30_0(30, 0, 0),
    // WildFly 38.0.0
    VERSION_31_0(31, 0, 0),
    // WildFly 39.0.0
    VERSION_32_0(32, 0, 0),

    // Latest
    CURRENT(Version.MANAGEMENT_MAJOR_VERSION, Version.MANAGEMENT_MINOR_VERSION, Version.MANAGEMENT_MICRO_VERSION);

    final ModelVersion modelVersion;

    KernelAPIVersion(int major, int minor, int micro) {
        this.modelVersion = ModelVersion.create(major, minor, micro);
    }

    public ModelVersion getModelVersion() {
        return modelVersion;
    }

    static ChainedTransformationDescriptionBuilderFactory createChainedTransformationDescriptionBuilderFactory(TransformerRegistry registry) {
        return new ChainedTransformationDescriptionBuilderFactory() {
            @Override
            public Stability getStability() {
                return registry.getStability();
            }

            @Override
            public ModelVersion getCurrentVersion() {
                return CURRENT.getModelVersion();
            }
        };
    }

    static ResourceTransformationDescriptionBuilder createBuilder(ChainedTransformationDescriptionBuilder chainBuilder, KernelAPIVersion from, KernelAPIVersion to) {
        return chainBuilder.createBuilder(from.modelVersion, to.modelVersion);
    }

    static ResourceTransformationDescriptionBuilder createBuilderFromCurrent(ChainedTransformationDescriptionBuilder chainBuilder, KernelAPIVersion to) {
        return chainBuilder.createBuilder(CURRENT.modelVersion, to.modelVersion);
    }

    static ModelVersion[] toModelVersions(KernelAPIVersion... versions) {
        ModelVersion[] result = new ModelVersion[versions.length];
        for (int i = 0; i < versions.length; i++) {
            result[i] = versions[i].modelVersion;
        }
        return result;
    }
}
