package ru.vkatzyn.geofencing_test_app;

import android.Manifest;
import android.app.LoaderManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class MainActivity extends AppCompatActivity
        implements
        LoaderManager.LoaderCallbacks<List<Geodata>>,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        OnMapReadyCallback,
        GoogleMap.OnMapClickListener,
        GoogleMap.OnMarkerClickListener,
        ResultCallback<Status> {

    private static final String TAG = MainActivity.class.getSimpleName();


    private static final String HTTP_REQUEST = "http://mycompany.1.doubleb-automation-production.appspot.com/api/company/modules";
    private static final int GEODATA_LOADER = 0;
    private static final String NOTIFICATION_MSG = "NOTIFICATION MSG";
    private static final long GEO_DURATION = 60 * 60 * 1000;
    private static final String GEOFENCE_REQ_ID = "My Geofence";
    private static final float GEOFENCE_RADIUS = 500.0f; // in meters
    private final int REQ_PERMISSION = 999;
    private final int UPDATE_INTERVAL = 3 * 60 * 1000;
    private final int FASTEST_INTERVAL = 1 * 60 * 1000;
    private final int GEOFENCE_REQ_CODE = 0;
    private final String KEY_GEOFENCES = "GEOFENCES";
    private List<Geodata> geodata;
    private GoogleMap map;
    private GoogleApiClient googleApiClient;
    private Location lastLocation;
    private TextView textLatitude, textLongitude;
    private MapFragment mapFragment;
    private LocationRequest locationRequest;
    private Marker locationMarker;
    private PendingIntent geoFencePendingIntent;
    private List<Circle> geofenceLimits;
    private boolean firstRun;
    private Queue<LatLng> curProcessingGeofences;

    public static Intent makeNotificationIntent(Context context, String msg) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(NOTIFICATION_MSG, msg);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textLatitude = (TextView) findViewById(R.id.lat);
        textLongitude = (TextView) findViewById(R.id.lon);

        SharedPreferences sharedPref = getPreferences(MODE_PRIVATE);
        if (sharedPref.getBoolean("firstrun", true)) {
            firstRun = true;
            sharedPref.edit().putBoolean("firstrun", false).apply();
        } else {
            firstRun = false;
        }
        curProcessingGeofences = new LinkedList<>();

        initGoogleMaps();
        createGoogleApi();
    }

    private void createGoogleApi() {
        Log.d(TAG, "createGoogleApi()");
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        googleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();

        googleApiClient.disconnect();
    }

    // Check for permission to access Location.
    private boolean checkPermission() {
        Log.d(TAG, "checkPermission()");
        // Ask for permission if it wasn't granted yet
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED);
    }

    // Asks for permission
    private void askPermission() {
        Log.d(TAG, "askPermission()");
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQ_PERMISSION
        );
    }

    // Verify user's response of the permission requested
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult()");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQ_PERMISSION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted
                    getLastKnownLocation();

                } else {
                    // Permission denied
                    permissionsDenied();
                }
                break;
            }
        }
    }

    // App cannot work without the permissions
    private void permissionsDenied() {
        Log.w(TAG, "permissionsDenied()");
        // TODO close app and warn user
    }

    private void initGoogleMaps() {
        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.d(TAG, "onMapReady()");
        map = googleMap;
        map.setOnMapClickListener(this);
        map.setOnMarkerClickListener(this);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        Log.d(TAG, "onMarkerClickListener: " + marker.getPosition());
        return false;
    }

    private void startLocationUpdates() {
        Log.i(TAG, "startLocationUpdates()");
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL);

        if (checkPermission())
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "onLocationChanged [" + location + "]");
        lastLocation = location;
        writeActualLocation(location);
    }

    // GoogleApiClient.ConnectionCallbacks connected.
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "onConnected()");
        getLastKnownLocation();
        if (firstRun) {
            getLoaderManager().initLoader(GEODATA_LOADER, null, this).startLoading();
        } else {
            recoverGeofences();
        }
    }

    // GoogleApiClient.ConnectionCallbacks suspended.
    @Override
    public void onConnectionSuspended(int i) {
        Log.w(TAG, "onConnectionSuspended()");
    }

    // GoogleApiClient.CnnectionCallbacks failed.
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.w(TAG, "onConnectionFailed()");
    }

    @Override
    public Loader<List<Geodata>> onCreateLoader(int i, Bundle bundle) {
        Log.d(TAG, "onCreateLoader()");
        Uri uri = Uri.parse(HTTP_REQUEST);
        return new GeodataLoader(this, uri.toString());
    }

    @Override
    public void onLoadFinished(Loader<List<Geodata>> loader, List<Geodata> geodata) {
        Log.d(TAG, "onLoadFinished()");
        this.geodata = geodata;
        for (int i = 0; i < geodata.size(); i++) {
            Geodata curData = geodata.get(i);
            LatLng latLng = new LatLng(curData.getLatitude(), curData.getLongitude());
            float radius = curData.getRadius();
            Geofence geofence = createGeofence(latLng, radius);
            curProcessingGeofences.add(latLng);
            GeofencingRequest geofencingRequest = createGeofenceRequest(geofence);
            addGeofence(geofencingRequest);
        }
    }

    @Override
    public void onLoaderReset(Loader<List<Geodata>> loader) {
        Log.d(TAG, "onLoaderReset()");
    }

    // Get last known location
    private void getLastKnownLocation() {
        Log.d(TAG, "getLastKnownLocation()");
        if (checkPermission()) {
            lastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
            if (lastLocation != null) {
                Log.i(TAG, "LasKnown location. " +
                        "Long: " + lastLocation.getLongitude() +
                        " | Lat: " + lastLocation.getLatitude());
                writeLastLocation();
                startLocationUpdates();
            } else {
                Log.w(TAG, "No location retrieved yet");
                startLocationUpdates();
            }
        } else askPermission();
    }

    private void writeActualLocation(Location location) {
        textLatitude.setText("Lat: " + location.getLatitude());
        textLongitude.setText("Long: " + location.getLongitude());

        markerLocation(new LatLng(location.getLatitude(), location.getLongitude()));
    }

    private void writeLastLocation() {
        writeActualLocation(lastLocation);
    }

    private void markerLocation(LatLng latLng) {
        Log.i(TAG, "markerLocation(" + latLng + ")");
        String title = latLng.latitude + ", " + latLng.longitude;
        MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .title(title);
        if (map != null) {
            if (locationMarker != null)
                locationMarker.remove();
            locationMarker = map.addMarker(markerOptions);
            float zoom = 14f;
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, zoom);
            map.animateCamera(cameraUpdate);
        }
    }

    // Create a Geofence
    private Geofence createGeofence(LatLng latLng, float radius) {
        Log.d(TAG, "createGeofence");
        return new Geofence.Builder()
                .setRequestId(GEOFENCE_REQ_ID)
                .setCircularRegion(latLng.latitude, latLng.longitude, radius)
                .setExpirationDuration(GEO_DURATION)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER
                        | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();
    }

    // Create a Geofence Request
    private GeofencingRequest createGeofenceRequest(Geofence geofence) {
        Log.d(TAG, "createGeofenceRequest");
        return new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();
    }

    private PendingIntent createGeofencePendingIntent() {
        Log.d(TAG, "createGeofencePendingIntent");
        if (geoFencePendingIntent != null)
            return geoFencePendingIntent;

        Intent intent = new Intent(this, GeofenceTransitionService.class);
        return PendingIntent.getService(
                this, GEOFENCE_REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // Add the created GeofenceRequest to the device's monitoring list
    private void addGeofence(GeofencingRequest request) {
        Log.d(TAG, "addGeofence");
        if (checkPermission())
            LocationServices.GeofencingApi.addGeofences(
                    googleApiClient,
                    request,
                    createGeofencePendingIntent()
            ).setResultCallback(this);
    }

    @Override
    public void onResult(@NonNull Status status) {
        Log.i(TAG, "onResult: " + status);
        LatLng curGeofenceCoords = curProcessingGeofences.remove();
        if (status.isSuccess()) {
            saveGeofence(curGeofenceCoords);
            drawGeofence(curGeofenceCoords);
        } else {
            Log.e(TAG, "!addGeofence() failed!");
        }
    }

    private void drawGeofence(LatLng centerCoordinates) {
        Log.d(TAG, "drawGeofence()");
        if (geofenceLimits == null) {
            geofenceLimits = new ArrayList<Circle>();
        }

        CircleOptions circleOptions = new CircleOptions()
                .center(centerCoordinates)
                .strokeColor(Color.argb(50, 70, 70, 70))
                .fillColor(Color.argb(100, 150, 150, 150))
                .radius(GEOFENCE_RADIUS);
        geofenceLimits.add(map.addCircle(circleOptions));
    }

    private void saveGeofence(LatLng centerCoordinates) {
        Log.d(TAG, "saveGeofence()");
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        Set<String> geofencesCoordinates = new HashSet<String>();
        if (sharedPref.contains(KEY_GEOFENCES)) {
            geofencesCoordinates.addAll(sharedPref.getStringSet(KEY_GEOFENCES, new HashSet<String>()));
        }

        Double lat = centerCoordinates.latitude;
        Double lng = centerCoordinates.longitude;
        String coord1 = lat.toString();
        String coord2 = lng.toString();
        String coords = coord1 + "," + coord2;

        geofencesCoordinates.add(coords);

        editor.putStringSet(KEY_GEOFENCES, geofencesCoordinates);
        editor.apply();
    }

    private void recoverGeofences() {
        Log.d(TAG, "recoverGeofences()");
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);

        Set<String> geofencesCoordinates = new HashSet<String>();

        if (sharedPref.contains(KEY_GEOFENCES)) {
            geofencesCoordinates.addAll(sharedPref.getStringSet(KEY_GEOFENCES, new HashSet<String>()));
            for (String coords : geofencesCoordinates) {
                String[] values = coords.split(",");
                double lat = Double.parseDouble(values[0]);
                double lon = Double.parseDouble(values[1]);
                LatLng latLng = new LatLng(lat, lon);
                drawGeofence(latLng);
            }
        }
    }

    // Clear Geofence
    private void clearGeofence() {
        Log.d(TAG, "clearGeofence()");
        LocationServices.GeofencingApi.removeGeofences(
                googleApiClient,
                createGeofencePendingIntent()
        ).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                if (status.isSuccess()) {
                    removeGeofenceDraw();
                }
            }
        });
    }

    private void removeGeofenceDraw() {
        Log.d(TAG, "removeGeofenceDraw()");
        if (geofenceLimits != null && !geofenceLimits.isEmpty()) {
            for (Circle geofence : geofenceLimits)
                geofence.remove();
            geofenceLimits.clear();
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        Log.d(TAG, "onMapClick()");
    }
}