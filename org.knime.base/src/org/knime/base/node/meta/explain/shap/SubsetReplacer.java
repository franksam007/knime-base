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
 *   May 9, 2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.base.node.meta.explain.shap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.knime.base.node.meta.explain.feature.RowHandler;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
import org.knime.core.node.util.CheckUtils;

/**
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
final class SubsetReplacer {

    private final Iterable<DataRow> m_samplingSet;

    private final RowHandler m_rowHandler;

    SubsetReplacer(final Iterable<DataRow> samplingSet, final RowHandler rowHandler) {
        Iterator<DataRow> iter = samplingSet.iterator();
        CheckUtils.checkArgument(iter.hasNext(), "The sampling set must contain at least one row.");
        CheckUtils.checkArgument(iter.next().getNumCells() == rowHandler.getExpectedNumberOfCells(),
            "The number of cells in the sampling rows must match the number of feature handlers.");
        m_samplingSet = samplingSet;
        m_rowHandler = rowHandler;
    }

    Iterable<Iterable<DataCell>> replace(final DataRow roi, final Mask mask) {
        CheckUtils.checkArgument(roi.getNumCells() == m_rowHandler.getExpectedNumberOfCells(),
            "The roi has %s cells but %s were expected.", roi.getNumCells(), m_rowHandler.getExpectedNumberOfCells());
        final List<Iterable<DataCell>> samples = new ArrayList<>();
        m_rowHandler.setOriginal(roi);
        m_rowHandler.resetReplacementIndices();
        m_rowHandler.setReplacementIndices(mask.iterator());
        for (final DataRow sampleRow : m_samplingSet) {
            m_rowHandler.setReplacement(sampleRow);
            samples.add(m_rowHandler.createReplaced());
        }
        // TODO check if we can also implement this lazily
        return samples;
    }

}
