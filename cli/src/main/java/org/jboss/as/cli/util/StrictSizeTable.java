/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.util;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 *
 * @author Alexey Loubyansky
 */
public class StrictSizeTable {

    private final int rowsTotal;
    private final Map<String,Column> columns = new LinkedHashMap<String,Column>();//HashMap<String,Column>();

    private int rowIndex = 0;
    private int totalWidth;

    public StrictSizeTable(int rowsTotal) {
        if(rowsTotal <= 0) {
            throw new IllegalArgumentException("The number of rows must be bigger than zero: " + rowsTotal);
        }
        this.rowsTotal = rowsTotal;
    }

    public boolean isAtLastRow() {
        return rowIndex == rowsTotal - 1;
    }
    public void nextRow() {
        if(rowIndex + 1 >= rowsTotal) {
            throw new IndexOutOfBoundsException("Row index exceeded table size " + rowsTotal + ": " + rowIndex);
        }
        ++rowIndex;
    }

    public void addCell(String header, String value) {
        checkNotNullParam("header", header);
        Column column = columns.get(header);
        if(column == null) {
            column = new Column(header);
            columns.put(header, column);
            column.maxWidth = Math.max(header.length() + 1, 4); // for n/a
            totalWidth += column.maxWidth;
        }
        if(value != null) {
            column.cells[rowIndex] = value;
            if(value.length() + 1 > column.maxWidth) {
                totalWidth -= column.maxWidth;
                column.maxWidth = value.length() + 1;
                totalWidth += column.maxWidth;
            }
        }
    }

    public String toString() {
        final StringBuilder buf = new StringBuilder((rowsTotal + 1)*totalWidth);
        int columnOffset = 0;
        Iterator<Column> iter = columns.values().iterator();
        while(iter.hasNext()) {
            final Column column = iter.next();
            if(buf.length() < columnOffset) {
                buf.setLength(columnOffset);
            }
            buf.insert(columnOffset, column.header.toUpperCase(Locale.ENGLISH));
            for(int i = column.header.length(); i < column.maxWidth; ++i) {
                buf.insert(columnOffset + i, ' ');
            }
            if(columnOffset == 0) {
                buf.insert(column.maxWidth, '\n');
            }
            for(int i = 0; i < rowsTotal; ++i) {
                final int offset = (i + 1)*(columnOffset + column.maxWidth + 1) + columnOffset;
                if(buf.length() < offset) {
                    buf.setLength(offset);
                }
                String value = column.cells[i];
                if(value == null) {
                    value = "n/a";
                }
                buf.insert(offset, value);
                for(int j = value.length(); j < column.maxWidth; ++j) {
                    buf.insert(offset + j, ' ');
                }
                if(columnOffset == 0) {
                    buf.insert(offset + column.maxWidth, '\n');
                }
            }
            columnOffset += column.maxWidth;
        }
        return buf.toString();
    }

    public boolean isEmpty() {
        return columns.isEmpty();
    }

    private class Column {
        final String header;
        final String[] cells = new String[rowsTotal];
        int maxWidth;

        Column(String header) {
            this.header = header;
        }
    }

    public static void main(String[] args) throws Exception {

        StrictSizeTable t = new StrictSizeTable(4);
        t.addCell("h1", "r1 h1");
        t.nextRow();
        t.addCell("h1", "r2 h1");
        t.addCell("h2", "r2 h2");
        t.nextRow();
        t.addCell("h2", "r3 h2");
        t.nextRow();
        t.addCell("h3", "r4 h3");
        System.out.println(t.toString());
    }
}
