/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.EnumSet;
import java.util.Objects;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.version.Stability;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for {@link ResourceRegistration} factory methods.
 */
public class ResourceRegistrationTestCase {

    @Test
    public void test() {
        ResourceRegistration root = ResourceRegistration.root();
        Assert.assertNull(root.getPathElement());
        Assert.assertSame(Stability.DEFAULT, root.getStability());
        Assert.assertSame(root, ResourceRegistration.root());
        Assert.assertSame(root, ResourceRegistration.of(root.getPathElement()));
        Assert.assertSame(root, ResourceRegistration.of(root.getPathElement(), Stability.DEFAULT));
        verify(root);

        ResourceRegistration subsystem = ResourceRegistration.of(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, "foo"));
        Assert.assertNotEquals(subsystem, root);
        Assert.assertNotEquals(subsystem, ResourceRegistration.of(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, "bar")));
        verify(subsystem);

        ResourceRegistration unstableSubsystem = ResourceRegistration.of(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, "bar"), Stability.EXPERIMENTAL);
        Assert.assertNotEquals(unstableSubsystem, root);
        Assert.assertNotEquals(unstableSubsystem, subsystem);
        Assert.assertNotEquals(unstableSubsystem, ResourceRegistration.of(unstableSubsystem.getPathElement()));
        verify(unstableSubsystem);
    }

    private static void verify(ResourceRegistration subject) {
        Assert.assertEquals(Objects.hashCode(subject.getPathElement()), subject.hashCode());
        Assert.assertEquals(subject, ResourceRegistration.of(subject.getPathElement(), subject.getStability()));
        for (Stability stability : EnumSet.complementOf(EnumSet.of(subject.getStability()))) {
            Assert.assertNotEquals(subject, ResourceRegistration.of(subject.getPathElement(), stability));
        }
    }
}
