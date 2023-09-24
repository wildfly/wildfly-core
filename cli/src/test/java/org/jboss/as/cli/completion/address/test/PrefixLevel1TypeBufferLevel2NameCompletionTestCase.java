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
public class PrefixLevel1TypeBufferLevel2NameCompletionTestCase extends AbstractGeneratedAddressCompleterTest {

    @Test
    public void testStringValues() {

        assertAllCandidates(Arrays.asList("last3", "link3", "other3"));
        assertSelectedCandidates(Arrays.asList("last3", "link3"));
        assertBufferPrefix("./link2/");
        assertContextPrefix("/link1");
    }

    @Override
    protected int getBufferLevel() {
        return 2;
    }

    @Override
    protected int getPrefixLevel() {
        return 1;
    }
}
