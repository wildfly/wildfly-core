/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.operations;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.server.Services;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * Handler that dumps all services in the server container
 *
 * @author Jason T. Greene
 */
public class DumpServicesHandler implements OperationStepHandler {

    private static final String OPERATION_NAME = "dump-services";
    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, ServerDescriptions.getResourceDescriptionResolver())
            .setRuntimeOnly()
            .setReplyType(ModelType.STRING)
            .build();
    public static final DumpServicesHandler INSTANCE = new DumpServicesHandler();

    private DumpServicesHandler() {
    }

    /** {@inheritDoc} */
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final ServiceName serviceName;
        if (context.getProcessType().isServer()) {
            serviceName = Services.JBOSS_AS;
        } else {
            //The HC/DC service name
            serviceName = ServiceName.JBOSS.append("host", "controller");

        }

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                ServiceController<?> service = context.getServiceRegistry(false).getRequiredService(serviceName);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                PrintStream print = new PrintStream(out);
                service.getServiceContainer().dumpServices(print);
                print.flush();
                context.getResult().set(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        }, OperationContext.Stage.RUNTIME);
    }

}
