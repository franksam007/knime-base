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
 *   May 21, 2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.base.node.meta.explain.shap;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

/**
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
final class ShapWLS {

    private final RealVector m_nullPredictions;

    private final RealVector m_linkedNullPrediction;

    private final UnivariateFunction m_link;

    ShapWLS(final RealVector nullPredictions, final UnivariateFunction link) {
        m_link = link;
        m_nullPredictions = nullPredictions;
        m_linkedNullPrediction = m_nullPredictions.map(link);
    }

    RealVector getWLSCoefficients(final RealMatrix masks, final RealVector pred, final int dim, final RealVector fx,
        final RealVector weights) {

        final RealVector y = pred.map(m_link);

        final RealVector lastNonZero = masks.getColumnVector(masks.getColumnDimension() - 1);

        final double linkedNullPrediction = m_linkedNullPrediction.getEntry(dim);

        final RealVector eyAdj = y.mapSubtractToSelf(linkedNullPrediction);

        RealVector linkedFx = fx.map(m_link);

        linkedFx = linkedFx.mapSubtractToSelf(linkedNullPrediction);

        final RealVector eyAdj2 = eyAdj.subtract(lastNonZero.ebeMultiply(linkedFx));

        RealMatrix adjMasks = masks.getSubMatrix(0, masks.getRowDimension() - 1, 0, masks.getColumnDimension() - 1);

        subtractVec(adjMasks, lastNonZero);

        // X^T dot W where W is the diagonal matrix with the weights on the diagonal
        RealMatrix xTw = adjMasks.copy();

        scaleRowWise(xTw, weights);

        RealMatrix inverse = MatrixUtils.inverse(xTw.multiply(adjMasks));

        RealVector w = inverse.multiply(xTw).operate(eyAdj2);

        return w;
    }

    private static void scaleRowWise(final RealMatrix matrix, final RealVector vec) {
        final int nRows = matrix.getRowDimension();
        final int nCols = matrix.getColumnDimension();
        assert vec.getDimension() == nRows;

        for (int r = 0; r < nRows; r++) {
            for (int c = 0; c < nCols; c++) {
                matrix.multiplyEntry(r, c, vec.getEntry(r));
            }
        }
    }

    /**
     * Subtracts the column vector vec from all columns of matrix. matrix and vec must have the same number of rows. The
     * operation is performed in-place i.e. it changes the values in matrix.
     *
     * @param matrix to subtract vec from
     * @param vec to subtract from matrix
     */
    private static void subtractVec(final RealMatrix matrix, final RealVector vec) {
        final int nRows = matrix.getRowDimension();
        final int nCols = matrix.getColumnDimension();
        assert nRows == vec.getDimension();
        for (int r = 0; r < nRows; r++) {
            for (int c = 0; c < nCols; c++) {
                final double v = matrix.getEntry(r, c);
                matrix.setEntry(r, c, v - vec.getEntry(r));
            }
        }

    }

}
