package com.maslow.gpt.arduinogpt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import ArduinoUploader.ArduinoSketchUploader;
import ArduinoUploader.ArduinoUploaderException;
import ArduinoUploader.Config.Arduino;
import ArduinoUploader.Config.McuIdentifier;
import ArduinoUploader.Config.Protocol;
import ArduinoUploader.IArduinoUploaderLogger;
import CSharpStyle.IProgress;
import com.maslow.gpt.arduinogpt.R;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = MainActivity.class.getSimpleName();
    private UsbSerialManager usbSerialManager;

    public enum UsbConnectState {
        DISCONNECTED,
        CONNECT
    }

    private UsbConnectState usbStatus;

    private final BroadcastReceiver mUsbNotifyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                //Get intent
                case UsbSerialManager.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    Toast.makeText(context, "USB permission granted", Toast.LENGTH_SHORT).show();
                    break;
                case UsbSerialManager.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission denied", Toast.LENGTH_SHORT).show();
                    break;
                case UsbSerialManager.ACTION_NO_USB: // NO USB CONNECTED
                    Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbSerialManager.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();

                    usbStatus = UsbConnectState.DISCONNECTED;

                    usbConnectChange(UsbConnectState.DISCONNECTED);
                    break;
                case UsbSerialManager.ACTION_USB_CONNECT: // USB DISCONNECTED
                    Toast.makeText(context, "USB connected", Toast.LENGTH_SHORT).show();

                    usbStatus = UsbConnectState.CONNECT;

                    usbConnectChange(UsbConnectState.CONNECT);
                    break;
                case UsbSerialManager.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                    break;
                case UsbSerialManager.ACTION_USB_READY:
                    Toast.makeText(context, "Usb device ready", Toast.LENGTH_SHORT).show();
                    break;
                case UsbSerialManager.ACTION_USB_DEVICE_NOT_WORKING:
                    Toast.makeText(context, "USB device not working", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };
    private final BroadcastReceiver mUsbHardwareReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(UsbSerialManager.ACTION_USB_PERMISSION_REQUEST)) {
                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) // User accepted our USB connection. Try to open the device as a serial port
                {
                    UsbDevice grantedDevice = intent.getExtras().getParcelable(UsbManager.EXTRA_DEVICE);
                    usbPermissionGranted(grantedDevice.getDeviceName());
                    Intent it = new Intent(UsbSerialManager.ACTION_USB_PERMISSION_GRANTED);
                    context.sendBroadcast(it);

                } else // User not accepted our USB connection. Send an Intent to the Main Activity
                {
                    Intent it = new Intent(UsbSerialManager.ACTION_USB_PERMISSION_NOT_GRANTED);
                    context.sendBroadcast(it);
                }
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                Intent it = new Intent(UsbSerialManager.ACTION_USB_CONNECT);
                context.sendBroadcast(it);

            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                // Usb device was disconnected. send an intent to the Main Activity
                Intent it = new Intent(UsbSerialManager.ACTION_USB_DISCONNECTED);
                context.sendBroadcast(it);

            }
        }
    };

    private void setUsbFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbSerialManager.ACTION_USB_PERMISSION_REQUEST);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(mUsbHardwareReceiver, filter);
    }


    private TextView display;
    private TextView portSelect;
    private String deviceKeyName;
    private FloatingActionButton fab;

    private File tempHexFile; // Store the temporary HEX file

    private File createTempHexFile(String hexString) {
        try {
            // Create a temporary file to store the HEX string
            File tempDir = getCacheDir();
            File tempFile = File.createTempFile("temp", ".hex", tempDir);

            // Write the HEX string to the temporary file
            FileOutputStream fos = new FileOutputStream(tempFile);
            fos.write(hexString.getBytes());
            fos.close();

            return tempFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void usbConnectChange(UsbConnectState state) {
        if (state == UsbConnectState.DISCONNECTED) {
            //if (requestButton != null) requestButton.setVisibility(View.INVISIBLE);
            //if (fab != null) fab.hide();
        } else if (state == UsbConnectState.CONNECT) {
            //if (requestButton != null) requestButton.setVisibility(View.VISIBLE);

        }

    }


    public void usbPermissionGranted(String usbKey) {
        Toast.makeText(this, "UsbPermissionGranted:" + usbKey, Toast.LENGTH_SHORT).show();
        //portSelect.setText(usbKey);
        deviceKeyName = usbKey;
        //if (fab != null) fab.show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        HandleDeepLink();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        usbSerialManager = new UsbSerialManager(this);
        setUsbFilter();
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        portSelect = (TextView) findViewById(R.id.textViewTitle);
        display = (TextView) findViewById(R.id.textView1);
        fab = findViewById(R.id.fab);

        // Check if this Activity was started by another app with a HEX string
        HandleDeepLink();

        // Check USB connection status
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (usbDevices.isEmpty()) {
            usbStatus = UsbConnectState.DISCONNECTED;
        } else {
            usbStatus = UsbConnectState.CONNECT;
        }

        fab.setOnClickListener(view -> {
            boolean devicePlugged = usbStatus == UsbConnectState.CONNECT;

            if (!devicePlugged) {
                Toast.makeText(this, "No Arduino device is plugged. Plug an Arduino device, via USB, and try again", Toast.LENGTH_LONG).show();
            } else {
                Map.Entry<String, UsbDevice> entry = usbSerialManager.getUsbDeviceList().entrySet().iterator().next();
                String keySelect = entry.getKey();
                boolean hasPem = checkDevicePermission(keySelect);

                if (!hasPem) {
                    requestDevicePermission(keySelect);

                    Toast.makeText(this, "Let's allow the Arduino device USB, before installation", Toast.LENGTH_LONG).show();
                } else {
                    deviceKeyName = keySelect;

                    uploadHex();
                }
            }
        });

        fab.show();
    }

    private void HandleDeepLink() {
        Intent intent = getIntent();

        // Check if the intent has data
        if (intent != null) {
            Uri data = intent.getData();

            if (data != null) {
                // Retrieve the "hex_str" query parameter from the URI
                String hexString = data.getQueryParameter("hex_str");

                if (hexString != null && !hexString.isEmpty()) {
                    // Process the received HEX string and create the temporary HEX file
                    tempHexFile = createTempHexFile(hexString);
                    if (tempHexFile != null) {
                        Toast.makeText(this, "Temporary HEX file created", Toast.LENGTH_LONG).show();
                    }else {
                        Toast.makeText(this, "Hex file creation failed....", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this, "No hex data was given to the app. Please Use MaslowGPT or ArduinOGPT to feed to hungry app.", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "No hex data was given to the app. Please Use MaslowGPT or ArduinOGPT to feed to hungry app.", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "No hex data was given to the app. Please Use MaslowGPT or ArduinOGPT to feed to hungry app.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mUsbHardwareReceiver);
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbSerialManager.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbSerialManager.ACTION_NO_USB);
        filter.addAction(UsbSerialManager.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbSerialManager.ACTION_USB_CONNECT);
        filter.addAction(UsbSerialManager.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbSerialManager.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbNotifyReceiver, filter);
    }

    public void requestDevicePermission(String key) {
        usbSerialManager.getDevicePermission(key);

    }

    public boolean checkDevicePermission(String key) {
        return usbSerialManager.checkDevicePermission(key);
    }

    public UsbSerialDevice getUsbSerialDevice(String key) {
        return usbSerialManager.tryGetDevice(key);
    }

    @Override
    public void onResume() {
        super.onResume();
        setFilters();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mUsbNotifyReceiver);
    }

    public void uploadHex() {

        Boards board = Boards.ARDUINO_UNO;

        Arduino arduinoBoard = new Arduino(board.name, board.chipType, board.uploadBaudrate, board.uploadProtocol);

        Protocol protocol = Protocol.valueOf(arduinoBoard.getProtocol().name());
        McuIdentifier mcu = McuIdentifier.valueOf(arduinoBoard.getMcu().name());
        String preOpenRst = arduinoBoard.getPreOpenResetBehavior();
        String preOpenStr = preOpenRst;
        if (preOpenRst == null) preOpenStr = "";
        else if (preOpenStr.equalsIgnoreCase("none")) preOpenStr = "";

        String postOpenRst = arduinoBoard.getPostOpenResetBehavior();
        String postOpenStr = postOpenRst;
        if (postOpenRst == null) postOpenStr = "";
        else if (postOpenStr.equalsIgnoreCase("none")) postOpenStr = "";

        String closeRst = arduinoBoard.getCloseResetBehavior();
        String closeStr = closeRst;
        if (closeRst == null) closeStr = "";
        else if (closeStr.equalsIgnoreCase("none")) closeStr = "";

        Arduino customArduino = new Arduino("Custom", mcu, arduinoBoard.getBaudRate(), protocol);
        if (!TextUtils.isEmpty(preOpenStr))
            customArduino.setPreOpenResetBehavior(preOpenStr);
        if (!TextUtils.isEmpty(postOpenStr))
            customArduino.setPostOpenResetBehavior(postOpenStr);
        if (!TextUtils.isEmpty(closeStr))
            customArduino.setCloseResetBehavior(closeStr);
        if (protocol == Protocol.Avr109) customArduino.setSleepAfterOpen(0);
        else customArduino.setSleepAfterOpen(250);

        IArduinoUploaderLogger logger = new IArduinoUploaderLogger() {
            @Override
            public void Error(String message, Exception exception) {
                Log.e(TAG, "Error:" + message);
                logUI("Error:" + message);
            }

            @Override
            public void Warn(String message) {
                Log.w(TAG, "Warn:" + message);
                logUI("Warn:" + message);
            }

            @Override
            public void Info(String message) {
                Log.i(TAG, "Info:" + message);
                logUI("Info:" + message);
            }

            @Override
            public void Debug(String message) {
                Log.d(TAG, "Debug:" + message);
                logUI("Debug:" + message);
            }

            @Override
            public void Trace(String message) {
                Log.d(TAG, "Trace:" + message);
                logUI("Trace:" + message);
            }
        };

        IProgress progress = new IProgress<Double>() {
            @Override
            public void Report(Double value) {
                String result = String.format("Upload progress: %1$,3.2f%%", value * 100);
                Log.d(TAG, result);
                logUI("Procees:" + result);

            }
        };

        try {
            if (tempHexFile != null) {
                final FileInputStream file = new FileInputStream(tempHexFile);
                Reader reader = new InputStreamReader(file);
                Collection<String> hexFileContents = new LineReader(reader).readLines();
                ArduinoSketchUploader<SerialPortStreamImpl> uploader = new ArduinoSketchUploader<SerialPortStreamImpl>(this, SerialPortStreamImpl.class, null, logger, progress);

                uploader.UploadSketch(hexFileContents, customArduino, deviceKeyName);

                Toast.makeText(this, "Arduino Installation successful !! IT'S PARTY TIME ;-)", Toast.LENGTH_LONG).show();

            } else {
                Toast.makeText(this, "There's no hex data loaded. Use this app with ArduinoGPT or MaslowGPT, and it will work ;-)", Toast.LENGTH_LONG).show();
            }
        } catch (ArduinoUploaderException ex) {
            Toast.makeText(this, "The Arduino Installation failed... Try again soldier, never give up ;-)", Toast.LENGTH_LONG).show();

            ex.printStackTrace();
        } catch (Exception ex) {
            Toast.makeText(this, "The Arduino Installation failed... Try again soldier, never give up ;-)", Toast.LENGTH_LONG).show();

            ex.printStackTrace();
        }

    }

    private void logUI(String text) {
        runOnUiThread(() -> display.append(text + "\n"));
    }

    private class UploadRunnable implements Runnable {
        @Override
        public void run() {
            uploadHex();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


}