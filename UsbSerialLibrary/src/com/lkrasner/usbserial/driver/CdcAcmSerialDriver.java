package com.lkrasner.usbserial.driver;

import java.io.IOException;
import java.util.Arrays;

import com.lkrasner.usbserial.driver.CdcAcmSerialDriver;
import com.lkrasner.usbserial.driver.UsbSerialDriver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.util.Log;

/**
 * USB CDC/ACM serial driver implementation.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
public class CdcAcmSerialDriver implements UsbSerialDriver {
    private final String TAG = CdcAcmSerialDriver.class.getSimpleName();

    private UsbDevice mDevice;
    private UsbDeviceConnection mConnection;
    private final byte[] mReadBuffer = new byte[4096];

    private UsbInterface mControlInterface;
    private UsbInterface mDataInterface;

    private UsbEndpoint mControlEndpoint;
    private UsbEndpoint mReadEndpoint;
    private UsbEndpoint mWriteEndpoint;

    /**
     * @param usbDevice
     * @param connection
     */
    public CdcAcmSerialDriver(UsbDevice usbDevice, UsbDeviceConnection connection) {
        mDevice = usbDevice;
        mConnection = connection;
    }

    @Override
    public void open() throws IOException {
        Log.d(TAG, "claiming interfaces, count=" + mDevice.getInterfaceCount());

        Log.d(TAG, "Claiming control interface.");
        mControlInterface = mDevice.getInterface(0);
        Log.d(TAG, "Control iface=" + mControlInterface);
        // class should be USB_CLASS_COMM

        if (!mConnection.claimInterface(mControlInterface, true)) {
            throw new IOException("Could not claim control interface.");
        }
        mControlEndpoint = mControlInterface.getEndpoint(0);
        Log.d(TAG, "Control endpoint direction: " + mControlEndpoint.getDirection());

        Log.d(TAG, "Claiming data interface.");
        mDataInterface = mDevice.getInterface(1);
        Log.d(TAG, "data iface=" + mDataInterface);
        // class should be USB_CLASS_CDC_DATA

        if (!mConnection.claimInterface(mDataInterface, true)) {
            throw new IOException("Could not claim data interface.");
        }
        mReadEndpoint = mDataInterface.getEndpoint(1);
        Log.d(TAG, "Read endpoint direction: " + mReadEndpoint.getDirection());
        mWriteEndpoint = mDataInterface.getEndpoint(0);
        Log.d(TAG, "Write endpoint direction: " + mWriteEndpoint.getDirection());

        Log.d(TAG, "Setting line coding");
        setBaudRate(115200);

    }

    private static final int USB_RECIP_INTERFACE = 0x01;
    private static final int USB_RT_ACM = UsbConstants.USB_TYPE_CLASS | USB_RECIP_INTERFACE;

    private static final int SET_LINE_CODING = 0x20;  // USB CDC 1.1 section 6.2

    private int sendAcmControlMessage(int request, int value, byte[] buf) {
        return mConnection.controlTransfer(USB_RT_ACM, request, value, 0, buf, buf != null ? buf.length : 0, 5000);
    }

    private int setAcmLineCoding(int bitRate, int stopBits, int parity, int dataBits) {
        byte[] msg = {
                (byte) ( bitRate & 0xff),
                (byte) ((bitRate >> 8 ) & 0xff),
                (byte) ((bitRate >> 16) & 0xff),
                (byte) ((bitRate >> 24) & 0xff),

                (byte) stopBits,
                (byte) parity,
                (byte) dataBits};
        return sendAcmControlMessage(SET_LINE_CODING, 0, msg);
    }

    @Override
    public void close() throws IOException {
        mConnection.close();
    }

    @Override
    public int read(byte[] dest, int timeoutMillis) throws IOException {
        int readAmt = Math.min(dest.length, mReadBuffer.length);
        readAmt = Math.min(readAmt, mReadEndpoint.getMaxPacketSize());
        final int transferred = mConnection.bulkTransfer(mReadEndpoint, mReadBuffer, readAmt,
                timeoutMillis);

        if (transferred < 0) {
            // This sucks: we get -1 on timeout, not 0 as preferred.
            // We *should* use UsbRequest, except it has a bug/api oversight
            // where there is no way to determine the number of bytes read
            // in response :\ -- http://b.android.com/28023
            return 0;
        }
        System.arraycopy(mReadBuffer, 0, dest, 0, transferred);
        return transferred;
    }

    @Override
    public int write(byte[] src, int timeoutMillis) throws IOException {
        int offset = 0;
        final int chunksize = mWriteEndpoint.getMaxPacketSize();

        while (offset < src.length) {
            final byte[] writeBuffer;
            final int writeLength;

            // bulkTransfer does not support offsets; make a copy if necessary.
            writeLength = Math.min(src.length - offset, chunksize);
            if (offset == 0) {
                writeBuffer = src;
            } else {
                writeBuffer = Arrays.copyOfRange(src, offset, offset + writeLength);
            }

            final int amt = mConnection.bulkTransfer(mWriteEndpoint, writeBuffer, writeLength,
                    timeoutMillis);
            if (amt <= 0) {
                throw new IOException("Error writing " + writeLength
                        + " bytes at offset " + offset + " length=" + src.length);
            }
            Log.d(TAG, "Wrote amt=" + amt + " attempted=" + writeBuffer.length);
            offset += amt;
        }
        return offset;
    }

    @Override
    public int setBaudRate(int baudRate) throws IOException {
        setAcmLineCoding(baudRate, 0, 0, 8);
        return baudRate;
    }

    @Override
    public UsbDevice getDevice() {
        return mDevice;
    }

    public static boolean probe(UsbDevice usbDevice) {
        return usbDevice.getVendorId() == 0x2341;
    }

}
