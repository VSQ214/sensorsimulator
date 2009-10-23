package controller;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;
import property.Property;
import simulatorexception.SimulatorException;

/**
 *
 * @author CZC
 */
public class TransmitBufferThread extends Thread {

    private OutputStream os;
    private TransmissionBuffer buffer;
    private int index; // index in the taskBuffer
    private boolean stop;
    private int bufferSize;
    private int sleepInterval;
    private Property wtPro;
    private long byteSum;
    private StateListner stateListner;

    public TransmitBufferThread(Property p, TransmissionBuffer buffer, OutputStream os, StateListner stateListner) {
        this.os = os;
        this.buffer = buffer;
        wtPro = p;
        index = 0;
        stop = false;
        bufferSize = buffer.getBufferSize();
        this.stateListner = stateListner;

        if (wtPro.getTransMode() == Property.TransMode_Frequency) {
            sleepInterval = p.getSleepInterval();
        }
    }

    @Override
    public void run() {
        long delay = wtPro.getTransMode() == Property.TransMode_Frequency ? sleepInterval : -1; //delay between each transmission
        byteSum = 0;
        Timer t = new Timer();
        t.schedule(new ProcessTimerTask(this), 1000, 2000);
        byte[] b;
        try {
            switch (wtPro.getTransMode()) {
                case Property.TransMode_Frequency:
                    while (!stop) {
                        try {
                            if (wtPro.getPacketHeaderContent() == Property.HeaderContent_None) {
                                os.write(buffer.getBufferElementAt(index).getTaskData());
                                byteSum += buffer.getBufferElementAt(index).getTaskData().length;
                            } else if (wtPro.getPacketHeaderContent() == Property.HeaderContent_TimeStamp) {
                                b = createNewSensorPacket(index);
                                os.write(b);
                                byteSum += b.length;
                            }
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                        index++;

                        //turn on loading once half of the buffer have been sent
                        if (index == bufferSize) {
                            index = 0;
                            buffer.turnOnWriting();
                        } else if (index == bufferSize / 2) {
                            buffer.turnOnWriting();
                        }

                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                    }
                    break;
                case Property.TransMode_TimeStamp:
                    while (!stop) {
                        try {
                            if (wtPro.getPacketHeaderContent() == Property.HeaderContent_None) {
                                os.write(buffer.getBufferElementAt(index).getTaskData());
                                byteSum += buffer.getBufferElementAt(index).getTaskData().length;
                            } else if (wtPro.getPacketHeaderContent() == Property.HeaderContent_TimeStamp) {
                                b = createNewSensorPacket(index);
                                os.write(createNewSensorPacket(index));
                                byteSum += b.length;
                            }
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }

                        delay = buffer.getBufferElementAt(index).getDelay();

                        index++;

                        //turn on loading once half of the buffer have been sent
                        if (index == bufferSize) {
                            index = 0;
                            buffer.turnOnWriting();
                        } else if (index == bufferSize / 2) {
                            buffer.turnOnWriting();
                        }

                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                    }
                    break;
                default:
                    throw new SimulatorException("Error 010: Unknown transmission mode");
            }

        } catch (IllegalArgumentException ex) {
            stateListner.systemInforEvent(ex.getMessage() + ". " + "Probably caused by obsolute buffer or file end reached");
            System.out.println(ex.getMessage() + ". " + "Probably caused by obsolute buffer or file end reached");
            return;
        } catch (SimulatorException e) {
            stateListner.systemInforEvent(e.getMessage() + " at TransmitBufferThread");
            System.out.println(e.getMessage() + " at TransmitBufferThread");
            return;
        }
    }

    public void stopTransmission() {
        stop = true;
    }

    /**
     * compose a new packet with transmit time stamp in the header, packet start with a start character "*",
     * end with a end character "%"
     * @param bufferIndex: the index in transmissionBuffer which store the actual data
     * @return a byte array of new packet
     */
    private byte[] createNewSensorPacket(int bufferIndex) {
        int size = 10 + buffer.getBufferElementAt(bufferIndex).getDataLength();
        byte[] temp = new byte[size];

        temp[0] = (byte) 42;//"*" start character

        //transmit time stamp
        long t = System.currentTimeMillis();
        System.arraycopy(longToBinary(t, wtPro.getOutputByteOrder()), 0, temp, 1, 8);
        //actual data
        System.arraycopy(buffer.getBufferElementAt(bufferIndex).getTaskData(), 0, temp, 9, buffer.getBufferElementAt(bufferIndex).getDataLength());

        temp[size - 1] = (byte) 37; //"%" end character
        return temp;
    }

    /**
     *
     * @param value
     * @param byteorder
     * @return byte array or null if no byte order matched
     */
    private byte[] longToBinary(long value, int byteorder) {

        switch (byteorder) {
            case Property.ByteOrder_HighLow:
                return new byte[]{
                            (byte) (value >>> 56), (byte) (value >> 48 & 0xFF), (byte) (value >> 40 & 0xff), (byte) (value >> 32 & 0xff),
                            (byte) (value >> 24 & 0xff), (byte) (value >> 16 & 0xFF), (byte) (value >> 8 & 0xff), (byte) (value & 0xff)
                        };
            case Property.ByteOrder_LowHigh:
                return new byte[]{
                            (byte) (value & 0xff), (byte) (value >> 8 & 0xff), (byte) (value >> 16 & 0xFF), (byte) (value >> 24 & 0xff),
                            (byte) (value >> 32 & 0xff), (byte) (value >> 40 & 0xff), (byte) (value >> 48 & 0xFF), (byte) (value >>> 56)
                        };
            default:
                return null;
        }
    }

    protected void reportProgress() {
        stateListner.transmitProgressEvent(byteSum);
    }
}

class ProcessTimerTask extends TimerTask {

    private TransmitBufferThread tbt;

    public ProcessTimerTask(TransmitBufferThread tbt) {
        this.tbt = tbt;
    }

    @Override
    public void run() {
        tbt.reportProgress();
    }
}
