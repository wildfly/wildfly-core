/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.completion.address.test;

import java.util.Arrays;

import org.junit.Test;


/**
 *
 * @author Alexey Loubyansky
 */
public class BufferLevel2NameCompletionTestCase extends AbstractGeneratedAddressCompleterTest {

    @Test
    public void testStringValues() {

        assertAllCandidates(Arrays.asList("last4", "link4", "other4"));
        assertSelectedCandidates(Arrays.asList("last4", "link4"));
        assertBufferPrefix("./link1=link2/link3=");
        assertContextPrefix("/");
    }

    @Override
    protected int getBufferLevel() {
        return 4;
    }
}
