/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static java.lang.Integer.signum;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MICRO_VERSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_MINOR_VERSION;

import org.jboss.as.version.Version;
import org.jboss.dmr.ModelNode;
import org.wildfly.common.Assert;

/**
 * @author Emanuel Muckenhuber
 */
public final class ModelVersion implements ModelVersionRange, Comparable<ModelVersion> {

    public static final ModelVersion CURRENT = create(Version.MANAGEMENT_MAJOR_VERSION,
            Version.MANAGEMENT_MINOR_VERSION, Version.MANAGEMENT_MICRO_VERSION);

    private final int major;
    private final int minor;
    private final int micro;

    private ModelVersion(int major, int minor, int micro) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getMicro() {
        return micro;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof ModelVersion && equals((ModelVersion) o);
    }

    private boolean equals(ModelVersion other) {
        return major == other.major && minor == other.minor && micro == other.micro;
    }

    @Override
    public int hashCode() {
        int result = major;
        result = 31 * result + minor;
        result = 31 * result + micro;
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(major).append(".").append(minor).append(".").append(micro);
        return builder.toString();
    }

    public ModelNode toModelNode() {
        final ModelNode node = new ModelNode();
        addToExistingModel(node);
        return node;
    }

    public void addToExistingModel(final ModelNode node) {
        node.get(MANAGEMENT_MAJOR_VERSION).set(major);
        node.get(MANAGEMENT_MINOR_VERSION).set(minor);
        node.get(MANAGEMENT_MICRO_VERSION).set(micro);
    }

    @Override
    public ModelVersion[] getVersions() {
        return new ModelVersion[] { this };
    }

    public static ModelVersion create(final int major) {
        return create(major, 0, 0);
    }

    public static ModelVersion create(final int major, final int minor) {
        return create(major, minor, 0);
    }

    public static ModelVersion create(final int major, final int minor, final int micro) {
        Assert.checkMinimumParameter("major", 0, major);
        Assert.checkMinimumParameter("minor", 0, minor);
        Assert.checkMinimumParameter("micro", 0, micro);
        return new ModelVersion(major, minor, micro);
    }

    public static ModelVersion fromString(final String s) {
        return convert(s);
    }

    static ModelVersion convert(final String version) {
        final String[] s = version.split("\\.");
        final int length = s.length;
        if(length > 3) {
            throw new IllegalStateException();
        }
        int major = Integer.parseInt(s[0]);
        int minor = length > 1 ? Integer.parseInt(s[1]) : 0;
        int micro = length == 3 ? Integer.parseInt(s[2]) : 0;
        return ModelVersion.create(major, minor, micro);
    }

    /**
     * Compares two model versions
     *
     * @param versionA a model version
     * @param versionB a model version
     *
     * @return <ul>
     *           <li>{@code 1} if {@code versionB > versionA}<li>
     *           <li>{@code -1} if {@code versionB < versionA}<li>
     *           <li>{@code 0} if {@code versionB == versionA}<li>
     *         </ul>
     */
    public static int compare(ModelVersion versionA, ModelVersion versionB) {
        return versionA.compareTo(versionB);
    }

    public int compareTo(final ModelVersion o) {
        int res = signum(o.major - major);
        if (res == 0) res = signum(o.minor - minor);
        if (res == 0) res = signum(o.micro - micro);
        return res;
    }
}
