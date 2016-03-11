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
package org.weasis.dicom.mf;

public class WadoParameters extends ArcParameters {

    // Manifest 1.0
    public static final String TAG_WADO_QUERY = "wado_query";
    public static final String WADO_URL = "wadoURL";
    public static final String WADO_ONLY_SOP_UID = "requireOnlySOPInstanceUID";

    private final boolean requireOnlySOPInstanceUID;

    public WadoParameters(String archiveID, String wadoURL, boolean requireOnlySOPInstanceUID,
        String additionnalParameters, String overrideDicomTagsList, String webLogin) {
        super(archiveID, wadoURL, additionnalParameters, overrideDicomTagsList, webLogin);
        this.requireOnlySOPInstanceUID = requireOnlySOPInstanceUID;
    }

    public boolean isRequireOnlySOPInstanceUID() {
        return requireOnlySOPInstanceUID;
    }
}
