/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.network;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class NetworkUtilsTestCase {

    @Test
    public void testFormatIPv6Test() {
        checkSameFormat("localhost");
        checkSameFormat("127.0.0.1");
        checkSameFormat("www.jboss.org");
        checkSameFormat("[::1]");
        checkSameFormat("[fe80::200:f8ff:fe21:67cf]");
        checkEqualFormat("[::1]", "::1");
        checkEqualFormat("[fe80::200:f8ff:fe21:67cf]", "fe80::200:f8ff:fe21:67cf");
    }

    @Test
    public void testFormatInetAddress() throws Exception {
        InetAddress inetAddress = InetAddress.getByName("127.0.0.1");
        Assert.assertEquals("127.0.0.1", NetworkUtils.formatAddress(inetAddress));

        inetAddress = InetAddress.getByName("0:0:0:0:0:0:0:1");
        Assert.assertEquals("::1", NetworkUtils.formatAddress(inetAddress));

        inetAddress = InetAddress.getByName("fe80:0:0:0:f24d:a2ff:fe63:5766");
        Assert.assertEquals("fe80::f24d:a2ff:fe63:5766", NetworkUtils.formatAddress(inetAddress));

        inetAddress = InetAddress.getByName("1:0:0:1:0:0:0:1");
        Assert.assertEquals("1:0:0:1::1", NetworkUtils.formatAddress(inetAddress));

        inetAddress = InetAddress.getByName("1:0:0:1:1:0:0:1");
        Assert.assertEquals("1::1:1:0:0:1", NetworkUtils.formatAddress(inetAddress));
    }

    @Test
    public void testFormatIPAddressForURI() throws Exception {
        InetAddress inetAddress = InetAddress.getByName("127.0.0.1");
        Assert.assertEquals("127.0.0.1", NetworkUtils.formatIPAddressForURI(inetAddress));

        inetAddress = InetAddress.getByName("0:0:0:0:0:0:0:1");
        Assert.assertEquals("[::1]", NetworkUtils.formatIPAddressForURI(inetAddress));

        inetAddress = InetAddress.getByName("fe80:0:0:0:f24d:a2ff:fe63:5766");
        Assert.assertEquals("[fe80::f24d:a2ff:fe63:5766]", NetworkUtils.formatIPAddressForURI(inetAddress));

        inetAddress = InetAddress.getByName("1:0:0:1:0:0:0:1");
        Assert.assertEquals("[1:0:0:1::1]", NetworkUtils.formatIPAddressForURI(inetAddress));

        inetAddress = InetAddress.getByName("1:0:0:1:1:0:0:1");
        Assert.assertEquals("[1::1:1:0:0:1]", NetworkUtils.formatIPAddressForURI(inetAddress));
    }

    @Test
    public void testFormatSocketAddress() throws Exception {
        InetAddress inetAddress = InetAddress.getByName("127.0.0.1");
        InetSocketAddress socketAddress = new InetSocketAddress(inetAddress, 8000);
        Assert.assertEquals("127.0.0.1:8000", NetworkUtils.formatAddress(socketAddress));

        inetAddress = InetAddress.getByName("0:0:0:0:0:0:0:1");
        socketAddress = new InetSocketAddress(inetAddress, 8000);
        Assert.assertEquals("[::1]:8000", NetworkUtils.formatAddress(socketAddress));

        inetAddress = InetAddress.getByName("fe80:0:0:0:f24d:a2ff:fe63:5766");
        socketAddress = new InetSocketAddress(inetAddress, 8000);
        Assert.assertEquals("[fe80::f24d:a2ff:fe63:5766]:8000", NetworkUtils.formatAddress(socketAddress));

        inetAddress = InetAddress.getByName("1:0:0:1:0:0:0:1");
        socketAddress = new InetSocketAddress(inetAddress, 8000);
        Assert.assertEquals("[1:0:0:1::1]:8000", NetworkUtils.formatAddress(socketAddress));

        inetAddress = InetAddress.getByName("1:0:0:1:1:0:0:1");
        socketAddress = new InetSocketAddress(inetAddress, 8000);
        Assert.assertEquals("[1::1:1:0:0:1]:8000", NetworkUtils.formatAddress(socketAddress));

        socketAddress = InetSocketAddress.createUnresolved("127.0.0.1", 8000);
        Assert.assertEquals("127.0.0.1:8000", NetworkUtils.formatAddress(socketAddress));

        socketAddress = InetSocketAddress.createUnresolved("fe80:0:0:0:f24d:a2ff:fe63:5766", 8000);
        Assert.assertEquals("fe80:0:0:0:f24d:a2ff:fe63:5766:8000", NetworkUtils.formatAddress(socketAddress));

        socketAddress = InetSocketAddress.createUnresolved("jboss.org", 8000);
        Assert.assertEquals("jboss.org:8000", NetworkUtils.formatAddress(socketAddress));
    }

    private void checkSameFormat(String nochange) {
        checkEqualFormat(nochange, nochange);
    }

    private void checkEqualFormat(String expected, String input) {
        Assert.assertEquals(expected, NetworkUtils.formatPossibleIpv6Address(input));
    }
}
