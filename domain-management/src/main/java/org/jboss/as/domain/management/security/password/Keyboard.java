/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.password;

/**
 * Keybord interface. Defines methods which allow user to detect if characters in 'word' are situated next to each other on
 * keybord. For instance:
 *
 * <pre>
 *    -----     -----          -----
 *    | G | --- | H | .... --- | K |
 *    -----     -----          -----
 *      |
 *    -----
 *    | V |
 *    -----
 *
 * </pre>
 *
 * <i>V</i> and <i>H</i> are siblings of <b>G</b>. While <i>K</i> is not.
 *
 * @author baranowb
 *
 */
public interface Keyboard {
    /**
     * Detects if char next to index is its sibling.
     *
     * @param word - string against which checks are performed.
     * @param index - index of character to be tested.
     * @return <b>true</b> if characters are siblings.
     * @throws IllegalArgumentException if index is wrong or null argument is passed.
     */
    boolean siblings(String word, int index);

    /**
     * Just as {@link #siblings(String, int)}, but it accepts index of second char that is a subject to test. This allows to
     * detect patterns of keystrokes.
     *
     * @param word - string against which checks are performed.
     * @param index - index of character to be tested.
     * @param isSiblingIndex - index of second character to be tested.
     * @return <b>true</b> if characters are siblings.
     * @throws IllegalArgumentException if index is wrong or null argument is passed.
     */
    boolean siblings(String word, int index, int isSiblingIndex);

    /**
     * Detects sequence of keys in word. If {@link #siblings(String, int)} returns true for 'index', Than this method will
     * return at least '1' - indcating that after 'index' there is sequence of chars. Example:
     *
     * <pre>
     *  String word = "ASDFG";
     *  Keyboard.sequence(word,0) == word.length()-1;
     * </pre>
     *
     * @param word - word in which keyboard is to search for keys sequence.
     * @param index - start index of search.
     * @return - number of characters in sequence
     */
    int sequence(String word, int index);
}
