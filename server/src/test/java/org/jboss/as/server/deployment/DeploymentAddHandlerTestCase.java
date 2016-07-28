/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2011, Red Hat, Inc., and individual contributors
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
        final DeploymentAddHandler handler = DeploymentAddHandler.create(contentRepository, null);
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
        final DeploymentAddHandler handler = DeploymentAddHandler.create(contentRepository, null);
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
