/*
 * Copyright (c) 2014 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf;

import org.weasis.core.util.StringUtil;

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
