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
public class BufferLevel2TypeCompletionTestCase extends AbstractGeneratedAddressCompleterTest {

    @Test
    public void testStringValues() {

        assertAllCandidates(Arrays.asList("last3", "link3", "other3"));
        assertSelectedCandidates(Arrays.asList("last3", "link3"));
        assertBufferPrefix("./link1=link2/");
        assertContextPrefix("/");
    }

    @Override
    protected int getBufferLevel() {
        return 3;
    }
}
