/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.version;

/**
 * @author Paul Ferraro
 */
public enum FeatureStream {

    STABLE("stable"),
    PREVIEW("preview"),
    EXPERIMENTAL("experimental"),
    ;
    public static final FeatureStream DEFAULT = STABLE;

    private final String value;

    FeatureStream(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    /**
     * Indicates whether this feature stream enables the specified feature stream.
     * @param stream a feature stream
     * @return true, if this feature stream enables the specified feature stream, false otherwise.
     */
    public boolean enables(FeatureStream stream) {
        return stream.ordinal() <= this.ordinal();
    }
}
