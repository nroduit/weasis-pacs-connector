# weasis-pacs-connector #

The easiest way to launch Weasis from a web context  and to connect Weasis to any PACS supporting WADO.

## New features in weasis-pacs-connector 5 ##

* Used weasis-dicom-tools (based on dcm4che3) for building the manifest
* Starting Weasis and building manifest are executed in parallel (improve the time to get the images)
* Error messages are transmitted to the viewer through the manifest
* New context (/IHEInvokeImageDisplay) compliant to the [IHE IID profile](http://www.ihe.net/Technical_Framework/upload/IHE_RAD_Suppl_IID.pdf)
* Parameters at patient level defined in IID profile (mostRecentResults, lowerDateTime, upperDateTime, modalitiesInStudy) are also available in the other contexts (/viewer.jnlp and /manifest)
* Allows to use several version of Weasis simultaneously
* Allows to have on different servers for the following components: weasis-pacs-connector, PACS, Weasis, weasis-ext (additional plugins) and jnlp templates
* Major improvement of the JNLP builder servlet (allows dynamic injection of arguments, properties and templates)
* Option for DICOM query in TLS mode

TODO
* Option for launching Weasis as an Applet
* Option to use deploy.js


## URL examples ##

Launching Weasis with [IHE IID profile](http://www.ihe.net/Technical_Framework/upload/IHE_RAD_Suppl_IID.pdf):

* http://localhost:8080/weasis-pacs-connector/IHEInvokeImageDisplay?requestType=PATIENT&patientID=97026728&mostRecentResults=1  
  => query at patient level with a the number of the most recent studies
* http://localhost:8080/weasis-pacs-connector/IHEInvokeImageDisplay?requestType=PATIENT&patientID=97026728&lowerDateTime=2010-01-01T12:00:00Z
* http://localhost:8080/weasis-pacs-connector/IHEInvokeImageDisplay?requestType=PATIENT&patientID=97026728&upperDateTime=2010-01-01T12:00:00Z
* http://localhost:8080/weasis-pacs-connector/IHEInvokeImageDisplay?requestType=PATIENT&patientID=97026728&modalitiesInStudy=MR,XA
* http://localhost:8080/weasis-pacs-connector/IHEInvokeImageDisplay?requestType=STUDY&accessionNumber=1657271  
  => query at study level with _accessionNumber_ or _studyUID_

Launching Weasis:

* http://localhost:8080/weasis-pacs-connector/viewer.jnlp?patientID=9702672
* http://localhost:8080/weasis-pacs-connector/viewer.jnlp?patientID=9702672%5E%5E%5Etest  
  => add IssuerOfPatientID as in hl7: patientID=9702672^^^test
* http://localhost:8080/weasis-pacs-connector/viewer.jnlp?patientID=97026728&patientID=PACS-2023231696  
  => multiple patients
* http://localhost:8080/weasis-pacs-connector/viewer.jnlp?studyUID=1.3.6.1.4.1.5962.1.2.2.20031208063649.855
* http://localhost:8080/weasis-pacs-connector/viewer.jnlp?accessionNumber=3224252
* http://localhost:8080/weasis-pacs-connector/viewer.jnlp?seriesUID=1.2.840.113704.1.111.4924.1273631010.17
* http://localhost:8080/weasis-pacs-connector/viewer.jnlp?objectUID=1.2.840.113704.1.111.3520.1273640118.5118

Note: It is possible to have multiple UIDs for patient, study, series and instance. When using a combination of UIDs the order is not relevant.

For getting only the xml manifest:

* http://localhost:8080/weasis-pacs-connector/manifest?studyUID=1.3.6.1.4.1.5962.1.2.2.20031208063649.855
* http://localhost:8080/weasis-pacs-connector/manifest?studyUID=1.3.6.1.4.1.5962.1.2.2.20031208063649.855&gzip


See [How to launch Weasis from any environments](http://www.dcm4che.org/confluence/display/WEA/How+to+launch+Weasis+from+any+environments)

![weasis-pacs-connector schema](http://www.dcm4che.org/confluence/download/attachments/16122034/weasis_pacs_connector.png)