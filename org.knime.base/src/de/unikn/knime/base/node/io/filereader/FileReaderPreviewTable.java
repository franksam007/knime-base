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
 *   11.01.2006 (ohl): created
 */
package de.unikn.knime.base.node.io.filereader;

import java.util.LinkedList;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.knime.core.data.DataTableSpec;
import org.knime.core.data.RowIterator;


/**
 * The data table displayed in the file reader's dialog's preview. We need an
 * extra incarnation of a data table (different from from the {@link FileTable}),
 * because if settings are not correct yet, the table in the preview must not
 * throw any exception on unexpected or invalid data it reads (which the
 * "normal" file table does). Thus, this table returns a row iterator that will
 * create an error row when a error occurs during file reading. It will end the
 * table after the errornous element was read.
 * 
 * @author Peter Ohl, University of Konstanz
 */
public class FileReaderPreviewTable extends FileTable {

    private String m_errorMsg;

    private int m_errorLine;

    private final LinkedList<ChangeListener> m_listeners;

    /**
     * Creates a new table, its like the "normal" {@link FileTable}, just not
     * failing on invalid data files.
     * 
     * @param settings settings for the underlying <code>FileTable</code>
     * @param tableSpec table spec for the underlying <code>FileTable</code>
     * @see FileTable
     */
    FileReaderPreviewTable(final DataTableSpec tableSpec,
            final FileReaderNodeSettings settings) {
        super(tableSpec, settings);
        m_listeners = new LinkedList<ChangeListener>();
        m_errorMsg = null;
        m_errorLine = -1;
    }

    /**
     * @see java.lang.Iterable#iterator()
     */
    @Override
    public RowIterator iterator() {
        return new FileReaderPreviewRowIterator(super.iterator(), this);
    }

    /**
     * This sets the flag indicating that the row iterator ended the table with
     * an error.
     * 
     * @param msg the message to store
     * @param lineNumber the line in which the error occured
     */
    void setError(final String msg, final int lineNumber) {
        if (msg == null) {
            throw new NullPointerException("Set a nice error message");
        }
        if (lineNumber < 0) {
            throw new IllegalArgumentException("Line numbers must be larger "
                    + "than zero.");
        }
        m_errorMsg = msg;
        m_errorLine = lineNumber;
        // notify all interested
        fireErrorOccuredEvent();
    }

    /**
     * @return <code>true</code> if an error occured in an underlying row
     *         iterator. Meaning the table contains invalid data. NOTE: if
     *         <code>false</code> is returned it is not guaranteed that all
     *         data in the table is valid. It could be that no row iterator
     *         reached the invalid data yet.
     */
    boolean getErrorOccured() {
        return m_errorMsg != null;
    }

    /**
     * @return the error msg set by a row iterator that came accross an error in
     *         the table. This is <code>null</code> if not set.
     */
    String getErrorMsg() {
        return m_errorMsg;
    }

    /**
     * @return the line number where the error occured - if an error occured and
     *         an error line number was set. Otherweise -1 is returned.
     */
    int getErrorLine() {
        return m_errorLine;
    }

    /**
     * If someone wants to be notified if an error occured he should register
     * through this method.
     * 
     * @param listener the object being notified when an error occurs.
     */
    void addChangeListener(final ChangeListener listener) {
        if (!m_listeners.contains(listener)) {
            m_listeners.add(listener);
        }
    }

    private void fireErrorOccuredEvent() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener l : m_listeners) {
            l.stateChanged(event);
        }
    }
}
