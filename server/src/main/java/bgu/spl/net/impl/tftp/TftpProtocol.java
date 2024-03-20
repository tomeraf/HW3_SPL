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
    public static Map<Integer,Boolean> users=new HashMap<>();
    public static Map<Integer,Boolean> getUsers(){
        if (users == null)
             users=new HashMap<>();
        return users;
    }


}

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {
    final private String PATH = FindPath()+"\\";
    private boolean shouldTerminate = false;
    private int connectionId;
    private Connections<byte[]> connections;
    private LinkedList<byte[]> dataHolder;
    private String FileName;
    private LinkedList<byte[]> packetsToSend;

    static String FindPath(){
        Path directoryPath = Paths.get("Files");
            return directoryPath.toAbsolutePath().toString();
}


    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
        UsersHolder.getUsers().put(connectionId, false);
        this.packetsToSend = new LinkedList<>();
    }

    @Override
    public void process(byte[] message) {
        short opcode = (short) (((short) message[0]) << 8 | (short) (message[1]) & 0x00ff);
        byte[] messageData = new byte[message.length - 2];
        System.arraycopy(message, 2, messageData, 0, message.length - 2);
        switch (opcode) {
            case 1:
                RRQ(messageData);
                break;
            case 2:
                WRQ(messageData);
                break;
            case 3:
                receiveDATA(messageData);
                break;
            case 4:
                ACKReceive();
                break;
            //case 5: ERROR
            case 6:
                DIRQ(messageData);
                break;
            case 7:
                LOGRQ(messageData);
                break;
            case 8:
                DELRQ(messageData);
                break;
            case 9:
                String filename = new String(messageData);
                filename=filename.substring(0,filename.length()-1);
                BCAST((byte)1,filename.getBytes());
                break;
            case 10:
                if (isLoggedIn()) {
                    UsersHolder.getUsers().remove(connectionId);
                    shouldTerminate = true;
                    SendACK();
                    break;
                } else {
                    Error(6);
                    break;
                }
            default:
                Error(4);
        }
    }
    private boolean isLoggedIn() {
        boolean isLoggedIn = UsersHolder.getUsers().get(connectionId);
        if (!isLoggedIn) {
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

    private void Error(int Error) {
        byte[] BMsg = {(byte)0,(byte)5,(byte)0,(byte)Error,(byte)0};
        connections.send(connectionId, BMsg);
    }

    private void SendACK() {
        byte[] BMsg = {(byte)0,(byte)4,(byte)0,(byte)0};
        connections.send(connectionId, BMsg);
    }

    private void SendACK(short blockNumber) {
        byte first = (byte) ((blockNumber >> 8) & 0xFF); // Shifts and extracts the higher byte
        byte second = (byte) (blockNumber & 0xFF); // Extracts the lower byte
        byte[]  send = {0, 4, first, second};
        connections.send(connectionId, send);
    }

    private void RRQ(byte[] messageData) {
        dataHolder = new LinkedList<>();
        FileName = new String(messageData);
        FileName=FileName.substring(0,FileName.length()-1);
        Path filePath = Paths.get(PATH + FileName);
        if (Files.exists(filePath)) {
            if (!isLoggedIn()) {
                Error(6);
                return;
            }
            byte[] fileContent = new byte[0];
            try {
                fileContent = Files.readAllBytes(filePath);
            } catch (IOException e) {
                Error(2);
                return;
            }
            dataHolder.clear();
            dataHolder.add(fileContent);
            packetsToSend.clear();
            this.prepareDATA();
            ACKReceive();
        } else {
            Error(1);
        }
    }

    private void WRQ(byte[] messageData) {
        dataHolder = new LinkedList<>();
        FileName = new String(messageData);
        FileName=FileName.substring(0,FileName.length()-1);
        Path filePath = Paths.get(PATH + FileName);
        if (Files.exists(filePath))
            Error(5);
        else {
            if (!isLoggedIn()) {
                Error(6);
                return;
            }
            SendACK();
        }
    }

    private void receiveDATA(byte[] messageData) {
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
            try {
                Path filePath = Paths.get(PATH + FileName);
                if (Files.exists(filePath))
                    Error(5);
                else {
                    Files.write(filePath, theFile);
                    this.BCAST((byte) 1, FileName.getBytes());
                }
            } catch (IOException e) {

            }

        }

    }

    private void prepareDATA() {
        int sizeOfDataToSend = 0;
        for (byte[] b : dataHolder) {
            sizeOfDataToSend += b.length;
        }
        byte[] theFileToSend = new byte[sizeOfDataToSend];
        int placeHere = 0;
        for (byte[] arrays : dataHolder) {
            System.arraycopy(arrays, 0, theFileToSend, placeHere, arrays.length);
            placeHere += arrays.length;
        }
        boolean stop = false;
        short packetNumber = 1;
        while (!stop) {
            int currentPacketSize = Math.min(sizeOfDataToSend + 6, 512 + 6);
            byte[] packet = new byte[currentPacketSize];
            packet[0] = (byte) 0;
            packet[1] = (byte) 3;
            short dataSegmentSize = (short) (currentPacketSize - 6);
            byte[] dataSegmentSizeInBytes = {(byte) ((dataSegmentSize >> 8) & 0xFF), (byte) (dataSegmentSize & 0xFF)};
            packet[2] = dataSegmentSizeInBytes[0];
            packet[3] = dataSegmentSizeInBytes[1];
            byte[] packetNumberInBytes = {(byte) (packetNumber >> 8), (byte) (packetNumber & 0xFF)};
            packet[4] = packetNumberInBytes[0];
            packet[5] = packetNumberInBytes[1];
            System.arraycopy(theFileToSend, (packetNumber - 1) * 512, packet, 6, currentPacketSize - 6);
            packetsToSend.addLast(packet);
            packetNumber++;
            if (currentPacketSize < 512+6) {
                stop = true;
            } else {
                sizeOfDataToSend-=512;
            }
        }
    }

    private void DIRQ(byte[] messageData) {
        if (!isLoggedIn()) {
            Error(5);
            return;
        }
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

    private void ACKReceive() {
        if (!packetsToSend.isEmpty())
            connections.send(connectionId, packetsToSend.removeFirst());
    }

    private void LOGRQ(byte[] messageData) {
        if (isLoggedIn()) {
            Error(7);
            return;
        }
        UsersHolder.getUsers().put(connectionId, true);
        SendACK();
    }

    private void DELRQ(byte[] messageData) {
        dataHolder = new LinkedList<>();
        FileName = new String(messageData);
        Path filePath = Paths.get(PATH + FileName);
        if (Files.exists(filePath)) {
            if (!isLoggedIn()) {
                Error(5);
                return;
            }
            try {
                Files.delete(filePath);
                this.SendACK();
                this.BCAST((byte) 0, messageData);
            } catch (IOException e) {
            }
        } else {
            Error(1);
        }
    }

    private void BCAST(byte addedOrDeleted, byte[] filename) {
        byte[] packet = new byte[4 + filename.length];
        packet[0] = (byte) 0;
        packet[1] = (byte) 9;
        packet[2] = addedOrDeleted;
        System.arraycopy(filename, 0, packet, 3, filename.length);
        packet[packet.length - 1] = (byte) 0;
        for (int connectionID : UsersHolder.getUsers().keySet()) {
            if (UsersHolder.getUsers().get(connectionID)) {
                connections.send(connectionID, packet);
            }
        }
    }

    public boolean shouldTerminate() {
        return shouldTerminate;
    }


}
