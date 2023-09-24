/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.validation;

import java.io.File;

import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchMetadataResolver;
import org.jboss.as.patching.metadata.PatchXml;

/**
 * Artifact representing a xml file.
 *
 * @author Alexey Loubyansky
 * @author Emanuel Muckenhuber
 */
class PatchingXmlArtifact<E extends Patch> extends AbstractArtifact<PatchingFileArtifact.FileArtifactState, PatchingXmlArtifact.XmlArtifactState<E>> {

    PatchingXmlArtifact(PatchingArtifact<XmlArtifactState<E>, ? extends ArtifactState>... artifacts) {
        super(artifacts);
    }

    @Override
    public boolean process(PatchingFileArtifact.FileArtifactState parent, PatchingArtifactProcessor processor) {
        final File xmlFile = parent.getFile();
        final XmlArtifactState<E> state = new XmlArtifactState<E>(xmlFile, this);
        return processor.process(this, state);
    }

    protected E resolveMetaData(PatchMetadataResolver resolver) throws PatchingException {
        throw new IllegalStateException(); // this gets overriden by the actual artifacts used
    }

    static class XmlArtifactState<E extends Patch> implements PatchingArtifact.ArtifactState {

        private final File xmlFile;
        private final PatchingXmlArtifact<E> artifact;
        private E patch;

        XmlArtifactState(File xmlFile, PatchingXmlArtifact<E> artifact) {
            this.xmlFile = xmlFile;
            this.artifact = artifact;
        }

        public E getPatch() {
            return patch;
        }

        @Override
        public boolean isValid(PatchingArtifactValidationContext context) {
            if (patch != null) {
                return true;
            }
            try {
                final PatchMetadataResolver resolver = PatchXml.parse(xmlFile, context.getInstalledIdentity());
                patch = artifact.resolveMetaData(resolver);
                return true;
            } catch (Exception e) {
                context.getErrorHandler().addError(artifact, this);
            }
            return false;
        }

        @Override
        public String toString() {
            return xmlFile.getAbsolutePath();
        }
    }

}
