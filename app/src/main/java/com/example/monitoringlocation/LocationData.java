package com.example.monitoringlocation;

import com.google.gson.annotations.SerializedName;


public class LocationData {
    @SerializedName("personId")
    private int personId;

    @SerializedName("time")
    private String time;

    @SerializedName("latitude")
    private double latitude;

    @SerializedName("longitude")
    private double longitude;

    public LocationData(int personId, String time, double latitude, double longitude) {
        this.personId = personId;
        this.time = time;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public int getToken() {
        return personId;
    }

    public void setToken(int personId) {
        this.personId = personId;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}
