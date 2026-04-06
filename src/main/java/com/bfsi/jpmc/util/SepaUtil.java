package com.bfsi.jpmc.util;

import com.bfsi.jpmc.service.ProcessSepaTransactions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

@Component
public class SepaUtil {

    private static final Logger logger = LoggerFactory.getLogger(SepaUtil.class);

    public static final String currentDirectory = System.getProperty("user.dir");
    public static final String fileSeparator = File.separator;

    @Value("${sepa.xml-root:<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.03\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">}")
    private String xmlROOT;
    
    @Value("${sepa.scheme-name:JPMCOID}")
    private String schemeName;
    
    @Value("${sepa.debtor-currency:EUR}")
    private String debtorCurrency;
    
    @Value("${sepa.jpmc-client-id:862759}")
    private String jpmcClientId;
    
    private Random random = new Random();


    public String getMsgId() {
        String timestamp = new SimpleDateFormat("yyyyMMdd'T'HHmm").format(new Date());
        int randomNum = 100000 + random.nextInt(900000);
        String msgId = jpmcClientId + timestamp + randomNum;
        logger.info("msgId :- " + jpmcClientId + timestamp + randomNum);
        return msgId;
    }

    @PostConstruct
    public void init() {
        logger.info("SEPA Configuration initialized:");
        logger.info("xmlROOT:: {}", xmlROOT);
        logger.info("schemeName:: {}", schemeName);
        logger.info("DEBTOR_CURRENCY:: {}", debtorCurrency);
        logger.info("JPMC_CLIENT_ID :: {}", jpmcClientId);
        
        // Set AppConstants for backward compatibility
        AppConstants.jpmcClientId = jpmcClientId;
        AppConstants.debtorCurrency = debtorCurrency;
        AppConstants.schemeName = schemeName;
    }
    
    // Getters for configuration values
    public String getXmlRoot() {
        return xmlROOT;
    }
    
    public String getSchemeName() {
        return schemeName;
    }
    
    public String getDebtorCurrency() {
        return debtorCurrency;
    }
    
    public String getJpmcClientId() {
        return jpmcClientId;
    }

    public String getDelimiter(File file) {
        String delimiter = "#";
        if (file.getName().endsWith(".csv")) {
            logger.info("Processing with .csv file :- " + file.getName());
            delimiter = ",";
        } else if (file.getName().endsWith(".txt")) {
            logger.info("Processing with .txt file :- " + file.getName());
            delimiter = "#";
        }
        return delimiter;
    }

    private static String getTodayDate() {
        return new SimpleDateFormat("yyyyMMdd").format(new Date());
    }


    public String getPmtInfoId() {
        String todayTimestamp = getTodayDate();
        int randomNum = 10 + random.nextInt(90);
        String debtorBan = ProcessSepaTransactions.debtorBan;
        if (debtorBan == null) {
            debtorBan = "UNKNOWN";
        }
        String PmtInfoId = debtorBan + todayTimestamp + randomNum;
        logger.info("PmtInfoId :- " + debtorBan + todayTimestamp + randomNum);
        return PmtInfoId;
    }

}
