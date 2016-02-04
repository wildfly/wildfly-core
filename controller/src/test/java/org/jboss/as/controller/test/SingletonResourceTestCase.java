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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_SINGLETONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_TYPES_OPERATION;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SingletonResourceTestCase extends AbstractControllerTestBase {

    private static final String CORE = "core";
    static final String MODEL = "model";
    private static final String CHILD = "child";
    private static final String SERVICE = "service";
    private static final String DATASOURCE = "data-source";
    private static final String DS = "exampleDS";
    private static final String REMOTE = "remote";
    private static final String ASYNC = "async";

    @Test
    public void readChildrenTypes() throws Exception {
        ModelNode op = createOperation(READ_CHILDREN_TYPES_OPERATION);
        ModelNode result = executeForResult(op);
        List<ModelNode> list = result.asList();
        Assert.assertEquals(1, list.size());
        Assert.assertTrue(list.contains(new ModelNode(CORE)));

        op.get(OP_ADDR).setEmptyList().add(CORE, MODEL);
        result = executeForResult(op);
        list = result.asList();
        Assert.assertEquals(3, list.size());
        Assert.assertTrue(list.contains(new ModelNode(CHILD)));
        Assert.assertTrue(list.contains(new ModelNode(SERVICE)));
        Assert.assertTrue(list.contains(new ModelNode(DATASOURCE)));

        op.get(OP_ADDR).setEmptyList().add(CORE, MODEL);
        op.get(INCLUDE_SINGLETONS).set(true);
        result = executeForResult(op);
        list = result.asList();
        Assert.assertEquals(5, list.size());
        Assert.assertTrue(list.contains(new ModelNode(CHILD)));
        Assert.assertTrue(list.contains(new ModelNode(DATASOURCE)));
        Assert.assertTrue(list.contains(new ModelNode(DATASOURCE + '=' + DS)));
        Assert.assertTrue(list.contains(new ModelNode(SERVICE + '=' + ASYNC)));
        Assert.assertTrue(list.contains(new ModelNode(SERVICE + '=' + REMOTE)));
    }

    @Test
    public void readChildrenNames() throws Exception {
        ModelNode op = createOperation(READ_CHILDREN_NAMES_OPERATION);
        op.get(CHILD_TYPE).set(CORE);
        ModelNode result = executeForResult(op);
        List<ModelNode> list = result.asList();
        Assert.assertEquals(1, list.size());
        Assert.assertTrue(list.contains(new ModelNode(MODEL)));

        op.get(OP_ADDR).setEmptyList().add(CORE, MODEL);
        op.get(CHILD_TYPE).set(SERVICE);
        result = executeForResult(op);
        list = result.asList();
        Assert.assertEquals(0, list.size());

        op.get(OP_ADDR).setEmptyList().add(CORE, MODEL);
        op.get(CHILD_TYPE).set(SERVICE);
        op.get(INCLUDE_SINGLETONS).set(true);
        result = executeForResult(op);
        list = result.asList();
        Assert.assertEquals(2, list.size());

        Assert.assertTrue(list.contains(new ModelNode(ASYNC)));
        Assert.assertTrue(list.contains(new ModelNode(REMOTE)));
    }

    @Override
    protected void initModel(ManagementModel managementModel) {
        ManagementResourceRegistration registration = managementModel.getRootResourceRegistration();
        GlobalOperationHandlers.registerGlobalOperations(registration, processType);

        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);

        GlobalNotifications.registerGlobalNotifications(registration, processType);

        ManagementResourceRegistration coreResourceRegistration = registration.registerSubModel(new CoreResourceDefinition());
        coreResourceRegistration.registerSubModel(new ChildResourceDefinition(CHILD));
        coreResourceRegistration.registerSubModel(new SingletonResourceDefinition(SERVICE, ASYNC));
        coreResourceRegistration.registerSubModel(new SingletonResourceDefinition(SERVICE, REMOTE));
        coreResourceRegistration.registerSubModel(new ChildResourceDefinition(DATASOURCE));
        coreResourceRegistration.registerSubModel(new SingletonResourceDefinition(DATASOURCE, DS));
        Resource model = Resource.Factory.create();
        Resource rootResource = managementModel.getRootResource();
        rootResource.registerChild(PathElement.pathElement(CORE, MODEL), model);
        model.registerChild(PathElement.pathElement(CHILD, "myChild"), Resource.Factory.create());
    }

    private PathElement getCoreModelElement() {
        return PathElement.pathElement(CORE, MODEL);
    }

    private class CoreResourceDefinition extends SimpleResourceDefinition {

        public CoreResourceDefinition() {
            super(getCoreModelElement(), createResourceDescriptionResolver());
        }
    }

    private class ChildResourceDefinition extends SimpleResourceDefinition {

        public ChildResourceDefinition(String name) {
            super(PathElement.pathElement(name), createResourceDescriptionResolver());
        }
    }

    private class SingletonResourceDefinition extends SimpleResourceDefinition {

        public SingletonResourceDefinition(String parent, String name) {
            super(PathElement.pathElement(parent, name), createResourceDescriptionResolver());
        }
    }

    static ResourceDescriptionResolver createResourceDescriptionResolver() {
        final Map<String, String> strings = new HashMap<String, String>();
        strings.put("test", "The test resource");
        strings.put("test.child", "Child test resource");
        strings.put("test.data-source", "Override singleton test resource");
        strings.put("test.service", "Pure singleton test resource");

        return new StandardResourceDescriptionResolver("test", SingletonResourceTestCase.class.getName() + ".properties", SingletonResourceTestCase.class.getClassLoader(), true, false) {

            @Override
            public ResourceBundle getResourceBundle(Locale locale) {
                return new ResourceBundle() {

                    @Override
                    protected Object handleGetObject(String key) {
                        return strings.get(key);
                    }

                    @Override
                    public Enumeration<String> getKeys() {
                        return null;
                    }
                };
            }

        };
    }
}
