/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.shared;

import java.security.AccessController;
import java.security.PrivilegedAction;

import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Adjusts default timeouts according to the system property.
 *
 * All tests influenced by machine slowness should employ this util.
 *
 * @author Ondrej Zizka
 * @author Radoslav Husar
 * @author Jan Lanik
 */
public class TimeoutUtil {

    public static final String FACTOR_SYS_PROP = "ts.timeout.factor";
    private static int factor;

    static {
        factor = WildFlySecurityManager.isChecking() ? AccessController.doPrivileged((PrivilegedAction<Integer>) () -> Integer.getInteger(FACTOR_SYS_PROP, 100)) : Integer.getInteger(FACTOR_SYS_PROP, 100);
    }

    /**
     * Adjusts timeout for operations.
     *
     * @return given timeout adjusted by ratio from system property "ts.timeout.factor"
     */
    public static int adjust(int amount) {
       if(amount<0){
          throw new IllegalArgumentException("amount must be non-negative");
       }
       int numerator = amount * factor;
       int finalTimeout;
       if(numerator % 100 == 0){
          //in this case there is no lost of accuracy in integer division
          finalTimeout = numerator / 100;
       } else {
          /*in this case there is a loss of accuracy. It's better to round the result up because
          if we round down, we would get 0 in case that amount<100.
           */
          finalTimeout = (numerator / 100) + 1;
       }
        return finalTimeout;
    }

    /**
     * Get timeout factor to multiply by.
     *
     * @return double factor value
     */
    public static double getFactor() {
        return (double)factor / 100;
    }

    /**
     * Get raw timeout factor.
     *
     * @return value of parsed system property "ts.timeout.factor"
     */
    public static int getRawFactor() {
        return factor;
    }
}