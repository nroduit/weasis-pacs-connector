# weasis-pacs-connector #

[![License](https://img.shields.io/badge/License-EPL%202.0-blue.svg)](https://opensource.org/licenses/EPL-2.0) [![Build Status](https://travis-ci.org/nroduit/weasis-pacs-connector.svg?branch=master)](https://travis-ci.org/nroduit/weasis-pacs-connector)   
[![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-pacs-connector&metric=ncloc)](https://sonarcloud.io/component_measures?id=org.weasis%3Aweasis-pacs-connector) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-pacs-connector&metric=reliability_rating)](https://sonarcloud.io/component_measures?id=org.weasis%3Aweasis-pacs-connector) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-pacs-connector&metric=sqale_rating)](https://sonarcloud.io/component_measures?id=org.weasis%3Aweasis-pacs-connector) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-pacs-connector&metric=security_rating)](https://sonarcloud.io/component_measures?id=org.weasis%3Aweasis-pacs-connector) [![Sonar](https://sonarcloud.io/api/project_badges/measure?project=org.weasis%3Aweasis-pacs-connector&metric=alert_status)](https://sonarcloud.io/dashboard?id=org.weasis%3Aweasis-pacs-connector)  

weasis-pacs-connector provides the easiest way to launch the Weasis DICOM viewer from a web context (see URL examples below) and to connect Weasis to any PACS supporting WADO or to a WEB API.

The master branch requires Java 8+ and a servlet container 3.1. The [6.x branch](https://github.com/nroduit/weasis-pacs-connector/tree/6.x) requires Java 7+ and a servlet container 2.5.

This component gathers different services:   

* **/viewer** launching Weasis with the patient ID, study UID... (can be configured to use a combination of UIDs or to hide some of them)
* **/IHEInvokeImageDisplay** launching Weasis at Patient and Study level, compliant to the [IHE IID profile](http://www.ihe.net/Technical_Framework/upload/IHE_RAD_Suppl_IID.pdf)
* **/viewer-applet** same as _/viewer_ but it can launch Weasis as Applet in a web page (the service returns an html page). This method is not recommended as most of browsers block Java plugin.
* **/manifest** building the xml manifest (containing the necessary UIDs) consumed by Weasis to retrieve all the images by WADO requests
* **/[name of the template]** (default template: /weasis.jnlp) building a jnlp file from a template (jnlp template path, jnlp properties and jnp arguments can be passed via URL parameters, see the [JNLP Builder documentation](JnlpBuilder))


## New features in weasis-pacs-connector 7 ##
* Requires Java 8 and Servlet 3.1
* Redirection for getting jnlp protocol from a http request
* Support of Java 9

## New features in weasis-pacs-connector 6.1.3 ##
* Enable running Weasis on Java 9
* Getting jnlp protocol by redirection (see [launching jnlp](#new-way-to-launch-jnlp))
* Allow the configuration of the default max memory size of Weasis
* Add double quotes for command parameters in jnlp (requires by Weasis 2.6.0 and later)

## New features in weasis-pacs-connector 6 ##
* Multi-PACS configuration (can be requested simultaneously or individually)
* Allows to query the PACS through its database (not recommended)
* Generates new manifest 2.5 (supported by Weasis 2.5)
* Requires Java 7

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

Prerequisites: JDK 8 and Maven 3

* Execute the maven command `mvn clean package` in the root directory of the project and get the package from /target/weasis-pacs-connector.war. Official releases are available at [here](http://sourceforge.net/projects/dcm4che/files/Weasis/weasis-pacs-connector/).

* Use the loggerless profile for web application container which already embeds slf4j and log4j (like JBoss): `mvn clean package -Ploggerless`


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
  => only studies containing CT which are more recent than 2010-01-01 12:00:00 (UTC time, do not use 'Z' if the store files don't have a time zone)  
* http://localhost:8080/weasis-pacs-connector/viewer?studyUID=1.3.6.1.4.1.5962.1.2.2.20031208063649.855
* http://localhost:8080/weasis-pacs-connector/viewer?accessionNumber=3224252
* http://localhost:8080/weasis-pacs-connector/viewer?seriesUID=1.2.840.113704.1.111.4924.1273631010.17
* http://localhost:8080/weasis-pacs-connector/viewer?objectUID=1.2.840.113704.1.111.3520.1273640118.5118

Note: It is allowed to have multiple UIDs for patient, study, series and instance but within the same level. The [configuration file](src/main/resources/weasis-connector-default.properties) enables to set which ID is allowed and if a combination of UIDs is required. When using a combination of UIDs, the order is not relevant.

##### Launch Weasis as an Applet in a web browser (not recommended as most of browsers block Java plugin) #####
* http://localhost:8080/weasis-pacs-connector/viewer-applet?patientID=97026728   
  => same as _/viewer_ but it can launch Weasis as Applet in a webpage.

##### Upload the manifest via http POST #####
* http://localhost:8080/weasis-pacs-connector/viewer?upload=manifest  
  => with the parameter "Content-Type: text/xml; charset=UTF-8" and the manifest in the body of the POST request
  
##### Embed the manifest into the jnlp #####
* http://localhost:8080/weasis-pacs-connector/viewer?patientID=97026728&embedManifest   
  => embedManifest parameter will embed the manifest into the jnlp   
  Note: in this case building manifest is executed before starting Weasis (otherwise it is done in parallel) and the images can be always displayed via the jnlp file (could be a security issue when jnlp has been downloaded or kept by Java cache)

##### Open non DICOM images #####
* http://launcher-weasis.rhcloud.com/weasis-pacs-connector/getJnlpScheme/viewer?arg=%24image%3Aget%20-u%20%22https%3A%2F%2Fdcm4che.atlassian.net%2Fwiki%2Fdownload%2Fattachments%2F3670024%2Fweasis-mpr.png%22     
  => open image from an URL (from weasis 2.5)   
  Note: the jnlp argument must be encoded as URL encoding ($image:get -u "https://dcm4che.atlassian.net/wiki/download/attachments/3670024/weasis-mpr.png")

##### Launch with specific parameters #####
* http://launcher-weasis.rhcloud.com/weasis-pacs-connector/viewer?studyUID=1.3.6.1.4.1.5962.1.2.2.20031208063649.855&mhs=1024m   
  => modify in jnlp the maximum memory used by the application (max-heap-size="1024m")
* http://localhost:8080/weasis-pacs-connector/viewer?studyUID=1.3.6.1.4.1.5962.1.2.2.20031208063649.855&mfv=1    
  => modify the manifest version (default is 2.5, old one is 1 - required by Weasis lesser than 2.5)
* to inject other properties or arguments see the [JNLP Builder documentation](JnlpBuilder)

### Getting the xml manifest ###
Build an XML file containing the UIDs of the images which will be retrieved in Weasis. There is an [XLS schema](https://github.com/nroduit/Weasis/blob/master/weasis-dicom/weasis-dicom-explorer/src/main/resources/config/wado_query.xsd) to validate the content of xml. This file can be either compressed in gzip or uncompressed. Here are examples:  

* http://localhost:8080/weasis-pacs-connector/manifest?studyUID=1.3.6.1.4.1.5962.1.2.2.20031208063649.855
* http://localhost:8080/weasis-pacs-connector/manifest?studyUID=1.3.6.1.4.1.5962.1.2.2.20031208063649.855&gzip   
  => return a gzip-compressed XML file
* http://localhost:8080/weasis-pacs-connector/manifest?patientID=97026728&modalitiesInStudy=MR&upperDateTime=2014-05-20T12:00:00  
  => only studies containing MR which are more recent than 2014-05-20 12:00:00
  
### JNLP Builder ###

See the [JNLP Builder documentation](JnlpBuilder)

### Installation ###

It requires a web application container like Tomcat or JBoss.

Go [here](https://sourceforge.net/projects/dcm4che/files/Weasis/) and download these Weasis files.
* From the folder with the latest version number:  
	- [weasis.war] Weasis Web distribution which run with Java Web Start.
	- [weasis-ext.war] Optional package for additional plug-ins (e.g. exporting the images to build an ISO image for CD/DVD)
	- [weasis-i18n.war] Optional package for Weasis translations
* From weasis-pacs-connector folder:  
	- [weasis-pacs-connector.war] Connector between the archive and the viewer
	- [dcm4chee-web-weasis.jar] Optional package for [dcm4che-web3](https://nroduit.github.io/en/getting-started/dcm4chee/)

## Configuration of weasis-pacs-connector ##

The default configurations works directly with [dcm4che-web3](https://nroduit.github.io/en/getting-started/dcm4chee/). To override the configuration of weasis-pacs-connector, download [weasis-connector-default.properties](src/main/resources/weasis-connector-default.properties) and rename it **weasis-pacs-connector.properties**. This file named **weasis-pacs-connector.properties** and **[dicom-dcm4chee.properties](src/main/resources/dicom-dcm4chee.properties)** must be placed in the classpath of the application:

* In JBoss inferior to version 7, the best location would be "/server/default/conf/"
* In JBoss 7.2 and 8.x, see [here](https://developer.jboss.org/wiki/HowToPutAnExternalFileInTheClasspath)
* In JBoss Wildfly 10, the location is wildfly/standalone/configuration
* In Tomcat just specify the directory in shared.loader property of /conf/catalina.properties

To add properties or arguments in the JNLP there are two possibilities:

1. Add parameters via the URL, see the [JNLP Builder documentation](JnlpBuilder) (arg, prop, and src)
2. Change the [default template](src/main/webapp/weasis.jnlp), see _jnlp.default.name_ in [weasis-connector-default.properties](src/main/resources/weasis-connector-default.properties)

weasis-pacs-connector 6.1 generates new manifests and requires Weasis 2.5 and superior. However it is possible to run previous version of Weasis by modifying the [weasis-connector-default.properties](src/main/resources/weasis-connector-default.properties):    
1. Set the property _manifest.version=1_
2. Uncomment the property _jnlp.default.name=weasis1.jnlp_
3. Uncomment the property _jnlp.applet.name=weasisApplet1.jnlp_

Note: when multiple archives are configured, only the references of the first archive containing images will be incorporated in the manifest 1.0. Multiple archives can only work with Weasis 2.5.

For [dcm4chee-arc-light](https://github.com/dcm4che/dcm4chee-arc-light) see the [installation instructions](https://nroduit.github.io/en/getting-started/dcm4chee/).

## New way to launch jnlp ##

An alternative way to launch Java Webstart (JWS) by changing the scheme of URL:
* jnlp://localhost:8080/weasis-pacs-connector/viewer?patientID=9702672
* jnlps://localhost:8443/weasis-pacs-connector/viewer?patientID=9702672 (SSL connection)

or from weasis-pacs-connector 6.1.2 by adding "getJnlpScheme" in the web context:
* http://launcher-weasis.rhcloud.com/weasis-pacs-connector/getJnlpScheme/viewer?studyUID=1.3.6.1.4.1.5962.99.1.1839181372.1275896472.1436358291004.4.0   
=> This is useful when jnlp protocol is not allowed (like this wiki page). The getJnlpScheme servlet makes a redirection from http to jnlp.
* https://launcher-weasis.rhcloud.com/weasis-pacs-connector/getJnlpScheme/viewer?studyUID=1.3.6.1.4.1.5962.99.1.1839181372.1275896472.1436358291004.4.0


Advantages of jnlp protocol:
* Works at the system level (association of a MIME type with an application: jnlp => JWS)
* Works with most of browsers (Chrome, IE, Firefox, Safari, Opera...)
* Browsers do not download jnlp anymore. JWS reads directly the URL (do not show the popup "This application will run with unrestricted access" at every launch)
* Works with other applications which are requesting the default system application for the jnlp protocol
* No change is required at the client side or at the server side, only replacing the scheme of the jnlp URL is enough
* Registration of jnlp handler is available in Oracle Java Runtime installer from JRE 8_111 and in the Java 9 installer.
  * Works out of box on Windows
  * On Mac OS X, it could be necessary to run once Java Webstart to register the jnlp handler (/Library/Internet Plug-Ins/JavaAppletPlugin.plugin/Contents/Resources/javawslauncher.app)
  * On Linux, a [configuration](https://docs.oracle.com/javase/9/deploy/overview.htm#JSDPG-GUID-BC1669F9-7238-462D-80AA-3D42BAF99FA7) is required
* An implementation has be done on [IcedTea-WEB](http://icedtea.classpath.org/wiki/IcedTea-Web) (alternative of the Oracle JWS) and it will be available in the next release (1.7).

For more informations:
* [Oracle JWS documentation](https://docs.oracle.com/javase/9/deploy/overview.htm)
* [About the configuration in dcm4chee](https://nroduit.github.io/en/getting-started/dcm4chee/)


## Security ##

There are different ways to treat the security aspects. Here are some:

* Make a proxy servlet (URL forwarding) to handle authentication and authorization you want and configure weasis-pacs-connector to be called only by the proxy server (hosts.allow=serverhostname)
* Configure weasis-pacs-connector for UIDs encryption in the URL with a paraphrase (encrypt.key=paraphraseForIDs: just uncomment and set a new key). It works by default with dcm4chee-web3. For other web interface it requires to use the same [algorithm](src/main/java/org/weasis/util/EncryptUtils.java) with the same key. 
* Configure weasis-pacs-connector for accepting only limited IP/host
* Configure weasis-pacs-connector for accepting only requests with a combination of several UIDs

## Architecture of weasis-pacs-connector ##

![weasis-pacs-connector schema](https://nroduit.github.io/images/connector-wk-std.png)

See [How to launch Weasis from any environments](https://nroduit.github.io/en/basics/customize/integration/)
