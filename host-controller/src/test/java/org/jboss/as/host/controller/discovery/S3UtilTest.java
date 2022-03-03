/*
 * Copyright (C) 2014 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
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
