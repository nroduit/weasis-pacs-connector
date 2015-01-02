package org.weasis.dicom.wado;

public interface XmlManifest {

    /**
     * Returns current wado query in a string
     * 
     * @return current wado query in a string
     */
     String xmlManifest();

    Object getWadoMessage();

    String getCharsetEncoding();

}