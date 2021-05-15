package com.example.aplocator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.room.Room;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Iterator;

public class MapsActivity extends AppCompatActivity
        implements GoogleMap.OnMyLocationButtonClickListener,
        GoogleMap.OnMarkerDragListener,
        GoogleMap.OnMyLocationClickListener,
        OnMapReadyCallback,
        ActivityCompat.OnRequestPermissionsResultCallback,
        GoogleMap.OnMarkerClickListener
{
    private static final String TAG = "ap-locator";
    private static final String MIME_TYPE = "text/plain";
    private static final String CUR_POS = "currentPosition";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int WRITE_REQUEST_CODE = 101;
    private static final int OPEN_REQUEST_CODE = 102;
    private static final int DELETE_REQUEST_CODE = 103;
    public static AppDatabase db;
    private boolean permissionDenied = false;
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private UsbManager usb_manager;
    UsbDevice permitted_device;
    private Marker dragged_ap_marker;

    public static GoogleMap map;

    // The entry point to the Fused Location Provider.
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Location lastKnownLocation;
    private final LatLng defaultLocation = new LatLng(-33.8523341, 151.2106085);
    public static LatLng markerPosition;
    private static final int DEFAULT_ZOOM = 15;
    private static final int ACTION_PRINT_LAT_LNG = 100;
    private static final int ACTION_NEW_MARKER = 101;
    static Menu optionsMenu;
    public static String delete_tag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Find the toolbar view inside the activity layout
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        // Sets the Toolbar to act as the ActionBar for this Activity window.
        // Make sure the toolbar exists in the activity and is not null
        setSupportActionBar(toolbar);

        list_usb_devices(); // for already usb connected
        registerUSBBroadCast(); // for usb to be connected

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Construct a FusedLocationProviderClient.
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        //UsbAccessPoints.test_color();
        db = Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "aploc-db").build();
    }

    // Menu icons are inflated just as they were with actionbar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        /*MenuItem mi = menu.findItem(R.id.mi_usb); TODO disable menu item if no USB device ?
        mi.setEnabled(false);*/
        MenuItem mi = menu.findItem(R.id.add_AP_marker);
        mi.setEnabled(false);
        mi = menu.findItem(R.id.save_AP_marker);
        mi.setEnabled(false);
        optionsMenu = menu;
        return true;
    }

    public static void setEnabled_add_AP_marker(boolean en) {
        MenuItem mi = optionsMenu.findItem(R.id.add_AP_marker);
        mi.setEnabled(en);
    }

    @Override
    public void onMarkerDragStart(Marker marker) {
    }

    @Override
    public void onMarkerDrag(Marker marker) {
    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
        String o = (String) marker.getTag();
        if (o != null) {
            if (o.equals(CUR_POS)) {
                markerPosition = marker.getPosition();
                Log.i(TAG, "dragEnd curpos drag end " + markerPosition);
            } else {
                AzimuthDao ad = db.azimuthDao();
                LatLng pos = marker.getPosition();
                Log.i(TAG, "dragEnd curpos " + o + " end " + pos);
                new Thread(new Runnable() {
                    public void run() {
                        int n = ad.update_ap_location(o, pos.latitude, pos.longitude);
                        Log.i(TAG, Integer.toString(n) + " = update_ap_location");
                        if (n == 0) {
                            UsbAccessPoints.write_ap_location(pos.latitude, pos.longitude, o);
                        }
                    }
                }).start();
                dragged_ap_marker = marker;
            }
        } else{
            Log.e(TAG, "onMarkerDragEnd no tag");
        }
    }

    @Override
    public boolean onMarkerClick(final Marker marker) {
        String o = (String) marker.getTag();
        if (o != null) {
            Log.i(TAG, "onMarkerClick " + o);
            if (!o.equals(CUR_POS)) {
                float a = marker.getAlpha();
                Log.i(TAG, "alpha " + a);
                if (a > 0.7)
                    marker.setAlpha(0.5f);
                else
                    marker.setAlpha(1.0f);
                delete_tag = o;
            }
        } else {
            Log.e(TAG, "onMarkerClick no tag");
        }

        return true; // true default behavior: camera move to marker.
    }

    /**
     * Gets the current location of the device, and positions the map's camera.
     * https://developers.google.com/maps/documentation/android-sdk/current-place-tutorial
     */
    private void getDeviceLocation(int action) {
         // Get the best and most recent location of the device, which may be null in rare
         // cases when a location is not available.
        try {
            if (!permissionDenied) {
                Task<Location> locationResult = fusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            lastKnownLocation = task.getResult();
                            if (lastKnownLocation != null) {
                                /* https://developer.android.com/reference/android/location/Location */
                                /*
                                map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(lastKnownLocation.getLatitude(),
                                                lastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                                 */
                                if (action == ACTION_PRINT_LAT_LNG) {
                                    Log.i(TAG, "lat " + lastKnownLocation.getLatitude() + ", lng: " + lastKnownLocation.getLongitude());
                                } else if (action == ACTION_NEW_MARKER) {
                                    MarkerOptions mo = new MarkerOptions();
                                    markerPosition = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                                    mo.position(markerPosition);
                                    mo.draggable(true);
                                    mo.title("manually set position");
                                    map.addMarker(mo).setTag(CUR_POS);
                                    /* https://developers.google.com/maps/documentation/android-sdk/marker */
                                }
                            }
                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            map.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(defaultLocation, DEFAULT_ZOOM));
                            map.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage(), e);
        }
    }

    private void remove_line() {
        if (delete_tag == null) {
            Toast.makeText(this, "please select item to delete", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.i(TAG, "remove_item \"" + delete_tag + "\" " + delete_tag.indexOf(','));

        AzimuthDao ad = db.azimuthDao();
        if (delete_tag.indexOf(',') == -1) {
            // no bearing: location of AP
            final String target_mac = delete_tag;
            Log.i(TAG, "DEL_TYPE_AP_LOCATION");
            new Thread(new Runnable() {
                public void run() {
                    ad.deleteWithBearing(target_mac, -1);
                }
            }).start();
        } else {
            // have bearing
            int comma_at = delete_tag.indexOf(',');
            final String target_mac = delete_tag.substring(0, comma_at);
            String bearingStr = delete_tag.substring(comma_at+1);
            final int target_bearing = Integer.parseInt(bearingStr.trim());
            Log.i(TAG, "DEL_TYPE_BEARING target_mac=\"" + target_mac + "\", " + target_bearing);
            new Thread(new Runnable() {
                public void run() {
                    ad.deleteWithBearing(target_mac, target_bearing);
                }
            }).start();

        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;

        switch (item.getItemId()) {
            case R.id.mi_usb:
                if (permitted_device != null) {
                    intent = new Intent(this, UsbAccessPoints.class);
                    intent.putExtra(UsbManager.EXTRA_DEVICE, permitted_device);
                    startActivity(intent);
                } else {
                    Log.e(TAG, "null permitted_device");
                    Toast.makeText(this, "no USB device", Toast.LENGTH_SHORT)
                            .show();
                }
                return true;
            case R.id.show_file:
                map.clear();
                intent = new Intent(this, ShowAccessPoints.class);
                startActivity(intent);
                return true;
            case R.id.map_type_normal:
                map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                return true;
            case R.id.map_type_satellite:
                map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                return true;
            case R.id.map_type_hybrid:
                map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                return true;
            case R.id.map_type_terrain:
                map.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                return true;
            case R.id.map_type_none:
                map.setMapType(GoogleMap.MAP_TYPE_NONE);
                return true;
            case R.id.add_marker:
                map.clear();    // removes any marker already there, and anything else too
                getDeviceLocation(ACTION_NEW_MARKER);
                return true;
            /*case R.id.get_location:
                getDeviceLocation(ACTION_PRINT_LAT_LNG);
                return true;*/
            /*case R.id.create_file:
                Date currentTime = Calendar.getInstance().getTime();
                String str = currentTime.toString();
                String name = str.replace(' ', '_');
                intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(MIME_TYPE);
                intent.putExtra(Intent.EXTRA_TITLE, name + ".txt");
                startActivityForResult(intent, WRITE_REQUEST_CODE);
                return true;*/
                /*
            case R.id.open_file:
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(MIME_TYPE);
                startActivityForResult(intent, OPEN_REQUEST_CODE);
                return true;*/
                /*
            case R.id.remove_file:
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(MIME_TYPE);
                startActivityForResult(intent, DELETE_REQUEST_CODE);
                return true;
                 */
            case R.id.export_db:
                Log.e(TAG, "TODO export DB");
                return true;
            case R.id.add_AP_marker:
                if (ShowAccessPoints.ap_approx != null) {
                    MarkerOptions mo = new MarkerOptions();
                    mo.draggable(true);
                    mo.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
                    mo.position(ShowAccessPoints.ap_approx);
                    mo.title(ShowAccessPoints.ap_approx_mac);
                    map.addMarker(mo).setTag(ShowAccessPoints.ap_approx_mac);
                    /* https://developers.google.com/maps/documentation/android-sdk/marker */
                } else {
                    Toast.makeText(this, "no AP position exists", Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.save_AP_marker:
                LatLng pos = dragged_ap_marker.getPosition();
                String bssid = (String) dragged_ap_marker.getTag();
                Log.i(TAG, "save ap marker " + pos + ", " + bssid);
                //void write_ap_location(double lat, double lon, String bssid)
                UsbAccessPoints.write_ap_location(pos.latitude, pos.longitude, bssid);
                return true;
            case R.id.delete_item:
                Log.e(TAG, "delete_item, delete_tag " + delete_tag);
                remove_line();
                return true;
            /*case R.id.room_test:
                AzimuthDao ad = db.azimuthDao();
                List<Azimuth> azimuths = ad.getAll();
                for (int i = 0; i < azimuths.size(); i++) {
                    Log.i(TAG, "mac " + azimuths.get(i).macAddress);
                }
                return true;*/
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        /*
        https://developer.android.com/training/data-storage/shared/documents-files
        https://gist.github.com/neonankiti/05922cf0a44108a2e2732671ed9ef386
         */
        if (requestCode == WRITE_REQUEST_CODE || requestCode == OPEN_REQUEST_CODE) {
            String mode = "w";
            if (requestCode == OPEN_REQUEST_CODE)
                mode = "wa";
            switch (resultCode) {
                case Activity.RESULT_OK:
                    Log.e(TAG, "deprecated open output stream");
                    break;
                case Activity.RESULT_CANCELED:
                    break;
            }
        } else if (requestCode == DELETE_REQUEST_CODE) {
            if (data != null) {
                Uri uri = data.getData();
                try {
                    DocumentsContract.deleteDocument(getContentResolver(), uri);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     *  @SuppressLint("MissingPermission")
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        /* https://developers.google.com/maps/documentation/android-sdk/controls */
        map.setOnMyLocationButtonClickListener(this);
        map.setOnMyLocationClickListener(this);
        map.setOnMarkerDragListener(this);
        map.setOnMarkerClickListener(this);

        //Log.i(TAG, "onMapReady -> enableMyLocation");
        enableMyLocation();
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            if (map != null) {
                map.setMyLocationEnabled(true);
            }
        } else {
            // Permission to access the location is missing. Show rationale and request permission
            PermissionUtils.requestPermission(this, LOCATION_PERMISSION_REQUEST_CODE,
                    Manifest.permission.ACCESS_FINE_LOCATION, true);
        }
    }

    @Override
    public void onMyLocationClick(@NonNull Location location) {
        Toast.makeText(this, "Current location:\n" + location, Toast.LENGTH_LONG)
                .show();
    }

    @Override
    public boolean onMyLocationButtonClick() {
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return;
        }

        if (PermissionUtils.isPermissionGranted(permissions, grantResults, Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Enable the my location layer if the permission has been granted.
            enableMyLocation();
        } else {
            // Permission was denied. Display an error message
            // Display the missing permission error dialog when the fragments resume.
            permissionDenied = true;
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        Log.i(TAG, "onResumeFragments " + permissionDenied);
        if (permissionDenied) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError();
            permissionDenied = false;
        }
    }

    /**
     * Displays a dialog with error message explaining that the location permission is missing.
     */
    private void showMissingPermissionError() {
        PermissionUtils.PermissionDeniedDialog
                .newInstance(true).show(getSupportFragmentManager(), "dialog");
    }

    private void usb_request_connect_perms(UsbDevice device) {
        if (device.getVendorId() == 0x1234) {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            registerReceiver(mUsbReceiver, filter);
            usb_manager.requestPermission(device, permissionIntent);
            Log.i(TAG, "recognized " + device);
        }
        if (device.getDeviceClass() == UsbConstants.USB_CLASS_HID) {
            Log.i(TAG, "HID-device");
        } else {
            Log.i(TAG, "non-HID");
        }
    }

    private void list_usb_devices() {
        usb_manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usb_manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            Log.i(TAG, "list_usb vendor:" + Integer.toString(device.getVendorId(), 16) + ", product:" + device.getProductId());
            usb_request_connect_perms(device);
            break;
        }
    }

    private void registerUSBBroadCast() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(mUsbReceiver, filter);
    }

    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                Log.i(TAG, "BroadcastReceiver USB Connected");
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    usb_request_connect_perms(device);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    Log.i(TAG, "BroadcastReceiver USB Disconnected");
                    /*
                    https://stackoverflow.com/questions/30629071/sending-a-simple-message-from-service-to-activity
                     */
                }
            } else if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    permitted_device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (permitted_device != null) {

                        }
                    }
                    else {
                        Log.d(TAG, "permission denied for device " + permitted_device);
                    }
                }
            }
        }
    };
}