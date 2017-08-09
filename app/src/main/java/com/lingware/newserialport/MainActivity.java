package com.lingware.newserialport;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.lingware.newserialport.usbserial.driver.CdcAcmSerialDriver;
import com.lingware.newserialport.usbserial.driver.ProbeTable;
import com.lingware.newserialport.usbserial.driver.UsbSerialDriver;
import com.lingware.newserialport.usbserial.driver.UsbSerialPort;
import com.lingware.newserialport.usbserial.driver.UsbSerialProber;
import com.lingware.newserialport.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements MaterialTabListener{
    private final static String TAG = "ZWY";

    private MaterialTabHost tabHost;
    private ViewPager pager;
    private ViewPagerAdapter adapter;

    private UsbManager usbManager;
    private UsbSerialDriver driver;
    private UsbDeviceConnection usbConnection;
    private UsbSerialPort usbPort;
    private UsbDevice mUsbDevice;
    private SerialInputOutputManager mSerialIoManager;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private Vibrator vibrator;
    private FragmentOne fragmentOne;
    private PendingIntent mPermissionIntent;
    private myHandler mHandler;

    private boolean isSendVibrateCommandFlag = false;
    private static final int VIBRATE_THREAD_VALUE = 150;

    private final static int MESSAGE_CLOSE_USB = 1;
    private final static int MESSAGE_NEW_DATA = 2;
    private final static int MESSAGE_VIBRATE_STATUS = 3;

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);
        if(getSupportActionBar() != null)
            getSupportActionBar().hide();

        tabHost = (MaterialTabHost) findViewById(R.id.tabHost);
        pager = (ViewPager)findViewById(R.id.pager);

        adapter = new ViewPagerAdapter(getSupportFragmentManager());
        pager.setAdapter(adapter);
        pager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener(){
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                tabHost.setSelectedNavigationItem(position);
            }
        });

        for (int i = 0; i < adapter.getCount(); i++) {
            tabHost.addTab(
                    tabHost.newTab()
                            .setText(adapter.getPageTitle(i))
                            .setTabListener(this)
            );
        }

        mContext = getApplicationContext();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(mHandler == null)
            mHandler = new myHandler();

        if(vibrator == null)
            vibrator = (Vibrator)getSystemService(Service.VIBRATOR_SERVICE);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        mContext.registerReceiver(mUsbPermissionActionReceiver, filter);
        mPermissionIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);

        if(usbPort == null)
            tryToGetUsbPerrsion();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(mSerialIoManager != null) {
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }

        runOrStopVibrate(false, 0);
    }

    private void tryToGetUsbPerrsion() {
        new AsyncTask<Void, Void, List<UsbSerialPort>>() {
            @Override
            protected List<UsbSerialPort> doInBackground(Void... params) {
                //ProbeTable customTable = new ProbeTable();
                //customTable.addProduct(0x2341, 0x8237, CdcAcmSerialDriver.class);
                //customTable.addProduct(0x046d, 0xc077, FtdiSerialDriver.class);
                //UsbSerialProber prober = new UsbSerialProber(customTable);

                List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
                if (availableDrivers.isEmpty()) {
                    //Toast.makeText(mContext, "no device", Toast.LENGTH_SHORT).show();
                    return null;
                }

                driver = availableDrivers.get(0);
                usbPort = driver.getPorts().get(0);

                for (final UsbDevice usbDevice : usbManager.getDeviceList().values()) {
                    //add some conditional check if necessary
                    //if(isWeCaredUsbDevice(usbDevice)){

                    mUsbDevice = usbDevice;
                    if (usbManager.hasPermission(usbDevice)) {
                        //if has already got permission, just goto connect it
                        //that means: user has choose yes for your previously popup window asking for grant perssion for this usb device
                        //and also choose option: not ask again
                        afterGetUsbPermission(usbDevice);
                    } else {
                        //this line will let android popup window, ask user whether to allow this app to have permission to operate this usb device
                        usbManager.requestPermission(usbDevice, mPermissionIntent);
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(List<UsbSerialPort> usbSerialPorts) {
                super.onPostExecute(usbSerialPorts);
            }
        }.execute((Void)null);
    }

    private final BroadcastReceiver mUsbPermissionActionReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice usbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        //user choose YES for your previously popup window asking for grant perssion for this usb device
                        if (null != usbDevice&&usbDevice!=mUsbDevice) {
                            afterGetUsbPermission(usbDevice);
                        }
                        else {
                            Toast.makeText(mContext, "Already Open This Device", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        //user choose NO for your previously popup window asking for grant perssion for this usb device
                        Toast.makeText(context, String.valueOf("Permission denied for device" + usbDevice), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    };

    private void afterGetUsbPermission(UsbDevice usbDevice) {
        //call method to set up device communication

        //Toast.makeText(mContext, String.valueOf("Found USB device: VID=" + usbDevice.getVendorId() + " PID=" + usbDevice.getProductId()), Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Found USB device: VID=" + usbDevice.getVendorId() + " PID=" + usbDevice.getProductId());

        doYourOpenUsbDevice(usbDevice);
    }

    private void doYourOpenUsbDevice(UsbDevice usbDevice) {
        //now follow line will NOT show: User has not given permission to device UsbDevice
//        UsbDeviceConnection connection = mUsbManager.openDevice(usbDevice);
        initUsbSerialPort(usbDevice);
    }

    private void initUsbSerialPort(UsbDevice usbDevice) {
        if (usbConnection!=null) {
            usbConnection = null;
        }

        try {
            usbConnection = usbManager.openDevice(usbDevice);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.d(TAG, e.getMessage());
        }
        Log.d(TAG, "initUsbSerialPort: " + usbConnection.toString());
        if (usbPort == null) {
            return;
        }
        try {
            usbPort.open(usbConnection);
            usbPort.setDTR(true);
            usbPort.setRTS(true);
            usbPort.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            Log.d(TAG, "initUsbSerialPort: Error opening device:" + e.toString());
            return;
        }
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (usbPort != null && mSerialIoManager == null) {
            Log.i(TAG, "starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(usbPort, mInputDataListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void sendCmdBuffer(byte[] cmds) {
        Log.e(TAG, "---------------> mSerilaIOManger : " + mSerialIoManager + "  cmd : " + byteArrayToString(cmds, cmds.length));
        if(mSerialIoManager != null) {
            if(cmds[1] == 2)
                mSerialIoManager.writeSingleCmds(cmds);
            else
                mSerialIoManager.writeAsync(cmds);
        }
    }

    //command 1 ---> press
    //command 2 ---> virbate
    private void sendCommandToMcu(byte type, byte command, byte value) {
        byte[] cmds = new byte[4];
        cmds[0] = (byte)2;
        cmds[1] = type;
        cmds[2] = command;
        cmds[3] = value;

        sendCmdBuffer(cmds);
    }

    private String byteArrayToString(byte[] buffer,int length) {
        String h = "";
        for (int i = 0; i < buffer.length && (i < length); i++) {
            String temp = Integer.toHexString(buffer[i] & 0xFF);
            if (temp.length() == 1) {
                temp = "0" + temp;
            }
            temp = "0x" + temp;
            h = h + " " + temp;
        }
        return h;
    }

    @Override
    public void onTabSelected(MaterialTab tab) {

    }

    @Override
    public void onTabReselected(MaterialTab tab) {

    }

    @Override
    public void onTabUnselected(MaterialTab tab) {

    }

    private class ViewPagerAdapter extends FragmentStatePagerAdapter {

        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);

        }

        public Fragment getItem(int num) {
            Fragment fragment;
            switch (num) {
                case 0:
                    fragmentOne = new FragmentOne();
                    fragmentOne.setOnPageOneClickListener(new PageOneClickListener());
                    return fragmentOne;
                case 1:
                    FragmentTwo fragmentTwo = new FragmentTwo();
                    return  fragmentTwo;
                case 2:
                    FragmentThree fragmentThree = new FragmentThree();
                    return fragmentThree;
                default:
                    FragmentOne fragmentOne1 = new FragmentOne();
                    return  fragmentOne1;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return "remote " + position;
        }

    }

    private class PageOneClickListener implements FragmentOne.setOnClickPageOneListener {

        @Override
        public void onTouchPageOne(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startIoManager();
                    sendCommandToMcu((byte)1, (byte)1, (byte)0);
                    break;
                case MotionEvent.ACTION_UP:
                    sendCommandToMcu((byte)1, (byte)2, (byte)0);
                    Intent intent = new Intent();
                    intent.setAction("android.intent.action.START_FLOAT_WINDOW");
                    intent.setPackage("com.lingware.floatwindow");
                    startService(intent);
                    break;
            }
        }
    }


    private class myHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case MESSAGE_CLOSE_USB:
                    if (mSerialIoManager != null) {
                        mSerialIoManager.stop();
                        mSerialIoManager = null;
                    }

                    try {
                        if(usbPort != null)
                            usbPort.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        usbPort = null;
                    }

                    isSendVibrateCommandFlag = false;
                    break;
                case MESSAGE_NEW_DATA:
                    if(fragmentOne != null) {
                        fragmentOne.setPressedValue(msg.arg1);

                        if((fragmentOne.getThreadValue() >=  VIBRATE_THREAD_VALUE && fragmentOne.getThreadValue() <= 300) && !isSendVibrateCommandFlag) {
                            isSendVibrateCommandFlag = true;
                            sendCommandToMcu((byte)2, (byte)1, (byte)0);
                        } else if (fragmentOne.getThreadValue() >= 300 && isSendVibrateCommandFlag) {
                            isSendVibrateCommandFlag = false;
                            sendCommandToMcu((byte)2, (byte)2, (byte)0);
                        }
                    }
                    if(msg.arg1 == 500) {
                        runOrStopVibrate(true, 100);
                    } else if(msg.arg1 == 1500) {
                        runOrStopVibrate(true, 200);
                    } else if(msg.arg1 == 2500) {
                        runOrStopVibrate(true, 300);
                    } else if(msg.arg1 == 3500) {
                        runOrStopVibrate(true, 400);
                    } else if(msg.arg1 == 4500) {
                        runOrStopVibrate(true, 500);
                    }
                    break;
                case MESSAGE_VIBRATE_STATUS:
                    String result = "";

                    if(msg.arg1 == 1) {
                       result = "开始震动";
                    } else if (msg.arg1 == 2) {
                        result = "停止震动";
                    } else {
                        result = "震动解析错误";
                    }
                    Toast.makeText(getApplicationContext(), result, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    private final SerialInputOutputManager.Listener mInputDataListener = new SerialInputOutputManager.Listener(){
        @Override
        public void onNewData(byte[] data) {

            //Log.e(TAG, "-------------> onNewData ： " + byteArrayToString(data, data.length));
            if(data != null && (data.length == 4 && data[0] == 1 && data[1] == 1)){
                if (mHandler != null) {
                    Message msg = new Message();
                    msg.what = MESSAGE_NEW_DATA;

                    msg.arg1 = getPressValued(data[2], data[3]);
                    mHandler.sendMessage(msg);
                }
            } else if (data != null && (data.length == 4 && data[0] == 1 && data[1] ==2)) {
                //vibrate
                if(mHandler != null) {
                    Message msgVibrate = new Message();
                    msgVibrate.what = MESSAGE_VIBRATE_STATUS;
                    msgVibrate.arg1 = data[2];
                    mHandler.sendMessage(msgVibrate);
                }
            } else if (data != null && data.length == 4 && data[0] == 1 && data[1] == 0 && data[2] == 1 && data[3] == 0) {
                if(isSendVibrateCommandFlag)
                    sendCommandToMcu((byte)2, (byte)2, (byte)0);
                stopIoManager();
                runOrStopVibrate(false, 0);
            }
        }

        @Override
        public void onRunError(Exception e) {
            if(mHandler != null) {
                mHandler.sendEmptyMessage(MESSAGE_CLOSE_USB);
            }
        }
    };

    private int getPressValued(byte lowByte, byte highByte) {
        return (int)(((highByte << 8) & 0xff00) | (lowByte & 0xff));
    }

    //run true-----> run
    //    false ---> stop
    private void runOrStopVibrate(boolean run, int vibrateValue) {
        if (vibrator != null) {
            if(run) {
                vibrator.vibrate(new long[]{0, vibrateValue, 100}, -1);
            } else {
                vibrator.cancel();
            }
        }
    }

}
