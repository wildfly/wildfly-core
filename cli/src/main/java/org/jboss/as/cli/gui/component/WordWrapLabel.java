/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.gui.component;

import javax.swing.JLabel;

/**
 * This is a JLabel whose text will wrap at the given width.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class WordWrapLabel extends JLabel {

    private String text;
    private int width;

    public WordWrapLabel(String text, int width) {
        super(htmlText(text, width));
        this.text = text;
        this.width = width;
    }

    private static String htmlText(String text, int width) {
        return "<html><table><td width='" + width + "'>" + text + "</td></table></html>";
    }

    @Override
    public void setText(String text) {
        this.text = text;
        super.setText(htmlText(this.text, this.width));
    }

    @Override
    public String getText() {
        return this.text;
    }
}
