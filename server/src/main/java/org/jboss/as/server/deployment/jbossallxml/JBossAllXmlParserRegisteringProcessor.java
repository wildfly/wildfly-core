/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.server.deployment.jbossallxml;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.xml.namespace.QName;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;

/**
 * DUP that registers a {@link JBossAllXMLParserDescription} with the DU. These should all be registered in the
 * {@link org.jboss.as.server.deployment.Phase#STRUCTURE_REGISTER_JBOSS_ALL_XML_PARSER} phase.
 *
 * @author Stuart Douglas
 */
public class JBossAllXmlParserRegisteringProcessor<T> implements DeploymentUnitProcessor {

    private final List<? extends JBossAllXMLParserDescription<?>> descriptions;

    public JBossAllXmlParserRegisteringProcessor(final QName rootElement, final AttachmentKey<T> attachmentKey, final JBossAllXMLParser<T> parser) {
        descriptions = Collections.singletonList(new JBossAllXMLParserDescription<T>(attachmentKey, parser, rootElement));
    }

    private JBossAllXmlParserRegisteringProcessor(List<? extends JBossAllXMLParserDescription<?>> descriptions) {
        this.descriptions = descriptions;
    }

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        for (JBossAllXMLParserDescription<?> description : descriptions) {
            phaseContext.getDeploymentUnit().addToAttachmentList(JBossAllXMLParserDescription.ATTACHMENT_KEY, description);
        }
    }

    @Override
    public void undeploy(final DeploymentUnit unit) {
        unit.removeAttachment(JBossAllXMLParserDescription.ATTACHMENT_KEY);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for building JBossAllXmlParserRegisteringProcessor that registers multiple parsers at once. Useful
     * for cases when parsers for multiple versions of a particular schema are registered.
     *
     * @author Jozef Hartinger
     *
     */
    public static final class Builder {

        private final List<JBossAllXMLParserDescription<?>> descriptions = new LinkedList<>();

        private Builder() {
        }

        public <T> Builder addParser(final QName rootElement, final AttachmentKey<T> attachmentKey, final JBossAllXMLParser<T> parser) {
            descriptions.add(new JBossAllXMLParserDescription<T>(attachmentKey, parser, rootElement));
            return this;
        }

        public JBossAllXmlParserRegisteringProcessor<Object> build() {
            return new JBossAllXmlParserRegisteringProcessor<>(descriptions);
        }
    }
}
