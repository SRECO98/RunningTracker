package com.example.runningtracker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
/* LOCATION VARIABLES AND CONSTANT */
    static final int REQUEST_CHECK_SETTINGS = 2;
    static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;

    private LocationRequest locationRequest;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;

    private SettingsClient settingsClient;
    private LocationSettingsRequest.Builder settingsRequestBuilder;

    private Location lastKnownLocation;
    /* PERMISSION AND SETTINGS*/

    boolean hasPermission;
    boolean locationEnabled;

    boolean permissionRequested;
    boolean settingsChangeRequested;
    /* GOOGLE MAPS VARIABLES AND CONSTANTS */
    SupportMapFragment mapFragment;
    GoogleMap googleMap;

    Marker marker;
    Bitmap markerBitmap;

    static final PolylineOptions LINE_OPTIONS = new PolylineOptions().color(Color.parseColor("#E94335")).width(13f);
    ArrayList<Polyline> routes = new ArrayList<>();
    ArrayList<LatLng> routesPoints = null;

    boolean isRunning = false;
    /* UI ELEMENTS */
    Button startPauseButton;
    TextView distanceTextView;
    TextView durationTextView;

    TextView serviceInfoTextView;

    MenuItem targetButton;
    MenuItem clearButton;
    /* DISTANCE AND DURATION */
    private Timer timer;
    private int duration;
    private int distance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        distanceTextView = findViewById(R.id.distanceTextView);
        durationTextView = findViewById(R.id.durationTextView);
        serviceInfoTextView = findViewById(R.id.serviceInfoTextView);

        startPauseButton = findViewById(R.id.startPauseButton);
        //add home icon in action bar
        getSupportActionBar().setDisplayShowHomeEnabled(true);   // Dodavanje action bara
        getSupportActionBar().setIcon(R.mipmap.ic_launcher);    // i stavljanje ikonice u action bar
        getSupportActionBar().setTitle("Running Tracker");      // naslov u action baru

        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        // prepare market Bitmap
        BitmapDrawable bitmapDraw = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.location_marker);

        Bitmap b = bitmapDraw.getBitmap();
        int width = 120;
        int height = 120;
        markerBitmap = Bitmap.createScaledBitmap(b, width, height, false);
        //create LocationRequest object
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setSmallestDisplacement(10)  // 10 metara mora se pomjeriti uredjaj
                .setInterval(5) // svakih 5 sekundi salje podatke
                .setFastestInterval(1000); // minimalno jedna sekunda
        //create obcject FusedLocationClient
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        // Create Settings client object
        settingsClient = LocationServices.getSettingsClient(this);
        settingsRequestBuilder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);
        //callback for getting location updates
        locationCallback = new LocationCallback() {

            @Override
            public void onLocationResult(LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {


                    if (!cameraCalibrated) {
                        initialMapCameraCalibration(location);
                    } else {

                        if (location.getAccuracy() > 30)
                            return;

                        drawMarker(location);

                        if (isRunning) {

                            drawRoute(location);
                            updateDistance(location);

                        }

                    }

                    lastKnownLocation = location;

                }
            }

            @Override
            public void onLocationAvailability(LocationAvailability locationAvailability) {
                super.onLocationAvailability(locationAvailability);

                if (locationAvailability.isLocationAvailable()) {
                    setUiEnabled(true, null);

                } else {
                    setUiEnabled(false, "This app needs Location data. Please enable location in Settings.");
                }

            }
        };


        if (savedInstanceState != null) {

            isRunning = savedInstanceState.getBoolean("IS_RUNNING");
            cameraCalibrated = savedInstanceState.getBoolean("CAMERA_CALIBRATED");
            duration = savedInstanceState.getInt("DURATION");
            distance = savedInstanceState.getInt("DISTANCE");
            lastKnownLocation = savedInstanceState.getParcelable("LAST_KNOW_LOCATION");
            lastRoutePoints = savedInstanceState.getParcelableArrayList("LAST_ROUTE_POINTS");

            durationTextView.setText(String.valueOf(getDurationFromSeconds(duration)));
            distanceTextView.setText(String.valueOf(distance));

            routesPoints = savedInstanceState.getParcelableArrayList("ROUTES_POINTS");

        }


    }

    /******************GOOGLE MAPS LOGIC START*******************/


    @Override
    public void onMapReady(GoogleMap googleMap) {

        this.googleMap = googleMap;

        try {
            boolean success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.google_map));

            if (!success) {
                Log.e("gmap", "Style parsing failed.");
            }
        } catch (Resources.NotFoundException e) {
            Log.e("gmap", "Can't find style. Error: ", e);
        }

        //if activity is recreated, draw all polygons
        if (routesPoints != null) {


            List<LatLng> linePoints = new ArrayList<>();

            for (int i = 0; i < routesPoints.size(); i++) {

                if (routesPoints.get(i) != null) {
                    linePoints.add(routesPoints.get(i));
                } else {
                    Polyline polyline = googleMap.addPolyline(LINE_OPTIONS);
                    polyline.setPoints(linePoints);

                    routes.add(polyline);

                    linePoints = new ArrayList<>();
                }

            }


        }

        if (isRunning) {
            startTimer();
        }


    }

    /******************GOOGLE MAPS LOGIC END*******************/


    public boolean checkGooglePlayServices() {
        int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if(result == ConnectionResult.SUCCESS){
            return true;
        } else {
            return false;
        }

    }


    /******************LOCATION LOGIC START*******************/


    private void getLastKnowLocation() throws SecurityException {

        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {

                if (location != null) {

                    // handle location object

                    lastKnownLocation = location;

                    initialMapCameraCalibration(location);
                    drawMarker(location);


                } else {
                    // there is no last know location; wait
                }
            }
        });

    }

    private void startLocationUpdates() {


        if(!checkGooglePlayServices()){
            setUiEnabled(false, "Please, update Google Play Services.");
            return;
        }


        //check if permission is already granted
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            hasPermission = true;
            setUiEnabled(true, null);

            settingsClient.checkLocationSettings(settingsRequestBuilder.build()).addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
                @Override
                public void onSuccess(LocationSettingsResponse locationSettingsResponse) {

                    locationEnabled = true;
                    setUiEnabled(true, null);

                    getLastKnowLocation();

                }
            }).addOnFailureListener(this, new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    if (e instanceof ResolvableApiException) {

                        locationEnabled = false;
                        setUiEnabled(false, "Please, enable Location.");

                        // location settings are not satisfied, but this can be fixed by showing the user a dialog.
                        try {

                            if (!settingsChangeRequested) {

                                // show the dialog by calling startResolutionForResult(), and check the result in onActivityResult().
                                ResolvableApiException resolvable = (ResolvableApiException) e;
                                resolvable.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);

                                settingsChangeRequested = true;

                            } else {
                                serviceInfoTextView.setText("This app needs Location data. Please enable location in Settings.");
                            }


                        } catch (IntentSender.SendIntentException sendEx) {
                            // ignore the error.
                        }
                    }
                }
            });

            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
            serviceInfoTextView.setText("");

        } else {

            hasPermission = false;

            setUiEnabled(false, "App doesn't have permission for using location data.");

            if (!permissionRequested) {

                //if permission is not granted, ask user for permission
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            }

            permissionRequested = true;

        }
    }


    private void stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }


    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();

    }


    @Override
    protected void onResume() {

        super.onResume();
        startLocationUpdates();

    }


    /******************LOCATION LOGIC END*******************/


    /******************DATA PRESENTATION LOGIC START*******************/

    boolean uiEnabled;

    private void setUiEnabled(boolean enabled, String message) {

        uiEnabled = enabled;

        startPauseButton.setEnabled(enabled);

        if (clearButton != null) {
            clearButton.setEnabled(enabled);
        }

        if (targetButton != null) {
            targetButton.setEnabled(enabled);
        }

        if (message != null) {
            serviceInfoTextView.setVisibility(View.VISIBLE);
            serviceInfoTextView.setText(message);
        } else {
            serviceInfoTextView.setVisibility(View.GONE);
            serviceInfoTextView.setText("");
        }

    }


    private boolean cameraCalibrated = false;

    private void initialMapCameraCalibration(Location location) {

        LatLng demoLocation = new LatLng(location.getLatitude(), location.getLongitude());

        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(demoLocation)       // center of the map
                .zoom(17)                   // zoom
                .build();                   // creates a CameraPosition object


        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, null);

        cameraCalibrated = true;

    }

    private void reposition() {

        if (lastKnownLocation == null)
            return;

        LatLng location = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 17));

    }


    private void drawMarker(Location location) {

        if (marker == null) {
            marker = googleMap.addMarker(new MarkerOptions()
                    .position(new LatLng(location.getLatitude(), location.getLongitude()))
                    .title("You are here")
                    .alpha(0.8f)
                    .icon(BitmapDescriptorFactory.fromBitmap(markerBitmap)));
        } else {
            marker.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
        }


    }


    ArrayList<LatLng> lastRoutePoints;

    private void drawRoute(Location location) {

        LatLng newLocation = new LatLng(location.getLatitude(), location.getLongitude());

        if (lastRoutePoints == null) {
            lastRoutePoints = new ArrayList<>();
            lastRoutePoints.add(newLocation);


            routes.add(googleMap.addPolyline(LINE_OPTIONS));
            routes.get(routes.size() - 1).setPoints(lastRoutePoints);


        } else {

            lastRoutePoints.add(newLocation);
            routes.get(routes.size() - 1).setPoints(lastRoutePoints);

        }
    }


    private void clearAllRoutes() {

        for (int i = 0; i < routes.size(); i++) {
            routes.get(i).remove();
        }

        routes = new ArrayList<>();

        lastRoutePoints = null;

        if (isRunning) {
            drawRoute(lastKnownLocation);
        }

    }


    private void updateDistance(Location location) {

        float[] results = new float[1];
        Location.distanceBetween(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude(),
                location.getLatitude(), location.getLongitude(),
                results);

        distance += results[0];

        distanceTextView.setText(String.valueOf(distance));

    }

    private void clearDistance() {
        distance = 0;
        distanceTextView.setText(String.valueOf(distance));
    }


    private void startTimer() {

        timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        duration++;
                        durationTextView.setText(getDurationFromSeconds(duration));
                    }
                });

            }
        }, 0, 1000);

    }

    private String getDurationFromSeconds(int seconds) {
        int min = seconds / 60;
        int sec = seconds - (min * 60);

        String minutesString = String.valueOf(min);


        String secondsStrings = String.valueOf(sec);
        secondsStrings = (secondsStrings.length() == 1) ? "0" + secondsStrings : secondsStrings;

        return minutesString + ":" + secondsStrings;

    }

    private void pauseTimer() {

        if (timer != null) {
            timer.cancel();
        }
    }


    private void resetTimer() {
        duration = 0;
        durationTextView.setText("0:00");

    }


    /******************DATA PRESENTATION LOGIC END*******************/


    /******************ACTION BAR MENU LOGIC START*******************/
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar_menu, menu);

        targetButton = menu.findItem(R.id.targetButton);
        clearButton = menu.findItem(R.id.clearButton);

        targetButton.setEnabled(uiEnabled);
        clearButton.setEnabled(uiEnabled);

        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.clearButton:

                //users taps on CLEAR button

                clearAllRoutes();
                clearDistance();
                resetTimer();

                return true;
            case R.id.targetButton:

                //users taps on REPOSITION button
                reposition();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    /******************ACTION BAR MENU LOGIC END*******************/


    /**************************EVENT HANDLER AND CALLBACK METHODS START*******************************/


    public void startPauseButtonClick(View view) {
        isRunning = !isRunning;

        if (!isRunning) {
            lastRoutePoints = null;
            pauseTimer();
        } else {
            drawRoute(lastKnownLocation);
            startTimer();
        }
    }


    /**************************EVENT HANDLER METHODS END*******************************/


    /**************************STATE HANDLING LOGIC START*******************************/

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean("IS_RUNNING", isRunning);
        outState.putBoolean("CAMERA_CALIBRATED", cameraCalibrated);
        outState.putInt("DURATION", duration);
        outState.putInt("DISTANCE", distance);
        outState.putParcelable("MARKER", (googleMap != null) ? googleMap.getCameraPosition() : null);
        outState.putParcelableArrayList("LAST_ROUTE_POINTS", lastRoutePoints);
        outState.putParcelable("LAST_KNOW_LOCATION", lastKnownLocation);


        ArrayList<LatLng> routesPoints = new ArrayList<>();

        for (int i = 0; i < routes.size(); i++) {

            routesPoints.addAll(routes.get(i).getPoints());
            routesPoints.add(null);

        }

        outState.putParcelableArrayList("ROUTES_POINTS", routesPoints);

    }


    /**************************STATE HANDLING LOGIC END*******************************/


}