/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.validators;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SuffixValidatorTestCase {

    @Test
    public void testValidator() throws Exception {
        final SuffixValidator validator = new SuffixValidator();
        try {
            validator.validateParameter("suffix", new ModelNode("s"));
            Assert.assertTrue("The model should be invalid", false);
        } catch (OperationFailedException e) {
            // no-op
        }
        try {
            //invalid pattern with one single quote
            validator.validateParameter("suffix", new ModelNode(".yyyy-MM-dd'custom suffix"));
            Assert.assertTrue("The model should be invalid", false);
        } catch (OperationFailedException e) {
            // no-op
        }
        //valid pattern with custom suffix
        validator.validateParameter("suffix", new ModelNode(".yyyy-MM-dd'custom suffix'"));
    }

    @Test
    public void testCompressionSuffixes() {
        final SuffixValidator validator = new SuffixValidator();

        // A pattern should be able to end with .zip
        try {
            validator.validateParameter("suffix", new ModelNode(".yyyy-MM-dd'T'HH:mm.zip"));
        } catch (Exception e) {
            Assert.fail("Failed to allow .zip suffix: " + e.getMessage());
        }

        // A pattern should be able to end with .gz
        try {
            validator.validateParameter("suffix", new ModelNode(".yyyy-MM-dd'T'HH:mm.gz"));
        } catch (Exception e) {
            Assert.fail("Failed to allow .gz suffix: " + e.getMessage());
        }
    }
}
