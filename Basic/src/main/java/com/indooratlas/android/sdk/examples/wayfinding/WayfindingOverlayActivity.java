package com.indooratlas.android.sdk.examples.wayfinding;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.Vibrator;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.indooratlas.android.sdk.IALocation;
import com.indooratlas.android.sdk.IALocationListener;
import com.indooratlas.android.sdk.IALocationManager;
import com.indooratlas.android.sdk.IALocationRequest;
import com.indooratlas.android.sdk.IARegion;
import com.indooratlas.android.sdk.examples.R;
import com.indooratlas.android.sdk.examples.SdkExample;
import com.indooratlas.android.sdk.resources.IAFloorPlan;
import com.indooratlas.android.sdk.resources.IALatLng;
import com.indooratlas.android.sdk.resources.IALocationListenerSupport;
import com.indooratlas.android.sdk.resources.IAResourceManager;
import com.indooratlas.android.sdk.resources.IAResult;
import com.indooratlas.android.sdk.resources.IAResultCallback;
import com.indooratlas.android.sdk.resources.IATask;
import com.indooratlas.android.wayfinding.IARoutingLeg;
import com.indooratlas.android.wayfinding.IAWayfinder;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Target;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;


import org.apache.commons.lang3.ObjectUtils;

import java.io.InputStream;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

@SdkExample(description = R.string.example_wayfinding_description)
public class WayfindingOverlayActivity extends FragmentActivity implements SensorEventListener, LocationListener,GoogleMap.OnMapClickListener
{
    private static final int MY_PERMISSION_ACCESS_FINE_LOCATION = 42;
    private static final int CODE_PERMISSIONS = 0;
    private LocationManager locationManager;
    private Location LocationObj, destinationObj = new Location("");
    private Sensor sensor;
    private static SensorManager sensorService;
    private Bitmap arrowImg;
    private static final String TAG = "IndoorAtlasExample";
    /* used to decide when bitmap should be downscaled */
    private static final int MAX_DIMENSION = 2048;
    private Location customLocation = new Location("name");
    //private IALocation iacustomLocation ;
    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private Circle mCircle;
    private IARegion mOverlayFloorPlan = null;
    private GroundOverlay mGroundOverlay = null;
    private IALocationManager mIALocationManager;
    private IAResourceManager mResourceManager;
    private IATask<IAFloorPlan> mFetchFloorPlanTask;
    private Target mLoadTarget;
    private boolean mCameraPositionNeedsUpdating = true; // update on first location
    private boolean mShowIndoorLocation = false;
    private GeomagneticField geoField;
    private IAWayfinder mWayfinder;
    private LatLng mLocation;

    private LatLng mDestination;
    private Location customDestLocation = new Location("destname");
    private Location customlegLocation = new Location("destname");
    private Marker mDestinationMarker;

    private Polyline mPath;
    private Polyline mPathCurrent;
    private IARoutingLeg[] mCurrentRoute;

    private Integer mFloor;
    public Location locForDirection;
    private long sensorCallbacksCount = 0;
    private AsyncTask<Void, Void, Void> asyncTask;
    private static float[] magnitude_values = null;
    private static float[] accelerometer_values = null;
    private static boolean sensorReady = false;
    float baseAzimuth;


    private void showLocationCircle(LatLng center, double accuracyRadius) {
        if (mCircle == null) {
            // location can received before map is initialized, ignoring those updates
            if (mMap != null) {
                mCircle = mMap.addCircle(new CircleOptions()
                        .center(center)
                        .radius(accuracyRadius)
                        .fillColor(0x801681FB)
                        .strokeColor(0x800A78DD)
                        .zIndex(1.0f)
                        .visible(true)
                        .strokeWidth(5.0f));
            }
        } else {
            // move existing markers position to received location
            mCircle.setCenter(center);
            mCircle.setRadius(accuracyRadius);
        }
    }  // WAYFINDING

    /**
     * Listener that handles location change events.
     */
    private IALocationListener mListener = new IALocationListenerSupport() {

        /**
         * Location changed, move marker and camera position.
         */
        @Override
        public void onLocationChanged(IALocation location) { //WAYFINDING LISTNER - CALLED EVERYTIME THERE IS A LOCATION CHANGE
            LocationObj = location.toLocation();

            geoField = new GeomagneticField(
                    Double.valueOf(location.getLatitude()).floatValue(),
                    Double.valueOf(location.getLongitude()).floatValue(),
                    Double.valueOf(location.getAltitude()).floatValue(),
                    System.currentTimeMillis()
            );
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            Log.d(TAG, "  AAA new location received with coordinates: " + location.getLatitude()
                    + "," + location.getLongitude());
            customLocation.setLongitude(location.getLongitude());
            customLocation.setLatitude(location.getLatitude());


            // location received before map is initialized, ignoring update here
            if (mMap == null) {
                return;
            }

            final LatLng center = new LatLng(location.getLatitude(), location.getLongitude());

            //SETTING FLOOR NO.
            mFloor = location.getFloorLevel();

            //SETTING CURRENT LOCATION
            mLocation = new LatLng(location.getLatitude(), location.getLongitude());

            if (mWayfinder != null) {
                mWayfinder.setLocation(mLocation.latitude, mLocation.longitude, mFloor);
            }

            //UPDATING ROUTE WITH NEW CURRENT LOCATION
            updateRoute();

            if (mShowIndoorLocation) {
                showLocationCircle(center, location.getAccuracy());
            }

            // our camera position needs updating if location has significantly changed
            if (mCameraPositionNeedsUpdating) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 17.5f));
                mCameraPositionNeedsUpdating = false;
            }
        }
    };

    /**
     * Listener that changes overlay if needed - NO NEED OF USING THIS
     */
    private IARegion.Listener mRegionListener = new IARegion.Listener() {
        @Override
        public void onEnterRegion(IARegion region) {
            if (region.getType() == IARegion.TYPE_FLOOR_PLAN) {
                final String newId = region.getId();
                // Are we entering a new floor plan or coming back the floor plan we just left?
                if (mGroundOverlay == null || !region.equals(mOverlayFloorPlan)) {
                    mCameraPositionNeedsUpdating = true; // entering new fp, need to move camera
                    if (mGroundOverlay != null) {
                        mGroundOverlay.remove();
                        mGroundOverlay = null;
                    }
                    mOverlayFloorPlan = region; // overlay will be this (unless error in loading)
                    fetchFloorPlan(newId);
                } else {
                    mGroundOverlay.setTransparency(0.0f);
                }

                mShowIndoorLocation = true;
                showInfo("Showing IndoorAtlas SDK\'s location output");
            }
            showInfo("Enter " + (region.getType() == IARegion.TYPE_VENUE
                    ? "VENUE "
                    : "FLOOR_PLAN ") + region.getId());
        }

        @Override
        public void onExitRegion(IARegion region) {
            if (mGroundOverlay != null) {
                // Indicate we left this floor plan but leave it there for reference
                // If we enter another floor plan, this one will be removed and another one loaded
                mGroundOverlay.setTransparency(0.5f);
            }

            mShowIndoorLocation = false;
            showInfo("Exit " + (region.getType() == IARegion.TYPE_VENUE
                    ? "VENUE "
                    : "FLOOR_PLAN ") + region.getId());
        }

    };

    @Override
    protected void onStart() {
        super.onStart();
        Toast.makeText(this, "OnStart Called", Toast.LENGTH_LONG).show();
        if (sensor != null) {
            sensorService.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            sensorService.registerListener(this, sensorService.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            Toast.makeText(this, "Not Supported!", Toast.LENGTH_LONG).show();
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        Toast.makeText(this, "ONSTOP. ", Toast.LENGTH_LONG).show();
        sensorService.unregisterListener(this);
    }



    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSION_ACCESS_FINE_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startListeningPlatformLocations();
                }
                break;
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!mShowIndoorLocation) {
            Log.d(TAG, " BBBB new LocationService location received with coordinates: " + location.getLatitude()
                    + "," + location.getLongitude());

            showLocationCircle(
                    new LatLng(location.getLatitude(), location.getLongitude()),
                    location.getAccuracy());

        }


    } // MULTIPLE METHODS - THIS ONE ONLY UPDATES THE SHOWLOCATION CIRCLE

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Toast.makeText(this, "ONCREATE Called", Toast.LENGTH_LONG).show();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // prevent the screen going to sleep while app is on foreground
        findViewById(android.R.id.content).setKeepScreenOn(true);



        // instantiate IALocationManager and IAResourceManager
        mIALocationManager = IALocationManager.create(this);
        mResourceManager = IAResourceManager.create(this);

        //FIND A WAY TO ACTIVATE MAGNETIC SENSORS. AS SOON AS THEY ARE ACTIVATED THEY OVERWRITE IALOCATAION METHOD
        //magnetic sensors setups
        sensorService = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorService.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        //location manager setups
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);


        // Request GPS locations
        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSION_ACCESS_FINE_LOCATION);
            return;
        }

//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//
//
//            String[] neededPermissions = {
//                    Manifest.permission.CHANGE_WIFI_STATE,
//                    Manifest.permission.CHANGE_WIFI_STATE,
//                    Manifest.permission.ACCESS_WIFI_STATE,
//                    Manifest.permission.ACCESS_COARSE_LOCATION,
//                    Manifest.permission.CAMERA
//            };
//            ActivityCompat.requestPermissions(this, neededPermissions, CODE_PERMISSIONS);
//        }

        startListeningPlatformLocations(); // LOCATION LISTENER ACTIVATED HERE



        String graphJSON = loadGraphJSON();

        if (graphJSON == null) {
            Toast.makeText(this, "Could not find wayfinding_graph.json from raw " +
                    "resources folder. Cannot do wayfinding.", Toast.LENGTH_LONG).show();
        } else {
            mWayfinder = IAWayfinder.create(this, graphJSON);
        }



    }

//    private void requestPermissions() {
//        String[] neededPermissions = {
//                Manifest.permission.CHANGE_WIFI_STATE,
//                Manifest.permission.ACCESS_WIFI_STATE,
//                Manifest.permission.ACCESS_COARSE_LOCATION,
//                Manifest.permission.CAMERA
//        };
//        ActivityCompat.requestPermissions(this, neededPermissions, CODE_PERMISSIONS);
//    }

//    private void initData() {
//        // Decode the drawable into a bitmap
//       // arrowImg = BitmapFactory.decodeResource(getResources(), R.drawable.arrow3);
//
//        //magnetic sensors setups
//        sensorService = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
//        sensor = sensorService.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
//
//        //location manager setups
//        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
//        //destinationObj = new Location(locationManager.NETWORK_PROVIDER);
//        destinationObj = new Location("");
//        destinationObj.setLatitude(45.496618895017484);
//        destinationObj.setLongitude(-73.57782319799755);
//
//
//    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // remember to clean up after ourselves
        mIALocationManager.destroy();
        if (mWayfinder != null) {
            mWayfinder.close();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            mMap.setMyLocationEnabled(false);
        }

        // start receiving location updates & monitor region changes
        mIALocationManager.requestLocationUpdates(IALocationRequest.create(), mListener);
        mIALocationManager.registerRegionListener(mRegionListener);

        mMap.setOnMapClickListener(this);

    }

    @Override
    protected void onPause() {
        super.onPause();
        // unregister location & region changes
        mIALocationManager.removeLocationUpdates(mListener);
        mIALocationManager.registerRegionListener(mRegionListener);
    }


    /**
     * Sets bitmap of floor plan as ground overlay on Google Maps
     */
    private void setupGroundOverlay(IAFloorPlan floorPlan, Bitmap bitmap) {

        if (mGroundOverlay != null) {
            mGroundOverlay.remove();
        }

        if (mMap != null) {
            BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap);
            IALatLng iaLatLng = floorPlan.getCenter();
            LatLng center = new LatLng(iaLatLng.latitude, iaLatLng.longitude);
            GroundOverlayOptions fpOverlay = new GroundOverlayOptions()
                    .image(bitmapDescriptor)
                    .zIndex(0.0f)
                    .position(center, floorPlan.getWidthMeters(), floorPlan.getHeightMeters())
                    .bearing(floorPlan.getBearing());

            mGroundOverlay = mMap.addGroundOverlay(fpOverlay);
        }
    }

    /**
     * Download floor plan using Picasso library.
     */
    private void fetchFloorPlanBitmap(final IAFloorPlan floorPlan) {

        final String url = floorPlan.getUrl();

        if (mLoadTarget == null) {
            mLoadTarget = new Target() {

                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    Log.d(TAG, "onBitmap loaded with dimensions: " + bitmap.getWidth() + "x"
                            + bitmap.getHeight());
                    setupGroundOverlay(floorPlan, bitmap);
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {
                    // N/A
                }

                @Override
                public void onBitmapFailed(Drawable placeHolderDraweble) {
                    showInfo("Failed to load bitmap");
                    mOverlayFloorPlan = null;
                }
            };
        }

        RequestCreator request = Picasso.with(this).load(url);

        final int bitmapWidth = floorPlan.getBitmapWidth();
        final int bitmapHeight = floorPlan.getBitmapHeight();

        if (bitmapHeight > MAX_DIMENSION) {
            request.resize(0, MAX_DIMENSION);
        } else if (bitmapWidth > MAX_DIMENSION) {
            request.resize(MAX_DIMENSION, 0);
        }

        request.into(mLoadTarget);
    }


    /**
     * Fetches floor plan data from IndoorAtlas server.
     */
    private void fetchFloorPlan(String id) {

        // if there is already running task, cancel it
        cancelPendingNetworkCalls();

        final IATask<IAFloorPlan> task = mResourceManager.fetchFloorPlanWithId(id);

        task.setCallback(new IAResultCallback<IAFloorPlan>() {

            @Override
            public void onResult(IAResult<IAFloorPlan> result) {

                if (result.isSuccess() && result.getResult() != null) {
                    // retrieve bitmap for this floor plan metadata
                    fetchFloorPlanBitmap(result.getResult());
                } else {
                    // ignore errors if this task was already canceled
                    if (!task.isCancelled()) {
                        // do something with error
                        showInfo("Loading floor plan failed: " + result.getError());
                        mOverlayFloorPlan = null;
                    }
                }
            }
        }, Looper.getMainLooper()); // deliver callbacks using main looper

        // keep reference to task so that it can be canceled if needed
        mFetchFloorPlanTask = task;

    }

    /**
     * Helper method to cancel current task if any.
     */
    private void cancelPendingNetworkCalls() {
        if (mFetchFloorPlanTask != null && !mFetchFloorPlanTask.isCancelled()) {
            mFetchFloorPlanTask.cancel();
        }
    }

    private void showInfo(String text) {
        final Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), text,
                Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.button_close, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                snackbar.dismiss();
            }
        });
        snackbar.show();
    }

    private void startListeningPlatformLocations() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null && (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        }
    }

    /**
     * Load "wayfinding_graph.json" from raw resources folder of the app module
     * @return
     */
    private String loadGraphJSON() {
        try {
            Resources res = getResources();
            int resourceIdentifier = res.getIdentifier("wayfinding_graph", "raw", this.getPackageName());
            InputStream in_s = res.openRawResource(resourceIdentifier);

            byte[] b = new byte[in_s.available()];
            in_s.read(b);
            return new String(b);
        } catch (Exception e) {
            Log.e(TAG, "Could not find wayfinding_graph.json from raw resources folder");
            return null;
        }

    }



    @Override
    public void onMapClick(LatLng point) {

        if (mMap != null) {

            mDestination = point;
            if (mDestinationMarker == null) {
                mDestinationMarker = mMap.addMarker(new MarkerOptions()
                        .position(point)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            } else {
                mDestinationMarker.setPosition(point);
            }

            if (mWayfinder != null) {
                mWayfinder.setDestination(point.latitude, point.longitude, mFloor);
                customDestLocation.setLatitude(point.latitude);
                customDestLocation.setLongitude(point.longitude);


            }
            Log.d(TAG, "Set destination: (" + mDestination.latitude + ", " +
                    mDestination.longitude + "), floor=" + mFloor);

            updateRoute();
        }
    }

    public static double distFrom(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 3958.75; // miles (or 6371.0 kilometers)
        double dLat = Math.toRadians(lat2-lat1);
        double dLng = Math.toRadians(lng2-lng1);
        double sindLat = Math.sin(dLat / 2);
        double sindLng = Math.sin(dLng / 2);
        double a = Math.pow(sindLat, 2) + Math.pow(sindLng, 2)
                * Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double dist = earthRadius * c;

        return dist;
    }

    private void updateRoute() {
        if (mLocation == null || mDestination == null || mWayfinder == null) {
            return;
        }
        Log.d(TAG, "Updating the wayfinding route");

        mCurrentRoute = mWayfinder.getRoute();


        if (mCurrentRoute == null || mCurrentRoute.length == 0) {
            // Wrong credentials or invalid wayfinding graph
            return;
        }

        IARoutingLeg iaRoutingLeg = mCurrentRoute[0];



        destinationObj.setLatitude(iaRoutingLeg.getEnd().getLatitude());
        destinationObj.setLongitude(iaRoutingLeg.getEnd().getLongitude());



        //initData(iaRoutingLeg.getEnd().getLatitude(),iaRoutingLeg.getEnd().getLongitude());


        //Toast.makeText(getApplicationContext(),"Keep Going Forward",Toast.LENGTH_SHORT).show();
        Log.d(TAG, "F1 First Loc Point in Array: La: "+iaRoutingLeg.getEnd().getLatitude()
                +" Lo:"+iaRoutingLeg.getEnd().getLongitude()+" Direction: "+iaRoutingLeg.getDirection());

        //set compass location to new leg




        if (mPath != null) {
            // Remove old path if any
            clearOldPath();
        }
//         double dist = distFrom(desPoint.latitude,desPoint.longitude,iaRoutingLeg.getEnd().getLatitude()
//                ,iaRoutingLeg.getEnd().getLongitude());
        //  Toast.makeText(getApplicationContext(),"Keep Going Forward "+dist,Toast.LENGTH_SHORT).show();
        visualizeRoute(mCurrentRoute);


    }

    /**
     * Following links
     * https://stackoverflow.com/questions/7978618/rotating-an-imageview-like-a-compass-with-the-north-pole-set-elsewhere
     * https://stackoverflow.com/questions/10160144/android-destination-location
     *
     * @param sensorEvent
     */
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Log.v(" SENSOR CHANGED", "SENSOR CHANGED!!!!");

        ++sensorCallbacksCount;

        if (sensorCallbacksCount % 5 != 0 || LocationObj == null || asyncTask != null) {
            return;
        }

        asyncTask = new WayfindingOverlayActivity.AsyncSensorUpdater(sensorEvent);
        asyncTask.execute();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }


    public void initData(double lat, double lngitude) {
        Log.d("INIT DATA", "INIT DATA !!!! ");

        //destinationObj = new Location(locationManager.NETWORK_PROVIDER);
        destinationObj = new Location("");


        destinationObj.setLatitude(lat);
        destinationObj.setLongitude(lngitude);

        //Toast.makeText(getApplicationContext()," "+baseAzimuth,Toast.LENGTH_SHORT).show();
        //check for user permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            String[] neededPermissions = {
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CAMERA
            };

            ActivityCompat.requestPermissions(this, neededPermissions, CODE_PERMISSIONS);


        }

    }


    private class AsyncSensorUpdater extends AsyncTask<Void, Void, Void> {
        private SensorEvent sensorEvent;
        float direction;
        String bearingText;

        private AsyncSensorUpdater(SensorEvent sensorEvent) {
            Log.v("tt","ASYNCHRONOUS INITIALIZED!!!!");
            this.sensorEvent = sensorEvent;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            synchronized (LocationObj) {
                Log.v("tt2","DOING IN BACKGROUND??????");
                float[] actual_orientation = new float[3];
                switch (sensorEvent.sensor.getType()) {
                    case Sensor.TYPE_MAGNETIC_FIELD:
                        magnitude_values = sensorEvent.values.clone();
                        sensorReady = true;
                        break;
                    case Sensor.TYPE_ACCELEROMETER:
                        accelerometer_values = sensorEvent.values.clone();
                        sensorReady = true;
                }


                Log.v("Sensor Values:", magnitude_values + "  " + accelerometer_values + " " + sensorReady);

                float azimuth = 0;
                if (magnitude_values != null && accelerometer_values != null && sensorReady) {


                    float[] R = new float[16];
                    float[] I = new float[16];

                    SensorManager.getRotationMatrix(R, I, accelerometer_values, magnitude_values);

                    azimuth = (int) (Math.toDegrees(SensorManager.getOrientation(R, actual_orientation)[0]) + 360) % 360;

                    Log.v(TAG, azimuth + "" + (char) 0x00B0);
                }

                baseAzimuth = azimuth;

                GeomagneticField geoField = new GeomagneticField(Double
                        .valueOf(LocationObj.getLatitude()).floatValue(), Double
                        .valueOf(LocationObj.getLongitude()).floatValue(),
                        Double.valueOf(LocationObj.getAltitude()).floatValue(),
                        System.currentTimeMillis());

                azimuth -= geoField.getDeclination(); // converts magnetic north into true north


                //  Log.v("Accurecy", String.valueOf(destinationObj.getAccuracy()));
                // Store the bearingTo in the bearTo variable
                float bearTo = LocationObj.bearingTo(customDestLocation);
                // If the bearTo is smaller than 0, add 360 to get the rotation clockwise.
                if (bearTo < 0) {
                    bearTo = bearTo + 360;
                }

                //This is where we choose to point it
                direction = bearTo - azimuth;
                // If the direction is smaller than 0, add 360 to get the rotation clockwise.
                if (direction < 0) {
                    direction += 360;
                }



                if ((360 >= baseAzimuth && baseAzimuth >= 337.5)
                        || (0 <= baseAzimuth && baseAzimuth <= 22.5)) bearingText = "N";
                else if (baseAzimuth > 22.5 && baseAzimuth < 67.5) bearingText = "NE";
                else if (baseAzimuth >= 67.5 && baseAzimuth <= 112.5) bearingText = "E";
                else if (baseAzimuth > 112.5 && baseAzimuth < 157.5) bearingText = "SE";
                else if (baseAzimuth >= 157.5 && baseAzimuth <= 202.5) bearingText = "S";
                else if (baseAzimuth > 202.5 && baseAzimuth < 247.5) bearingText = "SW";
                else if (baseAzimuth >= 247.5 && baseAzimuth <= 292.5) bearingText = "W";
                else if (baseAzimuth > 292.5 && baseAzimuth < 337.5) bearingText = "NW";
                else bearingText = "?";
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Log.v("ttt","ON POST EXECUTE!!!");
            super.onPostExecute(aVoid);
            rotateImageView(direction);
            // Toast.makeText(getApplicationContext(),"DestLat "+baseAzimuth,Toast.LENGTH_SHORT).show();
            // Log.v("DIRECTION: ", String.valueOf(direction));
            //Log.v("BEARING: ", bearingText);
            asyncTask = null;
            sensorReady = false;
        }
    }

    /**
     *
     *  ROTATION CAPTURE TO FIND NORTH SOUTH EAST WEST
     */
    private  void rotateImageView(float rotate) {
        Log.v(" ROTATEIMAGEVIEW", "ROTATE IMAGE VIEW!!!!");
        Toast.makeText(getApplicationContext(),"ROTATEIMAGE :  "+rotate,Toast.LENGTH_SHORT).show();
        String bt;
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if ((360>= rotate && 330< rotate) || (0 <= rotate && rotate<=45))
        {
            bt = "N";
            Toast.makeText(getApplicationContext(),"NORTH :  ",Toast.LENGTH_SHORT).show();
            // 0 : Start without a delay
            // 400 : Vibrate for 400 milliseconds
            // 200 : Pause for 200 milliseconds
            // 400 : Vibrate for 400 milliseconds
           // long[] mVibratePattern = new long[]{100, 500, 100,500, 100,500, 100,500, 100};
            //long[] patterngom2 = { 500, 500, 100, 500, 100, 500, 100, 500, 100};

            // -1 : Do not repeat this pattern
            // pass 0 if you want to repeat this pattern from 0th index
            // v.vibrate(patterngom2, 1);
            v.vibrate(1000);


        }
        else if(46< rotate && rotate<=135) {
            bt = "E";
           // Toast.makeText(getApplicationContext(),"EAST :  ",Toast.LENGTH_SHORT).show();
//            long[] mVibratePattern = new long[]{100, 200, 100, 200, 100,200, 100,200, 100};
//            v.vibrate(mVibratePattern, -1);
            v.cancel();
        }
        else if(226< rotate && rotate<=314){
            bt ="W";
          // Toast.makeText(getApplicationContext(),"WEST :  ",Toast.LENGTH_SHORT).show();
            v.cancel();
//            long[] mVibratePattern = new long[]{100, 200, 100, 200, 100,200, 100,200, 100};
//            v.vibrate(mVibratePattern, -1);

        }
        else if(136< rotate && rotate<=225) {
            bt = "S";
           // Toast.makeText(getApplicationContext(),"SOUTH :  ",Toast.LENGTH_SHORT).show();
            v.cancel();
        }
        else {
            bt = "?";
           // Toast.makeText(getApplicationContext(),"?? :  ",Toast.LENGTH_SHORT).show();
            v.cancel();
        }

    }

    /**
     * Clear the visualizations for the wayfinding paths
     */
    private void clearOldPath() {
        mPath.remove();
        mPathCurrent.remove();
    }

    /**
     * Visualize the IndoorAtlas Wayfinding path on top of the Google Maps.
     * @param legs Array of IARoutingLeg objects returned from IAWayfinder.getRoute()
     */
    private void visualizeRoute(IARoutingLeg[] legs) {

        // optCurrent will contain the wayfinding path in the current floor and opt will contain the
        // whole path, including parts in other floors.
        PolylineOptions opt = new PolylineOptions();
        PolylineOptions optCurrent = new PolylineOptions();
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        double dist = distFrom(mDestination.latitude,mDestination.longitude,mLocation.latitude,mLocation.longitude);


        if(dist< 0.002){
            // Toast.makeText(getApplicationContext()," REACHED "+dist,Toast.LENGTH_SHORT).show();
            System.exit(0);
        }
        else {
            // Toast.makeText(getApplicationContext(), "DISTANCE LEFT " + dist, Toast.LENGTH_SHORT).show();

        }
        for (IARoutingLeg leg : legs) {

            // v.vibrate(pattern,100);
            //Toast.makeText(getApplicationContext(),"DestLat "+mDestination.latitude,Toast.LENGTH_SHORT).show();
            //Toast.makeText(getApplicationContext(),"DestLong "+mDestination.longitude,Toast.LENGTH_SHORT).show();

            opt.add(new LatLng(leg.getBegin().getLatitude(), leg.getBegin().getLongitude()));
            if (leg.getBegin().getFloor() == mFloor && leg.getEnd().getFloor() == mFloor) {
                optCurrent.add(
                        new LatLng(leg.getBegin().getLatitude(), leg.getBegin().getLongitude()));
                optCurrent.add(
                        new LatLng(leg.getEnd().getLatitude(), leg.getEnd().getLongitude()));

            }
        }
        optCurrent.color(Color.RED);
        if (legs.length > 0) {
            IARoutingLeg leg = legs[legs.length-1];
            opt.add(new LatLng(leg.getEnd().getLatitude(), leg.getEnd().getLongitude()));
        }
        // Here wayfinding path in different floor than current location is visualized in blue and
        // path in current floor is visualized in red
        mPath = mMap.addPolyline(opt);
        mPathCurrent = mMap.addPolyline(optCurrent);
    }

}
