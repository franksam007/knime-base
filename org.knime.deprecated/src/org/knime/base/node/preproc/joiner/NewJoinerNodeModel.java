/*
 * ------------------------------------------------------------------------
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
 *   27.07.2007 (thor): created
 */
package org.knime.base.node.preproc.joiner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.knime.base.data.sort.SortedTable;
import org.knime.base.node.preproc.joiner.NewJoinerSettings.DuplicateHandling;
import org.knime.base.node.preproc.joiner.NewJoinerSettings.JoinMode;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.RowKey;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.property.hilite.HiLiteHandler;
import org.knime.core.node.property.hilite.HiLiteManager;
import org.knime.core.util.DuplicateKeyException;

/**
 * This is the model of the joiner node that does all the dirty work.
 *
 * @author Thorsten Meinl, University of Konstanz
 */
public class NewJoinerNodeModel extends NodeModel {
    /**
     * Builds a map from the table's row keys to their row number.
     *
     * @param table a table
     * @param exec an execution monitor
     * @return the map
     * @throws CanceledExecutionException if execution has been canceled by the
     *             user
     */
    private static Map<String, Integer> buildTableOrdering(
            final BufferedDataTable table, final ExecutionMonitor exec)
            throws CanceledExecutionException {
        HashMap<String, Integer> map =
                new HashMap<String, Integer>(table.getRowCount());

        int i = 0;
        for (DataRow row : table) {
            exec.checkCanceled();
            map.put(row.getKey().getString(), i++);
        }

        return map;
    }

    private final HiLiteManager m_hiliteHandler = new HiLiteManager();

    private int m_secondTableColIndex;

    private int[] m_secondTableSurvivers;

    private final NewJoinerSettings m_settings = new NewJoinerSettings();

    /**
     * Creates a new model for the Joiner node.
     */
    public NewJoinerNodeModel() {
        super(2, 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs)
            throws InvalidSettingsException {
        if ((m_settings.secondTableColumn() == null)
                || (m_settings.secondTableColumn().length() < 1)) {
            throw new InvalidSettingsException(
                    "No column from the second table selected");
        }
        if (!NewJoinerSettings.ROW_KEY_IDENTIFIER.equals(
                m_settings.secondTableColumn())
                && inSpecs[1].findColumnIndex(m_settings.secondTableColumn())
                == -1) {
            throw new InvalidSettingsException("Join column '"
                    + m_settings.secondTableColumn()
                    + "' not found in second table");
        }

        return new DataTableSpec[]{createSpec(inSpecs)};
    }

    /**
     * Creates a spec for the output table by taking care of duplicate columns.
     *
     * @param specs the specs of the two input tables
     * @return the spec of the output table
     * @throws InvalidSettingsException if duplicate columns exists and the are
     *             neither filtered out nor renamed
     */
    private DataTableSpec createSpec(final DataTableSpec[] specs)
            throws InvalidSettingsException {
        final List<DataColumnSpec> takeSpecs = new ArrayList<DataColumnSpec>();
        Set<String> colNames = new HashSet<String>();
        for (DataColumnSpec colSpec : specs[0]) {
            takeSpecs.add(colSpec);
            colNames.add(colSpec.getName());
        }


        List<Integer> stci = new ArrayList<Integer>();
        for (int i = 0; i < specs[1].getNumColumns(); i++) {
            DataColumnSpec colSpec = specs[1].getColumnSpec(i);
            if (colNames.contains(colSpec.getName())) {
                if (m_settings.duplicateHandling().equals(
                        DuplicateHandling.DontExecute)) {
                    throw new InvalidSettingsException("Duplicate column '"
                            + colSpec.getName() + "', won't execute");
                } else if (m_settings.duplicateHandling().equals(
                        DuplicateHandling.AppendSuffix)) {
                    String newName = colSpec.getName();
                    do {
                        newName += m_settings.suffix();
                    } while (colNames.contains(newName));

                    DataColumnSpecCreator dcsc =
                            new DataColumnSpecCreator(colSpec);
                    dcsc.removeAllHandlers();
                    dcsc.setName(newName);
                    takeSpecs.add(dcsc.createSpec());
                    stci.add(i);
                    colNames.add(newName);
                }
                // else filter the column
            } else {
                colNames.add(colSpec.getName());
                takeSpecs.add(colSpec);
                stci.add(i);
            }
        }

        m_secondTableSurvivers = new int[stci.size()];
        for (int i = 0; i < m_secondTableSurvivers.length; i++) {
            m_secondTableSurvivers[i] = stci.get(i);
        }
        return new DataTableSpec(takeSpecs.toArray(
                new DataColumnSpec[takeSpecs.size()]));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData,
            final ExecutionContext exec) throws Exception {
        BufferedDataTable leftTable = inData[0];
        BufferedDataTable rightTable = inData[1];
        m_secondTableColIndex =
                rightTable.getDataTableSpec().findColumnIndex(
                        m_settings.secondTableColumn());
        if (!NewJoinerSettings.ROW_KEY_IDENTIFIER.equals(
                m_settings.secondTableColumn())
                && (m_secondTableColIndex == -1)) {
            throw new InvalidSettingsException("Join column '"
                    + m_settings.secondTableColumn()
                    + "' not found in second table");
        }

        BufferedDataContainer dc =
            exec.createDataContainer(createSpec(new DataTableSpec[]{
                    leftTable.getDataTableSpec(),
                    rightTable.getDataTableSpec()}));

        // create a row with missing values for left or full outer joins
        DataCell[] missingCells = new DataCell[rightTable.getDataTableSpec()
                                               .getNumColumns()];
        for (int i = 0; i < missingCells.length; i++) {
            missingCells[i] = DataType.getMissingCell();
        }
        DataRow missingRow = new DefaultRow(new RowKey(""), missingCells);

        exec.setMessage("Reading first table");
        // build a map for sorting the second table which maps the row keys of
        // the first table to their row number
        final Map<String, Integer> orderMap =
                buildTableOrdering(leftTable, exec);
        Comparator<DataRow> rowComparator = new Comparator<DataRow>() {
            public int compare(final DataRow o1, final DataRow o2) {
                Integer k1 = orderMap.get(getRightJoinKey(o1));
                Integer k2 = orderMap.get(getRightJoinKey(o2));

                if ((k1 != null) && (k2 != null)) {
                    return k1 - k2;
                } else if (k1 != null) {
                    return -1;
                } else if (k2 != null) {
                    return 1;
                } else {
                    return 0;
                }
            }
        };
        // sort the second table based on the key order from the first table
        // non-matching rows are placed at the end
        exec.setMessage("Sorting second table");
        SortedTable rightSortedTable =
                new SortedTable(rightTable, rowComparator, false, exec
                        .createSubExecutionContext(0.7));

        Iterator<DataRow> lit = leftTable.iterator();
        Iterator<DataRow> rit = rightSortedTable.iterator();

        exec.setMessage("Joining tables");
        final double max;
        boolean lofj = false;
        boolean rofj = false;
        if (JoinMode.InnerJoin.equals(m_settings.joinMode())) {
            max = Math.min(leftTable.getRowCount(), rightTable.getRowCount());
        } else if (JoinMode.LeftOuterJoin.equals(m_settings.joinMode())) {
            max = leftTable.getRowCount();
            lofj = true;
        } else if (JoinMode.RightOuterJoin.equals(m_settings.joinMode())) {
            max = rightTable.getRowCount();
            rofj = true;
        } else {
            max = Math.max(leftTable.getRowCount(), rightTable.getRowCount());
            lofj = true;
            rofj = true;
        }

        // now join the two tables
        int p = 0;
        DataRow lrow = lit.hasNext() ? lit.next() : null;
        DataRow rrow = rit.hasNext() ? rit.next() : null;
        String lkey = (lrow != null) ? lrow.getKey().getString() : null;
        String rkey = (rrow != null) ? getRightJoinKey(rrow) : null;
        outer: while ((lrow != null) && (rrow != null)) {
            exec.checkCanceled();

            String key = lkey.toString();
            if (lkey.equals(rkey)) { // we found a match
                // loop over all matching rows in the second table
                for (int i = 0; lkey.equals(rkey); i++) {
                    dc.addRowToTable(createJoinedRow(key, lrow, rrow));
                    exec.setProgress(0.7 + 0.3 * p++ / max);
                    if (!rit.hasNext()) {
                        rrow = null;
                        break outer;
                    }
                    rrow = rit.next();
                    rkey = getRightJoinKey(rrow);
                    key = lkey.toString() + m_settings.keySuffix() + i;
                }
            } else if (lofj) {
                // no matching row from right table => fill with missing values
                // if left or full outer join is required
                dc.addRowToTable(createJoinedRow(lkey.toString(), lrow,
                        missingRow));
                exec.setProgress(0.7 + 0.3 * p++ / max);
            }

            if (!lit.hasNext()) {
                break outer;
            }
            lrow = lit.next();
            lkey = lrow.getKey().getString();
        }

        if (lit.hasNext() && lofj) {
            // add remaining non-joined rows from the left table if left or full
            // outer join
            while (lit.hasNext()) {
                lrow = lit.next();
                dc.addRowToTable(createJoinedRow(lrow.getKey().toString(),
                        lrow, missingRow));
                exec.setProgress(0.7 + 0.3 * p++ / max);
            }
        } else if ((rrow != null) && rofj) {
            // add remaining non-joined rows from the right table if right or
            // full outer join
            missingCells =
                    new DataCell[leftTable.getDataTableSpec().getNumColumns()];
            for (int i = 0; i < missingCells.length; i++) {
                missingCells[i] = DataType.getMissingCell();
            }
            missingRow = new DefaultRow(new RowKey(""), missingCells);

            boolean warningSet = false;
            while (true) {
                String key = rrow.getKey().toString();
                int c = 0;
                while (true) {
                    try {
                        dc.addRowToTable(
                                createJoinedRow(key, missingRow, rrow));
                        exec.setProgress(0.7 + 0.3 * p++ / max);
                        break;
                    } catch (DuplicateKeyException ex) {
                        if (++c > 10) {
                           throw ex;
                        }
                        key = key + "_r";
                        if (!warningSet) {
                            setWarningMessage("Encountered and fixed some "
                                    + "duplicate row keys at the end of the "
                                    + "table");
                            warningSet = true;
                        }
                    }
                }
                if (!rit.hasNext()) {
                    break;
                }
                rrow = rit.next();
            }
        }
        dc.close();

        return new BufferedDataTable[]{dc.getTable()};
    }

    /**
     * Creates a new row that is a join of the two input rows. The columns from
     * the right row are filtered based on the the duplicate handling mode.
     *
     * @param key the new row's key
     * @param leftRow the left row
     * @param rightRow the right which may get filtered
     * @return the new joined row
     */
    private DataRow createJoinedRow(final String key, final DataRow leftRow,
            final DataRow rightRow) {
        DataCell[] cells =
                new DataCell[leftRow.getNumCells()
                        + m_secondTableSurvivers.length];
        int i = 0;
        for (DataCell c : leftRow) {
            cells[i++] = c;
        }

        for (int j = 0; j < m_secondTableSurvivers.length; j++) {
            cells[i++] = rightRow.getCell(m_secondTableSurvivers[j]);
        }

        return new DefaultRow(new RowKey(key), cells);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected HiLiteHandler getOutHiLiteHandler(final int outIndex) {
        return m_hiliteHandler.getFromHiLiteHandler();
    }

    private String getRightJoinKey(final DataRow row) {
        if (NewJoinerSettings.ROW_KEY_IDENTIFIER.equals(
                m_settings.secondTableColumn())) {
            return row.getKey().getString();
        } else {
            return row.getCell(m_secondTableColIndex).toString();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        // nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        m_settings.loadSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        m_hiliteHandler.removeAllToHiliteHandlers();
        for (int i = 0; i < getNrInPorts(); i++) {
            HiLiteHandler hdl = getInHiLiteHandler(i);
            m_hiliteHandler.addToHiLiteHandler(hdl);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        // nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_settings.saveSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setInHiLiteHandler(final int inIndex,
            final HiLiteHandler hiLiteHdl) {
        super.setInHiLiteHandler(inIndex, hiLiteHdl);
        m_hiliteHandler.addToHiLiteHandler(hiLiteHdl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        NewJoinerSettings s = new NewJoinerSettings();
        s.loadSettings(settings);
        if (s.duplicateHandling() == null) {
            throw new InvalidSettingsException(
                    "No duplicate handling method selected");
        }
        if (s.joinMode() == null) {
            throw new InvalidSettingsException("No join mode selected");
        }
        if ((s.secondTableColumn() == null)
                || (s.secondTableColumn().length() < 1)) {
            throw new InvalidSettingsException(
                    "No column from the second table selected");
        }
        if (DuplicateHandling.AppendSuffix.equals(s.duplicateHandling())
                && ((s.suffix() == null) || (s.suffix().length() < 1))) {
            throw new InvalidSettingsException(
                    "No suffix for duplicate columns provided");
        }
    }
}
