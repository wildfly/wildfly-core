/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import java.util.List;

import org.jboss.as.controller.SubsystemSchema;
import org.jboss.staxmapper.IntVersion;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test case for {@link VersionedNamespace} factory methods.
 * @author Paul Ferraro
 */
public class VersionedNamespaceTestCase {

    @Test
    public void test() {
        Assert.assertEquals("urn:foo:1", VersionedNamespace.createURN(List.of("foo"), new IntVersion(1)).getUri());
        Assert.assertEquals("urn:foo:bar:1.0.0", VersionedNamespace.createURN(List.of("foo", "bar"), new IntVersion(1), IntVersionSchema.MAJOR_MINOR_MICRO).getUri());

        Assert.assertEquals("urn:foo:bar:2.0", IntVersionSchema.createURN(List.of("foo", "bar"), new IntVersion(2)).getUri());

        Assert.assertEquals("urn:jboss:domain:foo:1.0", SubsystemSchema.createLegacySubsystemURN("foo", new IntVersion(1)).getUri());
        Assert.assertEquals("urn:wildfly:foo:2.0", SubsystemSchema.createSubsystemURN("foo", new IntVersion(2)).getUri());
    }
}
