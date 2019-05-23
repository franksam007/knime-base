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
 *   May 10, 2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.base.node.meta.explain.shap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.knime.base.data.filter.column.FilterColumnTable;
import org.knime.base.node.meta.explain.ExplanationToDataRowConverter;
import org.knime.base.node.meta.explain.ExplanationToMultiRowConverter;
import org.knime.base.node.meta.explain.feature.FeatureManager;
import org.knime.base.node.meta.explain.shap.node.ShapLoopEndSettings;
import org.knime.base.node.meta.explain.shap.node.ShapLoopStartSettings;
import org.knime.base.node.meta.explain.util.DefaultRandomDataGeneratorFactory;
import org.knime.base.node.meta.explain.util.RandomDataGeneratorFactory;
import org.knime.base.node.meta.explain.util.TablePreparer;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTable;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataTableSpecCreator;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.RowKey;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.util.CheckUtils;

/**
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
public class ShapExplainer {

    private final TablePreparer m_featureTablePreparer;

    private final TablePreparer m_predictionTablePreparer;

    private final FeatureManager m_featureManager;

    private ShapLoopStartSettings m_settings;

    private ShapLoopEndSettings m_endSettings;

    private ShapSampler m_sampler;

    private ShapWLS m_shapWLS;

    private Iterator<DataRow> m_rowIterator;

    private CloseableRowIterator m_predictionIterator;

    private int m_maxIterations;

    private int m_currentIteration;

    private int m_samplingTableSize;

    private final SampleToRow<ShapSample, RowKey> m_topTableSampleToRow = ShapPredictableSampleToRow.INSTANCE;

    private final SampleToRow<ShapSample, RowKey> m_bottomTableSampleToRow = ShapSurrogateTrainingSampleToRow.INSTANCE;

    private String m_weightColumnName;

    private String m_currentRoi;

    private double[] m_nullFx;

    private double[] m_currentFx;

    private ExplanationToDataRowConverter m_explanationConverter;

    private BufferedDataContainer m_loopEndContainer;

    /**
     * @param settings
     */
    public ShapExplainer(final ShapLoopStartSettings settings) {
        m_settings = settings;
        m_featureTablePreparer = new TablePreparer(settings.getFeatureCols(), "feature");
        m_predictionTablePreparer = new TablePreparer(settings.getPredictionCols(), "prediction");
        // TODO add the treat collections as single feature flag to ShapSettings
        m_featureManager = new FeatureManager(false, m_settings.isDontUseElementNames());
    }

    /**
     * @return the name of the weight column (typically it will be 'weight' but this name might already be occupied by a
     *         column in the table)
     */
    public String getWeightColumnName() {
        return m_weightColumnName;
    }

    /**
     * @return the maximal number of iteration of the SHAP loop i.e. the number of rows in the ROI table
     */
    public int getMaxIterations() {
        return m_maxIterations;
    }

    /**
     * @return the current iteration of the SHAP loop i.e. the index of the current row of interest
     */
    public int getCurrentIteration() {
        return m_currentIteration;
    }

    /**
     * @return the number of predictions this SHAP loop explains
     */
    public int getNumberOfPredictions() {
        return m_predictionTablePreparer.getNumColumns();
    }

    public double getNullPrediction(final int idx) {
        CheckUtils.checkState(m_nullFx != null, "The null predictions have not been initialized, yet.");
        return m_nullFx[idx];
    }

    public double getCurrentPrediction(final int idx) {
        CheckUtils.checkState(m_currentFx != null, "The current predictions have not been initialized, yet");
        return m_currentFx[idx];
    }

    /**
     * Resets the LimeExplainer to its state right after creation.
     */
    public void reset() {
        m_maxIterations = 0;
        m_currentIteration = -1;
        m_rowIterator = null;
        m_sampler = null;
        if (m_predictionIterator != null) {
            m_predictionIterator.close();
        }
        m_predictionIterator = null;
        m_nullFx = null;
        m_currentFx = null;
        m_shapWLS = null;
        m_currentRoi = null;
        if (m_loopEndContainer != null) {
            m_loopEndContainer.close();
        }
        m_loopEndContainer = null;
    }

    /**
     * Performs the configuration of the loop start. The first output spec corresponds to the table that has to be
     * predicted by the user model i.e. it contains only the feature columns. The second output spec is for the table
     * used to train the surrogate model. It differs from the first one depending on the column types:
     * <ul>
     * <li>Nominal columns are represented as double column where a 1.0 indicates that the value matches the value of
     * the row of interest</li>
     * <li>Bit/Byte vector columns are split up into one double column per element. The values are also doubles sampled
     * according to the mean and std of the numerical features the elements represent.</li>
     * </ul>
     * Note that we can't create the second spec if vector columns are contained because we have no guarantee about the
     * number of elements a vector has.
     *
     * @param roiSpec {@link DataTableSpec} of the ROI table
     * @param samplingSpec {@link DataTableSpec} of the sampling table
     * @param settings the node settings
     * @return the spec of the table that has to be predicted by the user model and the spec of the table used to learn
     *         the surrogate model
     * @throws InvalidSettingsException if any feature columns are missing from <b>samplingSpec</b>
     */
    public DataTableSpec[] configureLoopStart(final DataTableSpec roiSpec, final DataTableSpec samplingSpec,
        final ShapLoopStartSettings settings) throws InvalidSettingsException {
        // TODO remove second output spec
        updateSettings(roiSpec, settings);
        m_featureTablePreparer.checkSpec(samplingSpec);
        m_predictionTablePreparer.checkSpec(samplingSpec);
        final DataTableSpec featureSpec = createInverseSpec();
        m_featureManager.updateWithSpec(featureSpec);
        final DataTableSpec[] specs = new DataTableSpec[2];
        specs[0] = featureSpec;
        if (!m_featureManager.containsCollection()) {
            // we can only create the spec during configure if no collections are contained
            // because there is no guarantee on the number of elements in a collection
            // in most cases the number of elements is likely to match the number of element names
            // in the column spec but it is possible that the number of elements doesn't match the
            // number of element names which would result in different specs during configuration and execution
            specs[1] = createDataSpec();
        }
        return specs;
    }

    /**
     * @param roiSpec
     * @param settings
     */
    private void updateSettings(final DataTableSpec roiSpec, final ShapLoopStartSettings settings) {
        m_settings = settings;
        m_featureTablePreparer.updateSpecs(roiSpec, settings.getFeatureCols());
        m_predictionTablePreparer.updateSpecs(roiSpec, m_settings.getPredictionCols());
        updateExplanationConverter();
    }

    private void updateExplanationConverter() {
        final DataTableSpec tableSpec = m_predictionTablePreparer.getTableSpec();
        m_explanationConverter = new ExplanationToMultiRowConverter(tableSpec.getColumnNames());
    }

    public DataTableSpec configureLoopEnd(final DataTableSpec predSpec, final DataTableSpec maskSpec,
        final ShapLoopEndSettings settings) {
        updateLoopEndSettings(settings);
        Optional<List<String>> optionalFeatureNames = m_featureManager.getFeatureNames();
        // TODO check if the mask table contains all the expected columns (one for the weight and one for each feature)
        if (!optionalFeatureNames.isPresent()) {
            // without feature names, we can't configure
            // can only happen if any collection column has no element names assigned
            // and the loop start has not been executed, yet
            return null;
        }
        return m_explanationConverter.createSpec(optionalFeatureNames.get());
    }

    public void consumePredictions(final BufferedDataTable predictedTable, final BufferedDataTable maskTable,
        final ExecutionContext exec) throws Exception {
        if (m_loopEndContainer == null) {
            m_loopEndContainer =
                exec.createDataContainer(m_explanationConverter.createSpec(getFeatureNamesInLoopEnd()));
        }
        final RealMatrix aggregatedPredictions = aggregatePredictionsPerSample(predictedTable, exec);
        final RealVector shapWeights = extractShapWeights(maskTable);
        final RealMatrix masks = extractMasks(maskTable);
        final int numPredictions = aggregatedPredictions.getColumnDimension();
        List<double[]> coeffsPerTarget = new ArrayList<>(numPredictions);
        for (int i = 0; i < numPredictions; i++) {
            coeffsPerTarget.add(m_shapWLS.getWLSCoefficients(masks, aggregatedPredictions.getColumnVector(i), i,
                m_currentFx[i], shapWeights));
        }
        final ShapExplanation explanation = new ShapExplanation(m_currentRoi, coeffsPerTarget);
        m_explanationConverter.convertAndWrite(explanation, m_loopEndContainer::addRowToTable);
    }

    /**
     * @return the table containing the Shapley Values
     */
    public BufferedDataTable getLoopEndTable() {
        CheckUtils.checkState(m_loopEndContainer != null,
            "The loop end container is null, this indicates a coding error.");
        CheckUtils.checkState(!m_loopEndContainer.isClosed(),
            "The loop end container is already closed, this indicates a coding error.");
        m_loopEndContainer.close();
        return m_loopEndContainer.getTable();
    }

    /**
     * @return
     */
    private List<String> getFeatureNamesInLoopEnd() {
        return m_featureManager.getFeatureNames().orElseThrow(() -> new IllegalStateException(
            "The feature names must be known after the first execution of the loop start node."));
    }

    private RealMatrix aggregatePredictionsPerSample(final BufferedDataTable predictedTable,
        final ExecutionContext exec) throws Exception {
        final int numPredCols = m_predictionTablePreparer.getNumColumns();

        assert predictedTable.size() % m_samplingTableSize == 0;
        final int explanationSetSize = getAsInt(predictedTable.size()) / m_samplingTableSize;
        final double[][] aggregatedPredictions = new double[explanationSetSize][numPredCols];
        try (final CloseableRowIterator iter =
            m_predictionTablePreparer.createTable(predictedTable, exec.createSilentSubExecutionContext(0)).iterator()) {
            int sampleIdx = -1;
            int i = 0;
            while (iter.hasNext()) {
                final DataRow row = iter.next();
                if (i % m_samplingTableSize == 0) {
                    sampleIdx++;
                }
                for (int j = 0; j < numPredCols; j++) {
                    // TODO add optional weighting
                    aggregatedPredictions[sampleIdx][j] +=
                        ((DoubleValue)row.getCell(j)).getDoubleValue() / m_samplingTableSize;
                }
                i++;
            }
        }

        return MatrixUtils.createRealMatrix(aggregatedPredictions);
    }

    private RealVector extractShapWeights(final BufferedDataTable maskTable) {
        final DataTableSpec spec = maskTable.getDataTableSpec();
        final DataColumnSpec weightColumnSpec = spec.getColumnSpec(m_weightColumnName);
        CheckUtils.checkArgument(weightColumnSpec != null, "The mask table must contain the weight column %s.",
            m_weightColumnName);
        CheckUtils.checkArgument(weightColumnSpec.getType().isCompatible(DoubleValue.class),
            "The weight column %s must be numeric.", m_weightColumnName);
        final DataTable weightTable = new FilterColumnTable(maskTable, m_weightColumnName);
        final double[] weights = new double[getAsInt(maskTable.size())];
        int i = 0;
        for (final DataRow row : weightTable) {
            weights[i] = ((DoubleValue)row.getCell(0)).getDoubleValue();
            i++;
        }
        return MatrixUtils.createRealVector(weights);
    }

    private RealMatrix extractMasks(final BufferedDataTable maskTable) {
        final DataTableSpec spec = maskTable.getDataTableSpec();
        checkMaskSpecContainsAllFeatures(spec);
        // TODO check the spec
        final List<String> featureNames =
            m_featureManager.getFeatureNames().orElseThrow(() -> new IllegalStateException(
                "The feature names must be known after the first execution of loop start."));
        int numFeatures = featureNames.size();
        final DataTable filtered = new FilterColumnTable(maskTable, featureNames.toArray(new String[numFeatures]));
        final RealMatrix masks = MatrixUtils.createRealMatrix(getAsInt(maskTable.size()), numFeatures);
        int i = 0;
        for (DataRow row : filtered) {
            for (int j = 0; j < numFeatures; j++) {
                masks.setEntry(i, j, ((DoubleValue)row.getCell(j)).getDoubleValue());
            }
            i++;
        }
        return masks;
    }

    private void checkMaskSpecContainsAllFeatures(final DataTableSpec maskTableSpec) {
        final List<String> featureNames =
            m_featureManager.getFeatureNames().orElseThrow(() -> new IllegalStateException(
                "The feature names must be known after the first execution of loop start."));
        for (final String name : featureNames) {
            final DataColumnSpec spec = maskTableSpec.getColumnSpec(name);
            CheckUtils.checkArgument(spec != null, "The mask table does not contain the feature %s.", name);
            CheckUtils.checkArgument(spec.getType().isCompatible(DoubleValue.class),
                "The feature column %s must be numeric.", name);
        }
    }

    private static int getAsInt(final long val) {
        CheckUtils.checkArgument(val <= Integer.MAX_VALUE, "The provided value %s exceeds Integer.MAX_VALUE", val);
        CheckUtils.checkArgument(val >= Integer.MIN_VALUE, "The provided value %s is smaller than Integer.MIN_VALUE",
            val);
        return (int)val;
    }

    private void updateLoopEndSettings(final ShapLoopEndSettings settings) {
        m_endSettings = settings;
    }

    private DataTableSpec createDataSpec() {
        final DataTableSpecCreator specCreator = new DataTableSpecCreator();
        final List<String> featureNames = m_featureManager.getFeatureNames().orElseThrow(
            () -> new IllegalStateException("The feature names are not known. This indicates an coding error."));
        for (String name : featureNames) {
            specCreator.addColumns(new DataColumnSpecCreator(name, DoubleCell.TYPE).createSpec());
        }
        final DataTableSpec featureNameSpec = specCreator.createSpec();
        m_weightColumnName = DataTableSpec.getUniqueColumnName(featureNameSpec, "weight");
        specCreator.addColumns(new DataColumnSpecCreator(m_weightColumnName, DoubleCell.TYPE).createSpec());
        return specCreator.createSpec();
    }

    private DataTableSpec createInverseSpec() {
        return m_featureTablePreparer.getTableSpec();
    }

    /**
     * @return true if the SHAP loop has more iterations i.e. there are more rows to explain
     */
    public boolean hasNextIteration() {
        return m_rowIterator.hasNext();
    }

    /**
     * Returns the tables for the next row of interest.
     *
     * @param roiTable the table containing the rows to explain (aka rows of interest)
     * @param samplingTable the table used for sampling (here we use it to extract feature statistics)
     * @param exec the execution context of the LIME loop start node
     * @return the table to predict with the user's model and the table for training the surrogate model
     * @throws Exception
     */
    public BufferedDataTable[] executeLoopStart(final BufferedDataTable roiTable, final BufferedDataTable samplingTable,
        final ExecutionContext exec) throws Exception {
        boolean isFirstIteration = false;
        if (m_sampler == null) {
            isFirstIteration = true;
            initialize(roiTable, samplingTable, exec.createSubExecutionContext(0.2));
        }
        m_currentIteration++;
        return doNextIteration(exec.createSubExecutionContext(isFirstIteration ? 0.8 : 1.0));
    }

    private BufferedDataTable[] doNextIteration(final ExecutionContext exec) {
        CheckUtils.checkState(m_rowIterator.hasNext(),
            "This method must not be called if there are no more rows left to process.");
        final DataRow roi = m_rowIterator.next();
        m_currentRoi = roi.getKey().getString();
        updateCurrentFx();
        final Iterator<ShapSample> sampleIterator = m_sampler.createSamples(roi);
        final BufferedDataContainer topContainer = exec.createDataContainer(createInverseSpec());
        final BufferedDataContainer dataContainer = exec.createDataContainer(createDataSpec());
        final long total = m_settings.getExplanationSetSize();
        final double progTotal = total;
        for (long i = 0; sampleIterator.hasNext(); i++) {
            final ShapSample sample = sampleIterator.next();
            final RowKey key = RowKey.createRowKey(i);
            m_topTableSampleToRow.write(sample, key, topContainer::addRowToTable);
            m_bottomTableSampleToRow.write(sample, key, dataContainer::addRowToTable);
            exec.setProgress(i / progTotal, "Created sample " + i + " of " + total);
        }
        topContainer.close();
        dataContainer.close();
        return new BufferedDataTable[]{topContainer.getTable(), dataContainer.getTable()};
    }

    private void updateCurrentFx() {
        CheckUtils.checkState(m_predictionIterator.hasNext(),
            "Not the same number of rows for predictions and features. This is a bug.");
        final DataRow fxs = m_predictionIterator.next();
        if (m_currentFx == null) {
            m_currentFx = new double[m_predictionTablePreparer.getNumColumns()];
        }
        for (int i = 0; i < m_currentFx.length; i++) {
            // the prediction iterator only contains double values
            m_currentFx[i] = ((DoubleValue)fxs.getCell(i)).getDoubleValue();
        }
    }

    private void initialize(final BufferedDataTable roiTable, final BufferedDataTable samplingTable,
        final ExecutionContext exec) throws Exception {
        initializeNullPredictions(samplingTable, exec.createSubExecutionContext(0.5));
        initializePredictionIterator(roiTable, exec);
        m_maxIterations = (int)roiTable.size();
        m_currentIteration = 0;
        m_samplingTableSize = getAsInt(samplingTable.size());
        final BufferedDataTable featureTable =
            m_featureTablePreparer.createTable(roiTable, exec.createSilentSubExecutionContext(0));
        final SubsetReplacer subsetReplacer = new SubsetReplacer(
            m_featureTablePreparer.createTable(samplingTable, exec), m_featureManager.createRowHandler());
        final int numFeatures = m_featureManager.getNumFeatures()
            .orElseThrow(() -> new IllegalStateException("The number of features must be known during execution."));
        final RandomDataGeneratorFactory rdgFactory = new DefaultRandomDataGeneratorFactory(m_settings.getSeed());
        m_sampler = new ShapSampler(subsetReplacer, new DefaultMaskFactory(numFeatures),
            m_settings.getExplanationSetSize(), numFeatures, rdgFactory);
        m_rowIterator = featureTable.iterator();
    }

    private void initializePredictionIterator(final BufferedDataTable roiTable, final ExecutionContext exec)
        throws Exception {
        m_predictionIterator =
            m_predictionTablePreparer.createTable(roiTable, exec.createSilentSubExecutionContext(0)).iterator();
    }

    private void initializeNullPredictions(final BufferedDataTable samplingTable, final ExecutionContext exec)
        throws Exception {
        // TODO add optional weighting
        final double size = samplingTable.size();
        long rowIdx = 1;
        m_nullFx = new double[m_predictionTablePreparer.getNumColumns()];
        try (final CloseableRowIterator iter =
            m_predictionTablePreparer.createTable(samplingTable, exec.createSilentSubExecutionContext(0)).iterator()) {
            while (iter.hasNext()) {
                exec.checkCanceled();
                DataRow row = iter.next();
                for (int i = 0; i < m_nullFx.length; i++) {
                    // the prediction table only contains columns that contain double values
                    m_nullFx[i] += ((DoubleValue)row.getCell(i)).getDoubleValue();
                }
                exec.setProgress(rowIdx / size);
            }
        }
        for (int i = 0; i < m_nullFx.length; i++) {
            m_nullFx[i] /= size;
        }

        // TODO allow different link functions e.g. logit
        m_shapWLS = new ShapWLS(new ArrayRealVector(m_nullFx), d -> d);
    }

}
