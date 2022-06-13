package org.restcomm.protocols.ss7.tools.simulator.bootstrap;

import java.text.DecimalFormat;
import java.util.Date;

public class SmsResult {
    int noOfSms;
    long duration;

    public SmsResult(int noOfSms, long duration) {
        this.noOfSms = noOfSms;
        this.duration = duration;
    }
    public String toString(){
        return "No of sms: " + this.noOfSms + System.lineSeparator() +
                "Duration (ms): " + this.duration + System.lineSeparator()+
                "Duration (s): " + getSeconds() + System.lineSeparator() +
                "SMS/sec: " +  (new DecimalFormat("##.00")).format((double)this.noOfSms/ getSeconds()) ;
    }

    private long getSeconds() {
        return this.duration / 1000;
    }
}
