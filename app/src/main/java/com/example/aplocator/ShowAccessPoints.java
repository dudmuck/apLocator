package com.example.aplocator;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.SquareCap;

import java.util.List;

/*
https://stackoverflow.com/questions/7222382/get-lat-long-given-current-point-distance-and-bearing
 */
public class ShowAccessPoints extends AppCompatActivity {
    private static final String TAG = "showAPs";
    private static final String MATH_TEST = "math test";
    static final double earth_R = 6378.1; //Radius of the Earth
    public static LatLng ap_approx;    // coarse starting AP marker position
    public static String ap_approx_mac;

    private void mathTest() {
        double brng = 1.57; //Bearing is 90 degrees converted to radians.
        double dist_km = 15; //Distance in km

        //lat2  52.20444 - the lat result I'm hoping for
        //lon2  0.36056 - the long result I'm hoping for.

        double lat1 = Math.toRadians(52.20472); // Current lat point converted to radians
        double lon1 = Math.toRadians(0.14056); // Current long point converted to radians

        double lat2 = Math.asin( Math.sin(lat1)*Math.cos(dist_km/earth_R) +
                Math.cos(lat1)*Math.sin(dist_km/earth_R)*Math.cos(brng));

        double lon2 = lon1 + Math.atan2(Math.sin(brng)*Math.sin(dist_km/earth_R)*Math.cos(lat1),
                Math.cos(dist_km/earth_R)-Math.sin(lat1)*Math.sin(lat2));
        //Log.i(TAG, "90degrees: " + Math.toRadians(90) + ", to degress: " + Math.toDegrees(1.57));
        Log.i(TAG, "mathTest radians lat2 " + lat2 + ", lon2 " + lon2);
        lat2 = Math.toDegrees(lat2);
        lon2 = Math.toDegrees(lon2);
        Log.i(TAG, "mathTest degrees lat2 " + lat2 + ", lon2 " + lon2);
    }

    private static LatLng getCoords(LatLng orig, double dist_km, double bearing_degrees) {
        double brng = Math.toRadians(bearing_degrees);
        double lat1 = Math.toRadians(orig.latitude); // Current lat point converted to radians
        double lon1 = Math.toRadians(orig.longitude); // Current long point converted to radians

        //Log.i(TAG, "getCoords dist " + dist_km + ", brng " + brng);
        double lat2 = Math.asin( Math.sin(lat1)*Math.cos(dist_km/earth_R) +
                Math.cos(lat1)*Math.sin(dist_km/earth_R)*Math.cos(brng));

        double lon2 = lon1 + Math.atan2(Math.sin(brng)*Math.sin(dist_km/earth_R)*Math.cos(lat1),
                Math.cos(dist_km/earth_R)-Math.sin(lat1)*Math.sin(lat2));

        //Log.i(TAG, "radians lat2 " + lat2 + ", lon2 " + lon2);
        return new LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2));
    }

    public static void my_poly_test() {
        PolylineOptions polylineOptions = new PolylineOptions();
        LatLng myLoc = new LatLng(34.21171204763935,-119.02987238019705);
        polylineOptions.add(myLoc);
        //34.21171204763935,-119.02987238019705

        LatLng out = getCoords(myLoc, 1, 200);
        polylineOptions.add(out);
        /*Polyline polyline =*/ MapsActivity.map.addPolyline(polylineOptions);
        // TODO polyline.setTag("A");
    }

    /* draw lines on map to an access point */
    private void plot_ap(String target_mac) {
        /*double lat_sum = 0, lon_sum = 0;
        int num_coords = 0;

        // probably want to show multiple: MapsActivity.map.clear();
        try (InputStream inputStream = getContentResolver().openInputStream(UsbAccessPoints.uri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int comma_at = line.indexOf(',');
                Log.i(TAG, "line \"" + line + "\" (comma at " + comma_at + ")");
                String first_element = line.substring(0, comma_at);
                if (target_mac.equals(first_element)) { // line starts with bssid mac address
                    String tagStr = first_element;
                    Log.i(TAG, "lineMatch " + line);
                    int next_comma_at = line.indexOf(',', comma_at+1);
                    String latStr = line.substring(comma_at+1, next_comma_at);
                    Double lat = Double.parseDouble(latStr);
                    Log.i(TAG, "latStr \"" + latStr + "\", double " + lat);

                    comma_at = next_comma_at;
                    next_comma_at = line.indexOf(',', comma_at+1);
                    String lonStr = line.substring(comma_at+1, next_comma_at);
                    Double lon = Double.parseDouble(lonStr);
                    Log.i(TAG, "lonStr \"" + lonStr + "\", double " + lon);

                    comma_at = next_comma_at;
                    String bearingStr = line.substring(comma_at+1);
                    tagStr += ", " + bearingStr;
                    int bearing = Integer.parseInt(bearingStr.trim());
                    Log.i(TAG, "bearing " + bearingStr);

                    LatLng rxing_antenna_at = new LatLng(lat, lon);
                    PolylineOptions polylineOptions = new PolylineOptions();
                    polylineOptions.add(rxing_antenna_at);
                    LatLng out = getCoords(rxing_antenna_at, 1, bearing);
                    polylineOptions.add(out);
                    Polyline polyline = MapsActivity.map.addPolyline(polylineOptions);
                    polyline.setColor(Color.RED);
                    polyline.setStartCap(new SquareCap());
                    polyline.setTag(tagStr);
                    polyline.setClickable(true);
                    MapsActivity.map.setOnPolylineClickListener(new GoogleMap.OnPolylineClickListener() {
                        @Override
                        public void onPolylineClick(Polyline polyline) {
                            // Flip the values of the red, green and blue components of the polyline's color.
                            polyline.setColor(polyline.getColor() ^ 0x00ffffff);
                            Log.i(TAG, "onPolylineClick " + polyline.getTag());
                            MapsActivity.delete_tag = (String) polyline.getTag();
                        }
                    });

                    lat_sum += lat;
                    lon_sum += lon;
                    num_coords++;
                } else if (!first_element.isEmpty()) {
                    // line starts with AP location
                    Double lat = Double.parseDouble(first_element);
                    Log.i(TAG, "first_element \"" + first_element + "\", lat " + lat);
                    int next_comma_at = line.indexOf(',', comma_at+1);
                    if (next_comma_at > -1) {
                        String lonStr = line.substring(comma_at + 1, next_comma_at);
                        Log.i(TAG, "lonStr \"" + lonStr + "\"" + ", next_comma_at " + next_comma_at);
                        Double lon = Double.parseDouble(lonStr);
                        next_comma_at++; // skip comma
                        while (line.charAt(next_comma_at) == ' ') {
                            next_comma_at++;
                        }
                        String bssid = line.substring(next_comma_at);
                        Log.i(TAG, "#### " + lat + ", " + lon + ", \"" + bssid + "\" #########");
                        MarkerOptions mo = new MarkerOptions();
                        mo.draggable(true);
                        mo.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA));
                        mo.position(new LatLng(lat, lon));
                        mo.title(bssid);
                        MapsActivity.map.addMarker(mo).setTag(bssid);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (num_coords > 0) {
            ap_approx = new LatLng(lat_sum / num_coords, lon_sum / num_coords);
            ap_approx_mac = target_mac;
            MapsActivity.setEnabled_add_AP_marker(true);
        }*/
        AzimuthDao ad = MapsActivity.db.azimuthDao();
        Log.w(TAG, "TODO plot_ap " + target_mac);

        new Thread(new Runnable() {
            public void run() {
                double lat_sum = 0, lon_sum = 0;
                int num_coords = 0;
                List<Azimuth> al =  ad.findByBSSID(target_mac);

                Log.i(TAG, "got " + al.size() + " azimuths");
                for (int i = 0; i < al.size(); i++) {
                    Azimuth a = al.get(i);
                    if (a.degrees >= 0) {
                        // TODO, handle AP location with degress of -1

                        lat_sum += a.lat;
                        lon_sum += a.lon;
                        num_coords++;

                        String tagStr = a.macAddress + ", " + a.degrees;
                        Log.i(TAG, "bearing " + a.degrees);
                        LatLng rxing_antenna_at = new LatLng(a.lat, a.lon);
                        PolylineOptions polylineOptions = new PolylineOptions();
                        polylineOptions.add(rxing_antenna_at);
                        LatLng out = getCoords(rxing_antenna_at, 1, a.degrees);
                        polylineOptions.add(out);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Polyline polyline = MapsActivity.map.addPolyline(polylineOptions);
                                polyline.setColor(Color.RED);
                                polyline.setStartCap(new SquareCap());
                                polyline.setTag(tagStr);
                                polyline.setClickable(true);
                                MapsActivity.map.setOnPolylineClickListener(new GoogleMap.OnPolylineClickListener() {
                                    @Override
                                    public void onPolylineClick(Polyline polyline) {
                                        // Flip the values of the red, green and blue components of the polyline's color.
                                        polyline.setColor(polyline.getColor() ^ 0x00ffffff);
                                        Log.i(TAG, "onPolylineClick " + polyline.getTag());
                                        MapsActivity.delete_tag = (String) polyline.getTag();
                                    }
                                });
                                MapsActivity.setEnabled_add_AP_marker(true);
                            }
                        });
                    } else {
                        // no bearing: this is location of access point
                        MarkerOptions mo = new MarkerOptions();
                        mo.draggable(true);
                        mo.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA));
                        mo.position(new LatLng(a.lat, a.lon));
                        mo.title(a.macAddress);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                MapsActivity.map.addMarker(mo).setTag(a.macAddress);
                            }
                        });
                    }
                }

                ap_approx = new LatLng(lat_sum / num_coords, lon_sum / num_coords);
                ap_approx_mac = target_mac;
                /*
                TextView tv = findViewById(label_ids[i]);
                tv.post(new Runnable() {
                    @Override
                    public void run() {
                        tv.setText(Integer.toString(aList.size()));
                    }
                });*/

            }
        }).start();
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*if (UsbAccessPoints.uri == null) {
            Toast.makeText(this, "no file open", Toast.LENGTH_LONG).show();
            return;
        }*/

        setContentView(R.layout.activity_show_access_points);

        /*TextView tv = (TextView) findViewById(R.id.tv_long);
        tv.setMovementMethod(new ScrollingMovementMethod());*/

        View.OnClickListener ocl = new View.OnClickListener() {
            public void onClick(View v) {
                TextView tv = (TextView) v;
                Log.i(TAG, "onClick " + tv.getText());
                if (tv.getText().equals(MATH_TEST)) {
                    PolylineOptions polylineOptions = new PolylineOptions();
                    LatLng myLoc = new LatLng(34.21171204763935,-119.02987238019705);
                    polylineOptions.add(myLoc);
                    //mathTest();
                    //LatLng getCoords(LatLng orig, double dist_km, double bearing_degrees)
                    LatLng out = getCoords(new LatLng(52.20472, 0.14056), 15, 90);
                    Log.i(TAG, "from getCoords " + out);
                    //34.21171204763935,-119.02987238019705

                    out = getCoords(myLoc, 1, 0);
                    polylineOptions.add(out);
                    /*Polyline polyline =*/ MapsActivity.map.addPolyline(polylineOptions);
                    // TODO polyline.setTag("A");
                    Log.i(TAG, "to north getCoords " + out);
                    out = getCoords(myLoc, 1, 90);
                    Log.i(TAG, "to east getCoords " + out);
                    out = getCoords(myLoc, 1, 180);
                    Log.i(TAG, "to south getCoords " + out);
                    out = getCoords(myLoc, 1, 270);
                    Log.i(TAG, "to west getCoords " + out);
                    /*
                    https://github.com/googlemaps/android-samples/blob/main/ApiDemos/java/app/src/gms/java/com/example/mapdemo/polyline/PolylineDemoActivity.java
                     */
                } else {
                    plot_ap(tv.getText().toString());
                }
            }
        };

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);

        LinearLayout myLayout = findViewById(R.id.main);

        Button myButton2 = new Button(this);
        myButton2.setLayoutParams(lp);
        myButton2.setText(MATH_TEST);
        myButton2.setOnClickListener(ocl);
        myLayout.addView(myButton2);

        /*LinkedList<String> ll = new LinkedList<String>();

        try (InputStream inputStream = getContentResolver().openInputStream(UsbAccessPoints.uri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                boolean new_mac = true;
                String firstElement = line.substring(0, line.indexOf(','));
                int colonAt = firstElement.indexOf(':');
                Log.i(TAG, "lines " + line + "[[" + colonAt + "]]");
                if (colonAt >= 0) { // line starts with mac address.  Is bearing to AP
                    for (int i = 0; i < ll.size(); i++) {
                        if (ll.get(i).equals(firstElement)) {
                            new_mac = false;
                        }
                    }
                    if (new_mac)
                        ll.add(firstElement);
                } // else line starts with latitude, is location of AP
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < ll.size(); i++) {
            Button but = new Button(this);
            but.setLayoutParams(lp);
            but.setText(ll.get(i));
            but.setOnClickListener(ocl);
            myLayout.addView(but);
        }*/

        AzimuthDao ad = MapsActivity.db.azimuthDao();
        Context c = this;

        new Thread(new Runnable() {
            public void run() {
                List<String> strList = ad.get_BSSIDs();
                Log.i(TAG, "num bssids: " + strList.size());
                for (int i = 0; i < strList.size(); i++) {
                    String bssid = strList.get(i);
                    Log.i(TAG, "BSSID " + strList.get(i));
                    myLayout.post(new Runnable() {
                        @Override
                        public void run() {
                            Button but = new Button(c);
                            but.setLayoutParams(lp);
                            but.setText(bssid);
                            but.setOnClickListener(ocl);
                            myLayout.addView(but);
                        }
                    });
                }
            }
        }).start();
    }

}