package com.bfsi.jpmc.service;

import com.bfsi.jpmc.model.ColumnNumMapper;
import com.bfsi.jpmc.model.ColumnNumMapper.Format;
import com.bfsi.jpmc.model.PainAllFields;
import com.bfsi.jpmc.util.SepaUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessPaymentsInputDataTest {

    private ProcessPaymentsInputData processPaymentsInputData;

    @Mock
    private ProcessSepaTransactions processSepaTransactions;

    @Mock
    private CreateErrorRecord createErrorRecord;

    @Mock
    private CsvTxtToExcelConverter csvTxtToExcelConverter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        processPaymentsInputData = new ProcessPaymentsInputData(processSepaTransactions, createErrorRecord, csvTxtToExcelConverter);
    }

    @Test
    void testProcessGenericRecord_CompactValid() {
        // Create a mock row for compact format (19 fields)
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet();
        Row row = sheet.createRow(0);
        
        // EU,SEPA,20251128,SEPACT,EUR,100.00,ES9121000418450200051332,ES,REF-001,CHASESM3,"COMPANY A","STREET 1",ES,BE49539007547085,"TARGET A","CITY A",MADRID,ES,BSCHESMM
        row.createCell(0).setCellValue("EU");
        row.createCell(1).setCellValue("SEPA");
        row.createCell(2).setCellValue("20251128");
        row.createCell(4).setCellValue("EUR");
        row.createCell(5).setCellValue("100.00");
        row.createCell(6).setCellValue("ES9121000418450200051332");
        row.createCell(8).setCellValue("REF-001");
        row.createCell(13).setCellValue("BE49539007547085");
        row.createCell(14).setCellValue("TARGET A");

        PainAllFields result = processPaymentsInputData.processGenericRecord(row, Format.COMPACT_TXT);

        assertNotNull(result);
        assertTrue(result.isValidRecord());
        assertEquals("EU", result.getCountryCode());
        assertEquals(new BigDecimal("100.00"), result.getTransactionAmount());
        assertEquals("ES9121000418450200051332", result.getDebitAccount());
        assertEquals("TARGET A", result.getBeneficiaryName());
    }

    @Test
    void testProcessGenericRecord_ExpandedHashValid() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet();
        Row row = sheet.createRow(0);
        
        // Match JPMC Client Input Row 2 format
        row.createCell(1).setCellValue("EU");
        row.createCell(3).setCellValue("SEPA");
        row.createCell(5).setCellValue("20251128");
        row.createCell(10).setCellValue("SEPACT");
        row.createCell(12).setCellValue("EUR");
        row.createCell(14).setCellValue("48.51");
        row.createCell(17).setCellValue("ES9121000418450200051332");
        row.createCell(29).setCellValue("ES"); // Ordering Party Country (REQUIRED for expanded)
        row.createCell(33).setCellValue("EXP-001");
        row.createCell(56).setCellValue("BE49539007547085");
        row.createCell(58).setCellValue("TARGET EXPANDED");
        row.createCell(62).setCellValue("#"); // This should be ignored

        PainAllFields result = processPaymentsInputData.processGenericRecord(row, Format.EXPANDED_TXT);

        assertNotNull(result);
        assertTrue(result.isValidRecord(), "Record should be valid. Error message: " 
                  + (result.getInputValidator() != null ? result.getInputValidator().getExceptionMessage(0) : "none"));
        assertEquals("EU", result.getCountryCode());
        assertEquals(new BigDecimal("48.51"), result.getTransactionAmount());
        assertEquals("TARGET EXPANDED", result.getBeneficiaryName());
    }

    @Test
    void testProcessGenericRecord_ExpandedExcelValid() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet();
        Row row = sheet.createRow(0);
        
        // Standard Excel indices
        row.createCell(1).setCellValue("ES");
        row.createCell(3).setCellValue("TRF");
        row.createCell(5).setCellValue("20251128");
        row.createCell(10).setCellValue("SEPACT");
        row.createCell(12).setCellValue("EUR");
        row.createCell(13).setCellValue("5000.00");
        row.createCell(16).setCellValue("ES9121000418450200051332");
        row.createCell(29).setCellValue("ES");
        row.createCell(33).setCellValue("REF-123");
        row.createCell(46).setCellValue("ORDERING PARTY NAME");
        row.createCell(58).setCellValue("BENEFICIARY NAME");
        row.createCell(56).setCellValue("BE49539007547085");

        PainAllFields result = processPaymentsInputData.processGenericRecord(row, Format.EXPANDED_EXCEL);

        assertNotNull(result);
        assertTrue(result.isValidRecord(), "Excel record should be valid. Error: " 
                  + (result.getInputValidator() != null ? result.getInputValidator().getExceptionMessage(0) : ""));
        assertEquals("ES", result.getCountryCode());
        assertEquals(new BigDecimal("5000.00"), result.getTransactionAmount());
    }

    @Test
    void testNegative_MissingMandatory() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet();
        Row row = sheet.createRow(0);
        
        row.createCell(0).setCellValue("EU");
        // missing amount and IBAN
        
        PainAllFields result = processPaymentsInputData.processGenericRecord(row, Format.COMPACT_TXT);

        assertNotNull(result);
        assertFalse(result.isValidRecord(), "Record should be invalid due to missing mandatory fields");
        assertTrue(result.getInputValidator().getExceptionMessage(0).contains("Amount Missing"));
    }

    @Test
    void testProcessGenericRecord_CompactInvalid() {
        Workbook workbook = new XSSFWorkbook();
        Row row = workbook.createSheet().createRow(0);
        
        // Compact format but missing mandatory Amount and Currency
        row.createCell(0).setCellValue("EU");
        row.createCell(6).setCellValue("ES9121000418450200051332");

        PainAllFields result = processPaymentsInputData.processGenericRecord(row, Format.COMPACT_TXT);

        assertNotNull(result);
        assertFalse(result.isValidRecord(), "Compact record missing amount/currency should be invalid");
        assertTrue(result.getInputValidator().getExceptionMessage(0).contains("Amount Missing"));
        assertTrue(result.getInputValidator().getExceptionMessage(0).contains("Currency Missing"));
    }

    @Test
    void testProcessGenericRecord_ExpandedHashInvalid() {
        Workbook workbook = new XSSFWorkbook();
        Row row = workbook.createSheet().createRow(0);
        
        // Expanded Hash format missing IBAN at index 56
        row.createCell(1).setCellValue("EU");
        row.createCell(12).setCellValue("EUR");
        row.createCell(14).setCellValue("100.00");
        // missing index 56 (Beneficiary IBAN)

        PainAllFields result = processPaymentsInputData.processGenericRecord(row, Format.EXPANDED_TXT);

        assertNotNull(result);
        assertFalse(result.isValidRecord(), "Expanded record missing IBAN should be invalid");
        assertTrue(result.getInputValidator().getExceptionMessage(0).contains("Beneficiary Account Missing"));
    }

    @Test
    void testEmptyRowDetection() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet();
        Row row = sheet.createRow(0);
        
        PainAllFields result = processPaymentsInputData.processGenericRecord(row, Format.EXPANDED_EXCEL);

        assertNull(result, "Completely empty row should return null");
    }

    @Test
    void testPrecisionLargeAmount() {
        Workbook workbook = new XSSFWorkbook();
        Row row = workbook.createSheet().createRow(0);
        row.createCell(5).setCellValue("1000000000.55"); // 1 Billion

        PainAllFields result = processPaymentsInputData.processGenericRecord(row, Format.COMPACT_TXT);

        assertEquals(new BigDecimal("1000000000.55"), result.getTransactionAmount());
    }

    @Test
    void testSpecialCharacters() {
        Workbook workbook = new XSSFWorkbook();
        Row row = workbook.createSheet().createRow(0);
        row.createCell(0).setCellValue("EU");
        row.createCell(5).setCellValue("10.00");
        row.createCell(14).setCellValue("Alice & Bob <Ltd>");

        PainAllFields result = processPaymentsInputData.processGenericRecord(row, Format.COMPACT_TXT);

        assertEquals("Alice & Bob <Ltd>", result.getBeneficiaryName());
    }

    @Test
    void testInvalidBIC() {
        Workbook workbook = new XSSFWorkbook();
        Row row = workbook.createSheet().createRow(0);
        
        row.createCell(0).setCellValue("EU");
        row.createCell(5).setCellValue("10.00");
        row.createCell(6).setCellValue("ES9121000418450200051332");
        row.createCell(13).setCellValue("BE49539007547085");
        row.createCell(14).setCellValue("TEST");
        row.createCell(18).setCellValue("INVALIDBIC");

        PainAllFields result = processPaymentsInputData.processGenericRecord(row, Format.COMPACT_TXT);

        // BIC is optional in code (it just logs and doesn't set validDataFlag=false currently)
        // because of line 424-426 in ProcessPaymentsInputData.
        // But the BIC wasn't set in the record.
        assertNull(result.getBeneficiaryRoutingCode());
    }

    @Test
    void testIntegrityAcrossAllPossibilities() {
        Format[] formats = {Format.COMPACT_TXT, Format.EXPANDED_TXT, Format.EXPANDED_EXCEL};
        for (Format f : formats) {
            Workbook workbook = new XSSFWorkbook();
            Row row = workbook.createSheet().createRow(0);
            
            // Populate EVERY possible mapped field for this format to avoid missing mandatory errors
            for (com.bfsi.jpmc.model.ColumnNumMapper.SepaField field : com.bfsi.jpmc.model.ColumnNumMapper.SepaField.values()) {
                Integer idx = ColumnNumMapper.getIndex(field, f);
                if (idx != null && idx >= 0) {
                    Cell cell = row.createCell(idx);
                    if (field == com.bfsi.jpmc.model.ColumnNumMapper.SepaField.AMOUNT) cell.setCellValue("100.00");
                    else if (field == com.bfsi.jpmc.model.ColumnNumMapper.SepaField.CURRENCY) cell.setCellValue("EUR");
                    else if (field == com.bfsi.jpmc.model.ColumnNumMapper.SepaField.DEBIT_DATE) cell.setCellValue("20251128");
                    else if (field == com.bfsi.jpmc.model.ColumnNumMapper.SepaField.DEBIT_ACCOUNT) cell.setCellValue("ES9121000418450200051332");
                    else if (field == com.bfsi.jpmc.model.ColumnNumMapper.SepaField.BENEFICIARY_IBAN) cell.setCellValue("BE49539007547085");
                    else cell.setCellValue("TEST");
                }
            }
            
            PainAllFields result = processPaymentsInputData.processGenericRecord(row, f);
            assertNotNull(result, "Failed for format: " + f);
            String errorMsg = result.getInputValidator() != null ? result.getInputValidator().getExceptionMessage(0) : "none";
            assertTrue(result.isValidRecord(), "Failed validity for format " + f + ". Details: " + errorMsg);
        }
    }
}
