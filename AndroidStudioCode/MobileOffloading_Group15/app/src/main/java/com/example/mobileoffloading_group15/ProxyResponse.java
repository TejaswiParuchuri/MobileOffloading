package com.example.mobileoffloading_group15;

import java.io.Serializable;

public class ProxyResponse implements Serializable {

    /*
    This helps in serialization of the data that needs to be sent the master by slave
    Slave will send an object of Proxy response to master which has 1 Row result of output and row number
    On master receiving this object store the result of the row vector in the row number based on row number sent by slave
    */
    private int[] rowResult;
    private int row;
    private String deviceName;

    //constructor
    public ProxyResponse(int[] rowResult, int row, String deviceName) {
        this.setrowResult(rowResult);
        this.setRow(row);
        this.setDeviceName(deviceName);
    }

    //getter methods
    public int[] getrowResult() {
        return rowResult;
    }

    public void setrowResult(int[] rowResult) {
        this.rowResult = rowResult;
    }



    public int getRow() {
        return row;
    }

    //setter methods

    public void setRow(int row) {
        this.row = row;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }
}
