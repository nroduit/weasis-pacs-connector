package org.weasis.dicom.mf;

import org.weasis.core.api.util.StringUtil;

public class UploadXml implements XmlManifest {

    private final String xmlContent;
    private final String charsetEncoding;

    public UploadXml(String xmlContent, String charsetEncoding) {
        this.xmlContent = xmlContent;
        this.charsetEncoding = StringUtil.hasText(charsetEncoding) ? charsetEncoding : "UTF-8";
    }

    @Override
    public String xmlManifest(String version) {
        return xmlContent;
    }

    @Override
    public String getCharsetEncoding() {
        return charsetEncoding;
    }

}
