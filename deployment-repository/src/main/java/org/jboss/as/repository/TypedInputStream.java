/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.repository;

import java.io.InputStream;

/**
 * InputStream with defined content-type.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public abstract class TypedInputStream extends InputStream {

    public abstract String getContentType();

}
