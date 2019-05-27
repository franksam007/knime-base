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
 *   May 24, 2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.base.node.meta.explain.shap;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.knime.base.node.meta.explain.CountingDataContainer;
import org.knime.base.node.meta.explain.ExplanationToDataCellsConverter;
import org.knime.base.node.meta.explain.ExplanationToMultiRowConverter;
import org.knime.base.node.meta.explain.shap.node.ShapLoopEndSettings;
import org.knime.base.node.meta.explain.shap.node.ShapLoopEndSettings.PredictionColumnSelectionMode;
import org.knime.base.node.meta.explain.util.MissingColumnException;
import org.knime.base.node.meta.explain.util.TablePreparer;
import org.knime.base.node.meta.explain.util.iter.DoubleIterable;
import org.knime.base.node.meta.explain.util.iter.DoubleIterator;
import org.knime.base.node.meta.explain.util.iter.IntIterator;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.data.container.ColumnRearranger;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.util.CheckUtils;

/**
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
final class ShapPredictionConsumer {

    private List<String> m_featureNames;

    private TablePreparer m_predictionTablePreparer;

    private ShapWLS m_shapWLS;

    private CountingDataContainer m_loopEndContainer;

    private ExplanationToDataCellsConverter m_explanationConverter;

    private DoubleIterable m_samplingWeights;

    private int m_samplingSetSize;

    void consumePredictions(final BufferedDataTable predictedTable, final ShapIteration shapIteration,
        final ExecutionContext exec) throws Exception {
        if (m_loopEndContainer == null) {
            // in the first iteration we only predicted the sampling table in order to obtain nullFx
            m_loopEndContainer =
                new CountingDataContainer(exec.createDataContainer(m_explanationConverter.createSpec(m_featureNames)));
            initializeNullPredictions(predictedTable, exec);
        } else {
            CheckUtils.checkArgument(shapIteration != null,
                "The shapIteration must not be null from the second iteration on.");
            explainCurrentRoi(predictedTable, shapIteration, exec);
        }
    }

    DataTableSpec getExplanationSpec() {
        if (m_featureNames != null) {
            return m_explanationConverter.createSpec(m_featureNames);
        } else {
            // without feature names, we can't configure
            // can only happen if any collection column has no element names assigned
            // and the loop start has not been executed, yet
            return null;
        }
    }

    void setSamplingWeights(final DoubleIterable samplingWeights) {
        m_samplingWeights = samplingWeights;
    }

    BufferedDataTable getExplanationTable() {
        CheckUtils.checkState(m_loopEndContainer != null,
            "The loop end container is null, this indicates a coding error.");
        return m_loopEndContainer.getTable();
    }

    void updateSettings(final DataTableSpec tableSpec, final ShapLoopEndSettings settings,
        final List<String> featureNames, final DataTableSpec featureSpec) {
        m_featureNames = featureNames;
        updatePredictionTablePreparer(tableSpec, settings, featureSpec);
        m_explanationConverter =
            new ExplanationToMultiRowConverter(m_predictionTablePreparer.getTableSpec().getColumnNames());
    }

    void reset() {
        if (m_loopEndContainer != null) {
            m_loopEndContainer.close();
        }
        m_loopEndContainer = null;
        m_explanationConverter = null;
    }

    private void explainCurrentRoi(final BufferedDataTable predictedTable, final ShapIteration shapIteration,
        final ExecutionContext exec)
        throws InvalidSettingsException, CanceledExecutionException, MissingColumnException {
        final double[] currentFx;
        final RealMatrix aggregatedPredictions;
        try (final CloseableRowIterator predictionIterator = createPredictionIterator(predictedTable, exec)) {
            currentFx = extractCurrentFx(predictionIterator.next());
            aggregatedPredictions =
                aggregatePredictionsPerSample(predictionIterator, shapIteration.getNumberOfSamples());
        }
        final RealVector shapWeights = MatrixUtils.createRealVector(shapIteration.getWeights());
        final RealMatrix masks = toMatrix(shapIteration.getMasks());
        final int numPredictions = aggregatedPredictions.getColumnDimension();
        final List<double[]> coeffsPerTarget = new ArrayList<>(numPredictions);
        for (int i = 0; i < numPredictions; i++) {
            coeffsPerTarget.add(m_shapWLS.getWLSCoefficients(masks, aggregatedPredictions.getColumnVector(i), i,
                currentFx[i], shapWeights));
        }
        final ShapExplanation explanation = new ShapExplanation(shapIteration.getRoiKey(), coeffsPerTarget);
        m_explanationConverter.convertAndWrite(explanation, m_loopEndContainer);
    }

    private RealMatrix aggregatePredictionsPerSample(final CloseableRowIterator iter, final int nSamples) {
        CheckUtils.checkState(m_samplingWeights != null, "No sampling weights set.");
        final int numPredCols = m_predictionTablePreparer.getNumColumns();
        final double[][] aggregatedPredictions = new double[nSamples][numPredCols];
        int sampleIdx = -1;
        int i = 0;
        final DoubleIterator weightIter = m_samplingWeights.iterator();
        while (iter.hasNext()) {
            assert weightIter.hasNext() : "The number of weights does not match the size of the sampling table.";
            final DataRow row = iter.next();
            final double weight = weightIter.next();
            if (i % m_samplingSetSize == 0) {
                sampleIdx++;
            }
            for (int j = 0; j < numPredCols; j++) {
                aggregatedPredictions[sampleIdx][j] +=
                    ((DoubleValue)row.getCell(j)).getDoubleValue() * weight;
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

    private static RealMatrix toMatrix(final Mask[] masks) {
        int nSamples = masks.length;
        assert masks.length > 0;
        int numFeatures = masks[0].getNumberOfFeatures();
        final RealMatrix matrix = MatrixUtils.createRealMatrix(nSamples, numFeatures);
        for (int i = 0; i < nSamples; i++) {
            final IntIterator nonZeroIter = masks[i].iterator();
            while (nonZeroIter.hasNext()) {
                matrix.setEntry(i, nonZeroIter.next(), 1.0);
            }
        }
        return matrix;
    }

    private void updatePredictionTablePreparer(final DataTableSpec predictionSpec, final ShapLoopEndSettings settings,
        final DataTableSpec featureSpec) {
        if (settings.getPredictionColumnSelectionMode() == PredictionColumnSelectionMode.AUTOMATIC) {
            final DataTableSpec predictionCols = determinePredictionColumns(predictionSpec, featureSpec);
            m_predictionTablePreparer = new TablePreparer(predictionCols, "prediction");
        } else {
            m_predictionTablePreparer = new TablePreparer(settings.getPredictionCols(), "prediction");
            m_predictionTablePreparer.updateSpecs(predictionSpec, settings.getPredictionCols());
        }
    }

    private static DataTableSpec determinePredictionColumns(final DataTableSpec predictionSpec,
        final DataTableSpec featureSpec) {
        final ColumnRearranger cr = new ColumnRearranger(predictionSpec);
        cr.remove(featureSpec.getColumnNames());
        final DataTableSpec initialPredictionSpec = cr.createSpec();
        for (DataColumnSpec potentialPredictionColumn : initialPredictionSpec) {
            if (!potentialPredictionColumn.getType().isCompatible(DoubleValue.class)) {
                cr.remove(potentialPredictionColumn.getName());
            }
        }
        return cr.createSpec();
    }

    private static int getAsInt(final long val) {
        CheckUtils.checkArgument(val <= Integer.MAX_VALUE, "The provided value %s exceeds Integer.MAX_VALUE", val);
        CheckUtils.checkArgument(val >= Integer.MIN_VALUE, "The provided value %s is smaller than Integer.MIN_VALUE",
            val);
        return (int)val;
    }

    private void initializeNullPredictions(final BufferedDataTable predictionTable, final ExecutionContext exec)
        throws InvalidSettingsException, CanceledExecutionException, MissingColumnException {
        // TODO add optional weighting
        final double size = predictionTable.size();
        m_samplingSetSize = getAsInt(predictionTable.size());

        long rowIdx = 1;
        final double[] nullFx = new double[m_predictionTablePreparer.getNumColumns()];
        try (final CloseableRowIterator iter = createPredictionIterator(predictionTable, exec)) {
            while (iter.hasNext()) {
                exec.checkCanceled();
                DataRow row = iter.next();
                for (int i = 0; i < nullFx.length; i++) {
                    // the prediction table only contains columns that contain double values
                    nullFx[i] += ((DoubleValue)row.getCell(i)).getDoubleValue();
                }
                exec.setProgress(rowIdx / size);
            }
        }
        for (int i = 0; i < nullFx.length; i++) {
            nullFx[i] /= size;
        }

        // TODO allow different link functions e.g. logit
        m_shapWLS = new ShapWLS(MatrixUtils.createRealVector(nullFx), d -> d);
    }

    private CloseableRowIterator createPredictionIterator(final BufferedDataTable samplingTable,
        final ExecutionContext exec)
        throws InvalidSettingsException, CanceledExecutionException, MissingColumnException {
        try {
            return m_predictionTablePreparer.createTable(samplingTable, exec.createSilentSubExecutionContext(0))
                .iterator();
        } catch (MissingColumnException e) {
            throw new InvalidSettingsException(
                "The prediction table is missing the columm '" + e.getMissingColumn() + "'.");
        }
    }

}
