/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.dmr.ModelNode;
import org.jboss.vfs.VirtualFile;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class DeploymentAddHandlerTestCase {

    @Test (expected = OperationFailedException.class)
    public void testTooMuchContent() throws OperationFailedException {
        final DeploymentAddHandler handler = DeploymentAddHandler.create(contentRepository);
        final ModelNode operation = new ModelNode();
        //operation.get("address").setEmptyList().get(0).get("deployment").set("test.war");
        operation.get("address").get(0).set("deployment", "test.war");
        operation.get("content").get(0).get("archive").set(true);
        operation.get("content").get(0).get("path").set("test.war");
        operation.get("content").add("muck");
        final ModelNode model = operation.clone();
        model.get("persistent").set(true);
        model.remove("address");
        final OperationContext context = Mockito.mock(OperationContext.class);
        Mockito.when(context.resolveExpressions(Mockito.<ModelNode>anyObject())).thenReturn(model);
        handler.execute(context, operation);
    }

    @Test
    public void testValidator() throws OperationFailedException {
        final DeploymentAddHandler handler = DeploymentAddHandler.create(contentRepository);
        final ModelNode operation = new ModelNode();
        operation.get("content").get(0).get("archive").set("wrong");
        final ModelNode model = operation.clone();
        model.get("persistent").set(true);
        final OperationContext context = Mockito.mock(OperationContext.class);
        Mockito.when(context.resolveExpressions(Mockito.<ModelNode>anyObject())).thenReturn(model);
        try {
            handler.execute(context, operation);
        } catch (OperationFailedException e) {
            // TODO: check exception
        }
    }

    private ContentRepository contentRepository = new ContentRepository() {

        @Override
        public void removeContent(ContentReference reference) {
        }

        @Override
        public boolean syncContent(ContentReference reference) {
            return hasContent(reference.getHash());
        }

        @Override
        public boolean hasContent(byte[] hash) {
            return false;
        }

        @Override
        public VirtualFile getContent(byte[] hash) {
            return null;
        }

        @Override
        public byte[] addContent(InputStream stream) throws IOException {
            return null;
        }

        @Override
        public void addContentReference(ContentReference reference) {
        }

        @Override
        public Map<String, Set<String>> cleanObsoleteContent() {
            return null;
        }
    };
}
