/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   14.03.2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.base.node.meta.explain.shap.node;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.knime.base.node.meta.explain.shap.node.ShapLoopEndSettings.PredictionColumnSelectionMode;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.util.filter.column.DataColumnSpecFilterPanel;

/**
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
class EndOptionsDialog implements OptionsDialog<ShapLoopEndSettings> {

    private final DataColumnSpecFilterPanel m_predictionColumns = new DataColumnSpecFilterPanel();

    private final JCheckBox m_useElementNames = new JCheckBox("Use element names for collection features");

    private final JRadioButton m_automaticColumnSelection =
        new JRadioButton("All numeric non-feature columns are prediction columns");

    private final JRadioButton m_manualColumnSelection = new JRadioButton("Manually select prediction columns");

    EndOptionsDialog() {
        ButtonGroup group = new ButtonGroup();
        group.add(m_automaticColumnSelection);
        group.add(m_manualColumnSelection);

        m_automaticColumnSelection
            .addActionListener(e -> reactToModeChange());
        m_manualColumnSelection.addActionListener(e -> reactToModeChange());
    }

    /**
     *
     */
    private void reactToModeChange() {
        m_predictionColumns.setEnabled(m_manualColumnSelection.isSelected());
    }

    @Override
    public JPanel getPanel() {
        // === Options Tab ===

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createGbc();
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(createPredictionColumnsPanel(), gbc);

        gbc.gridy++;
        gbc.weighty = 0;
        gbc.weightx = 0;
        panel.add(createOutputOptionsPanel(), gbc);

        return panel;
    }

    private JPanel createPredictionColumnsPanel() {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Prediction columns"));
        panel.add(m_automaticColumnSelection);
        panel.add(m_manualColumnSelection);
        panel.add(m_predictionColumns);
        return panel;
    }

    private JPanel createOutputOptionsPanel() {
        final JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder("Output options"));
        panel.add(m_useElementNames);
        return panel;
    }

    private static GridBagConstraints createGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        return gbc;
    }

    @Override
    public void saveSettingsTo(final ShapLoopEndSettings cfg) throws InvalidSettingsException {
        m_predictionColumns.saveConfiguration(cfg.getPredictionCols());
        cfg.setUseElementNames(m_useElementNames.isSelected());
        cfg.setPredictionColumnSelectionMode(m_automaticColumnSelection.isSelected()
            ? PredictionColumnSelectionMode.AUTOMATIC : PredictionColumnSelectionMode.MANUAL);
    }

    @Override
    public void loadSettingsFrom(final ShapLoopEndSettings cfg, final DataTableSpec inSpec) {
        m_predictionColumns.loadConfiguration(cfg.getPredictionCols(), inSpec);
        m_useElementNames.setSelected(cfg.isUseElementNames());
        final boolean isAutomatic = cfg.getPredictionColumnSelectionMode() == PredictionColumnSelectionMode.AUTOMATIC;
        m_automaticColumnSelection.setSelected(isAutomatic);
        reactToModeChange();
    }

}
