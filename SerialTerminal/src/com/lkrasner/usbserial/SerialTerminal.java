/* Copyright 2011 Google Inc.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * Code By: Luke Krasner
 */

package com.lkrasner.usbserial;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.hardware.usb.UsbManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.lkrasner.arduinocontrol.R;
import com.lkrasner.usbserial.driver.UsbSerialDriver;
import com.lkrasner.usbserial.driver.UsbSerialProber;
import com.lkrasner.usbserial.util.SerialInputOutputManager;
import android.hardware.GeomagneticField;

/**
 * A simple usb serial controller for an arduino based star tracking camera
 * 
 * @author Luke Krasner (luke.krasner@gmail.com)
 */

public class SerialTerminal extends Activity {

    private final String TAG = SerialTerminal.class.getSimpleName();

    /**
     * The device currently in use, or {@code null}.
     */
    private UsbSerialDriver mSerialDevice; // used to call any actions with the
                                           // serial device

    // bytes used to communicate. small single byte messages allow quick
    // transfer and reduce the risk of error.
    // first byte: align or camera. second byte: message. third(last) byte:
    // halt; used to check that the proper message format was received, message
    // is thrown away if not.
    final byte align = 0x01;
    final byte camera = 0x02;

    final byte left = 0x11;
    final byte right = 0x12;
    final byte up = 0x13;
    final byte down = 0x14;

    final byte powerOff = 0x20;
    final byte powerOn = 0x21;
    final byte trackOff = 0x30;
    final byte trackOn = 0x31;

    final byte halt = 0x2E;

    byte[] bytes;

    // whether to control alignment or camera
    byte alignOrCam = align;

    private UsbManager mUsbManager;

    private TextView mTitleTextView;
    private TextView mDumpTextView;
    private ScrollView mScrollView;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private SerialInputOutputManager mSerialIoManager;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {
                    SerialTerminal.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            SerialTerminal.this.updateReceivedData(data);
                        }
                    });
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) { //oncreate, define my buttons and shit
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        bytes = new byte[3];
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mTitleTextView = (TextView) findViewById(R.id.demoTitle);
        mDumpTextView = (TextView) findViewById(R.id.demoText);
        mScrollView = (ScrollView) findViewById(R.id.demoScroller);
        Button upButton = (Button) findViewById(R.id.upButton);
        Button downButton = (Button) findViewById(R.id.downButton);
        Button leftButton = (Button) findViewById(R.id.leftButton);
        Button rightButton = (Button) findViewById(R.id.rightButton);
        final ToggleButton toggleButton = (ToggleButton) findViewById(R.id.toggleButton);
        final ToggleButton powerButton = (ToggleButton) findViewById(R.id.powerButton);
        final ToggleButton trackButton = (ToggleButton) findViewById(R.id.trackButton);

        // On click Listeners to send proper data when one of the buttons is
        // pressed
        toggleButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (toggleButton.isChecked())
                    alignOrCam = camera;
                else
                    alignOrCam = align;
            }
        });

        powerButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (powerButton.isChecked())
                    bytes[1] = powerOn;
                else
                    bytes[1] = powerOff;
                try {
                    sendData(bytes);

                } catch (IOException e) {

                }
            }
        });

        trackButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (trackButton.isChecked())
                    bytes[1] = trackOn;

                else
                    bytes[1] = trackOff;

                try {
                    sendData(bytes);

                } catch (IOException e) {

                }
            }
        });

        leftButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                bytes[1] = left;

                try {
                    sendData(bytes);

                } catch (IOException e) {

                }
            }

        });

        rightButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                bytes[1] = right;

                try {
                    sendData(bytes);

                } catch (IOException e) {

                }
            }

        });

        upButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                bytes[1] = up;

                try {
                    sendData(bytes);

                } catch (IOException e) {

                }
            }

        });

        downButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                bytes[1] = down;

                try {
                    sendData(bytes);

                } catch (IOException e) {

                }
            }

        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (mSerialDevice != null) {
            try {
                mSerialDevice.close();
            } catch (IOException e) {
                // Ignore.
            }
            mSerialDevice = null;
        }
    }
/*
 * (non-Javadoc)
 * @see android.app.Activity#onResume()
 * 
 * a bunch of needed stuff for initializing the device and stuff.  yes, and stuff, deal with it, don't change it.
 */
    @Override
    protected void onResume() {
        super.onResume();
        mSerialDevice = UsbSerialProber.acquire(mUsbManager);
        Log.d(TAG, "Resumed, mSerialDevice=" + mSerialDevice);
        if (mSerialDevice == null) {
            mTitleTextView.setText("No serial device.");
        } else {
            try {
                mSerialDevice.open();
                mSerialDevice.setBaudRate(9600);   //ok, this you can change.  I am way to lazy to make it a setting in the app.
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                mTitleTextView.setText("Error opening device: " + e.getMessage());
                try {
                    mSerialDevice.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                mSerialDevice = null;
                return;
            }
            mTitleTextView.setText("Serial device: " + mSerialDevice);
        }
        onDeviceStateChange();
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (mSerialDevice != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(mSerialDevice, mListener);
            mExecutor.submit(mSerialIoManager);

        }
    }
    //Reset app when a new device connects.
    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }
    //called whenever there is new data available.  not used by the current arduino script.
    private void updateReceivedData(byte[] data) {
        final String message = new String(data);
        mDumpTextView.append(message);
        mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());

    }
    //used to send data.  ads the first and third byte to second which is provided.
    public void sendData(byte[] bytesToSend) throws IOException {
        // add the first and final bytes.
        bytesToSend[0] = alignOrCam;
        bytesToSend[2] = halt;
        try {
            mSerialDevice.write(bytesToSend, 1000); //write all the bytes, with a timeout of a very long 1 second.
        }

        catch (IOException e) {
            ///suck my dick, java
        }

    }

}
