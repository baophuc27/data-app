package com.reeco.bas.transport.receiver;

import com.fazecast.jSerialComm.SerialPort;
import lombok.Value;

public class SerialPortReader {
    private final SerialPort serialPort;
    private SerialDataListener dataListener;

    private static final int BAUDRATE=115200;

    public SerialPortReader(String portName) {
        this.serialPort = SerialPort.getCommPort(portName);
        this.serialPort.setComPortParameters(BAUDRATE, 8, 1, 0); // Baud rate, data bits, stop bits, parity
        // this.serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
    }

    public void setDataListener(SerialDataListener listener) {
        this.dataListener = listener;
    }


    public void startListening() {
        if (serialPort.openPort()) {
            System.out.println("Port " + serialPort.getSystemPortName() + " opened successfully.");
            new Thread(() -> {
                try {
                    while (true) {
                        if (serialPort.bytesAvailable() > 0) {
                            byte[] readBuffer = new byte[serialPort.bytesAvailable()];
                            int numRead = serialPort.readBytes(readBuffer, readBuffer.length);
                            String data = new String(readBuffer,0,numRead);
                            if (dataListener != null) {
                                dataListener.onDataReceived(data);
                            }
                            // System.out.println("Read port: " + serialPort.getSystemPortName() + " " + numRead + " bytes: " + data);
                        }
                        Thread.sleep(1000); 
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    serialPort.closePort();
                    System.out.println("Port " + serialPort.getSystemPortName() + " closed.");
                }
            }).start();
        } else {
            System.out.println("Failed to open port " + serialPort.getSystemPortName());
        }
    }
}
