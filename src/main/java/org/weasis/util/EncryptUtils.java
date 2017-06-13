/*******************************************************************************
 * Copyright (c) 2014 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.util;

import java.util.Arrays;
import java.util.Base64;

public class EncryptUtils {
    private static final char START = '~';
    private static final int KEY_MIN_SIZE = 10; // inclusive
    private static final int KEY_MAX_SIZE = 30; // inclusive

    private static final String ERROR_MSG = "message or key arguments cannot be null!";

    private EncryptUtils() {
    }

    public static String encrypt(String message, String key) {
        if (message == null || key == null) {
            throw new IllegalArgumentException(ERROR_MSG);
        }
        String result = xorMessage(message.trim(), key);
        return Base64.getUrlEncoder().encodeToString(result.getBytes());
    }

    private static String xorMessage(String message, String key) {
        if (message == null || key == null) {
            throw new IllegalArgumentException(ERROR_MSG);
        }
        char[] mChars = message.toCharArray();
        char[] kChars = key.toCharArray();
        if (mChars.length < 1) {
            throw new IllegalArgumentException("Cannot encode empty message!");
        }
        if (kChars.length < KEY_MIN_SIZE || kChars.length > KEY_MAX_SIZE) {
            throw new IllegalArgumentException(
                "key size must be >= " + KEY_MIN_SIZE + " and <= " + KEY_MAX_SIZE + " characters");
        }

        int kl = kChars.length;
        int ml = mChars.length;
        // If key is longer than message, take the key length for result
        char[] newmsg = new char[kl > ml ? kl : ml];

        for (int i = 0; i < newmsg.length; i++) {
            newmsg[i] = (char) (mChars[i % ml] ^ kChars[i % kl]);
        }
        if (ml < kl) {
            newmsg[ml] = (char) (START ^ kChars[ml]);
        }
        return new String(newmsg);
    }

    public static String decrypt(String message, String key) {
        if (message == null || key == null) {
            throw new IllegalArgumentException(ERROR_MSG);
        }
        String result = new String(Base64.getUrlDecoder().decode(message.trim()));
        return unXorMessage(result, key);
    }

    private static String unXorMessage(String message, String key) {
        if (message == null || key == null) {
            throw new IllegalArgumentException(ERROR_MSG);
        }
        char[] mChars = message.toCharArray();
        char[] kChars = key.toCharArray();

        int kl = kChars.length;
        int ml = mChars.length;
        // If key is longer than message, take the key length for result
        char[] newmsg = new char[kl > ml ? kl : ml];

        int cutMessage = -1;
        for (int i = 0; i < newmsg.length; i++) {
            newmsg[i] = (char) (mChars[i % ml] ^ kChars[i % kl]);
            if (newmsg[i] == START) {
                cutMessage = i;
                break;
            }
        }
        if (cutMessage > 0) {
            newmsg = Arrays.copyOfRange(newmsg, 0, cutMessage);
        }
        return new String(newmsg);
    }

}
