package com.samaun.ticktracker.reference;

public record CityResponse(String name, String region, double latitude, double longitude) {

    public static CityResponse from(UkCity city) {
        return new CityResponse(city.displayName(), city.region(), city.latitude(), city.longitude());
    }
}
