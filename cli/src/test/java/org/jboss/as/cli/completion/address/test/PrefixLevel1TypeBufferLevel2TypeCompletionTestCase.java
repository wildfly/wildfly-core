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
public class PrefixLevel1TypeBufferLevel2TypeCompletionTestCase extends AbstractGeneratedAddressCompleterTest {

    @Test
    public void testStringValues() {

        assertAllCandidates(Arrays.asList("last4", "link4", "other4"));
        assertSelectedCandidates(Arrays.asList("last4", "link4"));
        assertBufferPrefix("./link2/link3=");
        assertContextPrefix("/link1");
    }

    @Override
    protected int getBufferLevel() {
        return 3;
    }

    @Override
    protected int getPrefixLevel() {
        return 1;
    }
}
