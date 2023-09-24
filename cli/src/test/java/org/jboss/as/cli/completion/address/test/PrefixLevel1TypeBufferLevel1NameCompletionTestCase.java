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
public class PrefixLevel1TypeBufferLevel1NameCompletionTestCase extends AbstractGeneratedAddressCompleterTest {

    @Test
    public void testStringValues() {

        assertAllCandidates(Arrays.asList("last2", "link2", "other2"));
        assertSelectedCandidates(Arrays.asList("last2", "link2"));
        assertBufferPrefix("./");
        assertContextPrefix("/link1");
    }

    @Override
    protected int getPrefixLevel() {
        return 1;
    }
}
