/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller;

import static org.hamcrest.CoreMatchers.is;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Base64;

import org.hamcrest.MatcherAssert;
import org.junit.Test;

/**
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2013 Red Hat, inc.
 */
public class ServerInventoryImplTestCase {
    @Test
    public void testEncodingAndDecoding() throws UnsupportedEncodingException {
        byte[] array = new byte[]{0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x20, 0x00, 0x57, 0x6f, 0x72, 0x6c, 0x64};
        byte[] expected = new byte[]{0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x20, 0x00, 0x57, 0x6f, 0x72, 0x6c, 0x64};
        MatcherAssert.assertThat(Arrays.equals(Base64.getDecoder().decode(Base64.getEncoder().encode(array)), expected), is(true));
    }
}
