/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.jbossallxml;

import javax.xml.namespace.QName;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.AttachmentList;

/**
 * @author Stuart Douglas
 */
class JBossAllXMLParserDescription<T> {

    /**
     * The attachment key that the descriptions are registered under
     */
    public static final AttachmentKey<AttachmentList<JBossAllXMLParserDescription<?>>> ATTACHMENT_KEY = AttachmentKey.createList(JBossAllXMLParserDescription.class);

    /**
     * The attachment key that the parsed data is registered under
     */
    private final AttachmentKey<T> attachmentKey;

    /**
     * The parser responsible for parsing the data
     */
    private final JBossAllXMLParser<T> parser;

    /**
     * The namespace that this parser is responsible for handling.
     */
    private final QName rootElement;

    public JBossAllXMLParserDescription(final AttachmentKey<T> attachmentKey, final JBossAllXMLParser<T> parser, final QName rootElement) {
        this.attachmentKey = attachmentKey;
        this.parser = parser;
        this.rootElement = rootElement;
    }

    public AttachmentKey<T> getAttachmentKey() {
        return attachmentKey;
    }

    public JBossAllXMLParser<T> getParser() {
        return parser;
    }

    public QName getRootElement() {
        return rootElement;
    }
}
