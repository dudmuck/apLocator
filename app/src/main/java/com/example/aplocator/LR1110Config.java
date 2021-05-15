package com.example.aplocator;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Locale;

public class LR1110Config extends AppCompatActivity implements AdapterView.OnItemSelectedListener, TextView.OnEditorActionListener {
    private static final String TAG = "LR1110";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EditText et;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lr1110_config);

        Intent intent = getIntent();
        byte[] cfg = intent.getByteArrayExtra("currentConfig");
        String str = "";
        for (int i = 0; i < 9; i++) {
            str += String.format("%02x ", cfg[i+1] & 0xff);
        }
        Log.i(TAG, "wifi buf " + str);

        Spinner dropdown = findViewById(R.id.wifiTypeSpinner);
        String[] type_items = new String[]{"802.11b", "802.11g", "802.11n", "all"};
        //create an adapter to describe how the items are displayed, adapters are used in several places in android.
        //There are multiple variations of this, but this is the basic variant.
        ArrayAdapter<String> type_adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, type_items);
        //set the spinners adapter to the previously created one.
        dropdown.setAdapter(type_adapter);
        dropdown.setSelection(cfg[1] & 0xff);
        dropdown.setOnItemSelectedListener(this);

        et = findViewById(R.id.edittext_chanmask);
        int chanmask = cfg[2] & 0xff;
        chanmask <<= 8;
        chanmask |= cfg[3] & 0xff;
        String foo = String.format("0x%04x", chanmask);
        Log.i(TAG, "chanmask " + foo);
        //et.setText(String.format("0x%04x", chanmask));
        et.setText(foo);
        et.setOnEditorActionListener(this);

        // acqMode cfg[4]
        dropdown = findViewById(R.id.acqModeSpinner);
        String[] acq_items = new String[]{"RFU", "beacon only", "beacon & packet"};
        ArrayAdapter<String> acq_adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, acq_items);
        dropdown.setAdapter(acq_adapter);
        dropdown.setSelection(cfg[4] & 0xff);
        dropdown.setOnItemSelectedListener(this);

        et = findViewById(R.id.edittext_nbmaxres);
        et.setText(String.format(Locale.US, "%d", cfg[5] & 0xff), TextView.BufferType.EDITABLE);
        et.setOnEditorActionListener(this);

        // nbScamPerChan cfg[6]
        et = findViewById(R.id.edittext_nbscanperchan);
        et.setText(String.format(Locale.US, "%d", cfg[6] & 0xff), TextView.BufferType.EDITABLE);
        et.setOnEditorActionListener(this);

        // timeout cfg[7] cfg[8]
        et = findViewById(R.id.edittext_timeout);
        int timeout = cfg[7] & 0xff;
        timeout <<= 8;
        timeout |= cfg[8] & 0xff;
        foo = String.format(Locale.US, "%d", timeout);
        et.setText(foo);
        et.setOnEditorActionListener(this);

        // abort on timeout cfg[9]
        CheckBox cb = findViewById(R.id.AbortOnTimeout);
        cb.setChecked((cfg[9] & 0xff) != 0);
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
        int spinner_id = parent.getId();
        Log.i(TAG, "onItemSelected " + pos + ", " + parent.getItemAtPosition(pos) +". view id: " + view.getId() + ", parent id: " + parent.getId());

        if (view.getId() == R.id.wifiTypeSpinner)
            Log.i(TAG, "view is wifiTypeSpinner");
        else if (view.getId() == R.id.acqModeSpinner)
            Log.i(TAG, "view is acqModeSpinner");

        if (parent.getId() == R.id.wifiTypeSpinner)
            Log.i(TAG, "parent is wifiTypeSpinner");
        else if (parent.getId() == R.id.acqModeSpinner)
            Log.i(TAG, "parent is acqModeSpinner");

        UsbAccessPoints.bytesToDev[1] = (byte)pos;
        if (spinner_id == R.id.wifiTypeSpinner) {
            UsbAccessPoints.usbDevReq = (byte) UsbAccessPoints.WIFI_SET_WIFI_TYPE;
            Log.i(TAG, "sending for wifi type");
        } else if (spinner_id == R.id.acqModeSpinner) {
            UsbAccessPoints.usbDevReq = (byte) UsbAccessPoints.WIFI_SET_ACQMODE;
            Log.i(TAG, "sending acq mode");
        } else {
            Log.e(TAG, "unknown item selected " + id);
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                UsbAccessPoints.usb_service();
            }
        }).start();

        /*
        UsbAccessPoints.bytesToDev[0] = (byte)UsbAccessPoints.WIFI_SET_ACQMODE;
        UsbAccessPoints.bytesToDev[1] = (byte)pos;
        UsbAccessPoints.transfer_to_device();*/
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
        Log.i(TAG, "onNothingSelected");
    }

    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
        int textViewId = textView.getId();
        String newValue = String.valueOf(textView.getText());
        int value = Integer.decode(newValue);
        if (textViewId == R.id.edittext_chanmask) {
            Log.i(TAG, "onEditorAction edittext_chanmask " + newValue);
            // hi-byte first
            UsbAccessPoints.bytesToDev[2] = (byte)(value & 0xff); // lo-byte
            value >>= 8;
            UsbAccessPoints.bytesToDev[1] = (byte)(value & 0xff); // hi-byte
            UsbAccessPoints.usbDevReq = (byte) UsbAccessPoints.WIFI_SET_CHANMASK;
        } else if (textViewId == R.id.edittext_nbmaxres) {
            UsbAccessPoints.bytesToDev[1] = (byte)value;
            Log.i(TAG, "onEditorAction edittext_nbmaxres " + value);
            UsbAccessPoints.usbDevReq = (byte) UsbAccessPoints.WIFI_SET_NBMAXRES;
        } else if (textViewId == R.id.edittext_nbscanperchan) {
            UsbAccessPoints.bytesToDev[1] = (byte)value;
            Log.i(TAG, "onEditorAction edittext_nbscanperchan " + value);
            UsbAccessPoints.usbDevReq = (byte) UsbAccessPoints.WIFI_SET_NBSCANPERCHAN;
        } else if (textViewId == R.id.edittext_timeout) {
            Log.i(TAG, "onEditorAction edittext_timeout " + value);
            UsbAccessPoints.bytesToDev[2] = (byte)(value & 0xff); // lo-byte
            value >>= 8;
            UsbAccessPoints.bytesToDev[1] = (byte)(value & 0xff); // hi-byte
            UsbAccessPoints.usbDevReq = (byte) UsbAccessPoints.WIFI_SET_TIMEOUT;
        } else {
            Log.e(TAG, "onEditorAction unknown textview");
            return false;
        }
        // textView.getText()
        // TODO String.valueOf() ?
        // edittext_nbscanperchan

        new Thread(new Runnable() {
            public void run() {
                UsbAccessPoints.usb_service();
            }
        }).start();

        return false;
    }

    public void AbortOnTimeoutClicked(View v) {
        boolean checked = ((CheckBox) v).isChecked();
        Log.i(TAG, "AbortOnTimeoutClicked " + checked);
        UsbAccessPoints.bytesToDev[1] = (byte)(checked ? 1 : 0);
        UsbAccessPoints.usbDevReq = (byte) UsbAccessPoints.WIFI_SET_ABORT;

        new Thread(new Runnable() {
            public void run() {
                UsbAccessPoints.usb_service();
            }
        }).start();
    }
}