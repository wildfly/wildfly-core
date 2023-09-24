/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.Resource;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests of {@link org.jboss.as.controller.access.ResourceNotAddressableException}.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
public class ResourceNotAddressableExceptionTestCase {

    /**
     * Test that the exception message is what we expect to prevent this exception
     * looking different from a non-authorization triggered exception
     */
    @Test
    public void testFailureDescription() {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement("key", "value"));
        ResourceNotAddressableException rnae = new ResourceNotAddressableException(pa);
        @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
        Resource.NoSuchResourceException nsre = ControllerLogger.ROOT_LOGGER.managementResourceNotFound(pa);
        Assert.assertEquals(nsre.getFailureDescription(), rnae.getFailureDescription());
    }
}
