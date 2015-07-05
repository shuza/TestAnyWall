package com.shuza.testanywall;

import android.app.Dialog;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.CancelableCallback;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.parse.FindCallback;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseQuery;
import com.parse.ParseQueryAdapter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends FragmentActivity implements LocationListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private Location lastLocation, currentLocation;
    private SupportMapFragment mapFragment;

    private static final int MAX_POST_SEARCH_DISTANCE = 100;
    private static final int MAX_POST_SEARCH_RESULTS = 20;

    private String selectedPostObjectId;
    private float radius;

    private final Map<String, Marker> mapMarkers = new HashMap<String, Marker>();
    private ParseQueryAdapter<AnyWallPost> postsQueryAdapter;

    private LocationRequest locationRequest;
    private GoogleApiClient locationClient;
    private static final int MILLISECONDS_PER_SECOND = 1000;
    private static final int UPDATE_INTERVAL_IN_SECONDS = 5;    // The update interval
    private static final int FAST_CEILING_IN_SECONDS = 1;   // A fast interval ceiling
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = MILLISECONDS_PER_SECOND
            * UPDATE_INTERVAL_IN_SECONDS;                   // Update interval in milliseconds
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private static final long FAST_INTERVAL_CEILING_IN_MILLISECONDS = MILLISECONDS_PER_SECOND
            * FAST_CEILING_IN_SECONDS;                      // A fast ceiling of update intervals, used when the app is visible
    private static final float METERS_PER_FEET = 0.3048f;   // Conversion from feet to meters
    private static final int METERS_PER_KILOMETER = 1000;   // Conversion from kilometers to meters

    private boolean hasSetUpInitialLocation;
    private static final double OFFSET_CALCULATION_INIT_DIFF = 1.0;
    private static final float OFFSET_CALCULATION_ACCURACY = 0.01f;
    private Circle mapCircle;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationRequest = LocationRequest.create();
        locationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setFastestInterval(FAST_INTERVAL_CEILING_IN_MILLISECONDS);

        locationClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        ParseQueryAdapter adapter = new ParseQueryAdapter<AnyWallPost>(this, AnyWallPost.class);
        adapter.setTextKey("text");


        ParseQueryAdapter.QueryFactory<AnyWallPost> factory = new ParseQueryAdapter.QueryFactory<AnyWallPost>() {
            @Override
            public ParseQuery<AnyWallPost> create() {
                Location myLoc = (currentLocation == null)? lastLocation : currentLocation;
                ParseQuery<AnyWallPost> query = AnyWallPost.getQuery();
                query.include("user");
                query.orderByDescending("createdAt");
                query.whereWithinKilometers("location", geoPointFromLocation(myLoc), radius
                        * METERS_PER_FEET / METERS_PER_KILOMETER);
                query.setLimit(MAX_POST_SEARCH_RESULTS);
                return query;
            }
        };
        postsQueryAdapter = new ParseQueryAdapter<AnyWallPost>(this, factory){
            @Override
            public View getItemView(AnyWallPost post, View view, ViewGroup parent) {
                if(view == null){
                    view = View.inflate(getContext(), R.layout.anywall_post_item, null);
                }
                TextView contentView = (TextView) view.findViewById(R.id.content_view);
                TextView usernameView = (TextView) view.findViewById(R.id.username_view);
                contentView.setText(post.getText());
                usernameView.setText(post.getUser().getUsername());
                return view;
            }
        };
        postsQueryAdapter.setAutoload(false);
        postsQueryAdapter.setPaginationEnabled(false);
        ListView postsListView = (ListView) this.findViewById(R.id.posts_listview);
        postsListView.setAdapter(postsQueryAdapter);
        postsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView< ?> parent, View view, int position, long id) {
                final AnyWallPost item = postsQueryAdapter.getItem(position);
                selectedPostObjectId = item.getObjectId();
                mapFragment.getMap().animateCamera(
                        CameraUpdateFactory.newLatLng(new LatLng(item.getLocation().getLatitude(), item
                                .getLocation().getLongitude())), new GoogleMap.CancelableCallback() {
                            public void onFinish() {
                                Marker marker = mapMarkers.get(item.getObjectId());
                                if (marker != null) {
                                    marker.showInfoWindow();
                                }
                            }

                            public void onCancel() { }
                        });
                Marker marker = mapMarkers.get(item.getObjectId());
                if (marker != null) {
                    marker.showInfoWindow();
                }
            }
        });


        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        mapFragment.getMap().setMyLocationEnabled(true);
        mapFragment.getMap().setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                doMapQuery();
            }
        });


        Button postButton = (Button) findViewById(R.id.post_button);
        postButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Location myLoc = (currentLocation == null) ? lastLocation : currentLocation;
                if (myLoc == null) {
                    Toast.makeText(MainActivity.this, "Please try after your location appear on the map", Toast.LENGTH_SHORT).show();
                } else {
                    Intent intent = new Intent(MainActivity.this, PostActivity.class);
                    intent.putExtra(Application.INTENT_EXTRA_LOCATION, myLoc);
                    startActivity(intent);
                }
            }
        });


    }

    private void doListQuery() {
        Location myLoc = (currentLocation == null) ? lastLocation : currentLocation;
        if (myLoc != null) {
            postsQueryAdapter.loadObjects();
        }
    }

    private void doMapQuery() {
        Location myLoc = (currentLocation == null) ? lastLocation : currentLocation;
        if (myLoc == null) {
            cleanUpMarkers(new HashSet<String>());
            return;
        }

        final ParseGeoPoint myPoint = geoPointFromLocation(myLoc);
        ParseQuery<AnyWallPost> mapQuery = AnyWallPost.getQuery();
        mapQuery.whereWithinKilometers("location", myPoint, MAX_POST_SEARCH_DISTANCE);
        mapQuery.include("user");
        mapQuery.orderByDescending("createdAt");
        mapQuery.setLimit(MAX_POST_SEARCH_RESULTS);
        mapQuery.findInBackground(new FindCallback<AnyWallPost>() {
            @Override
            public void done(List<AnyWallPost> objects, ParseException e) {
                if (e != null) {
                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                Set<String> toKeep = new HashSet<String>();
                for (AnyWallPost post : objects) {
                    toKeep.add(post.getObjectId());
                    Marker oldMarker = mapMarkers.get(post.getObjectId());
                    MarkerOptions markerOpts = new MarkerOptions()
                            .position(new LatLng(post.getLocation().getLatitude(), post.getLocation().getLongitude()));
                    if(post.getLocation().distanceInKilometersTo(myPoint) > radius * METERS_PER_FEET / METERS_PER_KILOMETER){
                        if(oldMarker != null){
                            if(oldMarker.getSnippet() == null){
                                continue;
                            }else{
                                oldMarker.remove();
                            }
                        }
                        markerOpts = markerOpts.title("out of range")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                    }else{
                        if(oldMarker != null){
                            if(oldMarker.getSnippet() == null){
                                continue;
                            }else{
                                oldMarker.remove();
                            }
                        }
                        markerOpts = markerOpts.title(post.getText())
                                .snippet(post.getUser().getUsername())
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                    }
                    Marker marker = mapFragment.getMap().addMarker(markerOpts);
                    mapMarkers.put(post.getObjectId(), marker);
                    if(post.getObjectId().equals(selectedPostObjectId)){
                        marker.showInfoWindow();
                        selectedPostObjectId = null;
                    }
                }
                cleanUpMarkers(toKeep);
            }
        });

    }

    private ParseGeoPoint geoPointFromLocation(Location myLoc) {
        return new ParseGeoPoint(myLoc.getLatitude(), myLoc.getLongitude());
    }


    private void cleanUpMarkers(Set<String> markersToKeep) {
        for (String objId : new HashSet<String>(mapMarkers.keySet())) {
            if (!markersToKeep.contains(objId)) {
                Marker marker = mapMarkers.get(objId);
                marker.remove();
                mapMarkers.get(objId).remove();
                mapMarkers.remove(objId);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        doMapQuery();
        doListQuery();
    }

    // location related methods
    //
    private void startPeriodicUpdates(){
        LocationServices.FusedLocationApi.requestLocationUpdates(locationClient, locationRequest, this);
    }

    private void stopPeriodicUpdates() {
        locationClient.disconnect();
    }

    private Location getLocation(){
        if(servicesConnected()){
            return LocationServices.FusedLocationApi.getLastLocation(locationClient);
        }
        return null;
    }

    @Override
    public void onConnected(Bundle bundle) {
        currentLocation = getLocation();
        startPeriodicUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if(connectionResult.hasResolution()){
            try{
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {}
        }else{
            showErrorDialog(connectionResult.getErrorCode());
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        currentLocation = location;
        if(lastLocation != null && geoPointFromLocation(location)
                .distanceInKilometersTo(geoPointFromLocation(location)) < 0.01){
            return;
        }
        lastLocation = location;
        LatLng myLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        if(!hasSetUpInitialLocation){
            updateZoom(myLatLng);
            hasSetUpInitialLocation = true;
        }
        updateCircle(myLatLng);
        doMapQuery();
        doListQuery();
    }

    private void updateZoom(LatLng myLatLng){
        LatLngBounds bounds = calculateBoundsWithCenter(myLatLng);
        mapFragment.getMap().animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 5));
    }

    private LatLngBounds calculateBoundsWithCenter(LatLng myLatLng){
        LatLngBounds.Builder builder = LatLngBounds.builder();
        double lngDifference = calculateLatLngOffset(myLatLng, false);
        LatLng east = new LatLng(myLatLng.latitude, myLatLng.longitude + lngDifference);
        builder.include(east);
        LatLng west = new LatLng(myLatLng.latitude, myLatLng.longitude - lngDifference);
        builder.include(west);

        double latDifference = calculateLatLngOffset(myLatLng, true);
        LatLng north = new LatLng(myLatLng.latitude + latDifference, myLatLng.longitude);
        builder.include(north);
        LatLng south = new LatLng(myLatLng.latitude - latDifference, myLatLng.longitude);
        builder.include(south);
        return builder.build();
    }

    private double calculateLatLngOffset(LatLng myLatLng, boolean bLatOffset){
        double latLngOffset = OFFSET_CALCULATION_INIT_DIFF;
        float desiredOffsetInMeters = radius * METERS_PER_FEET;
        float[] distance = new float[1];
        boolean foundMax = false;
        double foundMinDiff = 0;
        do{
            if(bLatOffset){
                Location.distanceBetween(myLatLng.latitude, myLatLng.longitude, myLatLng.latitude+latLngOffset,
                        myLatLng.longitude, distance);
            }else{
                Location.distanceBetween(myLatLng.latitude, myLatLng.longitude, myLatLng.latitude,
                        myLatLng.longitude+latLngOffset, distance);
            }

            float distanceDiff = distance[0] - desiredOffsetInMeters;
            if(distanceDiff < 0){
                if(foundMax){
                    foundMinDiff = latLngOffset;
                    latLngOffset *= 2;
                }else {
                    double tmp = latLngOffset;
                    latLngOffset += (latLngOffset - foundMinDiff) / 2;
                    foundMinDiff = tmp;
                }
            }else{
                latLngOffset -= (latLngOffset - foundMinDiff) / 2;
                foundMax = true;
            }
        }while (Math.abs(distance[0] - desiredOffsetInMeters) > OFFSET_CALCULATION_ACCURACY);
        return latLngOffset;
    }

    private void updateCircle(LatLng myLatLng){
        if(mapCircle == null){
            mapCircle = mapFragment.getMap().addCircle(
                    new CircleOptions().center(myLatLng).radius(radius*METERS_PER_FEET));
            int baseColor = Color.DKGRAY;
            mapCircle.setStrokeColor(baseColor);
            mapCircle.setStrokeWidth(2);
            mapCircle.setFillColor(Color.argb(50, Color.red(baseColor), Color.green(baseColor),
                    Color.blue(baseColor)));
        }
        mapCircle.setCenter(myLatLng);
        mapCircle.setRadius(radius*METERS_PER_FEET);
    }


    public static class ErrorDialogFragment extends DialogFragment {
        private Dialog mDialog;

        public ErrorDialogFragment(){
            super();
            mDialog = null;
        }

        public void setmDialog(Dialog dialog){
            mDialog = dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }

    private void showErrorDialog(int errorCode){
        Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(errorCode, this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
        if(errorDialog != null){
            ErrorDialogFragment errorFragment = new ErrorDialogFragment();
            errorFragment.setmDialog(errorDialog);
            errorFragment.show(getSupportFragmentManager(), Application.APPTAG);
        }
    }

    private boolean servicesConnected(){
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if(ConnectionResult.SUCCESS == resultCode){
            return true;
        }
        Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, 0);
        if(dialog != null){
            ErrorDialogFragment errorFragment = new ErrorDialogFragment();
            errorFragment.setmDialog(dialog);
            errorFragment.show(getSupportFragmentManager(), Application.APPTAG);
        }
        return false;
    }

    @Override
    protected void onStop() {
        if(locationClient.isConnected()){
            stopPeriodicUpdates();
        }
        locationClient.disconnect();
        super.onStart();
    }

    @Override
    protected void onStart() {
        super.onStart();
        locationClient.connect();
    }
}
