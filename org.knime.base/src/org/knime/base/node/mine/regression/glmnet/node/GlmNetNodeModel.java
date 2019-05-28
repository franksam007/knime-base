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
 *   26.05.2019 (Adrian): created
 */
package org.knime.base.node.mine.regression.glmnet.node;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;

import org.knime.base.node.mine.regression.glmnet.ElasticNet;
import org.knime.base.node.mine.regression.glmnet.ElasticNets;
import org.knime.base.node.mine.regression.glmnet.LinearModel;
import org.knime.base.node.mine.regression.glmnet.RegularizationPath;
import org.knime.base.node.mine.regression.glmnet.data.Data;
import org.knime.base.node.mine.regression.glmnet.data.ModularDataBuilder;
import org.knime.base.node.mine.regression.glmnet.lambda.LambdaSequence;
import org.knime.base.node.mine.regression.glmnet.lambda.LambdaSequences;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.RowKey;
import org.knime.core.data.container.ColumnRearranger;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelColumnFilter2;
import org.knime.core.node.defaultnodesettings.SettingsModelDoubleBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.util.filter.NameFilterConfiguration.FilterResult;

/**
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
final class GlmNetNodeModel extends NodeModel {

    /**
     *
     */
    private static final int DATA_PORT_IDX = 0;

    static SettingsModelColumnFilter2 createFeatureColumnsModel() {
        return new SettingsModelColumnFilter2("featureColumns", DoubleValue.class);
    }

    static SettingsModelString createTargetColumnModel() {
        return new SettingsModelString("targetColumn", null);
    }

    static SettingsModelString createWeightColumnModel() {
        return new SettingsModelString("weightColumn", null);
    }

    static SettingsModelIntegerBounded createMaxIterationsModel() {
        return new SettingsModelIntegerBounded("maxIterations", 1000, 1, Integer.MAX_VALUE);
    }

    static SettingsModelDoubleBounded createLambdaModel() {
        return new SettingsModelDoubleBounded("lambda", 0.01, 0, Double.MAX_VALUE);
    }

    static SettingsModelDoubleBounded createAlphaModel() {
        return new SettingsModelDoubleBounded("alpha", 0.5, 0.0, 1.0);
    }

    static SettingsModelIntegerBounded createRoundsModel() {
        return new SettingsModelIntegerBounded("rounds", 100, 1, Integer.MAX_VALUE);
    }

    static SettingsModelIntegerBounded createMaxActiveFeaturesModel() {
        return new SettingsModelIntegerBounded("maxActiveFeatures", 10, 1, Integer.MAX_VALUE);
    }

    static SettingsModelDoubleBounded createEpsilonModel() {
        return new SettingsModelDoubleBounded("epsilon", 1e-5, 0, 1.0);
    }

    private final SettingsModelColumnFilter2 m_featureColumns = createFeatureColumnsModel();

    private final SettingsModelString m_targetColumn = createTargetColumnModel();

    private final SettingsModelString m_weightColumn = createWeightColumnModel();

    private final SettingsModelIntegerBounded m_maxIterations = createMaxIterationsModel();

    private final SettingsModelIntegerBounded m_maxActiveFeatures = createMaxActiveFeaturesModel();

    private final SettingsModelIntegerBounded m_rounds = createRoundsModel();

    private final SettingsModelDoubleBounded m_lambda = createLambdaModel();

    private final SettingsModelDoubleBounded m_alpha = createAlphaModel();

    private final SettingsModelDoubleBounded m_epsilon = createEpsilonModel();

    private String[] m_featureNames;

    /**
     */
    public GlmNetNodeModel() {
        super(1, 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {
        final DataTableSpec inSpec = inSpecs[DATA_PORT_IDX];
        final FilterResult fr = m_featureColumns.applyTo(inSpec);
        m_featureNames = fr.getIncludes();
        return new DataTableSpec[]{createOutspec()};
    }

    private DataTableSpec createOutspec() {
        final String[] colNames =
            Stream.concat(Stream.of("Lambda", "Intercept"), Arrays.stream(m_featureNames)).toArray(String[]::new);
        final DataType[] colTypes =
            Stream.generate(() -> DoubleCell.TYPE).limit(colNames.length).toArray(DataType[]::new);
        return new DataTableSpec(colNames, colTypes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec)
        throws Exception {
        final BufferedDataTable table = inData[DATA_PORT_IDX];
        final Data data = createData(filterTable(table, exec.createSilentSubExecutionContext(0.0)));
        final double lambda = m_lambda.getDoubleValue();
        final double alpha = m_alpha.getDoubleValue();
        final double epsilon = m_epsilon.getDoubleValue();
        final LambdaSequence lambdas = LambdaSequences.lambdaMinLogScale(lambda, m_rounds.getIntValue(), alpha, data);
        final ElasticNet elasticNet = ElasticNets.createElasticNet(data, lambdas, alpha, epsilon,
            m_maxIterations.getIntValue(), m_maxActiveFeatures.getIntValue());
        elasticNet.fit();
        final RegularizationPath<LinearModel> path = elasticNet.getRegularizationPath();

        return new BufferedDataTable[]{write(path, exec.createDataContainer(createOutspec()))};
    }

    private static BufferedDataTable write(final RegularizationPath<LinearModel> path,
        final BufferedDataContainer container) {
        for (int i = 0; i < path.length(); i++) {
            container.addRowToTable(toRow(path.getLambda(i), path.getModel(i), i));
        }
        container.close();
        return container.getTable();
    }

    private static DataRow toRow(final double lambda, final LinearModel model, final long idx) {
        final double[] values = new double[model.getNumCoefficients() + 2];
        values[0] = lambda;
        values[1] = model.getIntercept();
        for (int i = 2; i < values.length; i++) {
            values[i] = model.getCoefficient(i - 2);
        }
        return new DefaultRow(RowKey.createRowKey(idx), values);
    }

    private Data createData(final BufferedDataTable table) {
        final DataTableSpec tableSpec = table.getDataTableSpec();
        final int targetIdx = tableSpec.findColumnIndex(m_targetColumn.getStringValue());
        CheckUtils.checkState(targetIdx >= 0, "Can't find the target column %s.", m_targetColumn.getStringValue());
        final int weightIdx = tableSpec.findColumnIndex(m_weightColumn.getStringValue());
        final ModularDataBuilder builder = new ModularDataBuilder(table, targetIdx, weightIdx);
        return builder.build();
    }

    private BufferedDataTable filterTable(final BufferedDataTable table, final ExecutionContext exec)
        throws CanceledExecutionException {
        final DataTableSpec tableSpec = table.getDataTableSpec();
        final FilterResult fr = m_featureColumns.applyTo(tableSpec);
        Stream<String> relevantCols =
            Stream.concat(Stream.of(m_targetColumn.getStringValue()), Arrays.stream(fr.getIncludes()));
        if (tableSpec.findColumnIndex(m_weightColumn.getStringValue()) >= 0) {
            relevantCols = Stream.concat(Stream.of(m_weightColumn.getStringValue()), relevantCols);
        }
        final ColumnRearranger cr = new ColumnRearranger(tableSpec);
        cr.keepOnly(relevantCols.toArray(String[]::new));
        return exec.createColumnRearrangeTable(table, cr, exec);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // nothing to load
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // nothing to save
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_featureColumns.saveSettingsTo(settings);
        m_targetColumn.saveSettingsTo(settings);
        m_weightColumn.saveSettingsTo(settings);
        m_maxIterations.saveSettingsTo(settings);
        m_maxActiveFeatures.saveSettingsTo(settings);
        m_rounds.saveSettingsTo(settings);
        m_lambda.saveSettingsTo(settings);
        m_alpha.saveSettingsTo(settings);
        m_epsilon.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_featureColumns.validateSettings(settings);
        m_targetColumn.validateSettings(settings);
        m_weightColumn.validateSettings(settings);
        m_maxIterations.validateSettings(settings);
        m_maxActiveFeatures.validateSettings(settings);
        m_rounds.validateSettings(settings);
        m_lambda.validateSettings(settings);
        m_alpha.validateSettings(settings);
        m_epsilon.validateSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_featureColumns.loadSettingsFrom(settings);
        m_targetColumn.loadSettingsFrom(settings);
        m_weightColumn.loadSettingsFrom(settings);
        m_maxIterations.loadSettingsFrom(settings);
        m_maxActiveFeatures.loadSettingsFrom(settings);
        m_rounds.loadSettingsFrom(settings);
        m_lambda.loadSettingsFrom(settings);
        m_alpha.loadSettingsFrom(settings);
        m_epsilon.loadSettingsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // nothing to reset

    }

}
