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
 * -------------------------------------------------------------------
 * 
 * History
 *   Mar 16, 2005 (sp): created
 */
package org.knime.base.node.viz.parcoord;

import java.io.Serializable;

/**
 * 
 * @author Simona Pintilie, University of Konstanz
 */
public class TwoDim implements Serializable {

    /**
     * the minimum value.
     */
    private double m_min;
    /**
     * the maximum value.
     */
    private double m_max;
    /**
     * the string value.
     */
    private int m_stringReference;

    /**
     *@param min the min value
     *@param max the max value
     *@param s the reference to the string value or -1
     */
    public TwoDim(final double min, final double max, final int s) {
        m_min = min; 
        m_max = max;
        m_stringReference = s;
    }
    /**
     * 
     */
    public TwoDim() {
        this(0, 0, -1);
    }
    /**
     * 
     * @return min a double value
     */
    public double getMin() {
        return m_min;
    }
    /**
     * 
     * @return max a double value
     */
    public double getMax() {
        return m_max;
    }
    /**
     * 
     * @return reference an int value
     */
    public int getStringReference() {
        return m_stringReference;
    }
    /**
     * 
     * @param max a double value
     */
    public void setMax(final double max) {
        m_max = max;
    }
    /**
     * 
     * @param min a double value
     */
    public void setMin(final double min) {
        m_min = min;
    }
    /**
     * 
     * @param s an int value
     */
    public void setStringReference(final int s) {
        m_stringReference = s;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
       return "[" + m_min + ", " + m_max + "]";
    } 

}
