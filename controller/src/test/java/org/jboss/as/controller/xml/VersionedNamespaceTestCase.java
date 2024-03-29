/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.version.Stability;
import org.jboss.staxmapper.IntVersion;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test case for {@link VersionedNamespace} factory methods.
 * @author Paul Ferraro
 */
public class VersionedNamespaceTestCase {

    enum Schema implements VersionedSchema<IntVersion, Schema> {
        VERSION_1_DEFAULT(1, Stability.DEFAULT),
        VERSION_1_COMMUNITY(1, Stability.COMMUNITY),
        VERSION_1_PREVIEW(1, Stability.PREVIEW),
        VERSION_1_EXPERIMENTAL(1, Stability.EXPERIMENTAL),

        VERSION_2_EXPERIMENTAL(2, Stability.EXPERIMENTAL),
        VERSION_2_PREVIEW(2, Stability.PREVIEW),
        VERSION_2_COMMUNITY(2, Stability.COMMUNITY),
        VERSION_2_DEFAULT(2, Stability.DEFAULT),
        ;

        private final VersionedNamespace<IntVersion, Schema> namespace;

        Schema(int version, Stability stability) {
            this.namespace = VersionedNamespace.createURN(List.of("foo", "bar"), stability, new IntVersion(version));
        }

        @Override
        public String getLocalName() {
            return "";
        }

        @Override
        public VersionedNamespace<IntVersion, Schema> getNamespace() {
            return this.namespace;
        }
    }

    @Test
    public void uri() {
        Assert.assertEquals("urn:foo:bar:1", Schema.VERSION_1_DEFAULT.getNamespace().getUri());
        Assert.assertEquals("urn:foo:bar:community:1", Schema.VERSION_1_COMMUNITY.getNamespace().getUri());
        Assert.assertEquals("urn:foo:bar:preview:1", Schema.VERSION_1_PREVIEW.getNamespace().getUri());
        Assert.assertEquals("urn:foo:bar:experimental:1", Schema.VERSION_1_EXPERIMENTAL.getNamespace().getUri());

        Assert.assertEquals("urn:foo:bar:2", Schema.VERSION_2_DEFAULT.getNamespace().getUri());
        Assert.assertEquals("urn:foo:bar:community:2", Schema.VERSION_2_COMMUNITY.getNamespace().getUri());
        Assert.assertEquals("urn:foo:bar:preview:2", Schema.VERSION_2_PREVIEW.getNamespace().getUri());
        Assert.assertEquals("urn:foo:bar:experimental:2", Schema.VERSION_2_EXPERIMENTAL.getNamespace().getUri());

        Assert.assertEquals("urn:jboss:domain:foo:1.0", SubsystemSchema.createLegacySubsystemURN("foo", new IntVersion(1)).getUri());
        Assert.assertEquals("urn:jboss:domain:foo:1.0", SubsystemSchema.createLegacySubsystemURN("foo", Stability.DEFAULT, new IntVersion(1)).getUri());
        Assert.assertEquals("urn:jboss:domain:foo:experimental:1.0", SubsystemSchema.createLegacySubsystemURN("foo", Stability.EXPERIMENTAL, new IntVersion(1)).getUri());

        Assert.assertEquals("urn:wildfly:foo:2.0", SubsystemSchema.createSubsystemURN("foo", new IntVersion(2)).getUri());
        Assert.assertEquals("urn:wildfly:foo:2.0", SubsystemSchema.createSubsystemURN("foo", Stability.DEFAULT, new IntVersion(2)).getUri());
        Assert.assertEquals("urn:wildfly:foo:preview:2.0", SubsystemSchema.createSubsystemURN("foo", Stability.PREVIEW, new IntVersion(2)).getUri());
    }

    @Test
    public void stability() {
        Assert.assertSame(Schema.VERSION_1_COMMUNITY.getStability(), Stability.COMMUNITY);
        Assert.assertSame(Schema.VERSION_1_COMMUNITY.getNamespace().getStability(), Stability.COMMUNITY);
    }

    @Test
    public void since() {
        since(Schema.VERSION_1_DEFAULT, EnumSet.allOf(Schema.class));
        since(Schema.VERSION_1_COMMUNITY, EnumSet.complementOf(EnumSet.of(Schema.VERSION_1_DEFAULT, Schema.VERSION_2_DEFAULT)));
        since(Schema.VERSION_1_PREVIEW, EnumSet.of(Schema.VERSION_1_EXPERIMENTAL, Schema.VERSION_2_EXPERIMENTAL, Schema.VERSION_1_PREVIEW, Schema.VERSION_2_PREVIEW));
        since(Schema.VERSION_1_EXPERIMENTAL, EnumSet.of(Schema.VERSION_1_EXPERIMENTAL, Schema.VERSION_2_EXPERIMENTAL));
        since(Schema.VERSION_2_EXPERIMENTAL, EnumSet.of(Schema.VERSION_2_EXPERIMENTAL));
        since(Schema.VERSION_2_PREVIEW, EnumSet.of(Schema.VERSION_2_EXPERIMENTAL, Schema.VERSION_2_PREVIEW));
        since(Schema.VERSION_2_PREVIEW, EnumSet.of(Schema.VERSION_2_EXPERIMENTAL, Schema.VERSION_2_PREVIEW));
        since(Schema.VERSION_2_COMMUNITY, EnumSet.of(Schema.VERSION_2_EXPERIMENTAL, Schema.VERSION_2_PREVIEW, Schema.VERSION_2_COMMUNITY));
        since(Schema.VERSION_2_DEFAULT, EnumSet.of(Schema.VERSION_2_EXPERIMENTAL, Schema.VERSION_2_PREVIEW, Schema.VERSION_2_COMMUNITY, Schema.VERSION_2_DEFAULT));
    }

    private static void since(Schema testSchema, Set<Schema> since) {
        for (Schema schema : EnumSet.allOf(Schema.class)) {
            Assert.assertEquals(schema.name(), since.contains(schema), schema.since(testSchema));
        }
    }
}
