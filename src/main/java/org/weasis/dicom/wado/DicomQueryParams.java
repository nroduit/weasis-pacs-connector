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

import java.util.ArrayList;
import java.util.List;

import org.weasis.dicom.data.Patient;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;

public class DicomQueryParams {
    private final List<Patient> patients;
    private final DicomNode callingNode;
    private final DicomNode calledNode;
    private final AdvancedParams advancedParams;

    public DicomQueryParams(DicomNode callingNode, DicomNode calledNode, AdvancedParams params) {
        this.patients = new ArrayList<Patient>();
        this.callingNode = callingNode;
        this.calledNode = calledNode;
        this.advancedParams = params;
    }

    public List<Patient> getPatients() {
        return patients;
    }

    public DicomNode getCallingNode() {
        return callingNode;
    }

    public DicomNode getCalledNode() {
        return calledNode;
    }

    public AdvancedParams getAdvancedParams() {
        return advancedParams;
    }

}
