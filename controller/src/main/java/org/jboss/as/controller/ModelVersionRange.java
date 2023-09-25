/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

/**
 * @author Emanuel Muckenhuber
 */
public interface ModelVersionRange {

    /**
     * Get all version in the range.
     *
     * @return the versions
     */
    ModelVersion[] getVersions();

    public static class Versions {

        private Versions() {
            //
        }

        public static ModelVersionRange range(final ModelVersion... versions) {
            return new ModelVersionRange() {
                @Override
                public ModelVersion[] getVersions() {
                    return versions;
                }
            };
        }

    }

}
