/* Created on Jul 10, 2006 4:19:52 PM by thor
 * -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2007
 * University of Konstanz, Germany
 * Chair for Bioinformatics and Information Mining (Prof. M. Berthold)
 * and KNIME GmbH, Konstanz, Germany
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * -------------------------------------------------------------------
 *
 */
package org.knime.base.node.meta.xvalidation;

import java.io.File;
import java.io.IOException;

import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;

/**
 * This is the cross validation partitioning node model that divides the input
 * table into partitions. It will only work together with a successing
 * {@link AggregateOutputNodeModel}.
 *
 * @author Thorsten Meinl, University of Konstanz
 */
public class XValidatePartitionModel extends NodeModel {
    private final XValidateSettings m_settings = new XValidateSettings();

    private short[] m_partNumbers;

    private boolean m_inLoop;

    /**
     * Creates a new model for the internal partitioner node.
     */
    public XValidatePartitionModel() {
        super(1, 2);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_settings.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        new XValidateSettings().loadSettingsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        m_settings.loadSettingsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData,
            final ExecutionContext exec) throws Exception {
        XValLoopContext ctx;
        if (!m_inLoop) {
            if (m_settings.leaveOneOut()) {
                ctx = new XValLoopContext(inData[0].getRowCount());
            } else {
                m_partNumbers = new short[inData[0].getRowCount()];

                final double partSize =
                        m_partNumbers.length / (double)m_settings.validations();
                for (int i = 0; i < m_partNumbers.length; i++) {
                    m_partNumbers[i] =
                            (short)Math.min(i / partSize, m_partNumbers.length);
                }

                if (m_settings.randomSampling()) {
                    for (int i = 0; i < m_partNumbers.length; i++) {
                        int pos = (int)(Math.random() * m_partNumbers.length);
                        short x = m_partNumbers[pos];
                        m_partNumbers[pos] = m_partNumbers[i];
                        m_partNumbers[i] = x;
                    }
                }
                ctx = new XValLoopContext(m_settings.validations());
            }

            pushScopeContext(ctx);
            m_inLoop = true;
        } else {
            ctx = peekScopeContext(XValLoopContext.class);
        }

        final int currentIteration = ctx.currentIteration();

        BufferedDataContainer test =
                exec.createDataContainer(inData[0].getDataTableSpec());

        BufferedDataContainer train =
                exec.createDataContainer(inData[0].getDataTableSpec());

        int count = 0;
        final double max = inData[0].getRowCount();
        for (DataRow row : inData[0]) {
            exec.setProgress(count / max);

            if (m_settings.leaveOneOut() && (count == currentIteration)) {
                test.addRowToTable(row);
            } else if (!m_settings.leaveOneOut()
                    && (m_partNumbers[count] == currentIteration)) {
                test.addRowToTable(row);
            } else {
                train.addRowToTable(row);
            }
            count++;
        }
        test.close();
        train.close();

        ctx.nextIteration();
        return new BufferedDataTable[]{train.getTable(), test.getTable()};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        m_partNumbers = null;
        m_inLoop = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs)
            throws InvalidSettingsException {
        return new DataTableSpec[]{inSpecs[0], inSpecs[0]};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        // nothing to do here
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
        // nothing to do here
    }
}
