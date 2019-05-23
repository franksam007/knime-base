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
import org.knime.base.node.meta.explain.ExplanationToDataRowConverter;
import org.knime.base.node.meta.explain.ExplanationToMultiRowConverter;
import org.knime.base.node.meta.explain.feature.FeatureManager;
import org.knime.base.node.meta.explain.shap.node.ShapLoopEndSettings;
import org.knime.base.node.meta.explain.shap.node.ShapLoopStartSettings;
import org.knime.base.node.meta.explain.util.DefaultRandomDataGeneratorFactory;
import org.knime.base.node.meta.explain.util.RandomDataGeneratorFactory;
import org.knime.base.node.meta.explain.util.TablePreparer;
import org.knime.base.node.meta.explain.util.iter.IntIterator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.RowKey;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.data.def.DefaultRow;
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

    /**
     *
     */
    private static final RowKey ROI_KEY = new RowKey("roi");

    private final TablePreparer m_featureTablePreparer;

    private TablePreparer m_predictionTablePreparer;

    private final FeatureManager m_featureManager;

    private ShapLoopStartSettings m_settings;

    private ShapLoopEndSettings m_endSettings;

    private ShapSampler m_sampler;

    private ShapWLS m_shapWLS;

    private Iterator<DataRow> m_rowIterator;

    private int m_maxIterations;

    private int m_currentIteration;

    private int m_samplingTableSize;

    private static final SampleToRow<ShapSample, RowKey> SAMPLE_TO_ROW = ShapPredictableSampleToRow.INSTANCE;

    private String m_weightColumnName;

    private String m_currentRoi;

    private double[] m_nullFx;

    private ExplanationToDataRowConverter m_explanationConverter;

    private BufferedDataContainer m_loopEndContainer;

    private final List<ShapSample> m_currentShapSamples = new ArrayList<>();

    /**
     * @param settings
     */
    public ShapExplainer(final ShapLoopStartSettings settings) {
        m_settings = settings;
        m_featureTablePreparer = new TablePreparer(settings.getFeatureCols(), "feature");
        m_featureManager = new FeatureManager(settings.isTreatAllColumnsAsSingleFeature(), false);
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
     * Resets the LimeExplainer to its state right after creation.
     */
    public void reset() {
        m_maxIterations = 0;
        m_currentIteration = -1;
        m_rowIterator = null;
        m_sampler = null;
        m_nullFx = null;
        m_shapWLS = null;
        m_currentRoi = null;
        if (m_loopEndContainer != null) {
            m_loopEndContainer.close();
        }
        m_loopEndContainer = null;
        m_currentShapSamples.clear();
    }

    /**
     * Performs the configuration of the loop start.
     *
     * @param roiSpec {@link DataTableSpec} of the ROI table
     * @param samplingSpec {@link DataTableSpec} of the sampling table
     * @param settings the node settings
     * @return the spec of the table that has to be predicted by the user's model
     * @throws InvalidSettingsException if any feature columns are missing from <b>samplingSpec</b>
     */
    public DataTableSpec configureLoopStart(final DataTableSpec roiSpec, final DataTableSpec samplingSpec,
        final ShapLoopStartSettings settings) throws InvalidSettingsException {
        updateSettings(roiSpec, settings);
        m_featureTablePreparer.checkSpec(samplingSpec);
        final DataTableSpec featureSpec = createInverseSpec();
        m_featureManager.updateWithSpec(featureSpec);
        return featureSpec;
    }

    /**
     * @param roiSpec
     * @param settings
     */
    private void updateSettings(final DataTableSpec roiSpec, final ShapLoopStartSettings settings) {
        m_settings = settings;
        m_featureTablePreparer.updateSpecs(roiSpec, settings.getFeatureCols());
    }

    private void updateExplanationConverter() {
        if (m_explanationConverter == null) {
            final DataTableSpec tableSpec = m_predictionTablePreparer.getTableSpec();
            m_explanationConverter = new ExplanationToMultiRowConverter(tableSpec.getColumnNames());
        }
    }

    public DataTableSpec configureLoopEnd(final DataTableSpec predSpec, final ShapLoopEndSettings settings) {
        updateLoopEndSettings(predSpec, settings);
        final Optional<List<String>> optionalFeatureNames = m_featureManager.getFeatureNames();
        if (!optionalFeatureNames.isPresent()) {
            // without feature names, we can't configure
            // can only happen if any collection column has no element names assigned
            // and the loop start has not been executed, yet
            return null;
        }
        return m_explanationConverter.createSpec(optionalFeatureNames.get());
    }

    public void consumePredictions(final BufferedDataTable predictedTable, final ExecutionContext exec)
        throws Exception {
        if (m_loopEndContainer == null) {
            // in the first iteration we only predicted the sampling table in order to obtain nullFx
            m_loopEndContainer =
                exec.createDataContainer(m_explanationConverter.createSpec(getFeatureNamesInLoopEnd()));
            initializeNullPredictions(predictedTable, exec);
        } else {
            explainCurrentRoi(predictedTable, exec);
        }
    }

    private void explainCurrentRoi(final BufferedDataTable predictedTable, final ExecutionContext exec)
        throws Exception {
        final CloseableRowIterator predictionIterator =
            m_predictionTablePreparer.createTable(predictedTable, exec.createSilentSubExecutionContext(0.0)).iterator();
        final double[] currentFx = extractCurrentFx(predictionIterator.next());
        final RealMatrix aggregatedPredictions = aggregatePredictionsPerSample(predictionIterator);
        predictionIterator.close();
        final RealVector shapWeights = getShapWeights();
        final RealMatrix masks = getMasks();
        final int numPredictions = aggregatedPredictions.getColumnDimension();
        final List<double[]> coeffsPerTarget = new ArrayList<>(numPredictions);
        for (int i = 0; i < numPredictions; i++) {
            coeffsPerTarget.add(m_shapWLS.getWLSCoefficients(masks, aggregatedPredictions.getColumnVector(i), i,
                currentFx[i], shapWeights));
        }
        final ShapExplanation explanation = new ShapExplanation(m_currentRoi, coeffsPerTarget);
        m_explanationConverter.convertAndWrite(explanation, m_loopEndContainer::addRowToTable);
    }

    private RealMatrix aggregatePredictionsPerSample(final CloseableRowIterator iter) throws Exception {
        final int numPredCols = m_predictionTablePreparer.getNumColumns();
        final int explanationSetSize = m_currentShapSamples.size();
        final double[][] aggregatedPredictions = new double[explanationSetSize][numPredCols];
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

        return MatrixUtils.createRealMatrix(aggregatedPredictions);
    }

    private static double[] extractCurrentFx(final DataRow row) {
        final double[] currentFx = new double[row.getNumCells()];
        for (int i = 0; i < currentFx.length; i++) {
            // The cast is safe because the prediction table contains only numeric columns
            currentFx[i] = ((DoubleValue)row.getCell(i)).getDoubleValue();
        }
        return currentFx;
    }

    private RealVector getShapWeights() {
        CheckUtils.checkState(!m_currentShapSamples.isEmpty(), "No samples for the current iteration stored.");
        final int nSamples = m_currentShapSamples.size();
        final RealVector weights = MatrixUtils.createRealVector(new double[nSamples]);
        final Iterator<ShapSample> iter = m_currentShapSamples.iterator();
        for (int i = 0; i < nSamples; i++) {
            weights.setEntry(i, iter.next().getWeight());
        }
        return weights;
    }

    private RealMatrix getMasks() {
        CheckUtils.checkState(!m_currentShapSamples.isEmpty(), "No samples for the current iteration stored.");
        int nSamples = m_currentShapSamples.size();
        final RealMatrix masks = MatrixUtils.createRealMatrix(nSamples, getNumberOfFeaturesLoopEnd());
        final Iterator<ShapSample> iter = m_currentShapSamples.iterator();
        for (int i = 0; i < nSamples; i++) {
            final ShapSample sample = iter.next();
            final IntIterator nonZeroIter = sample.getMask().iterator();
            while (nonZeroIter.hasNext()) {
                masks.setEntry(i, nonZeroIter.next(), 1.0);
            }
        }
        return masks;
    }

    /**
     * @return
     */
    private int getNumberOfFeaturesLoopEnd() {
        return m_featureManager.getNumFeatures().orElseThrow(() -> new IllegalStateException(
            "The number of features must be known during the execution of the loop end."));
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

    private static int getAsInt(final long val) {
        CheckUtils.checkArgument(val <= Integer.MAX_VALUE, "The provided value %s exceeds Integer.MAX_VALUE", val);
        CheckUtils.checkArgument(val >= Integer.MIN_VALUE, "The provided value %s is smaller than Integer.MIN_VALUE",
            val);
        return (int)val;
    }

    private void updateLoopEndSettings(final DataTableSpec predictionSpec, final ShapLoopEndSettings settings) {
        if (m_endSettings == null || !m_endSettings.equals(settings)) {
            // either we have no settings or the settings changed so we need to update
            m_predictionTablePreparer = new TablePreparer(settings.getPredictionCols(), "prediction");
            m_predictionTablePreparer.updateSpecs(predictionSpec, settings.getPredictionCols());
            m_featureManager.setUseElementNames(settings.isUseElementNames());
            updateExplanationConverter();
        }
        m_endSettings = settings;
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
    public BufferedDataTable executeLoopStart(final BufferedDataTable roiTable, final BufferedDataTable samplingTable,
        final ExecutionContext exec) throws Exception {
        m_currentIteration++;
        if (m_sampler == null) {
            initialize(roiTable, samplingTable, exec.createSubExecutionContext(0.2));
            // the first loop iteration predicts the sampling set to calculate the mean prediction nullFx
            return m_featureTablePreparer.createTable(samplingTable, exec.createSubExecutionContext(0.8));
        }
        return doNextIteration(exec);
    }

    private BufferedDataTable doNextIteration(final ExecutionContext exec) {
        CheckUtils.checkState(m_rowIterator.hasNext(),
            "This method must not be called if there are no more rows left to process.");
        final DataRow roi = m_rowIterator.next();
        m_currentRoi = roi.getKey().getString();
        m_currentShapSamples.clear();
        final Iterator<ShapSample> sampleIterator = m_sampler.createSamples(roi);
        final BufferedDataContainer container = exec.createDataContainer(createInverseSpec());
        // the first row in each iteration is the roi
        container.addRowToTable(new DefaultRow(ROI_KEY, roi));
        final long total = m_settings.getExplanationSetSize();
        final double progTotal = total;
        for (long i = 0; sampleIterator.hasNext(); i++) {
            final ShapSample sample = sampleIterator.next();
            final RowKey key = RowKey.createRowKey(i);
            SAMPLE_TO_ROW.write(sample, key, container::addRowToTable);
            m_currentShapSamples.add(sample);
            exec.setProgress(i / progTotal, "Created sample " + i + " of " + total);
        }
        container.close();
        return container.getTable();
    }

    private void initialize(final BufferedDataTable roiTable, final BufferedDataTable samplingTable,
        final ExecutionContext exec) throws Exception {
        // plus one because the first iteration predicts the sampling table
        m_maxIterations = (int)roiTable.size() + 1;
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
