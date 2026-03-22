package com.bfsi.jpmc.model;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class ColumnNumMapper {
    
    public enum Format {
        COMPACT_TXT,    // The 19-field format
        EXPANDED_EXCEL, // The standard Excel format
        EXPANDED_TXT    // The TXT format with # placeholders (matches line 2 of input)
    }

    public enum SepaField {
        COUNTRY_CODE,
        PAYMENT_METHOD,
        DEBIT_DATE,
        PAYMENT_TYPE,
        CURRENCY,
        AMOUNT,
        DEBIT_ACCOUNT,
        ORDERING_PARTY_COUNTRY,
        CUSTOMER_REF,
        ORDERING_PARTY_ORG,
        ORDERING_PARTY_NAME,
        ORDERING_PARTY_ADDRESS,
        BENEFICIARY_COUNTRY,
        BENEFICIARY_IBAN,
        BENEFICIARY_NAME,
        BENEFICIARY_ADDRESS,
        BENEFICIARY_CITY,
        BENEFICIARY_BIC
    }

    // Map Format -> { SepaField -> Integer (0-based index) }
    private static final Map<Format, Map<SepaField, Integer>> formatMappings = new EnumMap<>(Format.class);
    
    static {
        // --- COMPACT_TXT (19 fields, 0-based) ---
        Map<SepaField, Integer> compact = new EnumMap<>(SepaField.class);
        compact.put(SepaField.COUNTRY_CODE, 0);
        compact.put(SepaField.PAYMENT_METHOD, 1);
        compact.put(SepaField.DEBIT_DATE, 2);
        compact.put(SepaField.CURRENCY, 4);
        compact.put(SepaField.AMOUNT, 5);
        compact.put(SepaField.DEBIT_ACCOUNT, 6);
        compact.put(SepaField.ORDERING_PARTY_COUNTRY, 7);
        compact.put(SepaField.CUSTOMER_REF, 8);
        compact.put(SepaField.ORDERING_PARTY_ORG, 9);
        compact.put(SepaField.ORDERING_PARTY_NAME, 10);
        compact.put(SepaField.ORDERING_PARTY_ADDRESS, 11);
        compact.put(SepaField.BENEFICIARY_IBAN, 13);
        compact.put(SepaField.BENEFICIARY_NAME, 14);
        compact.put(SepaField.BENEFICIARY_ADDRESS, 15);
        compact.put(SepaField.BENEFICIARY_CITY, 16);
        compact.put(SepaField.BENEFICIARY_COUNTRY, 17);
        compact.put(SepaField.BENEFICIARY_BIC, 18);
        formatMappings.put(Format.COMPACT_TXT, compact);

        // --- EXPANDED_EXCEL (Standard, 1-based in old code, let's use 0-based here) ---
        Map<SepaField, Integer> expanded = new EnumMap<>(SepaField.class);
        expanded.put(SepaField.COUNTRY_CODE, 1);
        expanded.put(SepaField.PAYMENT_METHOD, 3);
        expanded.put(SepaField.DEBIT_DATE, 5);
        expanded.put(SepaField.PAYMENT_TYPE, 10);
        expanded.put(SepaField.CURRENCY, 12);
        expanded.put(SepaField.AMOUNT, 13);
        expanded.put(SepaField.DEBIT_ACCOUNT, 16);
        expanded.put(SepaField.ORDERING_PARTY_COUNTRY, 29);
        expanded.put(SepaField.CUSTOMER_REF, 33);
        expanded.put(SepaField.ORDERING_PARTY_ORG, 38);
        expanded.put(SepaField.ORDERING_PARTY_NAME, 46);
        expanded.put(SepaField.ORDERING_PARTY_ADDRESS, 48);
        expanded.put(SepaField.BENEFICIARY_COUNTRY, 54);
        expanded.put(SepaField.BENEFICIARY_IBAN, 56);
        expanded.put(SepaField.BENEFICIARY_NAME, 58);
        expanded.put(SepaField.BENEFICIARY_ADDRESS, 61);
        expanded.put(SepaField.BENEFICIARY_CITY, 62);
        expanded.put(SepaField.BENEFICIARY_BIC, 64);
        formatMappings.put(Format.EXPANDED_EXCEL, expanded);

        // --- EXPANDED_TXT (Matches line 2 of input, with # placeholders) ---
        Map<SepaField, Integer> expandedTxt = new EnumMap<>(SepaField.class);
        expandedTxt.put(SepaField.COUNTRY_CODE, 1);
        expandedTxt.put(SepaField.PAYMENT_METHOD, 3);
        expandedTxt.put(SepaField.DEBIT_DATE, 5);
        expandedTxt.put(SepaField.PAYMENT_TYPE, 10);
        expandedTxt.put(SepaField.CURRENCY, 12);
        expandedTxt.put(SepaField.AMOUNT, 14);
        expandedTxt.put(SepaField.DEBIT_ACCOUNT, 17);
        expandedTxt.put(SepaField.ORDERING_PARTY_COUNTRY, 29);
        expandedTxt.put(SepaField.CUSTOMER_REF, 33);
        expandedTxt.put(SepaField.ORDERING_PARTY_ORG, 38);
        expandedTxt.put(SepaField.ORDERING_PARTY_NAME, 46);
        expandedTxt.put(SepaField.ORDERING_PARTY_ADDRESS, 48);
        expandedTxt.put(SepaField.BENEFICIARY_COUNTRY, 54);
        expandedTxt.put(SepaField.BENEFICIARY_IBAN, 56);
        expandedTxt.put(SepaField.BENEFICIARY_NAME, 58);
        expandedTxt.put(SepaField.BENEFICIARY_ADDRESS, 61);
        expandedTxt.put(SepaField.BENEFICIARY_CITY, 62);
        expandedTxt.put(SepaField.BENEFICIARY_BIC, 64);
        formatMappings.put(Format.EXPANDED_TXT, expandedTxt);
    }

    /**
     * Get index for a field in a specific format
     */
    public static Integer getIndex(SepaField field, Format format) {
        Map<SepaField, Integer> mapping = formatMappings.get(format);
        if (mapping == null) return -1;
        return mapping.getOrDefault(field, -1);
    }

    /**
     * Add or update mapping dynamically - for upcoming fields
     */
    public static void updateMapping(Format format, SepaField field, int index) {
        formatMappings.computeIfAbsent(format, k -> new EnumMap<>(SepaField.class))
                      .put(field, index);
    }
}

