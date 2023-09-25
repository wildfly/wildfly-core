/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
