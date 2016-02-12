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
package org.weasis.dicom.data;

public class TagW {

    public enum DICOM_LEVEL {
        PATIENT, STUDY, SERIES, INSTANCE
    };

    public enum TagType {
        // Period is 3 digits followed by one of the characters 'D' (Day),'W' (Week), 'M' (Month) or 'Y' (Year)
        STRING, STRING_ARRAY, URI, SEQUENCE, DATE, DATETIME, TIME, PERIOD, BOOLEAN, BYTEARRAY, INTEGER, INTEGERARRAY,
        FLOAT, FLOATARRAY, DOUBLE, DOUBLE_ARRAY, COLOR, THUMBNAIL, LIST, OBJECT

    };

    public static final TagW WadoCompressionRate = new TagW("Wado Compression Rate", TagType.INTEGER);
    public static final TagW WadoTransferSyntaxUID = new TagW("Wado Transfer Syntax UID", TagType.STRING);
    public static final TagW DirectDownloadFile = new TagW("Direct Download File", TagType.STRING); //$NON-NLS-1$
    public static final TagW DirectDownloadThumbnail = new TagW("Direct Download Thumbnail", TagType.STRING); //$NON-NLS-1$
    public static final TagW TransferSyntaxUID = new TagW(0x00020010, "Transfer Syntax UID", TagType.STRING);

    public static final TagW PatientName = new TagW(0x00100010, "Patient Name", TagType.STRING);
    public static final TagW PatientID = new TagW(0x00100020, "PatientID", TagType.STRING);
    public static final TagW IssuerOfPatientID = new TagW(0x00100021, "Issuer Of PatientID", TagType.STRING);
    public static final TagW PatientBirthDate = new TagW(0x00100030, "Patient Birth Date", TagType.DATE);
    public static final TagW PatientBirthTime = new TagW(0x00100032, "Patient Birth Time", TagType.TIME);
    public static final TagW PatientSex = new TagW(0x00100040, "Patient Sex", TagType.STRING);

    public static final TagW StudyInstanceUID = new TagW(0x0020000D, "Study Instance UID", TagType.STRING);
    public static final TagW SeriesInstanceUID = new TagW(0x0020000E, "Series Instance UID", TagType.STRING);
    public static final TagW StudyID = new TagW(0x00200010, "Study ID", TagType.STRING);
    public static final TagW InstanceNumber = new TagW(0x00200013, "Instance Number", TagType.INTEGER);
    public static final TagW ImageOrientationPatient = new TagW(0x00200037, "Image Orientation", TagType.DOUBLE_ARRAY);
    public static final TagW SliceLocation = new TagW(0x00201041, "Slice Location", TagType.FLOAT);

    public static final TagW SeriesDescription = new TagW(0x0008103E, "Series Description", TagType.STRING);
    public static final TagW SeriesNumber = new TagW(0x00200011, "Series Number", TagType.INTEGER);
    public static final TagW SOPInstanceUID = new TagW(0x00080018, "SOP Instance UID", TagType.STRING);
    public static final TagW StudyDate = new TagW(0x00080020, "Study Date", TagType.DATE);
    public static final TagW SeriesDate = new TagW(0x00080021, "Series Date", TagType.DATE);
    public static final TagW StudyTime = new TagW(0x00080030, "Study Time", TagType.TIME);
    public static final TagW AcquisitionTime = new TagW(0x00080032, "Acquisition Time", TagType.TIME);
    public static final TagW AccessionNumber = new TagW(0x00080050, "Accession Number", TagType.STRING);
    public static final TagW Modality = new TagW(0x00080060, "Modality", TagType.STRING);
    public static final TagW ReferringPhysicianName = new TagW(0x00080090, "Referring Physician Name", TagType.STRING);
    public static final TagW StudyDescription = new TagW(0x00081030, "Study Description", TagType.STRING);

    public static final TagW PixelData = new TagW(0x7FE00010, "Pixel Data", TagType.URI);
    public static final TagW PixelSpacing = new TagW(0x00280030, "Pixel Spacing", TagType.DOUBLE_ARRAY);
    public static final TagW WindowWidth = new TagW(0x00281051, "Window Width", TagType.FLOAT);
    public static final TagW WindowCenter = new TagW(0x00281050, "Window Center", TagType.FLOAT);
    public static final TagW RescaleSlope = new TagW(0x00281053, "Rescale Slope", TagType.FLOAT);
    public static final TagW RescaleIntercept = new TagW(0x00281052, "Rescale Intercept", TagType.FLOAT);
    public static final TagW SmallestImagePixelValue = new TagW(0x00280106, "Smallest ImagePixel Value", TagType.FLOAT);
    public static final TagW LargestImagePixelValue = new TagW(0x00200013, "Largest Image PixelValue", TagType.FLOAT);
    public static final TagW PixelPaddingValue = new TagW(0x00280120, "Pixel Padding Value", TagType.INTEGER);
    public static final TagW PixelPaddingRangeLimit =
        new TagW(0x00280121, "Pixel Padding Range Limit", TagType.INTEGER);
    public static final TagW SamplesPerPixel = new TagW(0x00280107, "Samples Per Pixel", TagType.INTEGER);
    public static final TagW MonoChrome = new TagW("MonoChrome", TagType.BOOLEAN);
    public static final TagW PhotometricInterpretation =
        new TagW(0x00280004, "Photometric Interpretation", TagType.STRING);

    protected final int id;
    protected final String name;
    protected final TagType type;

    public TagW(int id, String name) {
        this(id, name, null);
    }

    public TagW(int id, String name, TagType type) {
        this.id = id;
        this.name = name;
        this.type = type == null ? TagType.STRING : type;
    }

    public TagW(String name) {
        this(-1, name, null);
    }

    public TagW(String name, TagType type) {
        this(-1, name, type);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTagName() {
        return name.replaceAll(" ", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public TagType getType() {
        return type;
    }

    @Override
    public String toString() {
        return name;
    }
}
