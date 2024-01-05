/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for capability name resolvers.
 */
public class CapabilityNameResolverTestCase {

    private static final String GREATGRANDPARENT = "greatgrandparent";
    private static final String GRANDPARENT = "grandparent";
    private static final String PARENT = "parent";
    private static final String CHILD = "child";

    @Test
    public void test() {
        PathAddress address = PathAddress.pathAddress(PathElement.pathElement("foo", GREATGRANDPARENT), PathElement.pathElement("bar", GRANDPARENT), PathElement.pathElement("baz", PARENT), PathElement.pathElement("qux", CHILD));

        String[] resolved = UnaryCapabilityNameResolver.DEFAULT.apply(address);
        Assert.assertEquals(1, resolved.length);
        Assert.assertSame(CHILD, resolved[0]);

        resolved = UnaryCapabilityNameResolver.PARENT.apply(address);
        Assert.assertEquals(1, resolved.length);
        Assert.assertSame(PARENT, resolved[0]);

        resolved = UnaryCapabilityNameResolver.GRANDPARENT.apply(address);
        Assert.assertEquals(1, resolved.length);
        Assert.assertSame(GRANDPARENT, resolved[0]);

        resolved = UnaryCapabilityNameResolver.GREATGRANDPARENT.apply(address);
        Assert.assertEquals(1, resolved.length);
        Assert.assertSame(GREATGRANDPARENT, resolved[0]);


        resolved = BinaryCapabilityNameResolver.PARENT_CHILD.apply(address);
        Assert.assertEquals(2, resolved.length);
        Assert.assertSame(PARENT, resolved[0]);
        Assert.assertSame(CHILD, resolved[1]);

        resolved = BinaryCapabilityNameResolver.GRANDPARENT_CHILD.apply(address);
        Assert.assertEquals(2, resolved.length);
        Assert.assertSame(GRANDPARENT, resolved[0]);
        Assert.assertSame(CHILD, resolved[1]);

        resolved = BinaryCapabilityNameResolver.GRANDPARENT_PARENT.apply(address);
        Assert.assertEquals(2, resolved.length);
        Assert.assertSame(GRANDPARENT, resolved[0]);
        Assert.assertSame(PARENT, resolved[1]);

        resolved = BinaryCapabilityNameResolver.GREATGRANDPARENT_CHILD.apply(address);
        Assert.assertEquals(2, resolved.length);
        Assert.assertSame(GREATGRANDPARENT, resolved[0]);
        Assert.assertSame(CHILD, resolved[1]);

        resolved = BinaryCapabilityNameResolver.GREATGRANDPARENT_PARENT.apply(address);
        Assert.assertEquals(2, resolved.length);
        Assert.assertSame(GREATGRANDPARENT, resolved[0]);
        Assert.assertSame(PARENT, resolved[1]);

        resolved = BinaryCapabilityNameResolver.GREATGRANDPARENT_GRANDPARENT.apply(address);
        Assert.assertEquals(2, resolved.length);
        Assert.assertSame(GREATGRANDPARENT, resolved[0]);
        Assert.assertSame(GRANDPARENT, resolved[1]);


        resolved = TernaryCapabilityNameResolver.GRANDPARENT_PARENT_CHILD.apply(address);
        Assert.assertEquals(3, resolved.length);
        Assert.assertSame(GRANDPARENT, resolved[0]);
        Assert.assertSame(PARENT, resolved[1]);
        Assert.assertSame(CHILD, resolved[2]);

        resolved = TernaryCapabilityNameResolver.GREATGRANDPARENT_PARENT_CHILD.apply(address);
        Assert.assertEquals(3, resolved.length);
        Assert.assertSame(GREATGRANDPARENT, resolved[0]);
        Assert.assertSame(PARENT, resolved[1]);
        Assert.assertSame(CHILD, resolved[2]);

        resolved = TernaryCapabilityNameResolver.GREATGRANDPARENT_GRANDPARENT_CHILD.apply(address);
        Assert.assertEquals(3, resolved.length);
        Assert.assertSame(GREATGRANDPARENT, resolved[0]);
        Assert.assertSame(GRANDPARENT, resolved[1]);
        Assert.assertSame(CHILD, resolved[2]);

        resolved = TernaryCapabilityNameResolver.GREATGRANDPARENT_GRANDPARENT_PARENT.apply(address);
        Assert.assertEquals(3, resolved.length);
        Assert.assertSame(GREATGRANDPARENT, resolved[0]);
        Assert.assertSame(GRANDPARENT, resolved[1]);
        Assert.assertSame(PARENT, resolved[2]);


        resolved = QuaternaryCapabilityNameResolver.GREATGRANDPARENT_GRANDPARENT_PARENT_CHILD.apply(address);
        Assert.assertEquals(4, resolved.length);
        Assert.assertSame(GREATGRANDPARENT, resolved[0]);
        Assert.assertSame(GRANDPARENT, resolved[1]);
        Assert.assertSame(PARENT, resolved[2]);
        Assert.assertSame(CHILD, resolved[3]);
    }
}
