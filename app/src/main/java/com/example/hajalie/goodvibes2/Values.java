package com.example.hajalie.goodvibes2;

/**
 * Created by Carl on 11/15/2015.
 */
public class Values {
    static final int REQUEST_CODE_ACCESS_FINE_LOCATION = 1;
    static final int LOCATION_BUFFER = 20; // meters
    static final String ROUTE_TOO_LONG = "ERROR: The destination you entered is too far away.";
    static final String UNKNOWN_ERROR = "ERROR: Unknown error.";
    static final String LOCATION_ERROR = "ERROR: Cannot determine your location.";
    static final String DESTINATION_ERROR = "ERROR: Invalid destination.";
    static final String PATH_ERROR = "ERROR: No paths for this destination.";
    static final String ARRIVE_AT_DESTINATION = "Congratulations! You have arrived at your destination.";
    static final String PLACES_ERROR = "Error: Could not find your destination";
    static final int    FRONT = 2,
                        FRONT_RIGHT = 5,
                        RIGHT = 4,
                        BACK_RIGHT = 8,
                        BACK = 3,
                        BACK_LEFT = 7,
                        LEFT = 1,
                        FRONT_LEFT = 6,
                        ALL_DIRECTIONS = 9;
}

