/*
 * Copyright (c) 2014 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.util;

import org.hamcrest.core.IsEqual;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

/** @author Nicolas Roduit */
public class EncryptUtilsTest {

  private static StringBuilder builder = new StringBuilder();

  @AfterClass
  public static void afterClass() {
    System.out.print(builder);
  }

  @Test
  public void testShortMessage() {
    String message = "3";
    String key = "paraphrasefortest";
    run(message, key);
  }

  @Test
  public void testLongMessage() {
    String message = "this is a long message to be encrypted!";
    String key = "paraphrasefortest";
    run(message, key);
  }

  private void run(String message, String key) {
    String enc_res = EncryptUtils.encrypt(message, key);
    Assert.assertNotNull("Encryption returns null!", enc_res);
    builder.append("Encryption: [");
    builder.append(message);
    builder.append("] to [");
    builder.append(enc_res);
    builder.append("]\n");

    String dec_res = EncryptUtils.decrypt(enc_res, key);
    Assert.assertNotNull("Decryption returns null!", dec_res);
    builder.append("Decryption: [");
    builder.append(enc_res);
    builder.append("] to [");
    builder.append(dec_res);
    builder.append("]\n");

    Assert.assertThat(
        "Cannot get original message after decryption!", dec_res, IsEqual.equalTo(message));
  }
}
