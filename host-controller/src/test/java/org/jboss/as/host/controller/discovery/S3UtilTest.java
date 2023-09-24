/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.discovery;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.jboss.as.remoting.Protocol.REMOTE;
import static org.jboss.as.remoting.Protocol.REMOTE_HTTP;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class S3UtilTest {

    public S3UtilTest() {
    }

    /**
     * Test of domainControllerDataToByteBuffer method, of class S3Util.
     */
    @Test
    public void testDomainControllerDataToByteBuffer() throws Exception {
        List<DomainControllerData> data = Arrays.asList(new DomainControllerData[]{new DomainControllerData(REMOTE.toString(), "native.example.com", 9999), new DomainControllerData(REMOTE_HTTP.toString(), "http.example.com", 9990)});
        byte[] bytes = S3Util.domainControllerDataToByteBuffer(data);
        assertThat(bytes, is(notNullValue()));
        assertThat(bytes.length, is(78));
        List<DomainControllerData> result = S3Util.domainControllerDataFromByteBuffer(bytes);
        assertThat(result, is(notNullValue()));
        assertThat(result.size(), is(2));
    }

    /**
     * Test of writeString method, of class S3Util.
     */
    @Test
    public void testWriteString() throws Exception {
        String s = "Hello World";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        S3Util.writeString(s, new DataOutputStream(buffer));
        String result = S3Util.readString(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray())));
        assertThat(result, is(s));
    }

}
