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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.logging.DeploymentRepositoryLogger;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.vfs.VirtualFile;
import org.junit.Test;
import org.mockito.Mockito;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELATIVE_TO;
import static org.mockito.Matchers.eq;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class DeploymentAddHandlerTestCase {
    /**
        {
            "operation" => "add",
            "address" => [("deployment" => "test.war")],
            "content" => [{
                "archive" => true,
                "path" => "${jboss.home}/content/welcome.jar"
            }],
            "runtime-name" => "test-run.war",
            "enabled" => true
        }
     * @throws OperationFailedException
     */
    @Test
    public void testContent() throws OperationFailedException, URISyntaxException {
        final DeploymentAddHandler handler = DeploymentAddHandler.create(contentRepository, null);
        final OperationContext context = Mockito.mock(OperationContext.class);
        final Resource resource = Mockito.mock(Resource.class);
        Mockito.when(resource.getModel()).thenReturn(new ModelNode());
        Mockito.when(context.getResult()).thenReturn(new ModelNode());
        Mockito.when(context.createResource(PathAddress.EMPTY_ADDRESS)).thenReturn(resource);
        Mockito.when(context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS)).thenReturn(resource);
        Mockito.when(context.getProcessType()).thenReturn(ProcessType.STANDALONE_SERVER);
        Mockito.when(context.getRunningMode()).thenReturn(RunningMode.NORMAL);
        Mockito.when(context.isNormalServer()).thenReturn(true);
        final ServiceRegistry registry = Mockito.mock(ServiceRegistry.class);
        final ServiceController service = Mockito.mock(ServiceController.class);
        final PathManager pathManager = Mockito.mock(PathManager.class);
        File war = new File(DeploymentAddHandler.class.getClassLoader().getResource("test.war").toURI());
        ModelNode urlNode = new ModelNode(war.toURI().toString());
        Mockito.when(context.resolveExpressions(eq(urlNode))).thenReturn(urlNode);
        ModelNode enabledModelNode = new ModelNode(true);
        Mockito.when(context.resolveExpressions(eq(enabledModelNode))).thenReturn(enabledModelNode);
        Mockito.when(pathManager.resolveRelativePathEntry("test.war", "wildlfy")).thenReturn(war.getAbsolutePath());
        Mockito.when(service.getValue()).thenReturn(pathManager);
        Mockito.when(context.getServiceRegistry(true)).thenReturn(registry);
        Mockito.when(registry.getService(PathManagerService.SERVICE_NAME)).thenReturn(service);
        final ModelNode operation = new ModelNode();
        //operation.get("address").setEmptyList().get(0).get("deployment").set("test.war");
        operation.get("address").get(0).setExpression("deployment", "test.war");
        operation.get("content").get(0).get("archive").set(true);
        operation.get("content").get(0).get(DeploymentAttributes.CONTENT_PATH.getName()).set("test.war");
        operation.get("content").get(0).get(RELATIVE_TO).set("wildlfy");
        operation.get(ENABLED).set(true);
        handler.execute(context, operation);
        Mockito.verify(context).addStep(Mockito.any(OperationStepHandler.class), Mockito.eq(OperationContext.Stage.MODEL));
        Mockito.verify(context).completeStep(Mockito.any(OperationContext.ResultHandler.class));

    }

    @Test (expected = OperationFailedException.class)
    public void testTooMuchContent() throws OperationFailedException {
        final DeploymentAddHandler handler = DeploymentAddHandler.create(contentRepository, null);
        final OperationContext context = Mockito.mock(OperationContext.class);
        Mockito.when(context.createResource(PathAddress.EMPTY_ADDRESS)).thenReturn(Resource.Factory.create());
        final ModelNode operation = new ModelNode();
        //operation.get("address").setEmptyList().get(0).get("deployment").set("test.war");
        operation.get("address").get(0).setExpression("deployment", "test.war");
        operation.get("content").get(0).get("archive").set(true);
        operation.get("content").get(0).get("path").set("test.war");
        operation.get("content").add("muck");
        handler.execute(context, operation);
    }

    @Test
    public void testValidator() throws OperationFailedException {
        final DeploymentAddHandler handler = DeploymentAddHandler.create(contentRepository, null);
        final OperationContext context = Mockito.mock(OperationContext.class);
        Mockito.when(context.createResource(PathAddress.EMPTY_ADDRESS)).thenReturn(Resource.Factory.create());
        final ModelNode operation = new ModelNode();
        operation.get("content").get(0).get("archive").set("wrong");
        try {
            handler.execute(context, operation);
        } catch (OperationFailedException e) {
            // TODO: check exception
        }
    }

    private ContentRepository contentRepository = new ContentRepository() {

        @Override
        public void removeContent(byte[] hash, Object reference) {
        }

        @Override
        public boolean syncContent(byte[] hash) {
            return hasContent(hash);
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
            try {
                MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
                ByteArrayOutputStream fos = new ByteArrayOutputStream();
                try {
                    DigestOutputStream dos = new DigestOutputStream(fos, messageDigest);
                    BufferedInputStream bis = new BufferedInputStream(stream);
                    byte[] bytes = new byte[8192];
                    int read;
                    while ((read = bis.read(bytes)) > -1) {
                        dos.write(bytes, 0, read);
                    }
                    fos.flush();
                    fos.close();
                    fos = null;
                } finally {
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (Exception ignore) {
                            //
                        }
                    }
                }
                return messageDigest.digest();
            } catch (NoSuchAlgorithmException e) {
                throw DeploymentRepositoryLogger.ROOT_LOGGER.cannotObtainSha1(e, MessageDigest.class.getSimpleName());
            }
        }

        @Override
        public void addContentReference(byte[] hash, Object reference) {
        }
    };
}
