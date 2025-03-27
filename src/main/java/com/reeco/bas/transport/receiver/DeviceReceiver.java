package com.reeco.bas.transport.receiver;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

import java.io.InputStream;
import java.util.Enumeration;

public class DeviceReceiver implements SerialPortEventListener  {

    private SerialPort serialPort;
    private InputStream inputStream;

    // Set your serial port parameters
    private static final String PORT_NAME = "/dev/ttyS0"; // Replace with your port name
    private static final int BAUD_RATE = 9600; // Set your baud rate
    private static final int DATA_BITS = SerialPort.DATABITS_8;
    private static final int STOP_BITS = SerialPort.STOPBITS_1;
    private static final int PARITY = SerialPort.PARITY_NONE;

    public void initialize() {
        try {
            // Get the port identifier
            CommPortIdentifier portId = null;
            Enumeration portList = CommPortIdentifier.getPortIdentifiers();
            while (portList.hasMoreElements()) {
                CommPortIdentifier currentPortId = (CommPortIdentifier) portList.nextElement();
                if (currentPortId.getName().equals(PORT_NAME)) {
                    portId = currentPortId;
                    break;
                }
            }

            if (portId == null) {
                System.out.println("Port not found.");
                return;
            }

            // Open the port
            serialPort = (SerialPort) portId.open(this.getClass().getName(), 2000);

            // Set port parameters
            serialPort.setSerialPortParams(BAUD_RATE, DATA_BITS, STOP_BITS, PARITY);

            // Get input stream
            inputStream = serialPort.getInputStream();

            // Add event listener
            serialPort.addEventListener(this);
            serialPort.notifyOnDataAvailable(true);

            System.out.println("Port initialized.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
            try {
                int availableBytes = inputStream.available();
                byte[] readBuffer = new byte[availableBytes];
                inputStream.read(readBuffer);

                String receivedData = new String(readBuffer);
                System.out.println("Received Data: " + receivedData);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void close() {
        if (serialPort != null) {
            serialPort.removeEventListener();
            serialPort.close();
        }
    }

    public static void main(String[] args) {
        DeviceReceiver reader = new DeviceReceiver();
        reader.initialize();

        try {
            Thread.sleep(10000); // Keep the program running to listen for data
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        reader.close();
    }
}
