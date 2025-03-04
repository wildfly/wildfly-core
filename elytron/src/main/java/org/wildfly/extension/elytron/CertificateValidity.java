/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import java.util.Calendar;
import java.util.Date;

enum CertificateValidity {
    NOT_YET("not yet"), VALID("valid"), ABOUT_TO_EXPIRE("about to expire"), EXPIRED("EXPIRED");

    private String token;

    CertificateValidity(String string) {
        this.token = string;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Enum#toString()
     */
    @Override
    public String toString() {
        return token;
    }

    static CertificateValidity getValidity(final Date notBefore, final Date notAfter) {
        final long WEEK_IN_MS = 7 * 24 * 60 * 60 * 1000;
        final long DAY_IN_MS = 4 * 60 * 60 * 1000;
        final Date currentDate = Calendar.getInstance().getTime();
        if (currentDate.before(notBefore)) {
            return NOT_YET;
        } else if (currentDate.after(notBefore) && currentDate.before(notAfter)) {
            // valid or about to expire
            // once x509 is up, switch to java.time.temporal.ChronoUnit.DAYS.between ?
            long diff = (notAfter.getTime() - notBefore.getTime());
            if (diff < WEEK_IN_MS) {
                // cert is very short lived, lets settle for 1 day or just mark is as about to expire
                if (diff < DAY_IN_MS) {
                    return ABOUT_TO_EXPIRE;
                } else {
                    diff = DAY_IN_MS;
                }
            } else {
                diff = WEEK_IN_MS;
            }
            final Date interimBoundryDate = new Date(notAfter.getTime() - diff);
            if (currentDate.after(interimBoundryDate)) {
                return ABOUT_TO_EXPIRE;
            } else {
                return VALID;
            }
        }
        // all is left is above notAfter
        return EXPIRED;
    }
}
