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

    double[] getWLSCoefficients(final RealMatrix masks, final RealVector pred, final int dim, final double fx,
        final RealVector weights) {

        System.out.println("mask dims: " + masks.getRowDimension() + ", " + masks.getColumnDimension());
        System.out.println("pred dim: " + pred.getDimension());
        System.out.println("weights dim: " + weights.getDimension());

        final RealVector y = pred.map(m_link);

        final RealVector lastNonZero = masks.getColumnVector(masks.getColumnDimension() - 1);

        final double linkedNullPrediction = m_linkedNullPrediction.getEntry(dim);

        final RealVector eyAdj = y.mapSubtractToSelf(linkedNullPrediction);

        final double linkedFx = m_link.value(fx);

        final RealVector eyAdj2 = eyAdj.subtract(lastNonZero.mapMultiply(linkedFx - linkedNullPrediction));

        RealMatrix etmp = masks.getSubMatrix(0, masks.getRowDimension() - 1, 0, masks.getColumnDimension() - 2);

        ShapMatrixUtils.subtractVec(etmp, lastNonZero);
        System.out.println("etmp shape: " + etmp.getRowDimension() + ", " + etmp.getColumnDimension());

        // X^T dot W where W is the diagonal matrix with the weights on the diagonal
//        RealMatrix xT = x.transpose();

        RealMatrix tmp = etmp.copy();
        System.out.println("tmp shape: " + tmp.getRowDimension() + ", " + tmp.getColumnDimension());

        ShapMatrixUtils.scaleVec(tmp, weights);

        RealMatrix tmp2 = MatrixUtils.inverse(tmp.transpose().multiply(etmp));

        System.out.println("tmp2 shape: " + tmp2.getRowDimension() + ", " + tmp2.getColumnDimension());

        RealVector w = tmp2.operate(tmp.transpose().operate(eyAdj2));

//        RealMatrix xTw = xT.multiply(MatrixUtils.createRealDiagonalMatrix(weights.toArray()));
//
//        RealMatrix inverse = MatrixUtils.inverse(xTw.multiply(x));

//        RealVector w = inverse.multiply(xTw).operate(eyAdj2);

        final double[] phi = new double[w.getDimension() + 1];

        double sumW = 0.0;
        for (int i = 0; i < w.getDimension(); i++) {
            final double e = w.getEntry(i);
            sumW += e;
            phi[i] = e;
        }
        phi[phi.length - 1] = linkedFx - linkedNullPrediction - sumW;

        return phi;
    }

}
