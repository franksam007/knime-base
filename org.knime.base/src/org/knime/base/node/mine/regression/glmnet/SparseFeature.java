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
 *   31.03.2019 (Adrian): created
 */
package org.knime.base.node.mine.regression.glmnet;

/**
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
final class SparseFeature implements Feature {

    private final int[] m_nonZeroIndices;
    private final ValueHolder m_values;

    /**
     *
     */
    public SparseFeature(final int[] nonZeroIndices, final ValueHolder values) {
        assert nonZeroIndices.length == values.size();
        m_nonZeroIndices = nonZeroIndices;
        m_values = values;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void scale(final float scale) {
        m_values.scale(scale);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FeatureIterator getIterator() {
        return new SparseFeatureIterator();
    }

    private class SparseFeatureIterator extends AbstractFeatureIterator {

        /**
         * @param numNonZeroRows
         */
        public SparseFeatureIterator() {
            super(m_nonZeroIndices.length);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getRowIdx() {
            return m_nonZeroIndices[m_idx];
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public float getValue() {
            return m_values.get(m_idx);
        }

    }

    interface ValueHolder {
        float get(final int idx);
        void scale(final float scale);

        int size();
    }

    static final class ArrayValueHolder implements ValueHolder {
        private final float[] m_values;

        /**
         *
         */
        ArrayValueHolder(final float[] values) {
            m_values = values.clone();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public float get(final int idx) {
            return m_values[idx];
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int size() {
            return m_values.length;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void scale(final float scale) {
            for (int i = 0; i < m_values.length; i++) {
                m_values[i] *= scale;
            }
        }
    }

    static final class ConstantValueHolder implements ValueHolder {
        private float m_value;
        private final int m_numValues;

        /**
         *
         */
        public ConstantValueHolder(final float value, final int numValues) {
            m_value = value;
            m_numValues = numValues;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public float get(final int idx) {
            if (idx < 0 || idx >= m_numValues) {
                throw new IndexOutOfBoundsException();
            }
            return m_value;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int size() {
            return m_numValues;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void scale(final float scale) {
            m_value *= scale;
        }


    }

}
