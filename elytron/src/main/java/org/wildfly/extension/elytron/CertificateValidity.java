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

    /**
     * Fetch representation of health based on provided factors.
     * @param notBefore - certificate start date
     * @param notAfter - certificate expiration date
     * @param expirationWaterMark - watermark expressed in minutes.
     * @return
     * <ul>
     * <li>{@linkplain #NOT_YET} - if currentDate.before(notBefore)</li>
     * <li>{@linkplain #EXPIRE} - if currentDate.after(notAfter)</li>
     * <li>{@linkplain #ABOUT_TO_EXPIRE} - if (currentDate+waterMark).after(notAfter)</li>
     * <li>{@linkplain #VALID} - otherwise</li>
     * </ul>
     */
    static CertificateValidity getValidity(final Date notBefore, final Date notAfter, final long expirationWaterMark) {
        final Date currentDate = Calendar.getInstance().getTime();
        if (currentDate.before(notBefore)) {
            return NOT_YET;
        } else if(currentDate.after(notBefore) && currentDate.before(notAfter)) {
            if (expirationWaterMark > 0) {
                //if current_date+watermark>expiration_date
                final Date interimBoundryDate = new Date(currentDate.getTime() + expirationWaterMark*60L*1000L);
                if(interimBoundryDate.after(notAfter)) {
                    return ABOUT_TO_EXPIRE;
                } else {
                    return VALID;
                }
            } else {
                //watermark should be n+, but just in case someone abuse it lets have some fallback
                final long WEEK_IN_MS = 7 * 24 * 60 * 60 * 1000;
                final long DAY_IN_MS = 4 * 60 * 60 * 1000;
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
        } else /*if(currentDate.after(notAfter))*/ {
            return EXPIRED;
        }
    }
}
