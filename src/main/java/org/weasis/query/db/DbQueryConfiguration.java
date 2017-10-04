package org.weasis.query.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.EscapeChars;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.mf.Patient;
import org.weasis.dicom.mf.Series;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.Study;
import org.weasis.dicom.util.DateUtil;
import org.weasis.query.AbstractQueryConfiguration;
import org.weasis.query.CommonQueryParams;

public class DbQueryConfiguration extends AbstractQueryConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(DbQueryConfiguration.class);

    public DbQueryConfiguration(Properties properties) {
        super(properties);

    }

    @Override
    public void buildFromPatientID(CommonQueryParams params, String... patientIDs) {
        throw new IllegalStateException("Request by patientID into a DB is not implemented!");
    }

    @Override
    public void buildFromStudyInstanceUID(CommonQueryParams params, String... studyInstanceUIDs) {
        String studiesUIDsQuery = getQueryString(studyInstanceUIDs);
        String query = buildQuery(
            properties.getProperty("arc.db.query.studies.where").replaceFirst("%studies%", studiesUIDsQuery));

        executeDbQuery(query);
    }

    @Override
    public void buildFromStudyAccessionNumber(CommonQueryParams params, String... accessionNumbers) {
        String accessionNumbersQuery = getQueryString(accessionNumbers);
        String query = buildQuery(properties.getProperty("arc.db.query.accessionnum.where")
            .replaceFirst("%accessionnum%", accessionNumbersQuery));

        executeDbQuery(query);
    }

    @Override
    public void buildFromSeriesInstanceUID(CommonQueryParams params, String... seriesInstanceUIDs) {
        String seriesUIDsQuery = getQueryString(seriesInstanceUIDs);
        String query =
            buildQuery(properties.getProperty("arc.db.query.series.where").replaceFirst("%series%", seriesUIDsQuery));

        executeDbQuery(query);
    }

    @Override
    public void buildFromSopInstanceUID(CommonQueryParams params, String... sopInstanceUIDs) {
        // TODO implement this method
    }

    private void executeDbQuery(String query) {
        DbQuery dbQuery = null;
        try {
            dbQuery = DbQuery.executeDBQuery(query, properties);
            buildListFromDB(dbQuery.getResultSet());
        } catch (Exception e) {
            LOGGER.error("DB query Error of {}", getArchiveConfigName(), e);
        } finally {
            if (dbQuery != null) {
                dbQuery.close();
            }
        }
    }

    private void buildListFromDB(ResultSet resultSet) throws SQLException {
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
            // Do not handle issuer of patientID as it should be unique within a DB
            Patient patient = getPatient(getString(resultSet, patIDField));
            if (patient == null) {
                patient = new Patient(getString(resultSet, patIDField), null);
                patient.setPatientName(getString(resultSet, patientNameField));

                if ("VARCHAR2".equals(patientBirthdateTypeField)) {
                    patient.setPatientBirthDate(getDate(resultSet, patientBirthDateField, patientBirthdateFormatField));
                } else if ("DATE".equals(patientBirthdateTypeField)) {
                    patient.setPatientBirthDate(getDate(resultSet, patientBirthDateField));
                }

                if (patientBirthTimeField != null) {
                    patient.setPatientBirthTime(getTimeFromTimeStamp(resultSet, patientBirthTimeField));
                }

                patient.setPatientSex(getString(resultSet, patientSexField));
                addPatient(patient);
            }

            Study study = patient.getStudy(getString(resultSet, studyIUIDField));

            if (study == null) {
                study = new Study(getString(resultSet, studyIUIDField));

                if (studyDateField != null) {
                    LocalDateTime dateTime = getLocalDateTimeFromTimeStamp(resultSet, studyDateField);
                    if (dateTime != null) {
                        study.setStudyDate(DateUtil.formatDicomDate(dateTime.toLocalDate()));
                        study.setStudyTime(DateUtil.formatDicomTime(dateTime.toLocalTime()));
                    }
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
                if (StringUtil.hasText(wadotTsuid)) {
                    String[] val = wadotTsuid.split(":");
                    if (val.length > 0) {
                        series.setWadoTransferSyntaxUID(val[0]);
                    }
                    if (val.length > 1) {
                        series.setWadoCompression(val[1]);
                    }
                }
                study.addSeries(series);
            }

            Integer frame = StringUtil.getInteger(getString(resultSet, instanceNumberField));
            String sopUID = getString(resultSet, sopIUIDField);
            SopInstance sop = series.getSopInstance(sopUID, frame);
            if (sop == null) {
                series.addSopInstance(new SopInstance(sopUID, frame));
            }
        }
    }

    private Patient getPatient(String pid) {
        return patientMap.get(pid);
    }

    private String buildQuery(String clauseWhere) {
        StringBuilder query = new StringBuilder();
        query.append(properties.getProperty("arc.db.query.select"));
        query.append(" where ").append(clauseWhere).append(" ");
        query.append(properties.getProperty("arc.db.query.and"));
        return query.toString();
    }

    private static String getQueryString(String... strings) {
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

    private static String getString(ResultSet resultSet, String field) throws SQLException {
        if (field != null) {
            return EscapeChars.forXML(resultSet.getString(field));
        }
        return null;
    }

    private static LocalDateTime getLocalDateTimeFromTimeStamp(ResultSet resultSet, String field) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(field);
        if (timestamp != null) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private static String getDateFromTimeStamp(ResultSet resultSet, String field) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(field);
        if (timestamp != null) {
            return DateUtil.formatDicomDate(timestamp.toLocalDateTime().toLocalDate());
        }
        return null;
    }

    private static String getTimeFromTimeStamp(ResultSet resultSet, String field) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(field);
        if (timestamp != null) {
            return DateUtil.formatDicomTime(timestamp.toLocalDateTime().toLocalTime());
        }
        return null;
    }

    private static String getDate(ResultSet resultSet, String field) throws SQLException {
        java.sql.Date date = resultSet.getDate(field);
        if (date != null) {
            return DateUtil.formatDicomDate(date.toLocalDate());
        }
        return null;
    }

    private static String getDate(ResultSet resultSet, String field, String sourceFormat) throws SQLException {
        String dateStr = resultSet.getString(field);
        try {
            if (StringUtil.hasText(dateStr)) {
                return DateUtil.formatDicomDate(LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(sourceFormat)));
            }
        } catch (DateTimeParseException e) {
            LOGGER.error("Format Error: error parsing the field [{}] - {}", field, e.getMessage());
        }
        return null;
    }
}
