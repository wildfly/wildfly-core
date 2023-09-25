/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.gui.component;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;
import java.util.Vector;
import javax.swing.DefaultCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

/**
 * A JTable that displays all deployments for standalone or domain.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class DeploymentTable extends JTable {

    private boolean isStandalone;

    public DeploymentTable(TableModel dm, boolean isStandalone) {
        super(dm);
        this.isStandalone = isStandalone;

        setRowHeight(30);
        setAutoCreateRowSorter(true);

        setDefaultRenderer(String.class,
                new TableCellRenderer() {
                    public Component getTableCellRendererComponent(JTable table,
                            Object value,
                            boolean isSelected,
                            boolean hasFocus,
                            int row,
                            int column) {
                        JLabel label = new JLabel((String)value);
                        JPanel panel = new JPanel(new BorderLayout());
                        panel.add(label, BorderLayout.CENTER);
                        return panel;
                    }
                });

        setDefaultRenderer(JRadioButton.class,
                new TableCellRenderer() {
                    public Component getTableCellRendererComponent(JTable table,
                            Object value,
                            boolean isSelected,
                            boolean hasFocus,
                            int row,
                            int column) {
                        return (JRadioButton) value;
                    }
                });

        setDefaultRenderer(List.class,
                new TableCellRenderer() {
                    public Component getTableCellRendererComponent(JTable table,
                            Object value,
                            boolean isSelected,
                            boolean hasFocus,
                            int row,
                            int column) {
                        List<String> values = (List<String>)value;
                        return new JComboBox(new Vector(values));
                    }
                });

        setDefaultEditor(JRadioButton.class, new RadioButtonEditor(new JCheckBox()));
        setDefaultEditor(List.class, new ComboBoxEditor());

        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(700, 200);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        if (column == 0) return true;
        if ((column == 2) && !isStandalone) return true;
        return false;
    }

    @Override
    public void editingStopped(ChangeEvent e) {
        super.editingStopped(e);
        repaint();
    }

    class ComboBoxEditor extends DefaultCellEditor {
        ComboBoxEditor() {
            super(new JComboBox());
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            return new JComboBox(new Vector((List)value));
        }
    }

    class RadioButtonEditor extends DefaultCellEditor implements ItemListener {

        private JRadioButton button;

        public RadioButtonEditor(JCheckBox checkBox) {
            super(checkBox);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            if (value == null)  return null;

            button = (JRadioButton) value;
            button.addItemListener(this);
            return (Component) value;
        }

        @Override
        public Object getCellEditorValue() {
            button.removeItemListener(this);
            return button;
        }

        public void itemStateChanged(ItemEvent e) {
            super.fireEditingStopped();
        }
    }
}
