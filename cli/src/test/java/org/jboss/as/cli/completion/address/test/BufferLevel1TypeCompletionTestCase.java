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
public class BufferLevel1TypeCompletionTestCase extends AbstractGeneratedAddressCompleterTest {

    @Test
    public void testStringValues() {

        assertAllCandidates(Arrays.asList("last1", "link1", "other1"));
        assertSelectedCandidates(Arrays.asList("last1", "link1"));
        assertBufferPrefix("./");
        assertContextPrefix("/");
    }
}
