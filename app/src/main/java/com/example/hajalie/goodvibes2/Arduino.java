package com.example.hajalie.goodvibes2;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ahajalie on 12/11/2015.
 * Resource used: http://felhr85.net/2014/11/11/usbserial-a-serial-port-driver-library-for-android-v2-0/
 */
public class Arduino {
    private static final String ACTION_USB_PERMISSION =
            "com.example.hajalie.goodvibes2.USB_PERMISSION";
    private String response = "";
    UsbSerialDevice serialPort;
    PendingIntent mPermissionIntent;
    UsbManager usbManager;
    UsbDeviceConnection connection;
    UsbDevice device;
    IntentFilter filter;
    private Context context;
    public Arduino(Context context) {
        // This snippet will open the first usb device connected, excluding usb root hubs
        this.context = context;
        constructorHelper();

    }

    //no other way to call constructor than to have it in another function
    private void constructorHelper() {
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        device = null;
        connection = null;
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(
                ACTION_USB_PERMISSION), 0);
        filter = new IntentFilter(ACTION_USB_PERMISSION);
        context.registerReceiver(mUsbReceiver, filter);
        if(!usbDevices.isEmpty())
        {
            boolean keep = true;
            for(Map.Entry<String, UsbDevice> entry : usbDevices.entrySet())
            {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                int devicePID = device.getProductId();
                if(deviceVID != 0x1d6b || (devicePID != 0x0001 || devicePID != 0x0002 || devicePID != 0x0003))
                {
                    // We are supposing here there is only one device connected and it is our serial device
                    usbManager.requestPermission(device, mPermissionIntent);
//                    if(usbManager.hasPermission(device)) {
//                        connection = usbManager.openDevice(device);
//                    }
                    keep = false;
                }else
                {
                    connection = null;
                    device = null;
                }

                if(!keep)
                    break;
            }
        }
    }

    private void initSerial() {
        // This snippet will open the first usb device connected, excluding usb root hubs
        serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
        if(serialPort != null)
        {
            if(serialPort.open())
            {
                // Devices are opened with default values, Usually 9600,8,1,None,OFF
                // CDC driver default values 115200,8,1,None,OFF
                serialPort.setBaudRate(9200);
                serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                serialPort.read(mCallback);
            }else
            {
                // Serial port could not be opened, maybe an I/O error or it CDC driver was chosen it does not really fit
            }
        }else
        {
            // No driver for given device, even generic CDC driver could not be loaded
        }
    }

    public int write(String input) {
        if (serialPort != null) {
            serialPort.write(input.getBytes());
            Log.d("ARDUINOCLASS", response);
            return serialPort.read(mCallback);
        }
        else {
            constructorHelper();
            return -1;
        }
    }

    // A callback for received data must be defined
    private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback()
    {
        @Override
        public void onReceivedData(byte[] arg0)
        {
            response = "";
            // Code here
            for(int i = 0; i < arg0.length; ++i) {
                response += arg0[i];
            }
        }
    };

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up accessory communication
                            connection = usbManager.openDevice(device);
                            initSerial();
                        }
                    }
                    else {
                        Log.d("TAG", "permission denied for accessory " + device);
                    }
                }
            }
        }
    };
}
