package com.example.aplocator;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class UsbAccessPoints extends AppCompatActivity {
    private static final String TAG = "USB-AP";
    //private static final String BSSID_OF_INTEREST = "a8:40:41:1d:ba:3c";
    private static final String BSSID_OF_INTEREST = "none";
    private final int MAX_RESULTS = 17;

    private final int FROM_DEVICE_REPORT_LENGTH = 64;
    private final int TO_DEVICE_REPORT_LENGTH = 8;
    private final static int WIFI_RESULTS_START = 0x80;
    private final static int WIFI_RESULTS_CONT = 0x81;
    private final static int WIFI_REQ_CFG = 0x82;
    public final static int WIFI_SET_WIFI_TYPE = 0x83;
    public final static int WIFI_SET_CHANMASK = 0x84;
    public final static int WIFI_SET_ACQMODE = 0x85;
    public final static int WIFI_SET_NBMAXRES = 0x86;
    public final static int WIFI_SET_NBSCANPERCHAN = 0x87;
    public final static int WIFI_SET_TIMEOUT = 0x88;
    public final static int WIFI_SET_ABORT = 0x89;

    public static UsbDeviceConnection connection;
    private static  UsbEndpoint endpoint_in;
    private static UsbEndpoint endpoint_out;
    private static byte[] bytesFromDev;
    public static byte[] bytesToDev;
    public static byte usbDevReq;
    private static int TIMEOUT = 0;
    private boolean forceClaim = true;
    public static boolean keep_running;
    private static int rate;
    private static long xfer_at;
    private String[] ap_mac;
    private int[] ap_rssi;
    private int[] ap_rssi_prev;
    private long[] mac_addrs;
    private final int MAX_DEAD_FOR = 35;
    private int[] ap_dead_for;
    private final int[] mac_ids = {
            R.id.tvMAC_A, /* 1 */
            R.id.tvMAC_B,/* 2 */
            R.id.tvMAC_C,/* 3 */
            R.id.tvMAC_D,/* 4 */
            R.id.tvMAC_E,/* 5 */
            R.id.tvMAC_F,/* 6 */
            R.id.tvMAC_G,/* 7 */
            R.id.tvMAC_H,/* 8 */
            R.id.tvMAC_I,/* 9 */
            R.id.tvMAC_J,/* 10 */
            R.id.tvMAC_K,/* 11 */
            R.id.tvMAC_L,/* 12 */
            R.id.tvMAC_M,/* 13 */
            R.id.tvMAC_N,/* 14 */
            R.id.tvMAC_O,/* 15 */
            R.id.tvMAC_P,/* 16 */
            R.id.tvMAC_Q/* 17 */
    };
    private final int[] rssi_ids = {
            R.id.tvRssi_A,
            R.id.tvRssi_B,
            R.id.tvRssi_C,
            R.id.tvRssi_D,
            R.id.tvRssi_E,
            R.id.tvRssi_F,
            R.id.tvRssi_G,
            R.id.tvRssi_H,
            R.id.tvRssi_I,
            R.id.tvRssi_J,
            R.id.tvRssi_K,
            R.id.tvRssi_L,
            R.id.tvRssi_M,
            R.id.tvRssi_N,
            R.id.tvRssi_O,
            R.id.tvRssi_P,
            R.id.tvRssi_Q
    };
    private final int[] label_ids = {
            R.id.label_A,
            R.id.label_B,
            R.id.label_C,
            R.id.label_D,
            R.id.label_E,
            R.id.label_F,
            R.id.label_G,
            R.id.label_H,
            R.id.label_I,
            R.id.label_J,
            R.id.label_K,
            R.id.label_L,
            R.id.label_M,
            R.id.label_N,
            R.id.label_O,
            R.id.label_P,
            R.id.label_Q
    };
    private boolean[] new_ap;
    static final boolean dbg = false;
    private ColorStateList default_colors;


    private static final int DBM_LOWEST = -95;
    private static final int DBM_HIGHEST = -20;
    private static final double half_diff = (DBM_HIGHEST - DBM_LOWEST) / 2.0;
    private static final int mid_point = (DBM_HIGHEST + DBM_LOWEST) / 2;

    public static void test_color() {
        for (int dbm = DBM_LOWEST; dbm < DBM_HIGHEST; dbm += 3) {
            int color = rssi_to_color(dbm);
            //Log.i(TAG, dbm + " color " + Integer.toString(color, 16));
            String hexColor = String.format("#%08X", color);
            Log.i(TAG, dbm + " color " + hexColor);
        }
    }

    private static int rssi_to_color(int dbm) {
        int ret;
        double red, green;
        if (dbm < DBM_LOWEST || dbm > DBM_HIGHEST) {
            return 0;   // no color
        }
        if (dbm < mid_point) {
            red = 0xff;
            green = (dbm - DBM_LOWEST) / half_diff;
            green *= 255;
        } else {
            red = (DBM_HIGHEST - dbm) / half_diff;
            red *= 255;
            green = 0xff;
        }
        ret = Color.argb(0xff, (int)red, (int)green, 0);
        return ret;
    }

    private void take_results(int n_results, boolean cont) {
        int results = 0;

        if (!cont) {
            for (int x = 0; x < MAX_RESULTS; x++) {
                ap_dead_for[x]++;
                /* will be zero'd if not dead */
            }
        }

        for (int oi = 2; oi < FROM_DEVICE_REPORT_LENGTH; ) {
            int found_at = -1;
            String macStr = "";
            long mac_addr = 0;
            for (int mac_idx = 0; mac_idx < 6; mac_idx++) {
                int octet = bytesFromDev[oi++] & 0xff;
                mac_addr <<= 8;
                mac_addr += octet;
                macStr += Integer.toString(octet, 16);
                if (mac_idx < 5)
                    macStr += ":";
            }
            int rssi = bytesFromDev[oi++];

            /* is this mac address already displayed? */
            for (int n = 0; n < MAX_RESULTS; n++) {
                if (mac_addrs[n] == mac_addr) {
                    found_at = n;
                    break;
                }
            }

            if (found_at != -1) {
                /* already shown mac address */
                ap_mac[found_at] = macStr;
                ap_rssi_prev[found_at] = ap_rssi[found_at];
                ap_rssi[found_at] = rssi;
                //Log.i(TAG, "found_at " + found_at + " mac " + macStr + ", " + rssi + "dBm");
                if (macStr.equals(BSSID_OF_INTEREST))
                    Log.i(TAG, "found_at " + found_at + " mac " + macStr + ", " + rssi + "dBm, " + ap_dead_for[found_at]);
                ap_dead_for[found_at] = 0;
            } else {
                /* newly seen mac address */
                boolean shown = false;
                for (int x = 0; x < MAX_RESULTS; x++) {
                    if (ap_dead_for[x] >= MAX_DEAD_FOR) {
                        /*
                        if (ap_rssi[x] > -40)
                            Log.e(TAG, "overwriting dead " + ap_mac[x] + " " + ap_rssi[x] + "dBm");
                        else
                            Log.i(TAG, "overwriting dead " + ap_mac[x] + " " + ap_rssi[x] + "dBm");
                        */
                        if (macStr.equals(BSSID_OF_INTEREST))
                            Log.i(TAG, "overwriting dead " + ap_mac[x] + " " + ap_rssi[x] + "dBm");
                        ap_mac[x] = macStr;
                        ap_rssi_prev[x] = 0;
                        ap_rssi[x] = rssi;
                        ap_dead_for[x] = 0;
                        mac_addrs[x] = mac_addr;
                        new_ap[x] = true;
                        shown = true;
                        break;
                    }
                }
                if (!shown) {
                    if (dbg)
                        Log.w(TAG, macStr + " not shown");
                    int weakest_idx = -1;
                    int weakest_rssi = 0;
                    for (int x = 0; x < MAX_RESULTS; x++) {
                        if (ap_rssi[x] < weakest_rssi && ap_rssi_prev[x] < weakest_rssi) {
                            weakest_rssi = ap_rssi[x];
                            weakest_idx = x;
                        }
                    }
                    if (rssi > weakest_rssi) {
                        /* stronger AP wont show, make it show here */
                        if (ap_mac[weakest_idx].equals(BSSID_OF_INTEREST))
                            Log.i(TAG, weakest_idx + " overwriting weakest " + ap_mac[weakest_idx] + " at " + ap_rssi[weakest_idx] + "dBm, with new rssi " + rssi);
                        ap_mac[weakest_idx] = macStr;
                        ap_rssi_prev[weakest_idx] = 0;
                        ap_rssi[weakest_idx] = rssi;
                        ap_dead_for[weakest_idx] = 0;
                        mac_addrs[weakest_idx] = mac_addr;
                        new_ap[weakest_idx] = true;
                    }
                }
            }

            //Log.i(TAG, n_results + ") result " + results + ", " + mac + ", " + rssi);
            if (++results == n_results)
                break;
            if ((oi + 7) >= FROM_DEVICE_REPORT_LENGTH)
                break;
            if (!keep_running)
                return;
        }

        if (!cont && results < n_results) {
            /* more will be coming, dont show until have all */
            return;
        }

        runOnUiThread(new Runnable() {
            // https://www.tutorialspoint.com/how-do-we-use-runonuithread-in-android
            @Override
            public void run() {
                TextView tv_m, tv_r;
                for (int i = 0; i < MAX_RESULTS; i++) {
                    tv_m = findViewById(mac_ids[i]);
                    if (new_ap[i]) {
                        if (dbg)
                            Log.i(TAG,  Integer.toString(i) + " NEW AP " + ap_mac[i]);
                        tv_m.setText(ap_mac[i]);
                        tv_m.setEnabled(true);
                        // indicate new MAC address
                        tv_m.setBackgroundColor(Color.argb(0xff, 0xff, 0x00, 0x00));
                        new_ap[i] = false;
                    } else {
                        // default background
                        tv_m.setBackgroundColor(Color.argb(0x00, 0x00, 0x00, 0x00));
                    }
                    tv_r = findViewById(rssi_ids[i]);
                    tv_r.setBackgroundColor(rssi_to_color(ap_rssi[i]));
                    tv_r.setText(Integer.toString(ap_rssi[i]));
                    if (ap_dead_for[i] == 0) {
                        tv_m.setTextColor(default_colors);  // default
                        tv_m.setEnabled(true);
                    } else if (ap_dead_for[i] > (MAX_DEAD_FOR/2)) {
                        tv_m.setTextColor(Color.argb(0xff, 0xff, 0xff, 0x00));  // yellow
                    } else if (ap_dead_for[i] >= MAX_DEAD_FOR) {
                        tv_m.setEnabled(false);
                    }

                    tv_r = findViewById(R.id.upperLeft);
                    tv_r.setText(String.format(Locale.ENGLISH, "%4d", rate));
                }
            }
        });
    }

    private void writeBearing(CharSequence bearing, int tv_mac_id) {
        if (MapsActivity.markerPosition == null) {
            Toast.makeText(this, "no marker on map", Toast.LENGTH_SHORT).show();
            return;
        }
        TextView tv_m = findViewById(tv_mac_id);
        Log.i(TAG, "writeBearing " + bearing + ", " + tv_m.getText());
        AzimuthDao ad = MapsActivity.db.azimuthDao();
        Azimuth a = new Azimuth();
        a.macAddress = tv_m.getText().toString();
        a.lat = MapsActivity.markerPosition.latitude;
        a.lon = MapsActivity.markerPosition.longitude;
        a.degrees = Integer.parseInt(bearing.toString());

        new Thread(new Runnable() {
            public void run() {
                ad.insert(a);
            }
        }).start();
    }

    public static void write_ap_location(double lat, double lon, String bssid) {
        AzimuthDao ad = MapsActivity.db.azimuthDao();
        Azimuth a = new Azimuth();
        a.macAddress = bssid;
        a.lat = lat;
        a.lon = lon;
        a.degrees = -1;

        new Thread(new Runnable() {
            public void run() {
                ad.insert(a);
            }
        }).start();
    }

    public void readFile(View v) {
        AzimuthDao ad = MapsActivity.db.azimuthDao();

        new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < mac_ids.length; i++) {
                    TextView macView = findViewById(mac_ids[i]);
                    String tv_mac = macView.getText().toString();
                    List<Azimuth> aList = ad.findByBSSID(tv_mac);

                    TextView tv = findViewById(label_ids[i]);
                    tv.post(new Runnable() {
                        @Override
                        public void run() {
                            tv.setText(Integer.toString(aList.size()));
                        }
                    });
                }
            }
        }).start();
    }

    public void configRadio(View v) {
        Log.i(TAG, "configRadio");
        usbDevReq = (byte)WIFI_REQ_CFG;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        TextView.OnEditorActionListener eal;
        EditText et;
        Intent intent = getIntent();
        int dir;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usb_access_points);

        TextView tv = findViewById(mac_ids[0]);
        default_colors = tv.getTextColors();

        ap_mac = new String[MAX_RESULTS];
        ap_rssi = new int[MAX_RESULTS];
        ap_rssi_prev = new int[MAX_RESULTS];
        mac_addrs = new long[MAX_RESULTS];
        ap_dead_for = new int[MAX_RESULTS];
        new_ap = new boolean[MAX_RESULTS];

        for (int n = 0; n < MAX_RESULTS; n++) {
            ap_dead_for[n] = MAX_DEAD_FOR;
        }

        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device == null) {
            Toast.makeText(this, "UsbAccessPoints: null usb device", Toast.LENGTH_LONG).show();
            return;
        }
        UsbManager usb_manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        connection = usb_manager.openDevice(device);
        if (connection == null) {
            Toast.makeText(this, "UsbAccessPoints: null usb connection", Toast.LENGTH_LONG).show();
            return;
        }
        //call method to set up device communication
        Log.i(TAG, "have device. " + device.getInterfaceCount() + " interfaces");
        UsbInterface intf = device.getInterface(0);
        connection.claimInterface(intf, forceClaim);
        Log.i(TAG, intf.getName() + " endpoints:" + intf.getEndpointCount());
        /// yyy; https://developer.android.com/guide/topics/connectivity/usb/host#java
        UsbEndpoint endpointA = intf.getEndpoint(0);
        UsbEndpoint endpointB = intf.getEndpoint(1);

        dir = endpointA.getDirection();
        if (dir == UsbConstants.USB_DIR_IN)
            Log.i(TAG, "EP0: device to host (IN)");
        else if (dir == UsbConstants.USB_DIR_OUT)
            Log.i(TAG, "EP0: host to device (OUT)");
        dir = endpointB.getDirection();
        if (dir == UsbConstants.USB_DIR_IN)
            Log.i(TAG, "EP1: device to host (IN)");
        else if (dir == UsbConstants.USB_DIR_OUT)
            Log.i(TAG, "EP1: host to device (OUT)");
        if (endpointA.getDirection() == UsbConstants.USB_DIR_IN) {
            endpoint_in = endpointA;
            endpoint_out = endpointB;
        } else if (endpointB.getDirection() == UsbConstants.USB_DIR_IN) {
            endpoint_in = endpointB;
            endpoint_out = endpointA;
        } else {
            endpoint_in = null;
            endpoint_out = null;
        }

        eal = new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                CharSequence bearing = v.getText();
                int tv_mac_id, id = v.getId();
                Log.i(TAG, "editable " + bearing);
                if (id == R.id.bearing_A) {
                    tv_mac_id = R.id.tvMAC_A;
                } else if (id == R.id.bearing_B) {
                    tv_mac_id = R.id.tvMAC_B;
                } else if (id == R.id.bearing_C) {
                    tv_mac_id = R.id.tvMAC_C;
                } else if (id == R.id.bearing_D) {
                    tv_mac_id = R.id.tvMAC_D;
                } else if (id == R.id.bearing_E) {
                    tv_mac_id = R.id.tvMAC_E;
                } else if (id == R.id.bearing_F) {
                    tv_mac_id = R.id.tvMAC_F;
                } else if (id == R.id.bearing_G) {
                    tv_mac_id = R.id.tvMAC_G;
                } else if (id == R.id.bearing_H) {
                    tv_mac_id = R.id.tvMAC_H;
                } else if (id == R.id.bearing_I) {
                    tv_mac_id = R.id.tvMAC_I;
                } else if (id == R.id.bearing_J) {
                    tv_mac_id = R.id.tvMAC_J;
                } else if (id == R.id.bearing_K) {
                    tv_mac_id = R.id.tvMAC_K;
                } else if (id == R.id.bearing_L) {
                    tv_mac_id = R.id.tvMAC_L;
                } else if (id == R.id.bearing_M) {
                    tv_mac_id = R.id.tvMAC_M;
                } else if (id == R.id.bearing_N) {
                    tv_mac_id = R.id.tvMAC_N;
                } else if (id == R.id.bearing_O) {
                    tv_mac_id = R.id.tvMAC_O;
                } else if (id == R.id.bearing_P) {
                    tv_mac_id = R.id.tvMAC_P;
                } else if (id == R.id.bearing_Q) {
                    tv_mac_id = R.id.tvMAC_Q;
                } else {
                    Log.i(TAG, "editor action bearing_? ");
                    return false;
                }
                writeBearing(bearing, tv_mac_id);
                //return true; // true = consume, false = pass on
                return false;
            }
        };

        et = findViewById(R.id.bearing_A);
        et.setOnEditorActionListener(eal);
        /*
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                Log.i(TAG, "editor action bearing_A " + event);
                return true; // true = consume, false = pass on
            }
        });
         */
        et = findViewById(R.id.bearing_B);
        et.setOnEditorActionListener(eal);
        et = findViewById(R.id.bearing_C);
        et.setOnEditorActionListener(eal);
        et = findViewById(R.id.bearing_D);
        et.setOnEditorActionListener(eal);
        et = findViewById(R.id.bearing_E);
        et.setOnEditorActionListener(eal);
        et = findViewById(R.id.bearing_F);
        et.setOnEditorActionListener(eal);
        et = findViewById(R.id.bearing_G);
        et.setOnEditorActionListener(eal);
        et = findViewById(R.id.bearing_H);
        et.setOnEditorActionListener(eal);
        et = findViewById(R.id.bearing_I);
        et.setOnEditorActionListener(eal);
        et = findViewById(R.id.bearing_J);
        et.setOnEditorActionListener(eal);
        et = findViewById(R.id.bearing_K);
        et.setOnEditorActionListener(eal);
        et = findViewById(R.id.bearing_L);
        et.setOnEditorActionListener(eal);
        et = findViewById(R.id.bearing_M);
        et.setOnEditorActionListener(eal);
        et = findViewById(R.id.bearing_N);
        et.setOnEditorActionListener(eal);
        et = findViewById(R.id.bearing_O);
        et.setOnEditorActionListener(eal);
        et = findViewById(R.id.bearing_P);
        et.setOnEditorActionListener(eal);
        et = findViewById(R.id.bearing_Q);
        et.setOnEditorActionListener(eal);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        bytesFromDev = new byte[FROM_DEVICE_REPORT_LENGTH];
        bytesToDev = new byte[TO_DEVICE_REPORT_LENGTH];

        Log.i(TAG, "end-onCreate");
    }

    public static void usb_service() {
        if (usbDevReq != 0) {
            bytesToDev[0] = usbDevReq;
            Log.i(TAG, "xfer out...");
            connection.bulkTransfer(endpoint_out, bytesToDev, bytesToDev.length, TIMEOUT);
            Log.i(TAG, "...xfer out");
            usbDevReq = 0;
        }
        //Log.i(TAG, "xfer in...");
        connection.bulkTransfer(endpoint_in, bytesFromDev, bytesFromDev.length, TIMEOUT);
        //Log.i(TAG, "...xfer in");
    }

    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");

        keep_running = true;
        xfer_at = System.currentTimeMillis();
        usbDevReq = 0;
        Context c = this;

        /* https://os.mbed.com/docs/mbed-os/v6.10/apis/usbhid.html */
        new Thread(new Runnable() {
            public void run() {
                while (keep_running) {
                    int cmdFromDevice;
                    long now;

                    usb_service();

                    now = System.currentTimeMillis();
                    rate = (int)(now - xfer_at);
                    //Log.i(TAG, "now " + now + ", rate " + rate);
                    xfer_at = now;
                    cmdFromDevice = bytesFromDev[0] & 0xff;
                    if (cmdFromDevice == WIFI_RESULTS_START) {
                        int nbResults;
                        nbResults = bytesFromDev[1];
                        take_results(nbResults, false);
                    } else if (cmdFromDevice == WIFI_RESULTS_CONT) {
                        int remaining_results = bytesFromDev[1];
                        if (dbg)
                            Log.i(TAG, "WIFI_RESULTS_CONT " + remaining_results);
                        take_results(remaining_results, true);
                    } else if (cmdFromDevice == WIFI_REQ_CFG) {
                        String str = "";
                        for (int i = 0; i < 9; i++) {
                            str += String.format("%02x ", bytesFromDev[i+1] & 0xff);
                        }
                        Log.i(TAG, "wifi buf " + str);
                        Intent intent = new Intent(c, LR1110Config.class);
                        intent.putExtra("currentConfig", bytesFromDev);
                        startActivity(intent);
                    } else {
                        Log.e(TAG, "unknown cmd from device " + Integer.toString(cmdFromDevice, 16));
                    }
                }
            }
        }).start();
    }

    protected void onStop() {
        super.onStop();
        keep_running = false;
        Log.i(TAG, "onStop");
    }

}