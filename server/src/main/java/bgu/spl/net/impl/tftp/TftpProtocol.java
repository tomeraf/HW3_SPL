package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

class UsersHolder{
    public static Map<Integer,Boolean> users=new HashMap<>(); ;


}

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {
    final private String PATH = "server/Flies/";
    private boolean shouldTerminate = false;
    private int connectionId;
    private Connections<byte[]> connections;
    private LinkedList<byte[]> dataHolder;
    private String FileName;
    private Queue<byte[]> packetsToSend;
    private String toSend;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
        UsersHolder.users.put(connectionId, false);
    }

    @Override
    public void process(byte[] message) {//tomer and mor created
        short opcode = (short) (((short) message[0]) << 8 | (short) (message[1]) & 0x00ff);
        byte[] messageData = new byte[message.length - 2];
        System.arraycopy(message, 2, messageData, 0, message.length - 2);
        switch (opcode) {
            case 1:
                if (isLoggedIn()) {
                    RRQ(messageData);
                    break;
                }
            case 2:
                if (isLoggedIn()) {
                    WRQ(messageData);
                    break;
                }
            case 3:
                if (isLoggedIn()) {
                    receiveDATA(messageData);
                    break;
                }
            case 4:
                if (isLoggedIn()) {
                    ACKReceive();
                    break;
                }
                //case 5: ERROR
            case 6:
                if (isLoggedIn()) {
                    DIRQ(messageData);
                    break;
                }
            case 7:
                if (!isLoggedIn()) {
                    LOGRQ(messageData);
                    break;
                }
            case 8:
                if (isLoggedIn()) {
                    DELRQ(messageData);
                    break;
                }
                //case 9: BCAST
            case 10:
                if (isLoggedIn()) {
                    UsersHolder.users.remove(connectionId);
                    shouldTerminate = true;
                    SendACK();
                    break;
                }
            default:
                ERROR(4);
        }
    }

    private boolean isLoggedIn() {
        boolean isLoggedIn = UsersHolder.users.get(connectionId);
        if (!isLoggedIn) {
            ERROR(6);
            return false;
        }
        return true;
    }

    /*    error Value Meaning:
                0    Not defined, see error message (if any).
                1    File not found – RRQ DELRQ of non-existing file.
                2    File not found – File cannot be written, read or deleted.
                3    Disk full or allocation exceeded – No room in disk.
                4    Illegal TFTP operation – Unknown Opcode.
                5    File already exists – File name exists on WRQ.
                6    User not logged in – Any opcode received before Login completes.
                7    User already logged in – Login username already connected.  */
    private void ERROR(int Error) {
        String msg = "";
        switch (Error) {
            case 0:
                msg = "Error 0";
                break;
            case 1:
                msg = "Error 1";
                break;
            case 2:
                msg = "Error 2";
                break;
            case 3:
                msg = "Error 3";
                break;
            case 4:
                msg = "Error 4";
                break;
            case 5:
                msg = "Error 5";
                break;
            case 6:
                msg = "Error 6";
                break;
            case 7:
                msg = "Error 7";
        }
        byte[] BMsg = msg.getBytes();
        connections.send(connectionId, BMsg);


        private void SendACK () {
            byte[] BMsg = "ACK 0".getBytes();
            connections.send(connectionId, BMsg);
        }

        private void SendACK ( short blockNumber){
            byte[] ACK = "ACK".getBytes();
            byte first = (byte) (blockNumber & 0xFF); // Extracts the lower byte
            byte second = (byte) ((blockNumber >> 8) & 0xFF); // Shifts and extracts the higher byte
            byte[] BMsg = {0, 4, first, second};
            byte[] send = new byte[ACK.length + BMsg.length];
            System.arraycopy(ACK, 0, send, 0, ACK.length);
            System.arraycopy(BMsg, 0, send, ACK.length, BMsg.length);
            connections.send(connectionId, send);
        }

        private void RRQ ( byte[] messageData){
            byte[] fileName = this.removeZeroFromEnd(messageData);
            dataHolder = new LinkedList<>();
            FileName = new String(fileName);
            Path filePath = Paths.get(PATH + FileName);
            if (Files.exists(filePath)) {
                SendACK();
                byte[] fileContent = new byte[0];
                try {
                    fileContent = Files.readAllBytes(filePath);
                } catch (IOException e) {
                }
                dataHolder.clear();
                dataHolder.add(fileContent);
                packetsToSend.clear();
                this.prepareDATA();
                ACKReceive();
            } else {
                ERROR(1);
            }
        }

        private void WRQ ( byte[] messageData){
            byte[] fileName = removeZeroFromEnd(messageData);
            dataHolder = new LinkedList<>();
            FileName = new String(fileName);
            String filePath = PATH + FileName;
            if (!(new File(filePath).exists()))
                ERROR(1);
            else {
                SendACK();
            }
        }

        private void receiveDATA ( byte[] messageData){
            short packetSize = (short) (((short) messageData[0]) << 8 | (short) (messageData[1]) & 0x00ff);
            byte[] BlockNumberMessage = new byte[messageData.length - 2];
            System.arraycopy(messageData, 2, BlockNumberMessage, 0, messageData.length - 2);

            short blockNumber = (short) (((short) BlockNumberMessage[0]) << 8 | (short) (BlockNumberMessage[1]) & 0x00ff);
            byte[] data = new byte[BlockNumberMessage.length - 2];
            System.arraycopy(BlockNumberMessage, 2, data, 0, BlockNumberMessage.length - 2);

            dataHolder.addLast(data);
            SendACK(blockNumber);

            if (packetSize < 512) {
                //create file
                byte[] theFile = new byte[(blockNumber - 1) * 512 + packetSize];
                int i = 0;
                for (byte[] arrays : dataHolder) {
                    System.arraycopy(arrays, 0, theFile, i * 512, arrays.length);
                    i++;
                }
                Path path = Paths.get(PATH + FileName);
                try {
                    Files.write(path, theFile);
                    String msg = "WRQ " + FileName + " complete";
                    byte[] send = msg.getBytes();
                    connections.send(connectionId, send);

                } catch (IOException e) {

                }

            }

        }

        private void prepareDATA () {
            int sizeOfDataToSend = 0;
            for (byte[] b : dataHolder) {
                sizeOfDataToSend += b.length;
            }
            Byte[] theFileToSend = new Byte[sizeOfDataToSend];
            int placeHere = 0;
            for (byte[] arrays : dataHolder) {
                System.arraycopy(arrays, 0, theFileToSend, placeHere, arrays.length);
                placeHere += arrays.length;
            }
            boolean stop = false;
            short packetNumber = 1;
            int currentPacketSize = Math.min(sizeOfDataToSend + 6, 512 + 6);
            while (!stop) {
                byte[] packet = new byte[currentPacketSize];
                packet[0] = (byte) 0;
                packet[1] = (byte) 3;
                short dataSegmentSize = (short) (currentPacketSize - 6);
                byte[] dataSegmentSizeInBytes = {(byte) (dataSegmentSize >> 8), (byte) (dataSegmentSize & 0xff)};
                packet[2] = dataSegmentSizeInBytes[0];
                packet[3] = dataSegmentSizeInBytes[1];
                byte[] packetNumberInBytes = {(byte) (packetNumber >> 8), (byte) (packetNumber & 0xff)};
                packet[4] = packetNumberInBytes[0];
                packet[5] = packetNumberInBytes[1];
                System.arraycopy(theFileToSend, (packetNumber - 1) * 512, packet, 6, currentPacketSize - 6);
                packetsToSend.add(packet);
                packetNumber++;
                if (sizeOfDataToSend < 512) {
                    stop = true;
                } else {
                    Byte[] newFile = new Byte[theFileToSend.length - 512];
                    System.arraycopy(theFileToSend, 512, newFile, 0, newFile.length);
                    theFileToSend = newFile;
                }
            }
        }

        private void DIRQ ( byte[] messageData){
            toSend = "DIRQ complete";
            dataHolder = new LinkedList<>();
            String fileName = "";
            File directory = new File(PATH);
            File[] files = directory.listFiles();
            byte[] zero = new byte[1];
            zero[0] = (byte) 0;
            for (File file : files) {
                if (file.isFile()) {
                    fileName = file.getName();
                    dataHolder.addLast(fileName.getBytes());
                    dataHolder.addLast(zero);
                }
            }
            dataHolder.removeLast();
            packetsToSend.clear();
            this.prepareDATA();
            this.ACKReceive();
        }

        private void ACKReceive () {
            if (!packetsToSend.isEmpty())
                connections.send(connectionId, packetsToSend.remove());
            else {
                connections.send(connectionId, toSend.getBytes());

            }
        }

        private void LOGRQ ( byte[] messageData){
            UsersHolder.users.put(connectionId, true);
            SendACK();
        }

        private void DELRQ ( byte[] messageData){
            byte[] fileName = removeZeroFromEnd(messageData);

        }
        @Override
        public boolean shouldTerminate () {
            return shouldTerminate;
        }
        private byte[] removeZeroFromEnd ( byte[] messageData){
            byte[] fileName = new byte[messageData.length - 1];
            for (int i = 0; i < messageData.length - 1; i++) {
                fileName[i] = messageData[i];
            }
            return fileName;
        }
    }
}
