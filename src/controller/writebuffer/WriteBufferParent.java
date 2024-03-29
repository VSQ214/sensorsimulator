/**
 * This is the parent class of all WriteBuffer classes, it construct a TaskObject as buffer element and write it to buffer
 * There are hree abstract methods must be implemented.
 */
/** 
 * @author CZC
 */
package controller.writebuffer;

import controller.StateListener;
import controller.TaskObject;
import controller.TransmissionBuffer;
import property.Property;
import simulatorexception.SimulatorException;
import sourcehandler.SensorFileInputStream;
import sourcehandler.SensorPacket;

public abstract class WriteBufferParent extends Thread {

    protected int count;                                                        //the index for the next item to be filled in the buffer
    protected boolean stop;
    protected int maxSimultaneouslyPacketNo;
    protected int packetSize; //the size of one wiTilt packet
    protected int maxSize;  // the maximum size of tempData
    protected int headerSize;                                                   //the size of header of the original sensor packet based on sensor specification
    protected int tailSize;
    protected byte[] tempData;
    protected Property wtPro;
    protected SensorFileInputStream fileInputStream;
    protected TransmissionBuffer buffer;
    private StateListener stateListner;

    protected WriteBufferParent(Property p, TransmissionBuffer buffer, SensorFileInputStream in, StateListener stateListner) {
        count = 0;
        stop = false;
        this.buffer = buffer;
        fileInputStream = in;
        wtPro = p;
        this.stateListner = stateListner;
        if (wtPro.getTransMode() == Property.TransMode_TimeStamp) {
            //in time stamp mode, a limit is set for the number of packets would be transmit in one transmission
            maxSimultaneouslyPacketNo = wtPro.getMaxSimultaneouslyPacketNo();
        } else if (wtPro.getTransMode() == Property.TransMode_Frequency) {
            //in frequency mode, the number of packets in one transmission is required by the calculation result from desired frequency
            maxSimultaneouslyPacketNo = wtPro.getPacketsPerTrans();
        }
    }

    /**
     * initialize parameters about packet size
     */
    abstract protected void initPacketSize();

    /**
     * assemble packet header
     * @param offset, offset in byte array, tempData
     */
    abstract protected void fillPacketHeader(int offset);

    /**
     * assemble packet tail
     * @param offset, offset in byte array, tempData
     */
    abstract protected void fillPacketTail(int offset);

    @Override
    public void run() {
        //define the size of the packet
        initPacketSize();

        long currTime = -1, prevTime = -1;
        int repeatedPacketNo = 0;  // how many packet in one buffer element
        int offset = 0;  // offset in tempData
        int bufferSize = wtPro.getBufferSize();
        byte transMode = wtPro.getTransMode();
        boolean firstLoop = true; // after the first round to go through the whole buffer, switch to false       
        boolean rollOver = false;
        SensorPacket sp = null;

        while (!stop) {
            while (!stop && count < bufferSize) {

                //packet header and sample number
                fillPacketHeader(offset);

                try {
                    try {
                        sp = fileInputStream.readLine();                        //return exception when the end of file is reached
                    } catch (SimulatorException ee) {                           
                        if (rollOver) {                                         //prevent roop
                            throw new SimulatorException("Error 009: Source file is empty");
                        } else {                                                //replay
                            stateListner.systemInforEvent("Playback");
                            System.out.println("Playback");
                            rollOver = true;
                            fileInputStream.resetSensorFileInputStream();
                            sp = fileInputStream.readLine();
                        }
                    }
                    System.arraycopy(sp.getPacketData(), 0, tempData, headerSize + offset, sp.getDataLength());

                    fillPacketTail(offset);

                    if (transMode == Property.TransMode_TimeStamp) { //time stamp mode
                        currTime = sp.getTimeStamp();
                        if (currTime != prevTime) {
                            if (prevTime != -1) {  //current time stamp is different with previous time stamp, this is a new buffer element
                                //create a new TaskObject
                                TaskObject to = new TaskObject(tempData, 0, repeatedPacketNo * packetSize, rollOver ? 0 : currTime - prevTime);

                                rollOver = false;

                                //put it into the buffer
                                buffer.setBufferElementAt(count, to);

                                repeatedPacketNo = 0;  // reset buffer element size

                                //move the current packet to the beginning of tempData
                                for (int i = 0; i < packetSize; i++) {
                                    tempData[i] = tempData[i + offset];
                                }

                                count++;

                                //stop loading once half the buffer is filled
                                if (count == bufferSize / 2 && !firstLoop) {
                                    buffer.turnOffWriting();
                                }
                            }
                            prevTime = currTime;
                        }
                        repeatedPacketNo++;
                        offset = repeatedPacketNo * packetSize;
                        continue;
                    } else if (transMode == Property.TransMode_Frequency) { //frequency mode

                        if (repeatedPacketNo == maxSimultaneouslyPacketNo - 1) {
                            //create a new TaskObject
                            TaskObject to = new TaskObject(tempData, 0, maxSimultaneouslyPacketNo * packetSize, -1);

                            rollOver = false;

                            //put it into the buffer
                            buffer.setBufferElementAt(count, to);

                            repeatedPacketNo = 0;  // reset buffer element size
                            offset = 0;

                            count++;

                            //stop loading once half the buffer is filled
                            if (count == bufferSize / 2 && !firstLoop) {
                                buffer.turnOffWriting();
                            }
                        } else {
                            repeatedPacketNo++;
                            offset = repeatedPacketNo * packetSize;
                            continue;
                        }
                    } else {
                        throw new SimulatorException("Error 010: Unknown transmission mode");
                    }


                } catch (SimulatorException e) {
                    stateListner.systemInforEvent(e.getMessage() + " " + "Loading exit");
                    System.out.println(e.getMessage());
                    System.out.println("Loading exit");
                    stop = true;
                    break;
                }
            }

            buffer.turnOffWriting();
            if (firstLoop) {
                firstLoop = false;
                buffer.turnOnReading();
            }
            count = 0;
        }
        fileInputStream.close();
        fileInputStream = null;
        return;
    }

    public void stopWriteToBuffer() {
        stop = true;
    }
}
