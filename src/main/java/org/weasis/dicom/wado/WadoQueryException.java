/*******************************************************************************
 * Copyright (c) 2014 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.wado;

/**
 * This class implements exceptions raised by the WadoQuery class
 * 
 * @author jlrz, nirt
 */
public class WadoQueryException extends Exception {

    // Predefined exceptions codes
    public static int NO_PATIENTS_LIST = 0;
    public static int CANNOT_CREATE_TEMP_FILE = 1;
    public static int CANNOT_WRITE_TO_TEMP_FILE = 2;

    private static String exceptions[] =
        { "Empty Patient List", "Cannot Create Temporary File", "Cannot Write To Temporary File" };

    private int exception = 0;

    /**
     * Constructs a new exception with a predefined exception
     * 
     * @param exception
     *            a predefined exception code
     */
    public WadoQueryException(int code) {
        exception = code;
    }

    // Methods
    /**
     * Returns a short description of this exception
     * 
     * @return a string representation of this exception
     */
    @Override
    public String toString() {
        return "WadoQueryException: " + exceptions[exception];
    }

    /**
     * Returns the detail message string of this throwable
     * 
     * @return the detail message string of this throwable
     */
    @Override
    public String getMessage() {
        return exceptions[exception];
    }

    /**
     * Returns the code of this exception
     * 
     * @return the code of this exception
     */
    public int getExceptionCode() {
        return exception;
    }

}
