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

import java.util.Iterator;
import java.util.List;

import org.knime.base.node.meta.explain.feature.FeatureManager;
import org.knime.base.node.meta.explain.shap.node.ShapLoopEndSettings;
import org.knime.base.node.meta.explain.shap.node.ShapLoopStartSettings;
import org.knime.base.node.meta.explain.util.DefaultRandomDataGeneratorFactory;
import org.knime.base.node.meta.explain.util.RandomDataGeneratorFactory;
import org.knime.base.node.meta.explain.util.TablePreparer;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataTableSpecCreator;
import org.knime.core.data.RowIteratorBuilder;
import org.knime.core.data.RowKey;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
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

    private Iterator<DataRow> m_rowIterator;

    private int m_maxIterations;

    private int m_currentIteration;

    private final SampleToRow<ShapSample, RowKey> m_topTableSampleToRow = ShapPredictableSampleToRow.INSTANCE;

    private final SampleToRow<ShapSample, RowKey> m_bottomTableSampleToRow = ShapSurrogateTrainingSampleToRow.INSTANCE;

    private String m_weightColumnName;

    private double[] m_nullPredictions;

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
     * @return the maximal number of iteration of the LIME loop i.e. the number of rows in the ROI table
     */
    public int getMaxIterations() {
        return m_maxIterations;
    }

    /**
     * @return the current iteration of the LIME loop i.e. the index of the current row of interest
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
        m_settings = settings;
        m_featureTablePreparer.updateSpecs(roiSpec, settings.getFeatureCols());
        m_featureTablePreparer.checkSpec(samplingSpec);
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

    public DataTableSpec configureLoopEnd(final DataTableSpec predSpec, final ShapLoopEndSettings settings) {
        updateLoopEndSettings(settings);

        //TODO
        return null;
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
    public BufferedDataTable[] getNextTables(final BufferedDataTable roiTable, final BufferedDataTable samplingTable,
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
        final Iterator<ShapSample> sampleIterator = m_sampler.createSamples(m_rowIterator.next());
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

    private void initialize(final BufferedDataTable roiTable, final BufferedDataTable samplingTable,
        final ExecutionContext exec) throws Exception {
        m_maxIterations = (int)roiTable.size();
        m_currentIteration = 0;
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

    private void intializeNullPredictions(final BufferedDataTable samplingTable, final ExecutionMonitor monitor) {
        RowIteratorBuilder<? extends CloseableRowIterator> iterBuilder = samplingTable.iteratorBuilder();

    }

}
