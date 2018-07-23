package ru.vkatzyn.geofencing_test_app;

public class Geodata {
    private double latitude;
    private double longitude;
    private float radius;
    private int id;

    public Geodata(double latitude, double longitude, float radius, int id) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
        this.id = id;
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

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    public int getId() {
        return id;
    }

    public String toString() {
        return latitude + " " + longitude + " " + radius + " " + id;
    }
}
