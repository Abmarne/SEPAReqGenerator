package com.bfsi.jpmc.service;

import com.bfsi.jpmc.model.ColumnNumMapper;
import com.bfsi.jpmc.model.ColumnNumMapper.Format;
import com.bfsi.jpmc.model.ColumnNumMapper.SepaField;
import com.bfsi.jpmc.model.PainAllFields;
import com.bfsi.jpmc.validation.InputValidator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.iban4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@Service
public class ProcessPaymentsInputData {

    private static final Logger logger = LoggerFactory.getLogger(ProcessPaymentsInputData.class);

    private final ProcessSepaTransactions processSepaTransactions;
    private final CreateErrorRecord createErrorRecord;
    private final CsvTxtToExcelConverter csvTxtToExcelConverter;

    @Autowired
    public ProcessPaymentsInputData(ProcessSepaTransactions processSepaTransactions,
                                   CreateErrorRecord createErrorRecord,
                                   CsvTxtToExcelConverter csvTxtToExcelConverter) {
        this.processSepaTransactions = processSepaTransactions;
        this.createErrorRecord = createErrorRecord;
        this.csvTxtToExcelConverter = csvTxtToExcelConverter;
    }

    public void processPaymentInputData(String filePath) {
        File file = new File(filePath);
        processInputFile(file);
    }

    public void processInputFile(File file) {
        // Clear previous error records before processing new file
        CreateErrorRecord.clearErrorRecords();
        logger.info("Reading input file {}", file.getName());
        
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            processExcelFile(file);
        } else if (fileName.endsWith(".txt") || fileName.endsWith(".csv")) {
            processTxtCsvFile(file);
        } else {
            logger.error("Unsupported file format: " + fileName + ". Only .xlsx, .xls, .txt, and .csv files are supported.");
        }
    }

    private void processExcelFile(File file) {
        LinkedList<PainAllFields> transactionList = new LinkedList<>();
        try (InputStream br = new FileInputStream(file)) {
            Workbook workbook = new XSSFWorkbook(br);
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.rowIterator();
            if (rowIterator.hasNext()) {
                rowIterator.next();
            }
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                if (row.getRowNum() == 1 || row.getRowNum() == 2) {
                    continue;
                } else {
                    // Detect format for Excel - usually EXPANDED_EXCEL
                    Format format = detectRowFormat(row, true);
                    PainAllFields painAllFields = processGenericRecord(row, format);
                    if (painAllFields == null) {
                        continue;
                    }
                    if (!painAllFields.isValidRecord()) {
                        createErrorRecord.addErrorRecord(row.getRowNum(), painAllFields.getInputValidator().getExceptionMessage(row.getRowNum()));
                    } else {
                        transactionList.add(painAllFields);
                    }
                }
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        if (createErrorRecord.hasErrorRecords()) {
            createErrorRecord.createErrorFile();
            logger.warn("Validation errors found. Error file generated.");
            // createErrorRecord.createErrorFileExcel(file);
        }
        if (!transactionList.isEmpty()) {
            logger.info("Valid transactions found: {}", transactionList.size());
            processSepaTransactions.processTransactions(transactionList);
        } else {
            logger.warn("No valid transactions were found in the Excel file.");
        }
    }

    private void processTxtCsvFile(File file) {
        logger.info("Processing TXT/CSV file: {}", file.getName());
        
        try {
            // Convert TXT/CSV to Excel workbook format
            Workbook workbook = csvTxtToExcelConverter.convertToWorkbook(file);
            
            logger.info("Successfully converted TXT/CSV to workbook. Processing rows...");
            
            // Process the converted workbook using the same logic as Excel
            processConvertedWorkbook(workbook, file);
            
        } catch (IOException e) {
            logger.error("Error converting TXT/CSV file: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error processing TXT/CSV file: {}", e.getMessage(), e);
        }
    }

    private void processConvertedWorkbook(Workbook workbook, File originalFile) throws IOException {
        LinkedList<PainAllFields> transactionList = new LinkedList<>();
        Sheet sheet = workbook.getSheetAt(0);
        Iterator<Row> rowIterator = sheet.rowIterator();
        
        // Read original file to handle mixed-format rows
        List<String> originalLines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(originalFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    originalLines.add(line);
                }
            }
        }
        
        int rowNum = 0;
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            
            // Skip rows with insufficient columns (likely header/template rows)
            int lastCellNum = row.getLastCellNum();
            logger.debug("Row {} has {} cells", rowNum, lastCellNum);
            
            if (lastCellNum < 10) {
                logger.debug("Skipping row {} with only {} cells (likely header/template)", rowNum, lastCellNum);
                rowNum++;
                continue;
            }
            
            // Flexible processing of each row based on its detected format
            try {
                Format format = detectRowFormat(row, false);
                PainAllFields painAllFields = processGenericRecord(row, format);
                if (painAllFields != null && painAllFields.isValidRecord()) {
                    transactionList.add(painAllFields);
                } else if (painAllFields != null) {
                    createErrorRecord.addErrorRecord(rowNum, painAllFields.getInputValidator().getExceptionMessage(rowNum));
                }
            } catch (Exception e) {
                logger.error("Error processing row {}: {}", rowNum, e.getMessage());
                createErrorRecord.addErrorRecord(rowNum, "Processing error: " + e.getMessage());
            }
            rowNum++;
        }
        
        // Create error files if there are errors
        if (createErrorRecord.hasErrorRecords()) {
            createErrorRecord.createErrorFile();
            logger.warn("Validation errors found. Error file generated.");
            // Only create Excel error file for actual Excel files, not converted TXT/CSV
            // because the error record format doesn't match the expected delimiter-based format
            // createErrorRecord.createErrorFileExcel(originalFile);
        }
        
        // Process valid transactions
        if (!transactionList.isEmpty()) {
            logger.info("Valid transactions found: {}", transactionList.size());
            processSepaTransactions.processTransactions(transactionList);
        } else {
            logger.warn("No valid transactions were found in the text or CSV file.");
        }
        
        logger.info("TXT/CSV processing complete. Valid transactions: {}, Errors: {}", 
                   transactionList.size(), createErrorRecord.hasErrorRecords() ? "yes" : "no");
    }

    /**
     * Detects the row format based on cell count and placeholder presence.
     */
    private Format detectRowFormat(Row row, boolean isExcel) {
        if (isExcel) return Format.EXPANDED_EXCEL;
        
        int lastCell = row.getLastCellNum();
        
        // Compact format has ~19 fields
        if (lastCell > 0 && lastCell < 25) {
            return Format.COMPACT_TXT;
        }
        
        // Check for # placeholders which indicate Expanded TXT format
        int hashCount = 0;
        for (int i = 0; i < Math.min(lastCell, 20); i++) {
            Cell cell = row.getCell(i);
            String val = cell != null ? cell.toString().trim() : "null";
            if ("#".equals(val)) {
                hashCount++;
            }
        }
        
        return hashCount > 2 ? Format.EXPANDED_TXT : Format.EXPANDED_EXCEL;
    }

    /**
     * Extracts a cell value as string, handling numeric types and # placeholders.
     */
    private String getCellValue(Row row, SepaField sepaField, Format format) {
        Integer index = ColumnNumMapper.getIndex(sepaField, format);
        if (index == null || index < 0) return "";
        
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        
        String value = "";
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                value = BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
                if (value.endsWith(".0")) value = value.substring(0, value.length() - 2);
            } else {
                value = cell.getStringCellValue().trim();
            }
        } catch (Exception e) {
            value = cell.toString().trim();
        }
        
        // If the value is a # placeholder, treat it as empty for processing
        if ("#".equals(value)) return "";
        
        return value;
    }

    /**
     * Replaces both processTxtRecord and processRecord with a single flexible logic.
     * New fields can be added easily by adding to the SepaField enum and updating ColumnNumMapper.
     */
    public PainAllFields processGenericRecord(Row row, Format format) {
        boolean validDataFlag = true;
        PainAllFields painAllFields = new PainAllFields();
        InputValidator inputValidator = new InputValidator();
        StringBuilder missingFields = new StringBuilder();
        try {
            // Field 1: Country Code
            String countryCode = getCellValue(row, SepaField.COUNTRY_CODE, format);
            String amountStr = getCellValue(row, SepaField.AMOUNT, format);
            String debitAccount = getCellValue(row, SepaField.DEBIT_ACCOUNT, format);
            
            // If the core mandatory fields are all empty, this is likely an empty/template row
            if (countryCode.isEmpty() && amountStr.isEmpty() && debitAccount.isEmpty()) {
                logger.debug("Skipping empty row {}", row.getRowNum());
                return null;
            }
            
            if (countryCode.isEmpty()) {
                validDataFlag = false;
                missingFields.append("Country Code Missing, ");
                inputValidator(inputValidator, "Country Code Missing");
            } else {
                painAllFields.setCountryCode(countryCode);
            }
            
            // Field 2: Payment Method
            String paymentMethod = getCellValue(row, SepaField.PAYMENT_METHOD, format);
            if (paymentMethod.isEmpty()) {
                validDataFlag = false;
                missingFields.append("Payment Method Missing, ");
                inputValidator(inputValidator, "Payment Method Missing");
            } else {
                painAllFields.setPaymentMethod(paymentMethod);
            }
            
            // Field 3: Debit Value Date
            String debitDate = getCellValue(row, SepaField.DEBIT_DATE, format);
            if (debitDate.isEmpty()) {
                validDataFlag = false;
                missingFields.append("Debit Date Missing, ");
                inputValidator(inputValidator, "Debit Date Missing");
            } else {
                painAllFields.setDebitDate(debitDate);
            }
            
            // Field 4: Payment Type (Optional in some formats)
            String paymentType = getCellValue(row, SepaField.PAYMENT_TYPE, format);
            if (!paymentType.isEmpty()) {
                painAllFields.setPaymentType(paymentType);
            }
            
            // Field 5: Transaction Currency
            String currency = getCellValue(row, SepaField.CURRENCY, format);
            if (currency.isEmpty()) {
                validDataFlag = false;
                missingFields.append("Currency Missing, ");
                inputValidator(inputValidator, "Currency Missing");
            } else {
                if (currency.length() >= 3) {
                    painAllFields.setTransactionCurrency(currency.substring(0, 3));
                }
            }
            
            // Field 6: Transaction Amount
            if (amountStr.isEmpty()) {
                validDataFlag = false;
                missingFields.append("Amount Missing, ");
                inputValidator(inputValidator, "Amount Missing");
            } else {
                try {
                    BigDecimal amount = new BigDecimal(amountStr);
                    painAllFields.setTransactionAmount(amount.setScale(2));
                } catch (NumberFormatException e) {
                    validDataFlag = false;
                    missingFields.append("Amount Invalid, ");
                    inputValidator(inputValidator, "Amount Invalid");
                }
            }
            
            // Field 7: Debit Account (IBAN)
            if (debitAccount.isEmpty()) {
                validDataFlag = false;
                missingFields.append("Debit Account Missing, ");
                inputValidator(inputValidator, "Debit Account Missing");
            } else {
                try {
                    IbanUtil.validate(debitAccount);
                    painAllFields.setDebitAccount(debitAccount);
                } catch (Exception ex) {
                    validDataFlag = false;
                    missingFields.append("Debit Account Invalid, ");
                    inputValidator(inputValidator, "Debit Account Invalid");
                }
            }
            
            // Field 8: Ordering Party Country
            String opCountry = getCellValue(row, SepaField.ORDERING_PARTY_COUNTRY, format);
            if (opCountry.isEmpty() && format != Format.COMPACT_TXT) {
                // Ordering party country is required for expanded formats
                validDataFlag = false;
                inputValidator(inputValidator, "Ordering Party Country Missing");
            } else {
                painAllFields.setOrderingPartyCountryCode(opCountry);
            }
            
            // Field 9: Customer Reference Number
            String custRef = getCellValue(row, SepaField.CUSTOMER_REF, format);
            if (custRef.isEmpty()) {
                validDataFlag = false;
                inputValidator(inputValidator, "Customer Reference Missing");
            } else {
                painAllFields.setCustomerRefNum(custRef);
            }
            
            // Field 10: Ordering Party Org (BIC)
            String opOrg = getCellValue(row, SepaField.ORDERING_PARTY_ORG, format);
            if (!opOrg.isEmpty()) {
                try {
                    BicUtil.validate(opOrg);
                    painAllFields.setOrderingPartyOrg(opOrg);
                } catch (Exception e) {
                    // Log but maybe not fail if optional
                }
            }
            
            // Field 11: Ordering Party Name
            String opName = getCellValue(row, SepaField.ORDERING_PARTY_NAME, format);
            if (!opName.isEmpty()) {
                painAllFields.setOrderingPartyName(opName);
            }
            
            // Field 12: Ordering Party Address
            String opAddr = getCellValue(row, SepaField.ORDERING_PARTY_ADDRESS, format);
            if (!opAddr.isEmpty()) {
                painAllFields.setOrderingPartyAddress(opAddr);
            }
            
            // Field 13: Beneficiary IBAN
            String benIban = getCellValue(row, SepaField.BENEFICIARY_IBAN, format);
            if (benIban.isEmpty()) {
                validDataFlag = false;
                inputValidator(inputValidator, "Beneficiary Account Missing");
            } else {
                try {
                    IbanUtil.validate(benIban);
                    painAllFields.setBeneficiaryAccNo(benIban);
                } catch (Exception e) {
                    validDataFlag = false;
                    inputValidator(inputValidator, "Beneficiary Account Invalid");
                }
            }
            
            // Field 14: Beneficiary Name
            String benName = getCellValue(row, SepaField.BENEFICIARY_NAME, format);
            if (benName.isEmpty()) {
                validDataFlag = false;
                inputValidator(inputValidator, "Beneficiary Name Missing");
            } else {
                painAllFields.setBeneficiaryName(benName);
            }
            
            // Field 15: Beneficiary Address
            String benAddr = getCellValue(row, SepaField.BENEFICIARY_ADDRESS, format);
            if (!benAddr.isEmpty()) {
                painAllFields.setBeneficiaryAddress(benAddr);
            }
            
            // Field 16: Beneficiary City (Optional)
            String benCity = getCellValue(row, SepaField.BENEFICIARY_CITY, format);
            if (!benCity.isEmpty()) {
                painAllFields.setBeneficiaryCity(benCity);
            }
            
            // Field 17: Beneficiary Country
            String benCountry = getCellValue(row, SepaField.BENEFICIARY_COUNTRY, format);
            if (!benCountry.isEmpty()) {
                painAllFields.setBeneficiaryCntyCode(benCountry);
            }
            
            // Field 18: Beneficiary BIC
            String benBic = getCellValue(row, SepaField.BENEFICIARY_BIC, format);
            if (!benBic.isEmpty()) {
                try {
                    BicUtil.validate(benBic);
                    painAllFields.setBeneficiaryRoutingCode(benBic);
                } catch (Exception e) {
                    // Optional
                }
            }
            
            // Log missing fields
            if (missingFields.length() > 0) {
                logger.warn("Row {} format {} missing fields: {}", row.getRowNum(), format, missingFields);
            }
            
            painAllFields.setValidRecord(validDataFlag);
            painAllFields.setInputValidator(inputValidator);
            
        } catch (Exception e) {
            logger.error("Error processing record at row " + row.getRowNum(), e);
            painAllFields.setValidRecord(false);
            inputValidator(inputValidator, "Critical processing error: " + e.getMessage());
            painAllFields.setInputValidator(inputValidator);
        }
        
        return painAllFields;
    }

    private void inputValidator(InputValidator inputValidator, String exceptionMessage) {
        inputValidator.setExceptionMessage(exceptionMessage);
    }
}
