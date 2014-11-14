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
package org.weasis.dicom.data.xml;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DateUtil {

    private static Logger logger = LoggerFactory.getLogger(DateUtil.class);

    public static String DATE_FORMAT = "yyyyMMdd";

    public static String TIME_FORMAT = "HHmmss";

    public static SimpleDateFormat DicomTimeFormat = new SimpleDateFormat(TIME_FORMAT);

    public static SimpleDateFormat DicomDateFormat = new SimpleDateFormat(DATE_FORMAT);

    public static String getTimestamp(ResultSet resultSet, String field, String targetFormat) throws SQLException {
        String result = null;
        Timestamp timestamp = resultSet.getTimestamp(field);
        if (timestamp != null) {
            SimpleDateFormat sdf = new SimpleDateFormat(targetFormat);
            result = sdf.format(timestamp);
            logger.debug("Timestamp [{}] converted to [{}]", timestamp, result);
        }
        return result;
    }

    public static String convertDateFormat(String dateInput, String inputFormat) {
        if (dateInput == null) {
            return null;
        }
        String result = null;
        SimpleDateFormat sdf;
        java.util.Date date = null;
        try {
            sdf = new SimpleDateFormat(inputFormat);
            date = sdf.parse(dateInput);
        } catch (Exception e) {
            logger.error("Date Error: cannot convert the date [{}]", dateInput);
        }
        if (date != null) {
            result = DicomDateFormat.format(date);
            logger.debug("Date [{}] converted to [{}]", date, result);
        }
        return result;
    }

    public static String getDate(ResultSet resultSet, String field, String targetFormat) throws SQLException {
        String result = null;
        Date date = resultSet.getDate(field);
        if (date != null) {
            SimpleDateFormat sdf = new SimpleDateFormat(targetFormat);
            result = sdf.format(date);
            logger.debug("Date [{}] converted to [{}]", date, result);
        }
        return result;
    }

    public static String getDateFromStr(ResultSet resultSet, String field, String sourceFormat, String targetFormat)
        throws SQLException {
        String result = null;
        String dateStr = resultSet.getString(field);
        try {
            if ((dateStr != null) && (!dateStr.equalsIgnoreCase(""))) {
                SimpleDateFormat sdfSource = new SimpleDateFormat(sourceFormat);
                java.util.Date date = sdfSource.parse(dateStr);

                SimpleDateFormat sdfTarget = new SimpleDateFormat(targetFormat);
                result = sdfTarget.format(date);

                logger.debug("Date [{}] converted to [{}]", dateStr, result);
            }
        } catch (ParseException e) {
            logger.error("Format Error: error parsing the field [{}] [{}]", field, e.getMessage());
        }
        return result;
    }

    public static java.util.Date getDate(String dateInput) {
        if (dateInput != null) {
            try {
                return DicomDateFormat.parse(dateInput);
            } catch (Exception e) {
            }
        }
        return null;
    }

    public static java.util.Date getTime(String dateInput) {
        if (dateInput != null) {
            try {
                return DicomTimeFormat.parse(dateInput);
            } catch (Exception e) {
            }
        }
        return null;
    }
}
