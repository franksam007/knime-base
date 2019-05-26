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
 *   23.03.2019 (Adrian): created
 */
package org.knime.base.node.mine.regression.glmnet.data;

import java.util.Arrays;

import org.knime.base.node.mine.regression.glmnet.data.Feature.FeatureIterator;
import org.knime.core.node.util.CheckUtils;

/**
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
public final class DefaultData implements Data {

    private final Feature[] m_features;

    private final float[] m_target;

    private final float[] m_residuals;

    private final WeightContainer m_weights;

    private final int m_numFeatures;

    private final float[] m_weightedStdv;

    private final float[] m_weightedScaledMeans;

    private final float[] m_weightedSquaredMeans;

    private float m_weightedMeanTarget;

    private float m_totalWeightedResidual;

    private final FeatureTargetProducts m_innerFeatureTargetProducts;

    /**
     *
     */
    DefaultData(final Feature[] features, final float[] target, final WeightContainer weights) {
        CheckUtils.checkNotNull(weights);
        CheckUtils.checkNotNull(features);
        m_features = features.clone();
        final int numFeatures = features.length;
        m_target = target;
        m_residuals = target.clone();
        m_numFeatures = numFeatures;
        m_weightedStdv = new float[numFeatures];
        m_weightedScaledMeans = new float[numFeatures];
        m_weightedSquaredMeans = new float[numFeatures];
        m_weights = weights;
        calculateWeightedFeatureStatistics();
        calculateWeightedMeanTarget();
        scaleFeatures();
        m_innerFeatureTargetProducts = new FeatureTargetProducts(this);
    }

    private void scaleFeatures() {
        for (int i = 0; i < m_features.length; i++) {
            m_features[i].scale(1.0f / m_weightedStdv[i]);
        }
    }

    private void calculateWeightedFeatureStatistics() {
        Arrays.fill(m_weightedStdv, 0);
        for (int f = 0; f < m_numFeatures; f++) {
            calculateStats(f);
        }
    }

    private void calculateStats(final int f) {
        final Feature feature = m_features[f];
        float wm = 0;
        float wsm = 0;
        final FeatureIterator iter = feature.getIterator();
        while (iter.next()) {
            final float value = iter.getValue();
            final float weight = m_weights.get(iter.getRowIdx());
            wm += weight * value;
            wsm += weight * value * value;
        }
        final float variance = wsm - wm * wm;
        final float std = (float)Math.sqrt(variance);
        assert std > 0 : "Zero standard deviation detected. This can only happen for constant columns.";
        m_weightedScaledMeans[f] = wm / std;
        m_weightedSquaredMeans[f] = (wsm - wm) / (std * std);
        m_weightedStdv[f] = std;
    }

    private void calculateWeightedMeanTarget() {
        m_weightedMeanTarget = 0F;
        for (int i = 0; i < getNumRows(); i++) {
            m_weightedMeanTarget += m_target[i] * m_weights.get(i);
        }
        m_weightedMeanTarget /= m_weights.getTotal();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumRows() {
        return m_target.length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumFeatures() {
        return m_numFeatures;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getTotalWeight() {
        return m_weights.getTotal();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getWeightedStdv(final int featureIdx) {
        return m_weightedStdv[featureIdx];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getWeightedSquaredMean(final int featureIdx) {
        return m_weightedSquaredMeans[featureIdx];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataIterator getIterator(final int featureIdx) {
        return new DefaultDataIterator(m_features[featureIdx].getIterator(), m_weightedScaledMeans[featureIdx]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getWeightedMean(final int featureIdx) {
        return m_weightedScaledMeans[featureIdx];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getWeightedMeanTarget() {
        return m_weightedMeanTarget;
    }

    private class DefaultDataIterator implements DataIterator {
        private final FeatureIterator m_featureIterator;
        private final float m_featureMean;

        public DefaultDataIterator(final FeatureIterator featureIterator, final float featureMean) {
            m_featureIterator = featureIterator;
            m_featureMean = featureMean;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean next() {
            return m_featureIterator.next();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public float getWeight() {
            return m_weights.get(m_featureIterator.getRowIdx());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public float getFeature() {
            return m_featureIterator.getValue();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public float getTarget() {
            return m_target[m_featureIterator.getRowIdx()];
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public float getResidual() {
            return m_residuals[m_featureIterator.getRowIdx()];
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setResidual(final float value) {
            final float oldResidual = getResidual();
            final float diff = oldResidual - value;
            // TODO verify correctness
            m_totalWeightedResidual += getWeight() * diff;
            m_residuals[m_featureIterator.getRowIdx()] = value;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public float getWeightedResidual() {
            return getWeight() * getResidual();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public float getFeatureMean() {
            return m_featureMean;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public float getTotalWeightedResidual() {
            return m_totalWeightedResidual;
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getWeightedInnerFeatureTargetProduct(final int featureIdx) {
        return m_innerFeatureTargetProducts.getFeatureTargetProduct(featureIdx);
    }

}
