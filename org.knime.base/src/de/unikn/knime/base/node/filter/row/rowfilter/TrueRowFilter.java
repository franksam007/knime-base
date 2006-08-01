/*
 * ------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2006
 * University of Konstanz, Germany.
 * Chair for Bioinformatics and Information Mining
 * Prof. Dr. Michael R. Berthold
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any quesions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * -------------------------------------------------------------------
 * 
 * History
 *   04.07.2005 (ohl): created
 */
package de.unikn.knime.base.node.filter.row.rowfilter;

import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;

/**
 * Row filter that always matches all rows. Not really usefull - but used if the
 * user absolutly wants it.
 * 
 * @author Peter Ohl, University of Konstanz
 */
public class TrueRowFilter extends RowFilter {
    /**
     * @see RowFilter#matches(DataRow, int)
     */
    @Override
    public boolean matches(final DataRow row, final int rowIndex)
            throws IncludeFromNowOn {
        // we can immediately tell that we wanna include all rows.
        throw new IncludeFromNowOn();
    }

    /**
     * @see RowFilter#loadSettingsFrom(NodeSettingsRO)
     */
    @Override
    public void loadSettingsFrom(final NodeSettingsRO cfg)
            throws InvalidSettingsException {
        // no settings to load.
    }

    /**
     * @see RowFilter#saveSettings(NodeSettingsWO)
     */
    @Override
    protected void saveSettings(final NodeSettingsWO cfg) {
        // no settings to save.
    }

    /**
     * @see de.unikn.knime.base.node.filter.row.rowfilter.RowFilter
     *      #configure(org.knime.core.data.DataTableSpec)
     */
    @Override
    public DataTableSpec configure(final DataTableSpec inSpec)
            throws InvalidSettingsException {
        return inSpec;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "TRUE-Filter";
    }
}
