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
 *   Apr 15, 2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.base.node.meta.explain.shap.node;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.util.filter.column.DataColumnSpecFilterConfiguration;

/**
 * TODO unify settings for Shapley Values and ModelExplainer
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
public class ShapLoopEndSettings implements ShapSettings {

    private static final String CFG_PREDICTION_COLUMNS = "predictionColumns";

    private static final String CFG_USE_ELEMENT_NAMES = "useElementNames";

    private boolean m_useElementNames = false;

    private DataColumnSpecFilterConfiguration m_predictionCols = createPredictionCols();


    @Override
    public void loadSettingsInDialog(final NodeSettingsRO settings, final DataTableSpec inSpec) {
        m_predictionCols.loadConfigurationInDialog(settings, inSpec);
        m_useElementNames = settings.getBoolean(CFG_USE_ELEMENT_NAMES, false);
    }

    @Override
    public void loadSettingsInModel(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_predictionCols.loadConfigurationInModel(settings);
        m_useElementNames = settings.getBoolean(CFG_USE_ELEMENT_NAMES);
    }

    @Override
    public void saveSettings(final NodeSettingsWO settings) {
        m_predictionCols.saveConfiguration(settings);
        settings.addBoolean(CFG_USE_ELEMENT_NAMES, m_useElementNames);
    }

    /**
     * @return the featureCols
     */
    public DataColumnSpecFilterConfiguration getPredictionCols() {
        return m_predictionCols;
    }

    /**
     * @param featureCols the featureCols to set
     */
    void setPredictionCols(final DataColumnSpecFilterConfiguration featureCols) {
        m_predictionCols = featureCols;
    }

    private static DataColumnSpecFilterConfiguration createPredictionCols() {
        return new DataColumnSpecFilterConfiguration(CFG_PREDICTION_COLUMNS);
    }

    /**
     * @return the dontUseElementNames
     */
    public boolean isUseElementNames() {
        return m_useElementNames;
    }

    void setUseElementNames(final boolean useElementNames) {
        m_useElementNames = useElementNames;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof ShapLoopEndSettings) {
            final ShapLoopEndSettings other = (ShapLoopEndSettings)obj;
            return this.m_predictionCols.equals(other.m_predictionCols)
                && this.m_useElementNames == other.m_useElementNames;
            // we don't care if the loading flag is the same as it's just an indicator
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(m_useElementNames);
        builder.append(m_predictionCols);
        return builder.toHashCode();
    }

}
