/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * Validates that a String value can resolve to a subnet format based on class SubnetUtils in Apache Commons Net
 *
 * @author wangc based on work of @author rwinston@apache.org
 *
 */
public class SubnetValidator extends StringLengthValidator {

    private static final String IP_ADDRESS = "(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})";
    private static final String SLASH_FORMAT = IP_ADDRESS + "/(\\d{1,3})";
    private static final Pattern cidrPattern = Pattern.compile(SLASH_FORMAT);
    private static final int NBITS = 32;

    public SubnetValidator(final boolean allowNull, final boolean allowExpressions) {
        super(1, allowNull, allowExpressions);
    }

    @Override
    public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
        super.validateParameter(parameterName, value);

        if (value.isDefined() && value.getType() != ModelType.EXPRESSION) {
            String subnet = value.asString();
            try {
                calculate(subnet);
            } catch (IllegalArgumentException e) {
                throw ControllerLogger.ROOT_LOGGER.invalidSubnetFormat(subnet, parameterName);
            }

        }
    }

    /*
     * Initialize the internal fields from the supplied CIDR mask
     */
    private void calculate(String mask) {
        Matcher matcher = cidrPattern.matcher(mask);

        if (matcher.matches()) {
            matchAddress(matcher);

            /* Create a binary netmask from the number of bits specification /x */
            rangeCheck(Integer.parseInt(matcher.group(5)), 0, NBITS);
        } else {
            throw new IllegalArgumentException("Could not parse [" + mask + "]");
        }
    }

    /*
     * Convenience method to extract the components of a dotted decimal address and pack into an integer using a regex match
     */
    private int matchAddress(Matcher matcher) {
        int addr = 0;
        for (int i = 1; i <= 4; ++i) {
            int n = (rangeCheck(Integer.parseInt(matcher.group(i)), 0, 255));
            addr |= ((n & 0xff) << 8 * (4 - i));
        }
        return addr;
    }

    /*
     * Convenience function to check integer boundaries. Checks if a value x is in the range [begin,end]. Returns x if it is in
     * range, throws an exception otherwise.
     */
    private int rangeCheck(int value, int begin, int end) {
        if (value >= begin && value <= end) { // (begin,end]
            return value;
        }

        throw new IllegalArgumentException("Value [" + value + "] not in range [" + begin + "," + end + "]");
    }
}
