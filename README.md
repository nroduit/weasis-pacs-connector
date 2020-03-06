# weasis-pacs-connector

[![License](https://img.shields.io/badge/License-EPL%202.0-blue.svg)](https://opensource.org/licenses/EPL-2.0) [![Build Status](https://travis-ci.com/nroduit/weasis-pacs-connector.svg?branch=master)](https://travis-ci.com/nroduit/weasis-pacs-connector)   
[![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-pacs-connector&metric=ncloc)](https://sonarcloud.io/component_measures?id=org.weasis%3Aweasis-pacs-connector) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-pacs-connector&metric=reliability_rating)](https://sonarcloud.io/component_measures?id=org.weasis%3Aweasis-pacs-connector) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-pacs-connector&metric=sqale_rating)](https://sonarcloud.io/component_measures?id=org.weasis%3Aweasis-pacs-connector) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-pacs-connector&metric=security_rating)](https://sonarcloud.io/component_measures?id=org.weasis%3Aweasis-pacs-connector) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-pacs-connector&metric=alert_status)](https://sonarcloud.io/dashboard?id=org.weasis%3Aweasis-pacs-connector)  

weasis-pacs-connector provides the easiest way to launch the Weasis DICOM viewer from a web context (see URL [examples](#launch-weasis)) and to connect Weasis to any PACS supporting WADO-URI. If you want to make your own connector without weasis-pacs-connector, follow [these instructions](https://nroduit.github.io/en/basics/customize/integration/#build-your-own-connector).

**Note**: A simpler configuration without weasis-pacs-connector is possible if the DICOM archive has DICOMWeb services (see examples [here](https://nroduit.github.io/en/basics/customize/integration/#download-directly-with-dicomweb-restful-services)). 

* The master branch requires Java 8+ and a servlet container 3.1. It works by default with [dcm4chee-arc-light](https://github.com/dcm4che/dcm4chee-arc-light). 
* The [6.x branch](https://github.com/nroduit/weasis-pacs-connector/tree/6.x) requires Java 7+ and a servlet container 2.5. This is the latest version working with dcm4chee 2.18.x.

:heavy_exclamation_mark:As **Java Webstart is deprecated**, prefer to use the [weasis protocol](https://nroduit.github.io/en/getting-started/weasis-protocol) (defined below by the **/weasis** service) instead of Java Web Start because it has been removed from [Java 11 release](https://www.oracle.com/technetwork/java/javase/11-relnote-issues-5012449.html#JDK-8185077) and because the end of public Oracle Java 8 updates from April 2019. It only works with Weasis 3.5 (or superior) installed on the system with a [native installer](https://nroduit.github.io/en/getting-started/).

This component gathers different services (:warning: => deprecated):

| Service              | Description                                                                   |
| -------------------- | ----------------------------------------------------------------------------- |
| **/weasis**                | new protocol to launch Weasis with requested images, replacing Java Webstart 
| **/viewer** :warning:      | launching Weasis with Java Webstart
| **/IHEInvokeImageDisplay** | launching Weasis at Patient and Study level, compliant to the [IHE IID profile](http://www.ihe.net/Technical_Framework/upload/IHE_RAD_Suppl_IID.pdf)
| **/manifest**              | building the xml manifest (containing the necessary UIDs) consumed by Weasis to retrieve all the images by WADO-URI requests
| **/[template]** :warning:  | (default template: /weasis.jnlp) building a jnlp file from a template (jnlp template path, jnlp properties and jnp arguments can be passed via URL parameters, see the [JNLP Builder documentation](JnlpBuilder))

## [Release History](CHANGELOG.md)


## Build weasis-pacs-connector

Prerequisites: JDK 8 and Maven 3

* Execute the maven command `mvn clean package` in the root directory of the project and get the package from /target/weasis-pacs-connector.war. Official releases are available at [here](http://sourceforge.net/projects/dcm4che/files/Weasis/weasis-pacs-connector/).

* Use the loggerless profile for web application container which already embeds slf4j and log4j (like JBoss): `mvn clean package -Ploggerless`

## Launch Weasis

The **/weasis** service uses the [weasis protocol](https://nroduit.github.io/en/getting-started/weasis-protocol) by redirecting the `http://` request into `weasis://` (because some web frameworks such as the wiki or the URL field of some browsers only support the standard protocols). It replaces the old **/viewer** service using Java Webstart.

* http://localhost:8080/weasis-pacs-connector/weasis?patientID=9702672
* http://localhost:8080/weasis-pacs-connector/weasis?patientID=9702672%5E%5E%5Etest  
  => to handle an universal patientID, add IssuerOfPatientID like in hl7: patientID=9702672^^^test
* http://localhost:8080/weasis-pacs-connector/weasis?patientID=97026728&patientID=2023231696  
  => multiple patients
* http://localhost:8080/weasis-pacs-connector/weasis?patientID=97026728&modalitiesInStudy=MR,XA  
  => only studies containing MR or XA 
* http://localhost:8080/weasis-pacs-connector/weasis?patientID=97026728&containsInDescription=abdo,thorax  
  => only studies containing the string abdo or thorax (accent and case insensitive) in study description (from version 5.0.1) 
* http://localhost:8080/weasis-pacs-connector/weasis?patientID=97026728&modalitiesInStudy=CT&upperDateTime=2010-01-01T12:00:00Z  
  => only studies containing CT which are more recent than 2010-01-01 12:00:00 (UTC time, do not use 'Z' if the store files don't have a time zone)
* http://localhost:8080/weasis-pacs-connector/weasis?studyUID=1.3.6.1.4.1.5962.1.2.2.20031208063649.855
* http://localhost:8080/weasis-pacs-connector/weasis?accessionNumber=3224252
* http://localhost:8080/weasis-pacs-connector/weasis?seriesUID=1.2.840.113704.1.111.4924.1273631010.17
* http://localhost:8080/weasis-pacs-connector/weasis?objectUID=1.2.840.113704.1.111.3520.1273640118.5118
* http://localhost:8080/weasis-pacs-connector/weasis?studyUID=1.3.6.1.4.1.5962.1.2.2.20031208063649.855&studyUID=1.3.6.1.4.1.5962.1.2.2.20031208063649.857&seriesUID=1.2.840.113704.1.111.4924.1273631010.17  
  => multiple studies and series

Note: it is possible to limit the type of UIDs (patientID, studyUID, accessionNumber, seriesUID, objectUID) that can be called from services. See "request.ids" in this [configuration file](src/main/resources/weasis-pacs-connector.properties) which enables to set which ID is allowed, by default all are allowed.

### Launch Weasis with IHE IID profile

The [Invoke Image Display Profile](https://www.ihe.net/uploadedFiles/Documents/Radiology/IHE_RAD_Suppl_IID.pdf) allows the user of an Image Display Invoker, typically a nonimage-aware system like an EHR, PHR or RIS, to request the display of studies for a patient, and
have the display performed by an image-aware system like an Image Display (PACS).

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

### Launch Weasis with other parameters

Some Weasis [preferences](https://nroduit.github.io/en/basics/customize/preferences/) may be overridden in the URL parameters.

* http://localhost:8080/weasis-pacs-connector/weasis?patientID=9702672&pro="weasis.aet%20MYAET"  
  => Change the defaut Weasis calling AETitle for DICOM send and DICOM print (by default WEASIS_AE)
  
Property syntax (key and value must be URL encoded): &pro="`key`%20`value`"

To add parameters related to the launch configuration and user preferences without weasis-pacs-connector, refer to this [page](https://nroduit.github.io/en/getting-started/weasis-protocol/#modify-the-launch-parameters).


### Upload the manifest via http POST

When the manifest is built [outside weasis-pacs-connector](https://nroduit.github.io/en/basics/customize/integration/#build-your-own-connector) and needs to be transmitted to the viewer.

* http://localhost:8080/weasis-pacs-connector/weasis?upload=manifest  
  => with the header parameter "Content-Type: text/xml; charset=UTF-8" and the manifest in the body of the POST request
  
## Getting the xml manifest
Build an XML file containing the UIDs of the images which will be retrieved in Weasis. There is an [XLS schema](https://github.com/nroduit/Weasis/blob/master/weasis-dicom/weasis-dicom-explorer/src/main/resources/config/wado_query.xsd) to validate the content of xml. This file can be either compressed in gzip or uncompressed. Here are examples:  

* http://localhost:8080/weasis-pacs-connector/manifest?studyUID=1.3.6.1.4.1.5962.1.2.2.20031208063649.855
* http://localhost:8080/weasis-pacs-connector/manifest?studyUID=1.3.6.1.4.1.5962.1.2.2.20031208063649.855&gzip  
  => return a gzip-compressed XML file
* http://localhost:8080/weasis-pacs-connector/manifest?patientID=97026728&modalitiesInStudy=MR&upperDateTime=2014-05-20T12:00:00  
  => only studies containing MR which are more recent than 2014-05-20 12:00:00
  
## Installation

It requires a web application container like Jetty, Tomcat or JBoss on the server side and an installation of [Weasis (native installer)](https://nroduit.github.io/en/getting-started/#try-weasis-now) on the client side.

For installation with the dcm4chee user interface, see this [page](https://nroduit.github.io/en/getting-started/dcm4chee/).

Go [here](https://sourceforge.net/projects/dcm4che/files/Weasis/) and download these following files:

* From weasis-pacs-connector folder:
	- [weasis-pacs-connector.war] Connector between the archive of images and the viewer
* From the folder with the latest version number (Optional if you want to run only the native version installed on the client system):
	- [weasis.war] Weasis web package which will upgrade the local installation for minor releases (all the plug-ins except the launcher).
	- [weasis-ext.war] Optional package for additional plug-ins (e.g. exporting the images to build an ISO image for CD/DVD)
	- [weasis-i18n.war] Optional package for [Weasis translations](https://nroduit.github.io/en/getting-started/translating/)

**Note**: If Weasis is not installed on the server side, the parameter `cdb` with no value must be added to the URL (e.g. http://localhost:8080/weasis-pacs-connector/weasis?patientID=9702672&cdb) or the `weasis.base.url` property in the weasis-pacs-connector [configuration](src/main/resources/weasis-pacs-connector.properties#L53) must be commented or set to null.

## Configuration

The default configurations works directly with [dcm4chee-arc-light](https://nroduit.github.io/en/getting-started/dcm4chee/). To override the configuration of weasis-pacs-connector, download [weasis-pacs-connector.properties](src/main/resources/weasis-pacs-connector.properties). This file named **weasis-pacs-connector.properties** and **[dicom-dcm4chee.properties](src/main/resources/dicom-dcm4chee-arc.properties)** must be placed in the classpath of the application:

* In JBoss Wildfly 10, the location is wildfly/standalone/configuration
* In Tomcat just specify the directory in shared.loader property of /conf/catalina.properties

To add properties or arguments at launch, see [Launch Weasis with other parameters](#launch-weasis-with-other-parameters).

Several archives can be used simultaneously in [configuration](src/main/resources/weasis-pacs-connector.properties#L76) by defining an archive file in which the property `arc.activate` must be `true`. Otherwise requires to have the archive ID in the request URL (e.g. http://host?patientID=9702672&archive=1000).

For [dcm4chee-arc-light](https://github.com/dcm4che/dcm4chee-arc-light) see the [installation instructions](https://nroduit.github.io/en/getting-started/dcm4chee/).

## Security

There are different ways to treat the security aspects. Here are some:

* Make a proxy servlet (URL forwarding) to handle authentication and authorization you want and configure weasis-pacs-connector to be called only by the proxy server (hosts.allow=serverhostname)
* Configure weasis-pacs-connector for UIDs encryption in the URL with a paraphrase (encrypt.key=paraphraseForIDs: just uncomment and set a new key). It works by default with dcm4chee-web3. For other web interface it requires to use the same [algorithm](src/main/java/org/weasis/util/EncryptUtils.java) with the same key. 
* Configure weasis-pacs-connector for accepting only limited IPs/hosts
* Limit the type of UIDs (patientID, studyUID, accessionNumber, seriesUID, objectUID) that can be called from services

## Architecture

![Architecture schema](https://nroduit.github.io/images/weasis-pacs-connector.svg)

See [How to launch Weasis from any environments](https://nroduit.github.io/en/basics/customize/integration/)
