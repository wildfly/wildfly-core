/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
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
package org.jboss.as.test.syslogserver;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.jboss.logging.Logger;
import org.productivity.java.syslog4j.server.SyslogServerEventIF;

/**
 * Simple implementation of rfc5424 syslog message format (c.f. http://tools.ietf.org/html/rfc5424#section-6).
 *
 * @author Josef Cacek
 */
public class Rfc5424SyslogEvent implements SyslogServerEventIF {

    private static final char SP = ' ';
    private static final String CHARSET = "UTF-8";
    private static final String NIL = "-";
    private static final byte[] UTF_8_BOM = { (byte) 0xef, (byte) 0xbb, (byte) 0xbf };

    private static Logger LOGGER = Logger.getLogger(Rfc5424SyslogEvent.class);

    private final byte[] raw;

    private final String prioVersion;
    private final int facility;
    private final int level;
    private final int version;

    private final String timestamp;
    private final String host;
    private final String appName;
    private final String procId;
    private final String msgId;
    private final String structuredData;
    private final String message;

    public Rfc5424SyslogEvent(byte[] data, int offset, int length) {
        raw = new byte[length - offset];
        System.arraycopy(data, offset, raw, 0, length);
        int startPos = 0;
        int endPos = -1;

        endPos = searchChar(raw, startPos, SP);
        prioVersion = getString(raw, startPos, endPos);

        startPos = endPos + 1;
        endPos = searchChar(raw, startPos, ' ');
        timestamp = getString(raw, startPos, endPos);

        startPos = endPos + 1;
        endPos = searchChar(raw, startPos, ' ');
        host = getString(raw, startPos, endPos);

        startPos = endPos + 1;
        endPos = searchChar(raw, startPos, ' ');
        appName = getString(raw, startPos, endPos);

        startPos = endPos + 1;
        endPos = searchChar(raw, startPos, ' ');
        procId = getString(raw, startPos, endPos);

        startPos = endPos + 1;
        endPos = searchChar(raw, startPos, ' ');
        msgId = getString(raw, startPos, endPos);

        startPos = endPos + 1;
        if (raw[startPos] == '[') {
            endPos = searchChar(raw, startPos, ']') + 1;
        } else {
            endPos = searchChar(raw, startPos, ' ');
            if (endPos == -1)
                endPos = raw.length;
        }
        structuredData = getString(raw, startPos, endPos);

        startPos = endPos + 1;
        if (startPos < raw.length) {
            if (startPos + 3 < raw.length && raw[startPos] == UTF_8_BOM[0] && raw[startPos + 1] == UTF_8_BOM[1]
                    && raw[startPos + 2] == UTF_8_BOM[2]) {
                startPos += 3;
            }
            message = getString(raw, startPos, raw.length);
        } else {
            message = null;
        }

        // parse priority and version
        endPos = prioVersion.indexOf(">");
        final String priorityStr = prioVersion.substring(1, endPos);
        int priority = 0;
        try {
            priority = Integer.parseInt(priorityStr);
        } catch (NumberFormatException nfe) {
            LOGGER.error("Can't parse priority");
        }

        level = priority & 7;
        facility = (priority - level) >> 3;

        startPos = endPos + 1;
        int ver = 0;
        if (startPos < prioVersion.length()) {
            try {
                ver = Integer.parseInt(prioVersion.substring(startPos));
            } catch (NumberFormatException nfe) {
                LOGGER.error("Can't parse version");
                ver = -1;
            }
        }
        version = ver;
    }

    private String getString(byte[] data, int startPos, int endPos) {
        try {
            return new String(data, startPos, endPos - startPos, CHARSET);
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("Unsupported encoding", e);
        }
        return "";
    }

    /**
     * Try to find a character in given byte array, starting from startPos.
     *
     * @param data
     * @param startPos
     * @param c
     * @return position of the character or -1 if not found
     */
    private int searchChar(byte[] data, int startPos, char c) {
        for (int i = startPos; i < data.length; i++) {
            if (data[i] == c) {
                return i;
            }
        }
        return -1;
    }

    public String getPrioVersion() {
        return prioVersion;
    }

    public int getFacility() {
        return facility;
    }

    public int getLevel() {
        return level;
    }

    public int getVersion() {
        return version;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getHost() {
        return host;
    }

    public String getAppName() {
        return appName;
    }

    public String getProcId() {
        return procId;
    }

    public String getMsgId() {
        return msgId;
    }

    public String getStructuredData() {
        return structuredData;
    }

    public String getMessage() {
        return message;
    }

    public String getCharSet() {
        return CHARSET;
    }

    public byte[] getRaw() {
        return raw;
    }

    public Date getDate() {
        if (NIL.equals(timestamp)) {
            return null;
        }
        String fixTz = timestamp.replace("Z", "+00:00");
        final int tzSeparatorPos = fixTz.lastIndexOf(":");
        fixTz = fixTz.substring(0, tzSeparatorPos) + fixTz.substring(tzSeparatorPos + 1);
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSZ").parse(fixTz);
        } catch (ParseException e) {
            LOGGER.error("Unable to parse date " + timestamp, e);
        }
        return null;
    }

    public void setCharSet(String charSet) {
    }

    public void setFacility(int facility) {
    }

    public void setDate(Date date) {
    }

    public void setLevel(int level) {
    }

    public void setHost(String host) {
    }

    public void setMessage(String message) {
    }

    @Override
    public String toString() {
        return "Rfc5424SyslogEvent [prioVersion=" + prioVersion + ", facility=" + facility + ", level=" + level + ", version="
                + version + ", timestamp=" + timestamp + ", host=" + host + ", appName=" + appName + ", procId=" + procId
                + ", msgId=" + msgId + ", structuredData=" + structuredData + ", message=" + message + "]";
    }

}
