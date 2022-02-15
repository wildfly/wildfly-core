/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.controller.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ReadResourceChildOrderingTestCase extends AbstractControllerTestBase {

    private static final PathElement testSubsystem = PathElement.pathElement("test");
    private static final String[] STR = new String[]{"g", "e", "l", "d", "h", "h", "k", "f", "a", "b", "j", "g", "c", "i"};
    private static final String PROP = "prop";
    private static final LinkedHashMap<String, String> data;

    static {
        data = new LinkedHashMap<>();

        data.put("attr03", null);
        data.put("attr02", "g01");
        data.put("attr01", "g02");
    }

    ModelNode model;

    public ReadResourceChildOrderingTestCase() {
        model = new ModelNode();
        for (String aSTR : STR) {
            model.get("test", aSTR, PROP).set(aSTR.toUpperCase(Locale.ENGLISH));
        }
    }


    @Test
    public void testOrdering() throws Exception {
        ModelNode op = createOperation(READ_RESOURCE_OPERATION);
        op.get(RECURSIVE).set(true);

        ModelNode result = executeForResult(op);
        Assert.assertEquals(model, result);
    }


    /**
     * Test for alphabetical order of read-resource-operation - fix of defect WFCORE-1985.
     *
     * Test case reads resource description with following values: ["attr1", group "g2"], ["attr2", group "g1"], ["attr3", group none], ["prop", group none]
     *
     * Order of result before fix of WFCORE-1985 was: ["attr3", group none], ["prop", group none], ["attr2", group "g1"], ["attr1", group "g2"]. Result was firstly sorted by group, then by name
     *
     * New order is alphabetical by name only: ["attr1", group "g2"], ["attr2", group "g1"], ["attr3", group none], ["prop", group none]:
     */
    @Test
    public void testReadResourceDescriptionOrdering() throws Exception {
        final ModelController controller = getController();

        ModelNode address = new ModelNode();
        address.add("test", "*");

        final ModelNode read = new ModelNode();
        read.get(OP).set("read-resource-description");
        read.get(OP_ADDR).set(address);
        read.get("recursive").set(true);

        ModelNode response = controller.execute(read, null, null, null);
        assertEquals(response.toString(), "success", response.get("outcome").asString());
        ModelNode result = response.get("result").get(0).get("result");

        for (String attr : data.keySet()) {
            assertTrue(result.toString(), result.hasDefined("attributes", attr));
        }

        ModelNode attrs = result.get("attributes");

        int i = 1;
        for (; i < 3; i++) {
            String desc = attrs.asList().get(i-1).get(0).get("description").asString();
            assertEquals("Order of the "+i+". item is wrong.", "attr0"+i, desc);
        }
        String desc = attrs.asList().get(3).get(0).get("description").asString();
        assertEquals("Order of the 4. item is wrong.", PROP, desc);
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration registration = managementModel.getRootResourceRegistration();
        GlobalOperationHandlers.registerGlobalOperations(registration, processType);
        registration.registerOperationHandler(new SimpleOperationDefinitionBuilder("setup", NonResolvingResourceDescriptionResolver.INSTANCE)
                .setPrivateEntry()
                .build()
                , new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                createModel(context, model);
            }
        });

        GlobalNotifications.registerGlobalNotifications(registration, processType);

        ManagementResourceRegistration child = registration.registerSubModel(new SimpleResourceDefinition(testSubsystem, NonResolvingResourceDescriptionResolver.INSTANCE));
        child.registerReadOnlyAttribute(TestUtils.createNillableAttribute("prop", ModelType.STRING), null);



        for(Map.Entry<String, String> entry : data.entrySet() ) {
            child.registerReadOnlyAttribute(TestUtils.createAttribute(entry.getKey(), ModelType.STRING, entry.getValue(), true), null);
        }

        managementModel.getRootResource().getModel().set(model);
    }

}
