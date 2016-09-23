/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller.operations.global;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class ListOperations {

    public static final OperationDefinition LIST_ADD_DEFINITION = new SimpleOperationDefinitionBuilder("list-add", ControllerResolver.getResolver("global"))
            .setParameters(AbstractCollectionHandler.NAME, AbstractCollectionHandler.VALUE, AbstractListHandler.INDEX)
            .setRuntimeOnly()
            .build();
    public static final OperationDefinition LIST_GET_DEFINITION = new SimpleOperationDefinitionBuilder("list-get", ControllerResolver.getResolver("global"))
            .setParameters(AbstractCollectionHandler.NAME, AbstractListHandler.INDEX)
            .setRuntimeOnly()
            .setReadOnly()
            .build();
    public static final OperationDefinition LIST_REMOVE_DEFINITION = new SimpleOperationDefinitionBuilder("list-remove", ControllerResolver.getResolver("global"))
            .setParameters(AbstractCollectionHandler.NAME, AbstractCollectionHandler.VALUE, AbstractListHandler.INDEX)
            .setRuntimeOnly()
            .build();
    public static final OperationDefinition LIST_CLEAR_DEFINITION = new SimpleOperationDefinitionBuilder("list-clear", ControllerResolver.getResolver("global"))
            .setParameters(AbstractCollectionHandler.NAME)
            .setRuntimeOnly()
            .build();
    public static final OperationStepHandler LIST_ADD_HANDLER = new ListAddHandler();
    public static final OperationStepHandler LIST_REMOVE_HANDLER = new ListRemoveHandler();
    public static final OperationStepHandler LIST_GET_HANDLER = new ListGetHandler();
    public static final OperationStepHandler LIST_CLEAR_HANDLER = new ListClearHandler();

    public static final Set<String> LIST_OPERATION_NAMES = new HashSet<>(Arrays.asList(LIST_CLEAR_DEFINITION.getName(),
                LIST_REMOVE_DEFINITION.getName(),
                LIST_ADD_DEFINITION.getName(),
                LIST_GET_DEFINITION.getName()));


    /**
     * @author Tomaz Cerar (c) 2014 Red Hat Inc.
     */
    abstract static class AbstractListHandler extends AbstractCollectionHandler {
        static final SimpleAttributeDefinition INDEX = new SimpleAttributeDefinition("index", ModelType.INT, true);

        AbstractListHandler(AttributeDefinition... attributes) {
            super(attributes);
        }

        AbstractListHandler(boolean requiredReadWrite, AttributeDefinition... attributes) {
            super(requiredReadWrite, attributes);
        }


        @Override
        public void updateModel(OperationContext context, ModelNode model, AttributeDefinition attributeDefinition, ModelNode attribute) throws OperationFailedException {
            if (attribute.getType() != ModelType.LIST && attributeDefinition.getType() != ModelType.LIST) {
                throw ControllerLogger.MGMT_OP_LOGGER.attributeIsWrongType(attributeDefinition.getName(), ModelType.LIST, attributeDefinition.getType());
            }
            updateModel(context, model, attribute);
        }

        abstract void updateModel(final OperationContext context, ModelNode model, ModelNode listAttribute) throws OperationFailedException;

    }

    /**
     * Add element to list, with optional index where to put it
     * <p/>
     * <pre>:list-add(name=list-attribute, value="some value", [index=5])</pre>
     *
     * @author Tomaz Cerar (c) 2014 Red Hat Inc.
     */
    public static class ListAddHandler extends AbstractListHandler {
        private ListAddHandler() {
            super(VALUE, INDEX);
        }

        void updateModel(final OperationContext context, ModelNode model, ModelNode listAttribute) throws OperationFailedException {
            ModelNode value = model.get(VALUE.getName());
            ModelNode indexNode = INDEX.resolveModelAttribute(context, model);

            LinkedList<ModelNode> res = new LinkedList<>(listAttribute.isDefined() ? listAttribute.asList() : Collections.<ModelNode>emptyList());
            if (indexNode.isDefined()) {
                res.add(indexNode.asInt(), value);
            } else {
                res.add(value);
            }
            listAttribute.set(res);
        }
    }


    /**
     * Add element to list, with optional index where to put it
     * <p/>
     * <pre>:list-remove(name=list-attribute, value="some value")</pre>
     *
     * @author Tomaz Cerar (c) 2014 Red Hat Inc.
     */
    public static class ListRemoveHandler extends AbstractListHandler {
        private ListRemoveHandler() {
            super(VALUE, INDEX);
        }

        void updateModel(final OperationContext context, ModelNode model, ModelNode listAttribute) throws OperationFailedException {
            ModelNode value = model.get(VALUE.getName());
            ModelNode index = INDEX.resolveModelAttribute(context, model);
            List<ModelNode> res = new ArrayList<>(listAttribute.asList());
            if (index.isDefined()) {
                res.remove(index.asInt());
            } else {
                res.remove(value);
            }
            listAttribute.set(res);
        }
    }

    /**
     * Add element to list, with optional index where to put it
     * <p/>
     * <pre>:list-get(name=list-attribute, index=5)</pre>
     *
     * @author Tomaz Cerar (c) 2014 Red Hat Inc.
     */
    public static class ListGetHandler extends AbstractListHandler {
        private ListGetHandler() {
            super(false, INDEX);
        }

        void updateModel(final OperationContext context, ModelNode model, ModelNode listAttribute) throws OperationFailedException {
            int index = INDEX.resolveModelAttribute(context, model).asInt();
            if (listAttribute.hasDefined(index)) {
                context.getResult().set(listAttribute.get(index));
            }
        }
    }

    /**
     * Add element to list, with optional index where to put it
     * <p/>
     * <pre>:list-remove(name=list-attribute, value="some value")</pre>
     *
     * @author Tomaz Cerar (c) 2014 Red Hat Inc.
     */
    public static class ListClearHandler extends AbstractListHandler {
        private ListClearHandler() {
            super();
        }

        void updateModel(final OperationContext context, ModelNode model, ModelNode listAttribute) throws OperationFailedException {
            listAttribute.setEmptyList();
        }
    }

}
