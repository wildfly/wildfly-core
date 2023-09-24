/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.http.server.cors;

import io.undertow.util.HttpString;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class CorsHeaders {

    public static final String ORIGIN_STRING = "Origin";
    public static final String ACCESS_CONTROL_REQUEST_METHOD_STRING = "Access-Control-Request-Method";
    public static final String ACCESS_CONTROL_REQUEST_HEADERS_STRING = "Access-Control-Request-Headers";

    public static final String ACCESS_CONTROL_ALLOW_ORIGIN_STRING = "Access-Control-Allow-Origin";
    public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS_STRING = "Access-Control-Allow-Credentials";
    public static final String ACCESS_CONTROL_EXPOSE_HEADERS_STRING = "Access-Control-Expose-Headers";
    public static final String ACCESS_CONTROL_MAX_AGE_STRING = "Access-Control-Max-Age";
    public static final String ACCESS_CONTROL_ALLOW_METHODS_STRING = "Access-Control-Allow-Methods";
    public static final String ACCESS_CONTROL_ALLOW_HEADERS_STRING = "Access-Control-Allow-Headers";

    public static final HttpString ORIGIN = new HttpString("Origin");
    public static final HttpString ACCESS_CONTROL_REQUEST_METHOD = new HttpString("Access-Control-Request-Method");
    public static final HttpString ACCESS_CONTROL_REQUEST_HEADERS = new HttpString("Access-Control-Request-Headers");

    public static final HttpString ACCESS_CONTROL_ALLOW_ORIGIN = new HttpString("Access-Control-Allow-Origin");
    public static final HttpString ACCESS_CONTROL_ALLOW_CREDENTIALS = new HttpString("Access-Control-Allow-Credentials");
    public static final HttpString ACCESS_CONTROL_EXPOSE_HEADERS = new HttpString("Access-Control-Expose-Headers");
    public static final HttpString ACCESS_CONTROL_MAX_AGE = new HttpString("Access-Control-Max-Age");
    public static final HttpString ACCESS_CONTROL_ALLOW_METHODS = new HttpString("Access-Control-Allow-Methods");
    public static final HttpString ACCESS_CONTROL_ALLOW_HEADERS = new HttpString("Access-Control-Allow-Headers");
}
