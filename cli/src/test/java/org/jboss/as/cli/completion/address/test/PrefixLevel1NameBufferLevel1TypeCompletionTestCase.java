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
public class PrefixLevel1NameBufferLevel1TypeCompletionTestCase extends AbstractGeneratedAddressCompleterTest {

    @Test
    public void testStringValues() {

        assertAllCandidates(Arrays.asList("last3", "link3", "other3"));
        assertSelectedCandidates(Arrays.asList("last3", "link3"));
        assertBufferPrefix("./");
        assertContextPrefix("/link1=link2");
    }

    @Override
    protected int getPrefixLevel() {
        return 2;
    }
}
