package com.bfsi.jpmc.service;

import com.bfsi.jpmc.util.SepaUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvTxtToExcelConverter {
    private static final Logger logger = LoggerFactory.getLogger(CsvTxtToExcelConverter.class);
    
    private final SepaUtil sepaUtil;
    
    @Autowired
    public CsvTxtToExcelConverter(SepaUtil sepaUtil) {
        this.sepaUtil = sepaUtil;
    }

    public String detectDelimiter(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String firstLine = br.readLine();
            if (firstLine == null || firstLine.trim().isEmpty()) {
                return ","; // Default to comma
            }
            
            // Count occurrences of common delimiters
            int commaCount = countOccurrences(firstLine, ',');
            int hashCount = countOccurrences(firstLine, '#');
            int semicolonCount = countOccurrences(firstLine, ';');
            int tabCount = countOccurrences(firstLine, '\t');
            
            // Return the delimiter with highest count
            int maxCount = Math.max(Math.max(commaCount, hashCount), Math.max(semicolonCount, tabCount));
            
            if (maxCount == 0) {
                return ","; // Default to comma if no delimiter found
            }
            
            if (commaCount == maxCount) return ",";
            if (hashCount == maxCount) return "#";
            if (semicolonCount == maxCount) return ";";
            if (tabCount == maxCount) return "\t";
            
            return ","; // Fallback to comma
        }
    }
    
    private int countOccurrences(String line, char delimiter) {
        int count = 0;
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter && !inQuotes) {
                count++;
            }
        }
        return count;
    }

    /**
     * Smart CSV parser that handles quoted fields containing delimiters
     * Example: "CALLE PRINCIPE, 123" will be treated as single field
     */
    public String[] parseCSVLine(String line, String delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                // Toggle quote state
                inQuotes = !inQuotes;
            } else if (c == delimiter.charAt(0) && !inQuotes) {
                // Delimiter found outside quotes - end of field
                result.add(currentField.toString().trim());
                currentField.setLength(0);
            } else {
                // Regular character
                currentField.append(c);
            }
        }
        
        // Add last field
        result.add(currentField.toString().trim());
        
        return result.toArray(new String[0]);
    }

    public Workbook convertToWorkbook(File file) throws IOException {

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Data");
        String[] values = new String[0];

        String delimiter = detectDelimiter(file);
        logger.info("Detected delimiter: '" + delimiter + "'");
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int rowNum = 0;
            while ((line = br.readLine()) != null) {
                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                Row row = sheet.createRow(rowNum++);
                // Use smart CSV parser that handles quoted fields
                values = parseCSVLine(line, delimiter);

                for (int i = 0; i < values.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(values[i].trim());
                    logger.debug("Cell [" + rowNum + "," + i + "] = " + values[i].trim());
                }
            }
        } catch (Exception e) {
            throw new IOException("Error converting file to workbook: " + e.getMessage(), e);
        }
        return workbook;
    }

    public static void createHeaderRow(Sheet sheet, CellStyle greyStyle) {
        Row row = sheet.createRow(0);
        Cell cell0 = row.createCell(0, CellType.STRING);
        cell0.setCellValue("");
        cell0.setCellStyle(greyStyle);

        Cell cell1 = row.createCell(1, CellType.STRING);
        cell1.setCellValue("Country Code");
        cell1.setCellStyle(greyStyle);

        Cell cell2 = row.createCell(2, CellType.STRING);
        cell2.setCellValue("Payment Method");
        cell2.setCellStyle(greyStyle);

        Cell cell3 = row.createCell(3, CellType.STRING);
        cell3.setCellValue("Debit Value Date");
        cell3.setCellStyle(greyStyle);

        Cell cell4 = row.createCell(4, CellType.STRING);
        cell4.setCellValue("");
        cell4.setCellStyle(greyStyle);


        Cell cell5 = row.createCell(5, CellType.STRING);
        cell5.setCellValue("");
        cell5.setCellStyle(greyStyle);

        Cell cell6 = row.createCell(6, CellType.STRING);
        cell6.setCellValue("");
        cell6.setCellStyle(greyStyle);

        Cell cell7 = row.createCell(7, CellType.STRING);
        cell7.setCellValue("Payment Type");
        cell7.setCellStyle(greyStyle);
        Cell cell8 = row.createCell(8, CellType.STRING);
        cell8.setCellValue("Transaction Currency");
        cell8.setCellStyle(greyStyle);
        Cell cell9 = row.createCell(9, CellType.STRING);
        cell9.setCellValue("Transaction Amount");
        cell9.setCellStyle(greyStyle);
        Cell cell10 = row.createCell(10, CellType.STRING);
        cell10.setCellValue("");
        cell10.setCellStyle(greyStyle);

        Cell cell11 = row.createCell(11, CellType.STRING);
        cell11.setCellValue("Debit Account");
        cell11.setCellStyle(greyStyle);
        Cell cell12 = row.createCell(12, CellType.STRING);
        cell12.setCellValue("");
        cell12.setCellStyle(greyStyle);
        Cell cell13 = row.createCell(13, CellType.STRING);
        cell13.setCellValue("");
        cell13.setCellStyle(greyStyle);
        Cell cell14 = row.createCell(14, CellType.STRING);
        cell14.setCellValue("");
        cell14.setCellStyle(greyStyle);
        Cell cell15 = row.createCell(15, CellType.STRING);
        cell15.setCellValue("");
        cell15.setCellStyle(greyStyle);
        Cell cell16 = row.createCell(16, CellType.STRING);
        cell16.setCellValue("");
        cell16.setCellStyle(greyStyle);
        Cell cell17 = row.createCell(17, CellType.STRING);
        cell17.setCellValue("");
        cell17.setCellStyle(greyStyle);
        Cell cell18 = row.createCell(18, CellType.STRING);
        cell18.setCellValue("");
        cell18.setCellStyle(greyStyle);
        Cell cell19 = row.createCell(19, CellType.STRING);
        cell19.setCellValue("");
        cell19.setCellStyle(greyStyle);
        Cell cell20 = row.createCell(20, CellType.STRING);
        cell20.setCellValue("");
        cell20.setCellStyle(greyStyle);

        Cell cell21 = row.createCell(21, CellType.STRING);
        cell21.setCellValue("");
        cell21.setCellStyle(greyStyle);
        Cell cell22 = row.createCell(22, CellType.STRING);
        cell22.setCellValue("Ordering Party Country Code");
        cell22.setCellStyle(greyStyle);

        Cell cell23 = row.createCell(23, CellType.STRING);
        cell23.setCellValue("");
        cell23.setCellStyle(greyStyle);
        Cell cell24 = row.createCell(24, CellType.STRING);
        cell24.setCellValue("");
        cell24.setCellStyle(greyStyle);
        Cell cell25 = row.createCell(25, CellType.STRING);
        cell25.setCellValue("Customer Reference Number");
        cell25.setCellStyle(greyStyle);

        Cell cell26 = row.createCell(26, CellType.STRING);
        cell26.setCellValue("");
        cell26.setCellStyle(greyStyle);
        Cell cell27 = row.createCell(27, CellType.STRING);
        cell27.setCellValue("");
        cell27.setCellStyle(greyStyle);
        Cell cell28 = row.createCell(28, CellType.STRING);
        cell28.setCellValue("");
        cell28.setCellStyle(greyStyle);
        Cell cell29 = row.createCell(29, CellType.STRING);
        cell29.setCellValue("Ordering Party Organisation ID - BIC or BEI");
        cell29.setCellStyle(greyStyle);

        Cell cell30 = row.createCell(30, CellType.STRING);
        cell30.setCellValue("");
        cell30.setCellStyle(greyStyle);
        Cell cell31 = row.createCell(31, CellType.STRING);
        cell31.setCellValue("");
        cell31.setCellStyle(greyStyle);
        Cell cell32 = row.createCell(32, CellType.STRING);
        cell32.setCellValue("");
        cell32.setCellStyle(greyStyle);
        Cell cell33 = row.createCell(33, CellType.STRING);
        cell33.setCellValue("");
        cell33.setCellStyle(greyStyle);
        Cell cell34 = row.createCell(34, CellType.STRING);
        cell34.setCellValue("");
        cell34.setCellStyle(greyStyle);
        Cell cell35 = row.createCell(35, CellType.STRING);
        cell35.setCellValue("");
        cell35.setCellStyle(greyStyle);
        Cell cell36 = row.createCell(36, CellType.STRING);
        cell36.setCellValue("Ordering Party Name");
        cell36.setCellStyle(greyStyle);

        Cell cell37 = row.createCell(37, CellType.STRING);
        cell37.setCellValue("Ordering Party Address");
        cell37.setCellStyle(greyStyle);

        Cell cell38 = row.createCell(38, CellType.STRING);
        cell38.setCellValue("");
        cell38.setCellStyle(greyStyle);
        Cell cell39 = row.createCell(39, CellType.STRING);
        cell39.setCellValue("");
        cell39.setCellStyle(greyStyle);
        Cell cell40 = row.createCell(40, CellType.STRING);
        cell40.setCellValue("");
        cell40.setCellStyle(greyStyle);

        Cell cell41 = row.createCell(41, CellType.STRING);
        cell41.setCellValue("");
        cell41.setCellStyle(greyStyle);
        Cell cell42 = row.createCell(42, CellType.STRING);
        cell42.setCellValue("Beneficiary Country Code");
        cell42.setCellStyle(greyStyle);
        Cell cell43 = row.createCell(43, CellType.STRING);
        cell43.setCellValue("Beneficiary Account Number");
        cell43.setCellStyle(greyStyle);
        Cell cell44 = row.createCell(44, CellType.STRING);
        cell44.setCellValue("Beneficiary Name");
        cell44.setCellStyle(greyStyle);
        Cell cell45 = row.createCell(45, CellType.STRING);
        cell45.setCellValue("");
        cell45.setCellStyle(greyStyle);
        Cell cell46 = row.createCell(46, CellType.STRING);
        cell46.setCellValue("");
        cell46.setCellStyle(greyStyle);
        Cell cell47 = row.createCell(47, CellType.STRING);
        cell47.setCellValue("");
        cell47.setCellStyle(greyStyle);
        Cell cell48 = row.createCell(48, CellType.STRING);
        cell48.setCellValue("");
        cell48.setCellStyle(greyStyle);
        Cell cell49 = row.createCell(49, CellType.STRING);
        cell49.setCellValue("");
        cell49.setCellStyle(greyStyle);
        Cell cell50 = row.createCell(50, CellType.STRING);
        cell50.setCellValue("Beneficiary Bank Routing Code");
        cell50.setCellStyle(greyStyle);

    }
}
