/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.audit;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.services.path.PathManagerService;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 *  All methods on this class should be called with {@link ManagedAuditLoggerImpl}'s lock taken.
 *
 * @author <a href="mailto:istudens@redhat.com">Ivo Studensky</a>
 */
public class PeriodicRotatingFileAuditLogHandler extends AbstractFileAuditLogHandler {
    private SimpleDateFormat format;
    private Period period = Period.NEVER;
    private volatile String nextSuffix;
    private volatile long nextRollover = Long.MAX_VALUE;

    private TimeZone timeZone = TimeZone.getDefault();
    private String suffix;

    public PeriodicRotatingFileAuditLogHandler(final String name, final String formatterName, final int maxFailureCount, final PathManagerService pathManager, final String path, final String relativeTo, final String suffix, final TimeZone timeZone) {
        super(name, formatterName, maxFailureCount, pathManager, path, relativeTo);
        this.suffix = suffix;       // remember the value just for the sake of the method isDifferent()
        if (timeZone != null)
            this.timeZone = timeZone;   // needed for setSuffix in the next step
        setSuffix(suffix);
    }

    @Override
    protected void initializeAtStartup(final File file) {
        final long lastModified = file.lastModified();
        calcNextRollover((lastModified > 0) ? lastModified : System.currentTimeMillis());
    }

    @Override
    protected void rotateLogFile(final File file) {
        final long now = System.currentTimeMillis();
        if (now >= nextRollover) {
            rollOver(file);
            calcNextRollover(now);
        }
    }

    /**
     * Set the suffix string.  The string is in a format which can be understood by {@link java.text.SimpleDateFormat}.
     * The period of the rotation is automatically calculated based on the suffix.
     *
     * @param suffix the suffix
     * @throws IllegalArgumentException if the suffix is not valid
     */
    private void setSuffix(String suffix) throws IllegalArgumentException {
        // method code stolen from the logging subsystem
        final SimpleDateFormat format = new SimpleDateFormat(suffix);
        format.setTimeZone(timeZone);
        final int len = suffix.length();
        Period period = Period.NEVER;
        for (int i = 0; i < len; i ++) {
            switch (suffix.charAt(i)) {
                case 'y': period = min(period, Period.YEAR); break;
                case 'M': period = min(period, Period.MONTH); break;
                case 'w':
                case 'W': period = min(period, Period.WEEK); break;
                case 'D':
                case 'd':
                case 'F':
                case 'E': period = min(period, Period.DAY); break;
                case 'a': period = min(period, Period.HALF_DAY); break;
                case 'H':
                case 'k':
                case 'K':
                case 'h': period = min(period, Period.HOUR); break;
                case 'm': period = min(period, Period.MINUTE); break;
                case '\'': while (suffix.charAt(++i) != '\''){} break;
                case 's':
                case 'S': throw new IllegalArgumentException("Rotating by second or millisecond is not supported");
            }
        }
        this.format = format;
        this.period = period;
    }

    private void rollOver(final File file) {
        final File backup = new File(file.getParentFile(), file.getName() + nextSuffix);
        try {
            rename(file, backup);
        } catch (IOException e) {
            throw ControllerLogger.ROOT_LOGGER.couldNotBackUp(e, file.getAbsolutePath(), backup.getAbsolutePath());
        }
        createNewFile(file);
    }

    private void calcNextRollover(final long fromTime) {
        // method code stolen from the logging subsystem
        if (period == Period.NEVER) {
            nextRollover = Long.MAX_VALUE;
            return;
        }
        nextSuffix = format.format(new Date(fromTime));
        final Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(fromTime);
        final Period period = this.period;
        // clear out less-significant fields
        switch (period) {
            default:
            case YEAR:
                calendar.set(Calendar.MONTH, 0);
            case MONTH:
                calendar.set(Calendar.DAY_OF_MONTH, 0);
                calendar.clear(Calendar.WEEK_OF_MONTH);
            case WEEK:
                if (period == Period.WEEK) {
                    calendar.set(Calendar.DAY_OF_WEEK, 0);
                } else {
                    calendar.clear(Calendar.DAY_OF_WEEK);
                }
                calendar.clear(Calendar.DAY_OF_WEEK_IN_MONTH);
            case DAY:
                calendar.set(Calendar.HOUR_OF_DAY, 0);
            case HALF_DAY:
                calendar.set(Calendar.HOUR, 0);
            case HOUR:
                calendar.set(Calendar.MINUTE, 0);
            case MINUTE:
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
        }
        // increment the relevant field
        switch (period) {
            case YEAR:
                calendar.add(Calendar.YEAR, 1);
                break;
            case MONTH:
                calendar.add(Calendar.MONTH, 1);
                break;
            case WEEK:
                calendar.add(Calendar.WEEK_OF_YEAR, 1);
                break;
            case DAY:
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                break;
            case HALF_DAY:
                calendar.add(Calendar.AM_PM, 1);
                break;
            case HOUR:
                calendar.add(Calendar.HOUR_OF_DAY, 1);
                break;
            case MINUTE:
                calendar.add(Calendar.MINUTE, 1);
                break;
        }
        nextRollover = calendar.getTimeInMillis();
    }

    @Override
    boolean isDifferent(AuditLogHandler other) {
        if (other instanceof PeriodicRotatingFileAuditLogHandler == false){
            return true;
        }
        PeriodicRotatingFileAuditLogHandler otherHandler = (PeriodicRotatingFileAuditLogHandler)other;
        if (!suffix.equals(otherHandler.suffix)) {
            return true;
        }
        if (!timeZone.equals(otherHandler.timeZone)) {
            return true;
        }
        if (super.isDifferent(other)) {
            return true;
        }
        return false;
    }

    private static <T extends Comparable<? super T>> T min(T a, T b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /**
     * Possible period values.  Keep in strictly ascending order of magnitude.
     */
    public enum Period {
        MINUTE,
        HOUR,
        HALF_DAY,
        DAY,
        WEEK,
        MONTH,
        YEAR,
        NEVER,
    }

}
