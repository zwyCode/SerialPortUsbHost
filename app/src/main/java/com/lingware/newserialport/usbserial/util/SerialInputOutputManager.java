/* Copyright 2011-2013 Google Inc.
 * Copyright 2013 mike wakerly <opensource@hoho.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: https://github.com/mik3y/usb-serial-for-android
 */

package com.lingware.newserialport.usbserial.util;

import android.hardware.usb.UsbRequest;
import android.util.Log;

import com.lingware.newserialport.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Utility class which services a {@link UsbSerialPort} in its {@link #run()}
 * method.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
public class SerialInputOutputManager implements Runnable {

    private static final String TAG = "ZWY";
    private static final boolean DEBUG = true;

    private static final int READ_WAIT_MILLIS = 100;
    private static final int BUFSIZ = 4096;

    private final UsbSerialPort mDriver;

    private final ByteBuffer mReadBuffer = ByteBuffer.allocate(BUFSIZ);

    // Synchronized by 'mWriteBuffer'
    private final ByteBuffer mWriteBuffer = ByteBuffer.allocate(BUFSIZ);

    private final ByteBuffer mWriteSingleCmdsBuffer = ByteBuffer.allocate(BUFSIZ);

    private boolean isNeedAddNewCommand = false;
    private boolean isOnlyNeedSendOneCommand = false;

    private enum State {
        STOPPED,
        RUNNING,
        STOPPING
    }

    // Synchronized by 'this'
    private State mState = State.STOPPED;

    // Synchronized by 'this'
    private Listener mListener;

    public interface Listener {
        /**
         * Called when new incoming data is available.
         */
        public void onNewData(byte[] data);

        /**
         * Called when {@link SerialInputOutputManager#run()} aborts due to an
         * error.
         */
        public void onRunError(Exception e);
    }

    /**
     * Creates a new instance with no listener.
     */
    public SerialInputOutputManager(UsbSerialPort driver) {
        this(driver, null);
    }

    /**
     * Creates a new instance with the provided listener.
     */
    public SerialInputOutputManager(UsbSerialPort driver, Listener listener) {
        mDriver = driver;
        mListener = listener;
    }

    public synchronized void setListener(Listener listener) {
        mListener = listener;
    }

    public synchronized Listener getListener() {
        return mListener;
    }

    public void writeAsync(byte[] data) {
        synchronized (mWriteBuffer) {
            mWriteBuffer.clear();
            mWriteBuffer.put(data);
        }
    }

    public synchronized void stop() {
        if (getState() == State.RUNNING) {
            Log.i(TAG, "Stop requested");
            mState = State.STOPPING;
        }
    }

    private synchronized State getState() {
        return mState;
    }

    /**
     * Continuously services the read and write buffers until {@link #stop()} is
     * called, or until a driver exception is raised.
     *
     * NOTE(mikey): Uses inefficient read/write-with-timeout.
     * TODO(mikey): Read asynchronously with {@link UsbRequest#queue(ByteBuffer, int)}
     */
    @Override
    public void run() {
        Log.i(TAG, "Running .. 1");
        synchronized (this) {
            if (getState() != State.STOPPED) {
                throw new IllegalStateException("Already running.");
            }
            mState = State.RUNNING;
        }

        Log.i(TAG, "Running ..");
        try {
            while (true) {
                if (getState() != State.RUNNING && !isNeedAddNewCommand) {
                    Log.i(TAG, "Stopping mState=" + getState());
                    break;
                }
                Thread.sleep(10);
                step();
            }
        } catch (Exception e) {
            Log.w(TAG, "Run ending due to exception: " + e.getMessage(), e);
            final Listener listener = getListener();
            if (listener != null) {
              listener.onRunError(e);
            }
        } finally {
            synchronized (this) {
                mState = State.STOPPED;
                Log.i(TAG, "Stopped.");
            }
        }
    }

    private void step() throws IOException {
        // Handle outgoing data.
        int wrtieLen = 0;
        byte[] outBuff = null;
        int len = 0;
        final Listener listener = getListener();

        if(!isNeedAddNewCommand) {
            synchronized (mWriteBuffer) {
                wrtieLen = mWriteBuffer.position();
                if (wrtieLen > 0) {
                    outBuff = new byte[wrtieLen];
                    mWriteBuffer.rewind();
                    mWriteBuffer.get(outBuff, 0, wrtieLen);
                    //Log.e(TAG, "---------------------> write data : " + byteArrayToString(outBuff, outBuff.length));
                    //mWriteBuffer.clear();
                }
            }
            if (outBuff != null) {
                if (DEBUG) {
                    Log.d(TAG, "Writing data len=" + wrtieLen);
                }
                mDriver.write(outBuff, READ_WAIT_MILLIS);
            }

            // Handle incoming data.
            Log.d(TAG, "Read data 1");
            len = mDriver.read(mReadBuffer.array(), READ_WAIT_MILLIS);
            Log.d(TAG, "Read length : " + len);
            if (len > 0) {
                if (DEBUG) Log.d(TAG, "Read data len=" + len);

                if (listener != null) {
                    final byte[] data = new byte[len];
                    mReadBuffer.get(data, 0, len);
                    listener.onNewData(data);
                }
                mReadBuffer.clear();
            }

            if(isOnlyNeedSendOneCommand) {
                isOnlyNeedSendOneCommand = false;
                if (getState() == State.RUNNING) {
                    mState = State.STOPPING;
                }
                mWriteBuffer.clear();
            }
        } else {
            synchronized (mWriteSingleCmdsBuffer) {
                wrtieLen = mWriteSingleCmdsBuffer.position();
                if (wrtieLen > 0) {
                    outBuff = new byte[wrtieLen];
                    mWriteSingleCmdsBuffer.rewind();
                    mWriteSingleCmdsBuffer.get(outBuff, 0, wrtieLen);
                    mWriteSingleCmdsBuffer.clear();
                }
            }
            if (outBuff != null) {
                if (DEBUG) {
                    Log.d(TAG, "single Writing data len=" + wrtieLen);
                }
                mDriver.write(outBuff, READ_WAIT_MILLIS);
            }

            // Handle incoming data.
            Log.d(TAG, "single Read data 2" );
            len = mDriver.read(mReadBuffer.array(), READ_WAIT_MILLIS);
            Log.d(TAG, "single Read length : " + len );
            if (len > 0) {
                if (DEBUG) Log.d(TAG, "single Read data len=" + len);
                if (listener != null) {
                    final byte[] data = new byte[len];
                    mReadBuffer.get(data, 0, len);
                    listener.onNewData(data);
                }
                mReadBuffer.clear();
            }
            isNeedAddNewCommand = false;
        }
    }

    //针对正对运行收发数据时发送指令操作
    public void writeSingleCmds(byte[] data) {
        synchronized (mWriteSingleCmdsBuffer) {
            mWriteSingleCmdsBuffer.put(data);
            isNeedAddNewCommand = true;
        }
    }

    //针对正对运行收发数据时发送指令操作
    public void writeSingleCmdsOnlyOneCommand(byte[] data) {
        synchronized (mWriteBuffer) {
            mWriteBuffer.put(data);
            isOnlyNeedSendOneCommand = true;
        }
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
}
