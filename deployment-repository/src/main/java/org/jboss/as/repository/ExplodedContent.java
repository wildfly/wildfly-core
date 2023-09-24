/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.repository;

import java.io.InputStream;

/**
 * Represent a content in an exploded deployment.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class ExplodedContent {
    private final String relativePath;
    private final InputStream content;

    public ExplodedContent(String relativePath, InputStream content) {
        this.relativePath = relativePath;
        this.content = content;
    }

    public ExplodedContent(String relativePath) {
        this.relativePath = relativePath;
        this.content = InputStream.nullInputStream();
    }

    public String getRelativePath() {
        return relativePath;
    }

    public InputStream getContent() {
        return content;
    }

}
