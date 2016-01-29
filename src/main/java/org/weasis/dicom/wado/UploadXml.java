package org.weasis.dicom.wado;

public class UploadXml implements XmlManifest {

    private final String xmlContent;
    private final String charsetEncoding;

    public UploadXml(String xmlContent,String charsetEncoding) {
        this.xmlContent = xmlContent;
        this.charsetEncoding = charsetEncoding;
    }

    @Override
    public String xmlManifest() {
        return xmlContent;
    }

    @Override
    public String getCharsetEncoding() {
        return charsetEncoding;
    }

}
