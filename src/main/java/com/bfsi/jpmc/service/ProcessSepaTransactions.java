package com.bfsi.jpmc.service;


import com.bfsi.jpmc.model.PainAllFields;

import com.bfsi.jpmc.util.SepaUtil;
import io.inisos.bank4j.*;
import io.inisos.bank4j.impl.SimpleTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.bfsi.jpmc.util.SepaUtil.*;


@Service
public class ProcessSepaTransactions {
    private static final Logger logger = LoggerFactory.getLogger(ProcessSepaTransactions.class);
    
    private final SepaUtil sepaUtil;
    private final SepaFileService sepaFileService;
    
    @Autowired
    public ProcessSepaTransactions(
            SepaUtil sepaUtil,
            SepaFileService sepaFileService
    ) {
        this.sepaUtil = sepaUtil;
        this.sepaFileService = sepaFileService;
    }
    public static String debtorBan;
    static String debtorAgtBic;
    private String orderingPartyAdrs;
    private String orderingPartyAdrs1;
    private String orderingPartyAdrs2;
    private String debtorCountryCode;
    private String debtorAgentCountryCode;
    private String orderingPartyName;
    private LocalDate debitDate;

    public static boolean isXmlSuccess = false;

    public void processTransactions(LinkedList<PainAllFields> inputTransactionList) {
        logger.info("Building SEPA transactions for {} record(s).", inputTransactionList.size());
        List<Transaction> processedTransactions = new LinkedList<>();
        for (PainAllFields txnRecord : inputTransactionList) {
            Transaction transaction = processTransaction(txnRecord);
            processedTransactions.add(transaction);
        }
        HashMap<String, Object> debtorInfo = processDebtorInformation(processedTransactions);
        buildFinalTransactionStructure(processedTransactions, debtorInfo);
    }

    public Transaction processTransaction(PainAllFields painAllFields) {
        Transaction transaction = null;
        try {

            if (debtorBan == null) {
                debtorBan = painAllFields.getDebitAccount();
            }
            if (debtorAgtBic == null) {
                debtorAgtBic = painAllFields.getOrderingPartyOrg(); //added all the fields
            }
            if (orderingPartyAdrs == null) {
                orderingPartyAdrs = painAllFields.getOrderingPartyAddress();
            }
            if (orderingPartyAdrs1 == null) {
                orderingPartyAdrs1 = painAllFields.getOrderingPartyAddress_line_1();
            }
            if (orderingPartyAdrs2 == null) {
                orderingPartyAdrs2 = painAllFields.getOrderingPartyAddress_line_2();
            }
            if (debtorCountryCode == null) {
                debtorCountryCode = painAllFields.getOrderingPartyCountryCode();
            }
            if (orderingPartyName == null) {
                orderingPartyName = painAllFields.getOrderingPartyName();
            }
            String dateString = painAllFields.getDebitDate();
            DateTimeFormatter inputformat = DateTimeFormatter.ofPattern("yyyyMMdd");
//            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            debitDate = LocalDate.parse(dateString, inputformat);


            String beneficiaryAddress = painAllFields.getBeneficiaryAddress();

            // Transactions
            transaction = Bank.simpleTransaction()
                    .party(Bank.simpleParty()           // Optional creditor identification
                            .name(painAllFields.getBeneficiaryName())                 // Optional name
//                                    .name(JPMC_CLIENT_NAME)                 // Optional name
                            .postalAddress(Bank.simplePostalAddress() // Optional postal address
                                    .addressLine(beneficiaryAddress)
                                    //.streetName(street)
                                    //.buildingNumber(bldgNm)
                                    //.postCode(pstCd)
                                    .townName(painAllFields.getBeneficiaryCity())
                                    //.addressLine(painAllFields.getBeneficiaryAddress_line_1())
                                    //.addressLine(painAllFields.getBeneficiaryAddress_line_2())
                                    .country(painAllFields.getBeneficiaryCntyCode())
                                    .build())
                            .build())
                    .account(Bank.simpleBankAccount()   // Creditor account
                            .iban(painAllFields.getBeneficiaryAccNo()) // IBAN
                            .bic(painAllFields.getBeneficiaryRoutingCode())               // Optional BIC
                            .build())
                    .amount(painAllFields.getTransactionAmount())                                // Amount, converted to BigDecimal
                    .currency(painAllFields.getTransactionCurrency())                               // Currency code
                    .endToEndId(painAllFields.getCustomerRefNum())             // End to end identifier
//                    .id("Optional identifier 1")                    // Optional Transaction identifier
//                     <Client’s JPM Company Identifier ><YYYYMMDDTHHMM><unique value of 6 digits>
//                    .chargeBearerCode(ChargeBearerType1Code.CRED)   // Optional charge bearer code defines who is bearing the charges of the transfer
                    //.remittanceInformationUnstructured(Collections.singleton("Your remittance information"))   // Unstructured Remittance Information
                    .build();

            logger.info("Transaction In process :----  " + transaction);
        } catch (Exception ex) {
            logger.error("Exception ", ex);
        }
        return transaction;
    }

    public HashMap<String, Object> processDebtorInformation(List<Transaction> processedTransactions) {
        HashMap<String, Object> debtorInfo = new HashMap<>();
        SimpleTransaction transaction = null;
        Party debtor = Bank.simpleParty()
                .name(orderingPartyName) // Optional name
                .postalAddress(Bank.simplePostalAddress() // Optional postal address
                        .addressLine(orderingPartyAdrs)// this is debtor address 1
//                        .addressLine(orderingPartyAdrs1)// this is debtor address 1
//                        .addressLine(orderingPartyAdrs2)// this is debtor address 2
                        .country(debtorCountryCode).build())
                .build();
        debtorInfo.put("debtor", debtor);
        if(debtorBan != null) {
            BankAccount debtorAccount = Bank.simpleBankAccount()
                    .iban(debtorBan)                  // IBAN (DbtrAcct)
                    .bic(debtorAgtBic)               // Optional BIC of Debtor Agent(DbtrAgt)
                    .build();
            debtorInfo.put("debtorAccount", debtorAccount);
        }else{
            try {
                BankAccount debtorAccount = Bank.simpleBankAccount()
                        .bic(debtorAgtBic)               // Optional BIC of Debtor Agent(DbtrAgt)
                        .build();
                debtorInfo.put("debtorAccount", debtorAccount);
            } catch (Exception ex) {
                logger.error("Exception ", ex);
            }
        }



        logger.info("Process Debtor Information End");
        return debtorInfo;
    }


    public void buildFinalTransactionStructure(List<Transaction> creditorTransInfo, HashMap<String, Object> debtorInfo) {
        Party debtor = (Party) debtorInfo.get("debtor");
        BankAccount debtorAccount = (BankAccount) debtorInfo.get("debtorAccount");


        try {
            CreditTransferOperation creditTransfer = Bank.jaxbCreditTransferSepa()
                    .debtor(debtor)                                      // Optional debtor
                    .debtorAccount(debtorAccount)                        // Mandatory debtor account
                    .transactions(creditorTransInfo)                          // At least 1 transaction
                    // Optional additional transaction
                    .creationDateTime(LocalDateTime.now())               // Optional message creation date and time, defaults to now
//                   .creationDateTime(debitDate.atStartOfDay())               // Optional message creation date and time, defaults to now
//                   .requestedExecutionDate(LocalDate.now().plusDays(1)) // Optional requested execution date, defaults to tomorrow
                    .requestedExecutionDate(debitDate) // Optional requested execution date, defaults to tomorrow
                    .id(sepaUtil.getMsgId())                            //MsgId              // Optional identifier, defaults to creation date and time as yyyyMMddhhmmss
//                .chargeBearerCode(ChargeBearerType1Code.DEBT)        // Optional charge bearer code defines who is bearing the charges of the transfer
                    .build();

            // export to string

            String formattedOutput = creditTransfer.marshal(false); // true: enables formatting

            formattedOutput = formattedOutput.replace("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.03\">",
                    sepaUtil.getXmlRoot());
            generateSepaXml(formattedOutput);
            logger.info("SEPA XML structure built successfully.");


        } catch (Exception ex) {
            logger.error("Exception ", ex);
        }
    }

    private void generateSepaXml(String formattedOutput) {

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = "STA_" + timestamp + ".xml";
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setIgnoringElementContentWhitespace(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

            InputSource inputSource = new InputSource(new StringReader(formattedOutput));
            Document doc = dBuilder.parse(inputSource);
            doc.getDocumentElement().normalize();

            Node dbtrNode = doc.getElementsByTagName("Dbtr").item(0);
            Node dbtrAcctNode = doc.getElementsByTagName("DbtrAcct").item(0);
            Node PmtInf = doc.getElementsByTagName("PmtInf").item(0);
            Node ChrgBrNode = doc.getElementsByTagName("ChrgBr").item(0);
            Node PmtInfIdNode = doc.getElementsByTagName("PmtInfId").item(0);

            Node InitgPtyNode = doc.getElementsByTagName("InitgPty").item(0);
            Node PstlAdrNode = doc.getElementsByTagName("PstlAdr").item(0);


            if (dbtrNode == null) {
                logger.info("Dbtr is null");
            } else {
                Element id = doc.createElement("Id");
                Element orgId = doc.createElement("OrgId");
                Element othr = doc.createElement("Othr");
                Element othrId = doc.createElement("Id");
                othrId.setTextContent(sepaUtil.getJpmcClientId());
                Element schmeNm = doc.createElement("SchmeNm");
                Element prtry = doc.createElement("Prtry");
                prtry.setTextContent(sepaUtil.getSchemeName());
                othr.appendChild(othrId);
                schmeNm.appendChild(prtry);
                othr.appendChild(schmeNm);
                orgId.appendChild(othr);
                id.appendChild(orgId);
                dbtrNode.appendChild(id);

                if (dbtrAcctNode == null) {
                    logger.info("Dbtr Acct is null");
                } else {
                    Element ccy = doc.createElement("Ccy");
                    ccy.setTextContent(sepaUtil.getDebtorCurrency());
                    dbtrAcctNode.appendChild(ccy);
                    if (PmtInf == null) {
                        logger.info("PmtInf is null");
                    } else {

//                        PmtInf.appendChild(PmtInf.removeChild(ChrgBrNode));
                        PmtInf.removeChild(ChrgBrNode);
                        PmtInfIdNode.setTextContent(sepaUtil.getPmtInfoId());


                        if (InitgPtyNode == null) {
                            logger.info("InitgPtyNode is null");
                        } else {
                            InitgPtyNode.removeChild(PstlAdrNode);
                            // Write to XML file
                            TransformerFactory transformerFactory = TransformerFactory.newInstance();
                            Transformer transformer = transformerFactory.newTransformer();
                            DOMSource source = new DOMSource(doc);
                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                            StreamResult result = new StreamResult(outputStream);
                            transformer.transform(source, result);
                            sepaFileService.saveGeneratedFile(fileName, "application/xml", outputStream.toByteArray());
                            logger.info("Generated output file {}", fileName);

                        }
                    }
                }
            }
            logger.info("Successfully generated {}", fileName);
        } catch (IOException e) {
            logger.error("An error occurred while generating " + fileName, e);
        } catch (ParserConfigurationException e) {
            logger.error("An error occurred while generating " + fileName, e);
            throw new RuntimeException(e);
        } catch (SAXException e) {
            logger.error("An error occurred while generating " + fileName, e);
            throw new RuntimeException(e);
        } catch (TransformerConfigurationException e) {
            logger.error("An error occurred while generating " + fileName, e);
            throw new RuntimeException(e);
        } catch (TransformerException e) {
            logger.error("An error occurred while generating " + fileName, e);
            throw new RuntimeException(e);
        }
    }
}
