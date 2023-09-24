/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation.impl;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author jdenise@redhat.com
 */
public class DefaultOperationRequestAddressTestCase {
    @Test
    public void test() {
        DefaultOperationRequestAddress expected = new DefaultOperationRequestAddress();
        expected.toNode("org", "foo");
        expected.toNode("my", "node");
        DefaultOperationRequestAddress addr = new DefaultOperationRequestAddress();
        addr.toNode("org", "foo");
        DefaultOperationRequestAddress addr2 = new DefaultOperationRequestAddress();
        addr2.toNode("my", "node");
        addr.appendPath(addr2);
        Assert.assertEquals(expected, addr);
    }
}
