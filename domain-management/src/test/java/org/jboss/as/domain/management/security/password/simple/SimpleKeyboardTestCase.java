/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.password.simple;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * @author baranowb
 */
public class SimpleKeyboardTestCase {

    private final String tested = "axcvnhy";

    private SimpleKeyboard keyboard = new SimpleKeyboard();

    @Test
    public void testSiblings() {
        assertFalse(keyboard.siblings(tested, 0));
        assertTrue(keyboard.siblings(tested, 1));
        assertTrue(keyboard.siblings(tested, 2));
        assertFalse(keyboard.siblings(tested, 3));
        assertTrue(keyboard.siblings(tested, 4));
        assertTrue(keyboard.siblings(tested, 5));
    }

    @Test
    public void testSequence() {
        assertEquals(0, keyboard.sequence(tested, 0));
        assertEquals(2, keyboard.sequence(tested, 1));
        assertEquals(2, keyboard.sequence(tested, 4));
    }

}
