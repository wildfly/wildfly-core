/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
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
