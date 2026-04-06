package com.bfsi.jpmc.service;


import com.bfsi.jpmc.util.SepaUtil;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook; // For .xlsx files
import org.iban4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

@Service
public class CreateErrorRecord {
    private static final Logger logger = LoggerFactory.getLogger(CreateErrorRecord.class);
    static LinkedList<String> errorRecords = new LinkedList<>();
    static String allErrors = "";
    
    private final SepaUtil sepaUtil;
    private final SepaFileService sepaFileService;
    private final ProcessingLogService processingLogService;
    
    @Autowired
    public CreateErrorRecord(
            SepaUtil sepaUtil,
            SepaFileService sepaFileService,
            ProcessingLogService processingLogService
    ) {
        this.sepaUtil = sepaUtil;
        this.sepaFileService = sepaFileService;
        this.processingLogService = processingLogService;
    }

    public void createErrorFile() {
        for (String error : errorRecords) {
            allErrors = allErrors + System.lineSeparator() + error;
        }
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String fileName = "ERR_" + timestamp + ".txt";
        sepaFileService.saveGeneratedFile(fileName, "text/plain", allErrors.getBytes(StandardCharsets.UTF_8));
        logger.debug("Successfully generated {}", fileName);
        processingLogService.warn("Generated error file " + fileName);
    }

    public static boolean hasErrorRecords() {
        return !errorRecords.isEmpty();
    }

    public static void addErrorRecord(int rowNum, String painAllFields) {
        errorRecords.add("Row Num-" + rowNum  + ":: Error Message- " + painAllFields);
    }
    
    public static void clearErrorRecords() {
        errorRecords.clear();
        allErrors = "";
    }

    public void createErrorFileExcel(File file) {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String fileName = "ERR_" + timestamp + ".xlsx";
        String delimiter = sepaUtil.getDelimiter(file);
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Data");

        CellStyle greyStyle = workbook.createCellStyle();
        greyStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        greyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CsvTxtToExcelConverter.createHeaderRow(sheet, greyStyle);


        CellStyle redStyle = workbook.createCellStyle();
        redStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
        redStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        int rowNum = 1;
        for (String error : errorRecords) {
            String errorRowNum = error.substring(0, error.indexOf("::"));
            int indexBeforeErrorString = error.lastIndexOf("::");
            String inputString = error.substring(0, indexBeforeErrorString);
            createRecord(inputString, delimiter, sheet, rowNum, errorRowNum, redStyle);
            rowNum = ++rowNum;
        }

        try (ByteArrayOutputStream writer = new ByteArrayOutputStream()) {
            workbook.write(writer);
            sepaFileService.saveGeneratedFile(
                    fileName,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    writer.toByteArray()
            );
            logger.debug("Successfully generated {}", fileName);
            processingLogService.warn("Generated error workbook " + fileName);
        } catch (IOException e) {
            logger.debug("An error occurred while generating " + fileName + ": " + e.getMessage());
            processingLogService.error("Failed to generate error workbook " + fileName + ": " + e.getMessage());
        }
    }

    public static void createRecord(String inputString, String delimiter, Sheet sheet, int rowNum, String errorRowNum, CellStyle redStyle) {
        Row row = sheet.createRow(rowNum);
        String[] record = new String[0];
        record = inputString.split(delimiter, -1);
        
        if (record.length < 2) {
            logger.warn("Cannot create detailed Excel error record: input string doesn't match expected format. Input: {}", inputString);
            return;
        }

        Cell errorRowNumCell = row.createCell(0);
        if (errorRowNum.isEmpty()) {
            errorRowNumCell.setCellStyle(redStyle);
        } else {
            logger.info("0. ErrorRowNum :- " + errorRowNum);
            errorRowNumCell.setCellValue(errorRowNum);
        }

        String countryCode = record[1];
        Cell countryCodeCell = row.createCell(1);
        if (countryCode.isEmpty()) {
            countryCodeCell.setCellStyle(redStyle);
        } else {
            logger.info("1. countryCode :- " + countryCode);
            countryCodeCell.setCellValue(countryCode);
        }

        String paymentMethod = record[2];
        Cell paymentMethodCell = row.createCell(2);
        if (paymentMethod.isEmpty()) {
            paymentMethodCell.setCellStyle(redStyle);
        } else {
            logger.info("2. paymentMethod :- " + paymentMethod);
            paymentMethodCell.setCellValue(paymentMethod);
        }


        String debitDate = record[3];
        Cell debitDateCell = row.createCell(3);
        if (debitDate.isEmpty()) {
            debitDateCell.setCellStyle(redStyle);
        } else {
            debitDateCell.setCellValue(debitDate);
            logger.info("3. debitDate :- " + debitDate);
        }
        String paymentType = record[7];
        Cell paymentTypeCell = row.createCell(7);
        if (paymentType.isEmpty()) {
            paymentTypeCell.setCellStyle(redStyle);
        } else {
            paymentTypeCell.setCellValue(paymentType);
        }

        String transactionCurrency = record[8];
        Cell transactionCurrencyCell = row.createCell(8);
        if (transactionCurrency.isEmpty()) {
            transactionCurrencyCell.setCellStyle(redStyle);
        } else {
            transactionCurrencyCell.setCellValue(transactionCurrency);
            logger.info("8. transactionCurrency :- " + transactionCurrency);
        }

        String transactionAmount = record[9];
        Cell transactionAmountCell = row.createCell(9);
        if (transactionAmount.isEmpty()) {
            transactionAmountCell.setCellStyle(redStyle);
        } else {
            try {
                BigDecimal number = BigDecimal.valueOf(Long.parseLong(transactionAmount));
                transactionAmountCell.setCellValue(transactionAmount);
            } catch (NumberFormatException e) {
                transactionAmountCell.setCellValue(transactionAmount);
                transactionAmountCell.setCellStyle(redStyle);
            }

        }


        String debitAccountIBAN = record[11];
        Cell debitAccountIbanCell = row.createCell(11);
        if (debitAccountIBAN.isEmpty()) {
            debitAccountIbanCell.setCellStyle(redStyle);
        } else {
            try {
                IbanUtil.validate(debitAccountIBAN);
                debitAccountIbanCell.setCellValue(debitAccountIBAN);
            } catch (IbanFormatException | InvalidCheckDigitException | UnsupportedCountryException ex) {
                logger.error("IBAN is invalid: ", ex);
                debitAccountIbanCell.setCellValue(debitAccountIBAN);
                debitAccountIbanCell.setCellStyle(redStyle);
            } catch (Exception ex) {
                logger.error("IBAN is invalid: ", ex);
            }

        }
        String orderingPartyCountryCode = record[22];
        Cell orderingPartyCountryCodeCell = row.createCell(22);
        if (orderingPartyCountryCode.isEmpty()) {
            orderingPartyCountryCodeCell.setCellStyle(redStyle);
        } else {
            orderingPartyCountryCodeCell.setCellValue(orderingPartyCountryCode);
        }


        String customerReferNum = record[25];
        Cell customerReferNumCell = row.createCell(25);
        if (customerReferNum.isEmpty()) {
            customerReferNumCell.setCellStyle(redStyle);
        } else {
            customerReferNumCell.setCellValue(customerReferNum);
        }

        String orderingPartyOrganization = record[29];
        Cell orderingPartyOrganizationCell = row.createCell(29);
        if (orderingPartyOrganization.isEmpty()) {
            orderingPartyOrganizationCell.setCellStyle(redStyle);
        } else {
            orderingPartyOrganizationCell.setCellValue(orderingPartyOrganization);
        }


        String orderingPartyName = record[36];
        Cell orderingPartyNameCell = row.createCell(36);
        if (orderingPartyName.isEmpty()) {
            orderingPartyNameCell.setCellStyle(redStyle);
        } else {
            orderingPartyNameCell.setCellValue(orderingPartyName);
        }


        String orderingPartyAddress = record[37];
        Cell orderingPartyAddressCell = row.createCell(37);
        if (orderingPartyAddress.isEmpty()) {
            orderingPartyAddressCell.setCellStyle(redStyle);
        } else {
            orderingPartyAddressCell.setCellValue(orderingPartyAddress);
        }


        String beneficiaryCountryCode = record[42];
        Cell beneficiaryCountryCodeCell = row.createCell(42);
        if (beneficiaryCountryCode.isEmpty()) {
            beneficiaryCountryCodeCell.setCellStyle(redStyle);
        } else {
            beneficiaryCountryCodeCell.setCellValue(beneficiaryCountryCode);
        }


        String beneficiaryAccountNumber = record[43];
        Cell beneficiaryAccountNumberCell = row.createCell(43);
        if (beneficiaryAccountNumber.isEmpty()) {
            beneficiaryAccountNumberCell.setCellStyle(redStyle);
        } else {
            try {
                IbanUtil.validate(beneficiaryAccountNumber);
                beneficiaryAccountNumberCell.setCellValue(beneficiaryAccountNumber);
            } catch (IbanFormatException | InvalidCheckDigitException | UnsupportedCountryException ex) {
                logger.error("IBAN is invalid: " + ex);
                beneficiaryAccountNumberCell.setCellValue(beneficiaryAccountNumber);
                beneficiaryAccountNumberCell.setCellStyle(redStyle);
            } catch (Exception ex) {
                logger.error("IBAN is invalid:", ex);
            }

        }


        String beneficiaryAccountName = record[44];
        Cell benificiaryAccountNameCell = row.createCell(44);
        if (beneficiaryAccountName.isEmpty()) {
            benificiaryAccountNameCell.setCellStyle(redStyle);
        } else {
            benificiaryAccountNameCell.setCellValue(beneficiaryAccountName);
        }

        String beneficiaryBankCode = "";
        Cell beneficiaryBankCodeCell = null;
        try {


            beneficiaryBankCode = record[50];
            beneficiaryBankCodeCell = row.createCell(50);
            if (beneficiaryBankCode.isEmpty()) {
                beneficiaryBankCodeCell.setCellStyle(redStyle);
            } else {

                try {
                    BicUtil.validate(beneficiaryBankCode);
                    beneficiaryBankCodeCell.setCellValue(beneficiaryBankCode);
                } catch (BicFormatException ex) {
                    logger.error("BeneficiaryBankCode is invalid: " + ex);
                    beneficiaryBankCodeCell.setCellValue(beneficiaryBankCode);
                    beneficiaryBankCodeCell.setCellStyle(redStyle);
                } catch (Exception ex) {
                    logger.error("BeneficiaryBankCode is invalid: ", ex);

                }
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
            logger.error("BeneficiaryBankCode is invalid: " + ex);
            beneficiaryBankCodeCell.setCellValue(beneficiaryBankCode);
            beneficiaryBankCodeCell.setCellStyle(redStyle);
        } catch (Exception ex) {
            logger.error("BeneficiaryBankCode is invalid: ", ex);

        }

    }

}
