/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.gui.component;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * A text field and browse button as a single JPanel.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2014 Red Hat Inc.
 */
public class BrowsePanel extends JPanel {

    JTextField target = new JTextField(30);

    public BrowsePanel(JDialog parentDialog) {
        add(target);
        add(new BrowseButton(parentDialog, target));
    }

    public void setText(String text) {
        this.target.setText(text);
    }

    public String getText() {
        return "\"" + this.target.getText().replace("\\", "\\\\") + "\"";
    }

}
