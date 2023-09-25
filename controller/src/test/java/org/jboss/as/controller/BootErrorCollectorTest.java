/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.jboss.as.controller.access.Action.ActionEffect.ADDRESS;
import static org.jboss.as.controller.access.Action.ActionEffect.READ_CONFIG;
import static org.jboss.as.controller.access.Action.ActionEffect.READ_RUNTIME;
import static org.jboss.as.controller.access.Action.ActionEffect.WRITE_CONFIG;
import static org.jboss.as.controller.access.Action.ActionEffect.WRITE_RUNTIME;
import static org.hamcrest.MatcherAssert.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.registry.OperationEntry;
import org.junit.Test;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class BootErrorCollectorTest {

    public BootErrorCollectorTest() {
    }

    /**
     * Test of getReadBootErrorsHandler method, of class BootErrorCollector.
     */
    @Test
    public void testGetEffects() throws Exception {
        BootErrorCollector instance = new BootErrorCollector();
        BootErrorCollector.ListBootErrorsHandler handler = (BootErrorCollector.ListBootErrorsHandler) instance.getReadBootErrorsHandler();
        assertThat(handler, is(notNullValue()));
        OperationEntry entry = createOperationEntry(true, false);
        Set<Action.ActionEffect> effects = handler.getEffects(entry);
        assertThat(effects.size(), is(3));
        assertThat(effects, hasItems(ADDRESS, READ_CONFIG, READ_RUNTIME));
        entry = createOperationEntry(true, true);
        effects = handler.getEffects(entry);
        assertThat(effects.size(), is(2));
        assertThat(effects, hasItems(ADDRESS, READ_RUNTIME));
        entry = createOperationEntry(false, false);
        effects = handler.getEffects(entry);
        assertThat(effects.size(), is(5));
        assertThat(effects, hasItems(ADDRESS, READ_CONFIG, READ_RUNTIME, WRITE_CONFIG, WRITE_RUNTIME));
        entry = createOperationEntry(false, true);
        effects = handler.getEffects(entry);
        assertThat(effects.size(), is(3));
        assertThat(effects, hasItems(ADDRESS, READ_RUNTIME, WRITE_RUNTIME));
    }

    private OperationEntry createOperationEntry(boolean readOnly, boolean runtimeOnly) throws NoSuchMethodException,
            SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Constructor<OperationEntry> constructor = OperationEntry.class.getDeclaredConstructor(OperationDefinition.class,
                OperationStepHandler.class, boolean.class);
        constructor.setAccessible(true);
        SimpleOperationDefinitionBuilder odb = new SimpleOperationDefinitionBuilder("test", NonResolvingResourceDescriptionResolver.INSTANCE);
        if (readOnly) {
            odb.setReadOnly();
        }
        if (runtimeOnly) {
            odb.setRuntimeOnly();
        }
        return constructor.newInstance(odb.build(), null, false);
    }
}
