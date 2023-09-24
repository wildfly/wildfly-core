/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.password.simple;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jboss.as.domain.management.security.password.Keyboard;

/**
 * Simplest implementation for EN keyboard.
 *
 * @author baranowb
 *
 */
public class SimpleKeyboard implements Keyboard {

    protected Map<Character, Set<Character>> keys = new TreeMap<Character, Set<Character>>();

    public SimpleKeyboard() {
        InputStream is = Keyboard.class.getResourceAsStream("keybord.properties");
        if(is!=null){
            this.init(is);
        }else{
            //log?
        }
    }

    public SimpleKeyboard(InputStream is) {
        this.init(is);
    }

    protected void init(InputStream is) {
        try {
            Properties props = new Properties();
            props.load(is);

            Iterator it = props.keySet().iterator();
            while (it.hasNext()) {
                String keyString = (String) it.next();
                String charsString = (String) props.get(keyString);
                Character key = null;
                if (keyString.length() > 0) {
                    key = keyString.charAt(0);
                } else {
                    key = ' ';
                }
                Set<Character> chars = new TreeSet<Character>();
                for (int index = 0; index < charsString.length(); index++) {
                    Character c = charsString.charAt(index);
                    chars.add(c);
                }

                keys.put(key, chars);
            }
        } catch (Exception ioe) {
            ioe.printStackTrace();
        }

    }

    public boolean siblings(String word, int index) {
        return this.siblings(word, index, index + 1);
    }

    public boolean siblings(String word, int index, int isSiblingIndex) {
        checkNotNullParam("word", word);
        if (index >= isSiblingIndex) {
            throw new IllegalArgumentException();
        }

        int len = word.length();
        if (index >= len) {
            throw new IllegalArgumentException();
        }
        Character keyId = word.charAt(index);
        Character possibleSibling = word.charAt(isSiblingIndex);
        Set<Character> chars = this.keys.get(keyId);
        // JIC
        if (chars != null) {
            if (chars.contains(possibleSibling)) {
                return true;
            }
        }

        return false;
    }

    public int sequence(String word, int start) {
        int len = word.length();
        if (start >= len) {
            throw new IllegalArgumentException("Start index greater than word length");
        }
        int sequenceLength = 0;
        for (int index = start; index + 1 < len; index++) {
            if (siblings(word, index)) {
                sequenceLength++;
            } else {
                return sequenceLength;
            }
        }
        return sequenceLength;
    }
}
