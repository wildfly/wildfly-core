/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.gui;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.border.LineBorder;
import javax.swing.text.JTextComponent;

/**
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class CommandLine extends JPanel {
    private static final String SUBMIT_ACTION = "submit-action";

    private JTextArea cmdText = new JTextArea();
    private JCheckBox isVerbose = new JCheckBox("Verbose");
    private JButton submitButton = new JButton("Submit");

    public CommandLine(DoOperationActionListener opListener) {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0,0,0,5);
        add(new JLabel("cmd>"), gbc);

        cmdText.setBorder(new LineBorder(Color.BLACK));
        cmdText.setText("/");
        cmdText.setLineWrap(true);
        cmdText.setRows(1);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 100.0;
        add(cmdText, gbc);

        KeyStroke enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false);
        cmdText.getInputMap().put(enterKey, SUBMIT_ACTION);
        cmdText.getActionMap().put(SUBMIT_ACTION, opListener);

        JPanel submitPanel = new JPanel(new GridLayout(2,1));
        submitButton.addActionListener(opListener);
        submitButton.setToolTipText("Submit the command to the server.");
        submitPanel.add(submitButton);

        isVerbose.setToolTipText("Show the command's DMR request.");
        submitPanel.add(isVerbose);

        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.SOUTHEAST;
        gbc.weightx = 1.0;
        add(submitPanel, gbc);
    }

    public boolean isVerbose() {
        return isVerbose.isSelected();
    }

    public JTextComponent getCmdText() {
        return this.cmdText;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        submitButton.setEnabled(enabled);
        cmdText.setEnabled(enabled);
    }

}
