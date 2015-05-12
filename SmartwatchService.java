package com.smartwatch199.smartwatch.smartwatch0_1;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class SmartwatchService extends Service {

    private final String TAG = "SmartwatchService";
    private BluetoothAdapter mAdapter = null;
    private BluetoothSocket mSocket = null;
    private BluetoothDevice mDevice = null;
    private OutputStream outStream = null;
    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    //TODO make addresses not hard coded
    private static final String address = "20:14:11:27:36:33"; //grigor's
    //private static final String address = "30:14:11:26:02:37"; //tuan //

    public Handler swHandler;
    private boolean running;
    private SMSreceiver receiver;

    public SmartwatchService() {
        super();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "in onCreate");
        swHandler = new Handler();
        running = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startID) {
        Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "in onStartCommand");

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        //create device object
        try {
            mDevice = mAdapter.getRemoteDevice(address);
        } catch (Exception e) {
            Log.e(TAG, "couldn't getRemoteDevice from address");
            errorExit("Fatal error", "couldn't get device from address");
        }
        //create socket
        try {
            mSocket = mDevice.createRfcommSocketToServiceRecord(uuid);
        } catch (Exception e) {
            errorExit("Fatal error", "create socket failed: " + e.getMessage());
        }
        Log.d(TAG, "socket created");

        //try to connect to socket
        mAdapter.cancelDiscovery();
        Log.d(TAG, "connecting...");
        try {
            mSocket.connect();
            Log.d(TAG, "connected");
        } catch (Exception e) {
            Log.e(TAG, "couldn't connect socket");
            try {
                mSocket.close();
            } catch (Exception e2) {
                errorExit("Fatal error", "couldn't connect or close socket");
            }
            Log.e(TAG, "closed socket");
            errorExit("Fatal error", "Couldn't connect");
        }

        Log.d(TAG, "creating outstream...");
        try {
            outStream = mSocket.getOutputStream();
            Log.d(TAG, "outstream created");
        } catch (Exception e) {
            errorExit("Fatal error", "output stream creation failed:" + e.getMessage());
        }

        Log.d(TAG, "creating listening thread...");
        try {
            SmartwatchListener listen_thread = new SmartwatchListener(mSocket);
            listen_thread.start();
        } catch (Exception e) {
            errorExit("Fatal error", "listening thread creation failed:" + e.getMessage());
        }

        //register broadcast receiver for sms
        receiver = new SMSreceiver();
        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        registerReceiver(receiver, filter);


        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false; //should stop thread
        unregisterReceiver(receiver);
        swHandler.removeCallbacksAndMessages(null);
        try {
            outStream.close();
        } catch (Exception e) {
            Log.e(TAG, "Couldn't close outStream");
        }
        try {
            mSocket.close();
        } catch (Exception e) {
            Log.e(TAG, "couldn't close socekt");
        }
        //TODO end anything else?
        Toast.makeText(this, "Service Ended", Toast.LENGTH_SHORT).show();
        //super.onDestroy(); //pretty sure it works without this
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendData(String msg) {
        byte[] msgBuffer = msg.getBytes();
        Log.d(TAG,"sending data");
        try {
            outStream.write(msgBuffer);
        } catch (Exception e) {
            errorExit("Fatal error", "failed to send data");
        }
    }

    private void errorExit(String title, String message) {
        Toast msg = Toast.makeText(getBaseContext(), title + ":" + message, Toast.LENGTH_LONG);
        msg.show();
        Log.e(TAG, "stopSelf() called");
        stopSelf();
    }

    class SmartwatchListener extends Thread {
        BluetoothSocket mmSocket = null;
        InputStream inStream = null;

        public SmartwatchListener(BluetoothSocket socket) {
            mmSocket = socket;
            Log.d(TAG,"creating instream...");
            try {
                inStream = mmSocket.getInputStream();
            } catch (Exception e) {
                errorExit("Fatal error", "input stream creation failed:" + e.getMessage());
            }
            Log.d(TAG,"instream created");
        }

        @Override
        public void run() {
            byte[] buffer = new byte[256];
            int bytes;
            while (running) {
                try {
                    bytes = inStream.read(buffer);
                    Log.d(TAG, Integer.toString(bytes));
                    final String readIn = new String(buffer,0,bytes);
                    if (readIn.equals("T")) {
                        swHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                //send ex: (T,15:54:01,02/05/15)
                                Date curDate = new Date();
                                SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss,MM/dd/yy");
                                String DateToStr = dateFormat.format(curDate);
                                String toSend = "(T," + DateToStr + ")";
                                sendData(toSend);
                            }
                        });
                    }
                    swHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            //TV_fromArduino.setText(readIn);
                            Toast msg = Toast.makeText(getBaseContext(), readIn, Toast.LENGTH_SHORT);
                            msg.show();
                        }
                    });
                } catch (Exception e) {
                    Log.d(TAG,"listening thread exception");
                    running = false;
                }
            }
            Log.d(TAG,"listening thread exited loop");
            try {
                inStream.close();
            } catch (Exception e) {
                Log.e(TAG, "couldn't close inStream");
            }
        }
    }

    class SMSreceiver extends BroadcastReceiver {

        String message = "";

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "in onReceive");
            final Bundle bundle = intent.getExtras();
            try {
                if (bundle != null) {
                    final Object[] pdusObj = (Object[]) bundle.get("pdus");
                    for (int i=0; i<pdusObj.length; i++) {
                        SmsMessage cur_msg = SmsMessage.createFromPdu((byte[]) pdusObj[i]);
                        String msg_body = cur_msg.getMessageBody();
                        //String msg_src = cur_msg.getOriginatingAddress();
                        message = message + msg_body;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "error when getting sms message body:" + message);
                message = "You have a new text";
            }

            Log.d(TAG, "posting sms message to handler");
            swHandler.post(new Runnable() { //does this actually need a handler?
                @Override
                public void run() {
                    String raw = message;
                    if (raw.length() > 0) {
                        String toSend = "(N" + Integer.toString(raw.length()) + "," + raw + ")";
                        sendData(toSend);
                    }
                }
            });
        }
    }
}
