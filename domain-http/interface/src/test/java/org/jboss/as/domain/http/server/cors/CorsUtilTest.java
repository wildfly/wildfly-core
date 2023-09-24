/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.http.server.cors;

import static io.undertow.util.Headers.HOST;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.jboss.as.domain.http.server.cors.CorsHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static org.jboss.as.domain.http.server.cors.CorsHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.jboss.as.domain.http.server.cors.CorsHeaders.ORIGIN;
import static org.hamcrest.MatcherAssert.assertThat;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;

/**
 * Testing CORS utility class.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class CorsUtilTest {

    public CorsUtilTest() {
    }

    /**
     * Test of isCoreRequest method, of class CorsUtil.
     */
    @Test
    public void testIsCoreRequest() {
        HeaderMap headers = new HeaderMap();
        assertThat(CorsUtil.isCoreRequest(headers), is(false));
        headers = new HeaderMap();
        headers.add(ORIGIN, "");
        assertThat(CorsUtil.isCoreRequest(headers), is(true));
        headers = new HeaderMap();
        headers.add(ACCESS_CONTROL_REQUEST_HEADERS, "");
        assertThat(CorsUtil.isCoreRequest(headers), is(true));
        headers = new HeaderMap();
        headers.add(ACCESS_CONTROL_REQUEST_METHOD, "");
        assertThat(CorsUtil.isCoreRequest(headers), is(true));
    }

    /**
     * Test of matchOrigin method, of class CorsUtil.
     */
    @Test
    public void testMatchOrigin() throws Exception {
        HeaderMap headerMap = new HeaderMap();
        headerMap.add(HOST, "localhost:80");
        headerMap.add(ORIGIN, "http://localhost");
        HttpServerExchange exchange = new HttpServerExchange(null, headerMap, new HeaderMap(), 10);
        exchange.setRequestScheme("http");
        Collection<String> allowedOrigins = null;
        assertThat(CorsUtil.matchOrigin(exchange, allowedOrigins), is("http://localhost"));
        allowedOrigins = Collections.singletonList("http://www.example.com:9990");
        //Default origin
        assertThat(CorsUtil.matchOrigin(exchange, allowedOrigins), is("http://localhost"));
        headerMap.clear();
        headerMap.add(HOST, "localhost:80");
        headerMap.add(ORIGIN, "http://www.example.com:9990");
        assertThat(CorsUtil.matchOrigin(exchange, allowedOrigins), is("http://www.example.com:9990"));
        headerMap.clear();
        headerMap.add(HOST, "localhost:80");
        headerMap.add(ORIGIN, "http://www.example.com");
        assertThat(CorsUtil.matchOrigin(exchange, allowedOrigins), is(nullValue()));
        headerMap.addAll(ORIGIN, Arrays.asList("http://localhost:8080", "http://www.example.com:9990", "http://localhost"));
        allowedOrigins = Arrays.asList("http://localhost", "http://www.example.com:9990");
        assertThat(CorsUtil.matchOrigin(exchange, allowedOrigins), is("http://localhost"));
    }

    /**
     * Test of sanitizeDefaultPort method, of class CorsUtil.
     */
    @Test
    public void testSanitizeDefaultPort() {
        String url = "http://127.0.0.1:80";
        assertThat(CorsUtil.sanitizeDefaultPort(url), is("http://127.0.0.1"));
        url = "http://127.0.0.1";
        assertThat(CorsUtil.sanitizeDefaultPort(url), is("http://127.0.0.1"));
        url = "http://127.0.0.1:443";
        assertThat(CorsUtil.sanitizeDefaultPort(url), is("http://127.0.0.1:443"));
        url = "http://127.0.0.1:8080";
        assertThat(CorsUtil.sanitizeDefaultPort(url), is("http://127.0.0.1:8080"));
        url = "https://127.0.0.1:80";
        assertThat(CorsUtil.sanitizeDefaultPort(url), is("https://127.0.0.1:80"));
        url = "https://127.0.0.1:443";
        assertThat(CorsUtil.sanitizeDefaultPort(url), is("https://127.0.0.1"));
        url = "https://127.0.0.1";
        assertThat(CorsUtil.sanitizeDefaultPort(url), is("https://127.0.0.1"));
        url = "http://[::FFFF:129.144.52.38]:8080";
        assertThat(CorsUtil.sanitizeDefaultPort(url), is("http://[::FFFF:129.144.52.38]:8080"));
        url = "http://[::FFFF:129.144.52.38]:80";
        assertThat(CorsUtil.sanitizeDefaultPort(url), is("http://[::FFFF:129.144.52.38]"));
    }

    /**
     * Test of defaultOrigin method, of class CorsUtil.
     */
    @Test
    public void testDefaultOrigin() {
        HeaderMap headerMap = new HeaderMap();
        headerMap.add(HOST, "localhost:80");
        HttpServerExchange exchange = new HttpServerExchange(null, headerMap, new HeaderMap(), 10);
        exchange.setRequestScheme("http");
        assertThat(CorsUtil.defaultOrigin(exchange), is("http://localhost"));
        headerMap.clear();
        headerMap.add(HOST, "www.example.com:8080");
        assertThat(CorsUtil.defaultOrigin(exchange), is("http://www.example.com:8080"));
        headerMap.clear();
        headerMap.add(HOST, "www.example.com:443");
        exchange.setRequestScheme("https");
        assertThat(CorsUtil.defaultOrigin(exchange), is("https://www.example.com"));
        headerMap.clear();
        exchange.setRequestScheme("http");
        headerMap.add(HOST, "[::1]:80");
        assertThat(CorsUtil.defaultOrigin(exchange), is("http://[::1]"));
    }
}
