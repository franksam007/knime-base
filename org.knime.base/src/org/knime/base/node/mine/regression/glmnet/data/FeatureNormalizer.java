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
package org.knime.base.node.mine.regression.glmnet.data;

import org.knime.core.node.util.CheckUtils;

/**
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
public class FeatureNormalizer {

    private final boolean m_center;

    private final boolean m_scale;

    private float m_mean = 0.0f;

    private float m_stdv;

    private boolean m_normalized = false;

    private final int m_numRows;

    /**
     *
     */
    public FeatureNormalizer(final boolean center, final boolean scale, final int numRows) {
        CheckUtils.checkArgument(center || scale, "Using a FeatureNormalizer is superfluous if it is not used for normalization.");
        m_center = center;
        m_scale = scale;
        m_numRows = numRows;
    }


    public void acceptFeature(final float value) {
        CheckUtils.checkState(!m_normalized, "It is not allowed to add more values after the mean has been normalized.");
        m_mean += value;
    }

    private void normalizeMean() {
        m_mean /= m_numRows;
    }

    private void normalize(final ValueIterable featureIterable) {
        if (m_scale) {
            initStdv(featureIterable.iterator());
        }
        normalize(featureIterable.iterator());
    }

    private void normalize(final ValueIterator iter) {
        while (iter.hasNext()) {
            final float x = iter.next();
            // TODO figure out if this is actually a smart thing to do
        }
    }

    private void initStdv(final ValueIterator iter) {
        while (iter.hasNext()) {
            final float x = iter.next();
            final float diff = x - m_mean;
            m_stdv += diff * diff;
        }
        m_stdv /= m_numRows;
        m_stdv = (float)Math.sqrt(m_stdv);
    }

}
