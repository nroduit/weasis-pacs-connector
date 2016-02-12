package org.weasis.query.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.data.Patient;
import org.weasis.dicom.data.SOPInstance;
import org.weasis.dicom.data.Series;
import org.weasis.dicom.data.Study;
import org.weasis.dicom.data.xml.EscapeChars;
import org.weasis.dicom.util.DateUtil;
import org.weasis.dicom.util.StringUtil;
import org.weasis.dicom.wado.WadoQuery.WadoMessage;
import org.weasis.query.AbstractQueryConfiguration;
import org.weasis.query.CommonQueryParams;

public class DbQueryConfiguration extends AbstractQueryConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(DbQueryConfiguration.class);

    public DbQueryConfiguration(Properties properties) {
        super(properties);

    }

    @Override
    public void buildFromPatientID(CommonQueryParams params, String... patientIDs) throws Exception {
        // TODO
    }

    @Override
    public void buildFromStudyInstanceUID(CommonQueryParams params, String... studyInstanceUIDs) throws Exception {
        String studiesUIDsQuery = getQueryString(studyInstanceUIDs);
        String query = buildQuery(
            properties.getProperty("arc.db.query.studies.where").replaceFirst("%studies%", studiesUIDsQuery));

        executeDbQuery(query);
    }

    @Override
    public void buildFromStudyAccessionNumber(CommonQueryParams params, String... accessionNumbers) throws Exception {
        String accessionNumbersQuery = getQueryString(accessionNumbers);
        String query = buildQuery(properties.getProperty("arc.db.query.accessionnum.where")
            .replaceFirst("%accessionnum%", accessionNumbersQuery));

        executeDbQuery(query);
    }

    @Override
    public void buildFromSeriesInstanceUID(CommonQueryParams params, String... seriesInstanceUIDs) throws Exception {
        String seriesUIDsQuery = getQueryString(seriesInstanceUIDs);
        String query =
            buildQuery(properties.getProperty("arc.db.query.series.where").replaceFirst("%series%", seriesUIDsQuery));

        executeDbQuery(query);
    }

    @Override
    public void buildFromSopInstanceUID(CommonQueryParams params, String... sopInstanceUIDs) throws Exception {
        // TODO
    }

    private void executeDbQuery(String query) throws Exception {
        DbQuery dbQuery = null;
        try {
            dbQuery = DbQuery.executeDBQuery(query, properties);
            buildListFromDB(dbQuery.getResultSet());
        } catch (SQLException e) {
            throw new Exception("DB Access Error: " + e.getMessage());
        } finally {
            if (dbQuery != null) {
                dbQuery.close();
            }
        }
    }

    private void buildListFromDB(ResultSet resultSet) throws Exception {
        try {
            String patientNameField = properties.getProperty("arc.db.query.setpatientname");
            String patientBirthdateTypeField = properties.getProperty("arc.db.query.patientbirthdate.type");
            String patientBirthdateFormatField = properties.getProperty("arc.db.query.patientbirthdate.format");
            String patientBirthDateField = properties.getProperty("arc.db.query.patientbirthdate");
            String patientBirthTimeField = properties.getProperty("arc.db.query.patientbirthtime");
            String patientSexField = properties.getProperty("arc.db.query.patientsex");

            String studyDateTypeField = properties.getProperty("arc.db.query.studydate.type");
            String studyDateField = properties.getProperty("arc.db.query.studydate");
            String accessionNumberField = properties.getProperty("arc.db.query.accessionnumber");
            String studyIdField = properties.getProperty("arc.db.query.studyid");
            String referringPhysicianNameField = properties.getProperty("arc.db.query.referringphysicianname");
            String studyDescriptionField = properties.getProperty("arc.db.query.studydescription");

            String seriesDescriptionField = properties.getProperty("arc.db.query.seriesdescription");
            String modalityField = properties.getProperty("arc.db.query.modality");
            String seriesNumberField = properties.getProperty("arc.db.query.seriesnumber");

            String instanceNumberField = properties.getProperty("arc.db.query.instancenumber");

            String patIDField = properties.getProperty("arc.db.query.patientid");
            String studyIUIDField = properties.getProperty("arc.db.query.studyinstanceuid");
            String seriesIUIDField = properties.getProperty("arc.db.query.seriesinstanceuid");
            String sopIUIDField = properties.getProperty("arc.db.query.sopinstanceuid");

            while (resultSet.next()) {
                Patient patient = getPatient(getString(resultSet, patIDField));
                if (patient == null) {
                    patient = new Patient(getString(resultSet, patIDField));
                    patient.setPatientName(getString(resultSet, patientNameField));

                    if (patientBirthdateTypeField.equals("VARCHAR2")) {
                        patient.setPatientBirthDate(getDate(resultSet, patientBirthDateField,
                            patientBirthdateFormatField, DateUtil.DATE_FORMAT));
                    } else if (patientBirthdateTypeField.equals("DATE")) {
                        patient.setPatientBirthDate(getDate(resultSet, patientBirthDateField, DateUtil.DATE_FORMAT));
                    }

                    if (patientBirthTimeField != null) {
                        patient.setPatientBirthTime(getDate(resultSet, patientBirthTimeField, DateUtil.TIME_FORMAT));
                    }

                    patient.setPatientSex(getString(resultSet, patientSexField));
                    patients.add(patient);
                }

                Study study = patient.getStudy(getString(resultSet, studyIUIDField));

                if (study == null) {
                    study = new Study(getString(resultSet, studyIUIDField));

                    if (studyDateTypeField.equalsIgnoreCase("DATE")) {
                        study.setStudyDate(getDate(resultSet, studyDateField, DateUtil.DATE_FORMAT));
                        study.setStudyTime(getDate(resultSet, studyDateField, DateUtil.TIME_FORMAT));
                    } else if (studyDateTypeField.equalsIgnoreCase("TIMESTAMP")) {
                        study.setStudyDate(getTimeStamp(resultSet, studyDateField, DateUtil.DATE_FORMAT));
                        study.setStudyTime(getTimeStamp(resultSet, studyDateField, DateUtil.TIME_FORMAT));
                    }

                    study.setAccessionNumber(getString(resultSet, accessionNumberField));
                    study.setStudyID(getString(resultSet, studyIdField));
                    study.setReferringPhysicianName(getString(resultSet, referringPhysicianNameField));
                    study.setStudyDescription(getString(resultSet, studyDescriptionField));

                    patient.addStudy(study);
                }

                Series series = study.getSeries(getString(resultSet, seriesIUIDField));

                if (series == null) {
                    series = new Series(getString(resultSet, seriesIUIDField));
                    series.setSeriesDescription(getString(resultSet, seriesDescriptionField));
                    series.setModality(getString(resultSet, modalityField));
                    series.setSeriesNumber(getString(resultSet, seriesNumberField));

                    String wadotTsuid = properties.getProperty("wado.request.tsuid");
                    if (wadotTsuid != null) {
                        String[] val = wadotTsuid.split(":");
                        if (val.length > 0) {
                            series.setWadoTransferSyntaxUID(val[0]);
                        }

                        if (val.length > 1) {
                            try {
                                series.setWadoCompression(Integer.parseInt(val[1]));
                            } catch (NumberFormatException e) {
                                LOGGER.warn("Invalid compression profile value :" + val[1]);
                            }
                        }
                    }
                    study.addSeries(series);
                }

                SOPInstance sop = new SOPInstance(getString(resultSet, sopIUIDField));
                sop.setInstanceNumber(getString(resultSet, instanceNumberField));
                series.addSOPInstance(sop);
            }
        } catch (SQLException e) {
            throw new Exception("SQL Query Error" + e.getMessage());
        }
    }

    private Patient getPatient(String pid) {
        for (Patient p : patients) {
            if (p.getPatientID().equals(pid)) {
                return p;
            }
        }
        return null;
    }

    private String getQueryString(String... strings) {
        StringBuilder queryString = new StringBuilder();
        for (String str : strings) {
            if (StringUtil.hasText(str)) {
                if (queryString.length() > 0) {
                    queryString.append(",");
                }
                queryString.append("'");
                queryString.append(str);
                queryString.append("'");
            }
        }
        return queryString.toString();
    }

    private String buildQuery(String clauseWhere) {
        StringBuilder query = new StringBuilder();
        query.append(properties.getProperty("arc.db.query.select"));
        query.append(" where ").append(clauseWhere).append(" ");
        query.append(properties.getProperty("arc.db.query.and"));
        return query.toString();
    }

    private String getString(ResultSet resultSet, String field) throws SQLException {
        if (field != null) {
            return EscapeChars.forXML(resultSet.getString(field));
        }
        return null;
    }

    private static String getTimeStamp(ResultSet resultSet, String field, String targetFormat) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(field);
        if (timestamp != null) {
            return new SimpleDateFormat(targetFormat).format(timestamp);
        }
        return null;
    }

    private static String getDate(ResultSet resultSet, String field, String targetFormat) throws SQLException {
        Date date = resultSet.getDate(field);
        if (date != null) {
            return new SimpleDateFormat(targetFormat).format(date);
        }
        return null;
    }

    private static String getDate(ResultSet resultSet, String field, String sourceFormat, String targetFormat)
        throws SQLException {
        String dateStr = resultSet.getString(field);
        try {
            if ((dateStr != null) && (!dateStr.equalsIgnoreCase(""))) {
                return new SimpleDateFormat(targetFormat).format(new SimpleDateFormat(sourceFormat).parse(dateStr));
            }
        } catch (ParseException e) {
            LOGGER.error("Format Error: error parsing the field [{}] - {}", field, e.getMessage());
        }
        return null;
    }
}
