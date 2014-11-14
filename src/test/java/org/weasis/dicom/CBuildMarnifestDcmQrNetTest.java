package org.weasis.dicom;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.weasis.dicom.data.Patient;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.wado.BuildManifestDcmQR;
import org.weasis.dicom.wado.DicomQueryParams;
import org.weasis.dicom.wado.WadoParameters;
import org.weasis.dicom.wado.WadoQuery;
import org.weasis.dicom.wado.WadoQueryException;

public class CBuildMarnifestDcmQrNetTest {

    @Test
    public void testProcess() {

        DicomNode callingNode = new DicomNode("WEASIS-SCU");
        DicomNode calledNode = new DicomNode("DICOMSERVER", "dicomserver.co.uk", 11112);
        AdvancedParams p = new AdvancedParams();
        // EnumSet<QueryOption> queryOptions = EnumSet.of(QueryOption.RELATIONAL);
        // p.setQueryOptions(queryOptions);
        DicomQueryParams params = new DicomQueryParams(callingNode, calledNode, p);
        List<Patient> pts = null;
        try {

            // pts = BuildManifestDcmQR.buildFromPatientID(params, "PAT001");
            // Assert.assertFalse("No patient found!", pts.isEmpty());

            // long start = System.currentTimeMillis();
            // pts =
            // BuildManifestDcmQR.buildFromStudyInstanceUID(params,
            // "1.2.826.0.1.3680043.6.29390.24803.20140508142508.524.2");
            // Assert.assertFalse("No study found!", pts.isEmpty());
            // long end = System.currentTimeMillis();
            // System.out.println("Study query toke " + (end - start) + " MilliSeconds");

            pts =
                BuildManifestDcmQR.buildFromSeriesInstanceUID(params,
                    "1.2.826.0.1.3680043.9.4113.1.3.1754115794.7312.1404811886.876");
            Assert.assertFalse("No series found!", pts.isEmpty());

            // pts =
            // BuildManifestDcmQR.buildFromSopInstanceUID(params,
            // "1.2.826.0.1.3680043.9.4113.1.4.1754115794.7312.1404811886.877");
            // Assert.assertFalse("No image found!", pts.isEmpty());

            try {
                WadoParameters wado =
                    new WadoParameters("http://www.dicomserver.co.uk/wado/WADO.asp", false, "", null, null);
                WadoQuery wadoQuery = new WadoQuery(pts, wado, "utf-8", true);
                System.out.print(wadoQuery.toString());
            } catch (WadoQueryException e) {
                e.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // Should never happen
        Assert.assertNotNull(pts);

    }

}