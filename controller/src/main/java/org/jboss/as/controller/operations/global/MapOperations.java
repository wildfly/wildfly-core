/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.global;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class MapOperations {
    public static final SimpleAttributeDefinition KEY = new SimpleAttributeDefinitionBuilder("key", ModelType.STRING, false).build();
    public static final OperationDefinition MAP_CLEAR_DEFINITION = new SimpleOperationDefinitionBuilder("map-clear", ControllerResolver.getResolver("global"))
            .setParameters(AbstractCollectionHandler.NAME)
            .build();
    public static final OperationDefinition MAP_REMOVE_DEFINITION = new SimpleOperationDefinitionBuilder("map-remove", ControllerResolver.getResolver("global"))
            .setParameters(AbstractCollectionHandler.NAME, KEY)
            .build();
    public static final OperationDefinition MAP_GET_DEFINITION = new SimpleOperationDefinitionBuilder("map-get", ControllerResolver.getResolver("global"))
            .setParameters(AbstractCollectionHandler.NAME, KEY)
            .setReadOnly()
            .build();
    public static final OperationDefinition MAP_PUT_DEFINITION = new SimpleOperationDefinitionBuilder("map-put", ControllerResolver.getResolver("global"))
            .setParameters(AbstractCollectionHandler.NAME, KEY, AbstractCollectionHandler.VALUE)
            .build();

    public static final OperationStepHandler MAP_CLEAR_HANDLER = new MapClearHandler();
    public static final OperationStepHandler MAP_GET_HANDLER = new MapGetHandler();
    public static final OperationStepHandler MAP_REMOVE_HANDLER = new MapRemoveHandler();
    public static final OperationStepHandler MAP_PUT_HANDLER = new MapPutHandler();

    public static final Set<String> MAP_OPERATION_NAMES = new HashSet<>(Arrays.asList(MAP_CLEAR_DEFINITION.getName(),
            MAP_REMOVE_DEFINITION.getName(),
            MAP_PUT_DEFINITION.getName(),
            MAP_GET_DEFINITION.getName()));

    /**
     * @author Tomaz Cerar (c) 2014 Red Hat Inc.
     */
    abstract static class AbstractMapHandler extends AbstractCollectionHandler {

        AbstractMapHandler(AttributeDefinition... attributes) {
            super(attributes);
        }

        AbstractMapHandler(boolean requiredReadWrite, AttributeDefinition... attributes) {
            super(requiredReadWrite, attributes);
        }


        abstract void updateModel(final OperationContext context, ModelNode model, AttributeDefinition attributeDefinition, ModelNode attribute) throws OperationFailedException;
    }

    /**
     * Empty map, note that is not the same as :undefine(name=name-of-attribute)
     * <p/>
     * <pre>map-clear(name=name-of-attribute)</pre>
     *
     * @author Tomaz Cerar (c) 2014 Red Hat Inc.
     */

    public static class MapClearHandler extends AbstractMapHandler {
        private MapClearHandler() {
            super(true);
        }

        void updateModel(final OperationContext context, ModelNode model, AttributeDefinition attributeDefinition, ModelNode attribute) throws OperationFailedException {
            attribute.setEmptyObject();
        }
    }

    /**
     * Get entry from map:
     * <p/>
     * <pre>:map-get(name=name-of-attribute, key=some-key)</pre>
     *
     * @author Tomaz Cerar (c) 2014 Red Hat Inc.
     */
    public static class MapGetHandler extends AbstractMapHandler {
        private MapGetHandler() {
            super(false, KEY);
        }

        void updateModel(final OperationContext context, ModelNode model, AttributeDefinition attributeDefinition, ModelNode attribute) throws OperationFailedException {
            String key = KEY.resolveModelAttribute(context, model).asString();
            if (attribute.hasDefined(key)) {
                context.getResult().set(attribute.get(key));
            }
        }
    }

    /**
     * put entry to map
     * <p/>
     * <pre>:map-put(name=name-of-attribute, key=some-key, value="newvalue")</pre>
     *
     * @author Tomaz Cerar (c) 2014 Red Hat Inc.
     */
    public static class MapPutHandler extends AbstractMapHandler {
        private MapPutHandler() {
            super(KEY, VALUE);
        }

        void updateModel(final OperationContext context, ModelNode model, AttributeDefinition attributeDefinition, ModelNode attribute) throws OperationFailedException {
            String key = KEY.resolveModelAttribute(context, model).asString();
            ModelNode value = model.get(VALUE.getName());
            attribute.get(key).set(value);
        }
    }

    /**
     * Remove entry from map
     * <p/>
     * <pre>:map-remove(name=name-of-attribute, key=some-key)</pre>
     *
     * @author Tomaz Cerar (c) 2014 Red Hat Inc.
     */
    public static class MapRemoveHandler extends AbstractMapHandler {
        private MapRemoveHandler() {
            super(KEY);
        }

        void updateModel(final OperationContext context, ModelNode model, AttributeDefinition attributeDefinition, ModelNode attribute) throws OperationFailedException {
            String key = KEY.resolveModelAttribute(context, model).asString();
            attribute.remove(key);
            if(attribute.asList().isEmpty()){
                attribute.clear();
            }
        }
    }
}
