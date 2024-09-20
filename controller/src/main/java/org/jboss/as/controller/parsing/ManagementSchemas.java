/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.parsing;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.Feature;
import org.jboss.as.version.Stability;

/**
 * Factory for a set of schemas for a specific management file.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class ManagementSchemas {

    // TODO Maybe this could be calculated from static method, sometimes tests want to use "latest".
    public static final String CURRENT = "urn:jboss:domain:20.0";

    private enum Version implements Feature {

        VERSION_1_0(1, 0),
        VERSION_1_1(1, 1),
        VERSION_1_2(1, 2),
        VERSION_1_3(1, 3),
        VERSION_1_4(1, 4),
        VERSION_1_5(1, 5),
        VERSION_1_6(1, 6),
        VERSION_1_7(1, 7),
        VERSION_1_8(1, 8),
        VERSION_2_0(2, 0),
        VERSION_2_1(2, 1),
        VERSION_2_2(2, 2),
        VERSION_3_0(3, 0),
        VERSION_4_0(4, 0),
        VERSION_4_1(4, 1),
        VERSION_4_2(4,2),
        VERSION_5(5),
        VERSION_6(6),
        VERSION_7(7),
        VERSION_8(8),
        VERSION_9(9),
        VERSION_10(10),
        VERSION_11(11),
        VERSION_12(12),
        VERSION_13(13),
        VERSION_14(14),
        VERSION_15(15),
        VERSION_16(16),
        VERSION_17(17),
        VERSION_18(18),
        VERSION_19(19),
        VERSION_20(20),
        VERSION_20_COMMUNITY(20, Stability.COMMUNITY),
        ;

        private final int majorVersion;
        private final int minorVersion;
        private final Stability stability;

        Version(final int majorVersion) {
            this(majorVersion, 0);
        }

        Version(final int majorVersion, final Stability stability) {
            this(majorVersion, 0, stability);
        }

        Version(final int majorVersion, final int minorVersion) {
            this(majorVersion, minorVersion, Stability.DEFAULT);
        }

        Version(final int majorVersion, final int minorVersion, final Stability stability) {
            this.majorVersion = majorVersion;
            this.minorVersion = minorVersion;
            this.stability = stability;
        }

        public int getMajorVersion() {
            return majorVersion;
        }

        public int getMinorVersion() {
            return minorVersion;
        }

        @Override
        public Stability getStability() {
            return stability;
        }
    }

    private final Set<ManagementXmlSchema> additionalSchemas;
    private final ManagementXmlSchema currentSchema;

    protected ManagementSchemas(final Stability stability, final ManagementXmlReaderWriter readerWriterDelegate, final String localName) {
        Set<ManagementXmlSchema> allSchemas = new HashSet<>();
        int maxVersion = 0;
        for (Version version : Version.values()) {
            if (version.getMajorVersion() > maxVersion) {
                maxVersion = version.getMajorVersion();
            }
            allSchemas.add(ManagementSchema.create(stability.enables(version.getStability()) ? readerWriterDelegate
                : UnstableManagementReaderWriter.INSTANCE, version.getStability(), version.getMajorVersion(), version.getMinorVersion(), localName));
        }

        Set<ManagementXmlSchema> current = new HashSet<>();
        for (ManagementXmlSchema schema : allSchemas) {
            if (schema.getNamespace().getVersion().major() == maxVersion) {
                current.add(schema);
            }
        }

        this.currentSchema = Feature.map(current).get(stability);
        allSchemas.remove(currentSchema);
        this.additionalSchemas = Collections.unmodifiableSet(allSchemas);
    }

    /**
     * Get the current supported schema.
     *
     * This will have already taken into account the current stability level.
     *
     * @return the current supported schemas across the different stability levels.
     */
    public ManagementXmlSchema getCurrent() {
        return currentSchema;
    }

    /**
     * Get all the schemas in addition to the current schema for
     * this management file.
     *
     * @return all the schemas for this management file.
     */
    public Set<ManagementXmlSchema> getAdditional() {
        return additionalSchemas;
    }

}
