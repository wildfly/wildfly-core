/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat, Inc., and individual contributors as indicated
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

package org.jboss.as.subsystem.test.transformers.subsystem.similarity;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

import java.util.ArrayList;
import java.util.Collections;

/**
 * original code taken from http://www.catalysoft.com/articles/StrikeAMatch.html
 *
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> *
 */
class SimilarityIndex {
    /**
     * @return an array of adjacent letter pairs contained in the input string
     */
    private static String[] letterPairs(String str) {
        int numPairs = str.length() - 1;
        String[] pairs = new String[numPairs];
        for (int i = 0; i < numPairs; i++) {
            pairs[i] = str.substring(i, i + 2);
        }
        return pairs;
    }

    /**
     * @return an ArrayList of 2-character Strings.
     */
    private static ArrayList<String> wordLetterPairs(String str) {
        ArrayList<String> allPairs = new ArrayList<String>();
        // Tokenize the string and put the tokens/words into an array
        String[] words = str.split("\\s");
        // For each word
        for (String word : words) {
            // Find the pairs of characters
            String[] pairsInWord = letterPairs(word);
            Collections.addAll(allPairs, pairsInWord);
        }
        return allPairs;
    }

    /**
     * @return lexical similarity value in the range [0,1]
     */

    public static double compareStrings(String str1, String str2) {
        ArrayList pairs1 = wordLetterPairs(str1.toUpperCase());
        ArrayList pairs2 = wordLetterPairs(str2.toUpperCase());
        int intersection = 0;
        int union = pairs1.size() + pairs2.size();
        for (Object pair1 : pairs1) {
            for (int j = 0; j < pairs2.size(); j++) {
                Object pair2 = pairs2.get(j);
                if (pair1.equals(pair2)) {
                    intersection++;
                    pairs2.remove(j);
                    break;
                }
            }
        }
        return (2.0 * intersection) / union;
    }

    private static final double SIMILARITY_THRESHOLD = 0.90d;

    public static boolean isSimilar(String str1, String str2) {
        return compareStrings(str1, str2) > SIMILARITY_THRESHOLD;
    }


    public static double compareAttributes(AttributeDefinition attr1, AttributeDefinition attr2) {
        double res = 1d;
        res *= compareStrings(attr1.getName(), attr2.getName());
        res *= compareStrings(attr1.getType().name(), attr2.getType().name());
        res *= compareStrings(String.valueOf(attr1.isAllowExpression()), String.valueOf(attr2.isAllowExpression()));
        res *= compareStrings(String.valueOf(attr1.isRequired()), String.valueOf(attr2.isRequired()));
        res *= attr1.isAllowExpression() == attr2.isAllowExpression() ? 1d : 0.9d;
        res *= attr1.isRequired() == attr2.isRequired() ? 1d : 0.9d;
        return res;
    }

    public static double compareAttributes(Property attr1, Property attr2) {
        double res = 1d;
        res *= compareStrings(attr1.getName(), attr2.getName());
        ModelNode node1 = attr1.getValue();
        ModelNode node2 = attr2.getValue();
        boolean expressions1 = node1.get(ModelDescriptionConstants.EXPRESSIONS_ALLOWED).asBoolean(false);
        boolean expressions2 = node2.get(ModelDescriptionConstants.EXPRESSIONS_ALLOWED).asBoolean(false);
        boolean nullable1 = node1.get(ModelDescriptionConstants.NILLABLE).asBoolean(true);
        boolean nullable2 = node2.get(ModelDescriptionConstants.NILLABLE).asBoolean(true);

        res *= compareStrings(node1.getType().name(), node2.getType().name());
        res *= compareStrings(node1.get(ModelDescriptionConstants.DESCRIPTION).asString(), node2.get(ModelDescriptionConstants.DESCRIPTION).asString());
        res *= expressions1 == expressions2 ? 1d : 0.9d;
        res *= nullable1 == nullable2 ? 1d : 0.9d;

        return res;
    }


}
