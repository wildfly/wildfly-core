/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.password.simple;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author baranowb
 *
 */
public class SimpleDictionarTestCase {

    private final String tested = "XpasswordX";

    private SimpleDictionary dictionary = new SimpleDictionary();

    @Test
    public void testDictionary() {
        assertEquals(8, dictionary.dictionarySequence(tested));
    }
}
