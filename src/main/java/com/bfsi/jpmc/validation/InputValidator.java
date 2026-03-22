package com.bfsi.jpmc.validation;

public class InputValidator {
    String exceptionMessage = null;

    public String getExceptionMessage(int rowNum) {

        return exceptionMessage;// + ". Mandatory field not provided in row " + ++rowNum;
    }

    public void setExceptionMessage(String nextExceptionMessage) {
        if (exceptionMessage == null) {
            this.exceptionMessage = nextExceptionMessage;
        } else {
            this.exceptionMessage = this.exceptionMessage + "," + nextExceptionMessage;
        }
    }
}
