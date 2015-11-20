# weasis-pacs-connector #

weasis-pacs-connector provides the easiest way to launch Weasis from a web context (see URL examples below) and to connect Weasis to any PACS supporting WADO.

This component gathers different services:   

* **/viewer** launching Weasis with the patient ID, study UID... (can be configured to use a combination of UIDs or to hide some of them)
* **/IHEInvokeImageDisplay** launching Weasis at Patient and Study level, compliant to the [IHE IID profile](http://www.ihe.net/Technical_Framework/upload/IHE_RAD_Suppl_IID.pdf)
* **/viewer-applet** same as _/viewer_ but it can launch Weasis as Applet in a web page (the service returns an html page). This method is not recommended as several browsers block Java plugin.
* **/manifest** building the xml manifest (containing the necessary UIDs) consumed by Weasis to retrieve all the images by WADO requests
* **/[name of the template]** (default template: /weasis.jnlp) building a jnlp file from a template (jnlp template path, jnlp properties and jnp arguments can be passed via URL parameters, see the [JNLP Builder documentation](JnlpBuilder))

## New features in weasis-pacs-connector 5 ##

* Used [weasis-dicom-tools](https://github.com/nroduit/weasis-dicom-tools) (based on dcm4che3) for building the manifest
* Starting Weasis and building manifest are executed in parallel (improve the time to get the images)
* The manifest is not embedded any more by default in the jnlp, only an url with an id can be called once within 5 min. That means clicking on a jnlp a second time won't show any images (this behavior is desirable for security reasons as most browsers downloads jnlp)
* Configure the maximum number of manifests treated simultaneous and the maximum life time of a building manifest process (5 min by default)
* Error messages (when building the manifest) are transmitted to the viewer via the manifest
* New context (_/IHEInvokeImageDisplay_) compliant to the [IHE IID profile](http://www.ihe.net/Technical_Framework/upload/IHE_RAD_Suppl_IID.pdf)
* Parameters at patient level defined in IID profile (mostRecentResults, lowerDateTime, upperDateTime, modalitiesInStudy) are also available in the other contexts (_/viewer_, _/viewer-applet_ and _/manifest_)
* Allows to have on different servers the following components: weasis-pacs-connector, PACS, Weasis, weasis-ext (additional plugins) and jnlp templates
* Major improvement of the JNLP builder servlet (allows dynamic injection of arguments, properties and templates)
* Option for DICOM query in TLS mode
* Launching Weasis as an Applet in web page
* Uploading the manifest by http POST
* Option to embed the manifest into the jnlp

## Build weasis-pacs-connector ##

Prerequisites: JDK 6 and Maven

* Execute the maven command `mvn clean package` in the root directory of the project and get the package from /target/weasis-pacs-connector.war. Official releases can be downloaded [here](http://sourceforge.net/projects/dcm4che/files/Weasis/weasis-pacs-connector/).

* Use the loggerless profile for web application container which already embeds slf4j and log4j (like JBoss): `mvn clean package -Ploggerless`

Note: with a snapshot version, it can be necessary to build first the library [weasis-dicom-tools](https://github.com/nroduit/weasis-dicom-tools)

## Launching Weasis with [IHE IID profile](http://www.ihe.net/Technical_Framework/upload/IHE_RAD_Suppl_IID.pdf) ##

* http://localhost:8080/weasis-pacs-connector/IHEInvokeImageDisplay?requestType=PATIENT&patientID=97026728&mostRecentResults=2  
  => query at patient level to get a number of the most recent studies
* http://localhost:8080/weasis-pacs-connector/IHEInvokeImageDisplay?requestType=PATIENT&patientID=97026728&lowerDateTime=2010-01-01T12:00:00   
  => query at patient level to get the studies which are older than a date 
* http://localhost:8080/weasis-pacs-connector/IHEInvokeImageDisplay?requestType=PATIENT&patientID=97026728&upperDateTime=2010-01-01T12:00:00  
  => query at patient level to get the studies which are more recent than a date
* http://localhost:8080/weasis-pacs-connector/IHEInvokeImageDisplay?requestType=PATIENT&patientID=97026728&modalitiesInStudy=MR,XA   
  => query at patient level to get the studies containing MR or XA 
* http://localhost:8080/weasis-pacs-connector/IHEInvokeImageDisplay?requestType=PATIENT&patientID=97026728&containsInDescription=abdo,thorax   
  => query at patient level to get the studies containing the string abdo or thorax (accent and case insensitive) in study description (from version 5.0.1)    
* http://localhost:8080/weasis-pacs-connector/IHEInvokeImageDisplay?requestType=STUDY&accessionNumber=1657271  
  => query at study level with _accessionNumber_ or _studyUID_

## Launching Weasis ##

* http://localhost:8080/weasis-pacs-connector/viewer?patientID=9702672
* http://localhost:8080/weasis-pacs-connector/viewer?patientID=9702672%5E%5E%5Etest  
  => add IssuerOfPatientID as in hl7: patientID=9702672^^^test
* http://localhost:8080/weasis-pacs-connector/viewer?patientID=97026728&patientID=PACS-2023231696  
  => multiple patients
* http://localhost:8080/weasis-pacs-connector/viewer?patientID=97026728&modalitiesInStudy=MR,XA  
  => only studies containing MR or XA 
* http://localhost:8080/weasis-pacs-connector/viewer?patientID=97026728&containsInDescription=abdo,thorax  
  => only studies containing the string abdo or thorax (accent and case insensitive) in study description (from version 5.0.1) 
* http://localhost:8080/weasis-pacs-connector/viewer?patientID=97026728&modalitiesInStudy=CT&upperDateTime=2010-01-01T12:00:00Z  
  => only studies containing CT which are more recent than 2010-01-01 12:00:00  
* http://localhost:8080/weasis-pacs-connector/viewer?studyUID=1.3.6.1.4.1.5962.1.2.2.20031208063649.855
* http://localhost:8080/weasis-pacs-connector/viewer?accessionNumber=3224252
* http://localhost:8080/weasis-pacs-connector/viewer?seriesUID=1.2.840.113704.1.111.4924.1273631010.17
* http://localhost:8080/weasis-pacs-connector/viewer?objectUID=1.2.840.113704.1.111.3520.1273640118.5118
* http://localhost:8080/weasis-pacs-connector/viewer-applet?patientID=97026728   
  => same as _/viewer_ but it can launch Weasis as Applet in a webpage.
* http://localhost:8080/weasis-pacs-connector/viewer?upload=manifest  
  => upload the manifest via http POST with the parameter "Content-Type: text/xml; charset=UTF-8" and the manifest in the body of the POST request
* http://localhost:8080/weasis-pacs-connector/viewer?patientID=97026728&embedManifest   
  => embedManifest parameter will embed the manifest into the jnlp (in this case building manifest is executed before starting Weasis and the images can be always displayed via the jnlp file)
  
Note: It is allowed to have multiple UIDs for patient, study, series and instance but within the same level. The [configuration file](src/main/resources/weasis-connector-default.properties) enables to set which ID is allowed and if a combination of UIDs is required. When using a combination of UIDs, the order is not relevant.

### Getting the xml manifest ###
  
Build an XML file containing the UIDs of the images which will be retrieved in Weasis. There is an [XLS schema](https://github.com/nroduit/Weasis/blob/master/weasis-dicom/weasis-dicom-explorer/src/main/resources/config/wado_query.xsd) to validate the content of xml. This file can be either compressed in gzip or uncompressed. Here are examples:  

* http://localhost:8080/weasis-pacs-connector/manifest?studyUID=1.3.6.1.4.1.5962.1.2.2.20031208063649.855
* http://localhost:8080/weasis-pacs-connector/manifest?studyUID=1.3.6.1.4.1.5962.1.2.2.20031208063649.855&gzip   
  => return a gzip-compressed XML file
* http://localhost:8080/weasis-pacs-connector/manifest?patientID=97026728&modalitiesInStudy=MR&upperDateTime=2014-05-20T12:00:00  
  => only studies containing MR which are more recent than 2014-05-20 12:00:00
  
### JNLP Builder ###

See the [JNLP Builder documentation](JnlpBuilder)

## Configuration of weasis-pacs-connector ##

The default configurations works directly with [dcm4che-web3](http://www.dcm4che.org/confluence/display/WEA/Installing+Weasis+in+DCM4CHEE). To override the configuration of weasis-pacs-connector, download [weasis-connector-default.properties](src/main/resources/weasis-connector-default.properties) and rename it weasis-pacs-connector.properties. This file named weasis-pacs-connector.properties must be placed in the classpath of the application:

* In JBoss inferior to version 7, the best location would be "/server/default/conf/"
* In JBoss 7.2 and 8.x, see [here](https://developer.jboss.org/wiki/HowToPutAnExternalFileInTheClasspath)
* In Tomcat just specify the directory in shared.loader property of /conf/catalina.properties

To add properties or arguments in the JNLP there are two possibilities:

1. Add parameters via the URL, see the [JNLP Builder documentation](JnlpBuilder) (arg, prop, and src)
2. Change the [default template](src/main/webapp/weasis.jnlp), see _jnlp.default.name_ in [weasis-connector-default.properties](src/main/resources/weasis-connector-default.properties)


For [dcm4chee-arc](https://github.com/dcm4che/dcm4chee-arc-cdi):

* change the configuration of the wado server property to **pacs.wado.url=${server.base.url}/dcm4chee-arc/wado/DCM4CHEE**
* in JBoss WildFly 8.x place the file named weasis-pacs-connector.properties into _/modules/org/weasis/weasis-pacs-connector/main_
* create a file _module.xml_ and place it in the same directory. The content of this file is:

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <module xmlns="urn:jboss:module:1.1" name="org.weasis.weasis-pacs-connector">
        <resources>
            <resource-root path="."/>
        </resources>
    </module>
```

## Security ##

There are different ways to treat the security aspects. Here are some:

* Make a proxy servlet (URL forwarding) to handle authentication and authorization you want and configure weasis-pacs-connector to be called only by the proxy server (hosts.allow=serverhostname)
* Configure weasis-pacs-connector for UIDs encryption in the URL with a paraphrase (encrypt.key=paraphraseForIDs: just uncomment and set a new key). It works by default with dcm4chee-web3. For other web interface it requires to use the same [algorithm](src/main/java/org/weasis/util/EncryptUtils.java) with the same key. 
* Configure weasis-pacs-connector for accepting only request with a combination of several UIDs

## Architecture of weasis-pacs-connector ##

![weasis-pacs-connector schema](http://www.dcm4che.org/confluence/download/attachments/16122034/weasis_pacs_connector5.png)

See [How to launch Weasis from any environments](http://www.dcm4che.org/confluence/display/WEA/How+to+launch+Weasis+from+any+environments)
