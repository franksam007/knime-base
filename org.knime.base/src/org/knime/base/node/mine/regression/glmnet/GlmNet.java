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
 *   24.03.2019 (Adrian): created
 */
package org.knime.base.node.mine.regression.glmnet;

import org.knime.base.node.mine.regression.glmnet.cycle.FeatureCycle;
import org.knime.base.node.mine.regression.glmnet.cycle.FeatureCycleFactory;
import org.knime.base.node.mine.regression.glmnet.data.Data;
import org.knime.core.node.util.CheckUtils;

/**
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
final class GlmNet {

    private final float[] m_coefficients;

    private float m_intercept = 0.0f;

    private final Data m_data;

    private final float m_alpha;

    private float m_lambda;

    private final float m_epsilon;

    private final FeatureCycleFactory m_featureCycleFactory;

    private final int m_maxIterations;

    private Updater m_updater;

    /**
     *
     */
    public GlmNet(final Data data, final Updater updater, final float alpha, final float epsilon,
        final FeatureCycleFactory featureCycleFactory, final int maxIterations) {
        CheckUtils.checkNotNull(data);
        CheckUtils.checkNotNull(featureCycleFactory);
        CheckUtils.checkArgument(maxIterations > 0, "The maximal number of iterations must be positive.");
        CheckUtils.checkArgument(alpha >= 0 && alpha <= 1, "Alpha must be in the interval [0, 1].");
        CheckUtils.checkNotNull(updater);
        m_data = data;
        m_coefficients = new float[data.getNumFeatures()];
        m_alpha = alpha;
        m_epsilon = epsilon;
        m_maxIterations = maxIterations;
        m_featureCycleFactory = featureCycleFactory;
        m_updater = updater;
        calculateIntercept();
    }


    public LinearModel fit(final float lambda) {
        CheckUtils.checkArgument(lambda >= 0, "Lambda must be a non-negative value but was %s.", lambda);
        m_lambda = lambda;
        final FeatureCycle featureCycle = m_featureCycleFactory.create();
        for (int i = 0; i < m_maxIterations && featureCycle.hasNext(); i++) {
            final int featureIdx = featureCycle.next();
            performFeatureIteration(featureIdx);
        }
        return createSnapshot();
    }

    private LinearModel createSnapshot() {
        return new LinearModel(m_intercept, m_coefficients);
    }

    private void performFeatureIteration(final int featureIdx) {
        final float oldBeta = m_coefficients[featureIdx];
        final float newBeta = calculateUpdate(featureIdx);
        final float betaDiff = newBeta - oldBeta;
        if (abs(betaDiff) > m_epsilon) {
            m_coefficients[featureIdx] = newBeta;
            m_updater.betaChanged(m_data.getIterator(featureIdx), betaDiff);
        }
    }

    private float calculateUpdate(final int featureIdx) {
        // TODO go through formulas to ensure that this is the correct one
        final float grad = m_updater.calculateGradient(m_data.getIterator(featureIdx))
            +  m_coefficients[featureIdx];
        final float thresholded = softThresholding(grad, m_lambda * m_alpha);
        final float weightedSquaredMean = m_data.getWeightedSquaredMean(featureIdx);
        return thresholded / (weightedSquaredMean + m_lambda * (1 - m_alpha));
    }

    private static float softThresholding(final float z, final float gamma) {
        assert gamma >= 0;
        final float absZ = abs(z);
        if (z > 0 && gamma < absZ) {
            return z - gamma;
        } else if (z < 0 && gamma < absZ) {
            return z + gamma;
        } else {
            return 0;
        }
    }

    private void calculateIntercept() {
        float meanTarget = m_data.getWeightedMeanTarget();
        // TODO figure out where this notion of mean response comes from
//        float meanResponse = calculateMeanResponse();
        // for now we will use the mean target as eluded to in the paper
        final float delta = meanTarget - m_intercept;
        m_intercept = meanTarget;
        m_data.updateResidual(delta);
    }

    private void updateResidualsWithIntercept() {

    }

    /**
     * @return
     */
    private float calculateMeanResponse() {
        float meanResponse = 0;
        for (int i = 0; i < m_coefficients.length; i++) {
            meanResponse += m_data.getWeightedMean(i) * m_coefficients[i] / m_data.getWeightedStdv(i);
        }
        return meanResponse;
    }

    private static float abs(final float x) {
        return x < 0 ? -x : x;
    }

}
