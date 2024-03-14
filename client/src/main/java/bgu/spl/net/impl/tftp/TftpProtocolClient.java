package bgu.spl.net.impl.tftp;



import bgu.spl.net.api.MessagingProtocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class TftpProtocolClient implements MessagingProtocol<byte[]> {
    final private String PATH = "client/";
    private static LinkedList<byte[]> packetsToSend = new LinkedList<>();
    private static LinkedList<byte[]> dataHolder= new LinkedList<>();
    private static boolean shouldTerminate = false;
    private String FileName;
    private boolean nextTimeShouldTerminate=false;

    //final  private String PATH =  "server/Flies/";

    /** user input handler
     * this function receives a users input and determines what is the corresponding function to activate.
     * it then starts the right function to send the right message to the server.
     * note: this function does not handle server related operations, it only starts the process.
     * @param msg the string received from the user's terminal.
     */
    public byte[] process(byte[] msg){
        String message = new String(msg, StandardCharsets.UTF_8); // Convert byte array to String
        char[] toOPCode ={message.charAt(0),message.charAt(1),message.charAt(2)};
        switch (toOPCode[0]) {
            case 'R'://RRQ
                return RRQ(message);
            case 'W'://WRQ
                return WRQ(message);
            case 'D':
                switch (toOPCode[1]){
                    case 'I':
                        switch (toOPCode[2]){
                        case 'S'://DISC
                            return DISC();
                        case 'R'://DIRQ
                            return DIRQ();
                    }
                    break;
                    case 'E'://DELRQ
                        return DELRQ(message);
                }
            case 'L'://LOGRQ
                return LOGRQ(message);
            default:
                toPrint("Error 4");
                return null;
        }
    }
    /** server input handler
     * this function receives the server packets and determines what is the
     * corresponding function to activate according to the opcode.
     * it then starts the right function to send the right message to the server.
     * @param message the data sent from the server after being decoded.
     */
    public  byte[] processServer(byte[] message) {
        short opcode = (short) (((short) message[0]) << 8 | (short) (message[1]) & 0x00ff);
        byte[] messageData = new byte[message.length - 2];
        System.arraycopy(message, 2, messageData, 0, message.length - 2);
        switch (opcode) {
//            case 1://RRQ
//            case 2://WRQ
            case 3://DATA
                return receiveDATA(messageData);
            case 4://ACK
                return ACKReceive(messageData);
            case 5:
                return ERROR(messageData);
//            case 6://DIRQ
//            case 7://LOGRQ
//            case 8://DELRQ
            case 9:
                return BCAST(messageData);
//            case 10://disc
        }
        return null;
    }

    private byte[] ERROR(byte[] messageData) {
        toPrint("Error " + messageData[1]);
        return null;
    }

    private byte[] BCAST(byte[] messageData) {

        byte[] filenameInBytes = new byte[messageData.length-1];
        System.arraycopy(messageData,0,filenameInBytes,0,filenameInBytes.length);
        String filename = new String(filenameInBytes, StandardCharsets.UTF_8); // Convert byte array to String
        if (messageData[0]==(byte)0){
            toPrint("BCAST delete" + filename);
        } else {
            toPrint("BCAST add" + filename);
        }
        return new byte[]{(byte) 0};
    }
    // client keyboard operated commands
    /**
     * 2.1.1 LOGRQ
     * • Login User to the server
     * • Format: LOGRQ <Username>
     * • Result: Sending a LOGRQ packet to the server with the <Username> and waiting for ACK with block
     *              number 0 or ERROR packet to be received in the Listening thread.
     * • Example: LOGRQ KELVE_YAM
     * • NOTE: KELVE YAM is not equal to kelve yam they have two different UTF-8 encodings.
     */
    private byte[] LOGRQ(String Command){
        byte[] packet = new byte[Command.length()+3];
        byte[] usernameInBytes = Command.getBytes();
        packet[0] = (byte) 0;
        packet[1] = (byte) 7;
        System.arraycopy(usernameInBytes,6,packet,2,usernameInBytes.length-6);
        packet[Command.length()+2] = (byte) 0;
        return packet;
    }
    /**
     *2.1.2 DELRQ
     * • Delete File from the server.
     * • Format: DELRQ <Filename>
     * • Result: Sending a DELRQ packet to the server with the <Filename> and waiting for ACK with block number
     *           0 or ERROR packet to be received in the Listening thread.
     * • Example: DELRQ lehem hvita
     * • NOTE: ”lehem hvita” is one filename with space char in it (this is ok).
     */
    private byte[] DELRQ(String Command){
        byte[] packet = new byte[Command.length()+3];
        byte[] usernameInBytes = Command.getBytes();
        packet[0] = (byte) 0;
        packet[1] = (byte) 8;
        System.arraycopy(usernameInBytes,6,packet,2,usernameInBytes.length-6);
        packet[Command.length()+2] = (byte) 0;
        return packet;
    }
    /**
     *2.1.4 WRQ
     * • Upload File from current working directory to the server.
     * • Format: WRQ <Filename>
     * • Result: Check if file exist then send a WRQ packet and wait for ACK or ERROR packet to be received in
     *              the Listening thread. If received ACK start transferring the file.
     * • Error handling:
     *      ○ File does not exist in the client side: print to terminal ”file does not exists” and don’t send WRQ
     *          packet to the server.
     *      ○ Listening thread received an Error: stop transfer.
     * • On complete transfers: print to terminal ”WRQ <Filename> complete”.
     * • Example of Command: WRQ Operation Grandma.mp4
     */
    private byte[] WRQ(String Command){
        String filename = Command.substring(4);
        Path path = Paths.get(PATH + filename) ;
        if (!Files.exists(path)){
            System.out.println("file does not exists");
            return null;
        }
        byte[] packet = new byte[Command.length()+3];
        byte[] usernameInBytes = Command.getBytes();
        packet[0] = (byte) 0;
        packet[1] = (byte) 2;
        System.arraycopy(usernameInBytes,4,packet,2,usernameInBytes.length-4);
        packet[Command.length()+2] = (byte) 0;
        dataHolder.clear();
        try {
            dataHolder.add(Files.readAllBytes(path));
        } catch (IOException e) {}
        prepareDATA();
        return packet;
    }
    /**
     *2.1.3 RRQ
     * • Download file from the server Files folder to current working directory.
     * • Format: RRQ <Filename>
     * • Result: Creating a file in current working directories(if not exist) and then send a RRQ packet to the server with
     *           the <Filename> and waiting for file to complete the transfer (Server sending DATA Packets) or ERROR
     *           packet to be received in the Listening thread.
     * • Error handling:
     *          ○ File already exists in the client side: print to terminal ”file already exists” and don’t send RRQ
     *          packet to the server.
     *          ○ Listening thread received an Error: deleted created file.
     * • On complete transfers: print to terminal ”RRQ <Filename> complete”.
     * • Example of Command: RRQ kelve yam.mp3
     */
    private byte[] RRQ(String Command){
        String filename = Command.substring(4);
        this.FileName = filename;
        Path path = Paths.get(PATH + filename) ;
        if (Files.exists(path)){
            System.out.println("file already exists");
            return null;
        }
        byte[] packet = new byte[Command.length()+3];
        byte[] usernameInBytes = Command.getBytes();
        packet[0] = (byte) 0;
        packet[1] = (byte) 1;
        System.arraycopy(usernameInBytes,4,packet,2,usernameInBytes.length-4);
        packet[Command.length()+2] = (byte) 0;
        return packet;
    }
    /**
     * 2.1.5 DIRQ
     * • List all the file names that are in Files folder in the server.
     * • Format: DIRQ
     * • Result: Sending a DIRQ packet to the server with waiting for the filenames to complete the transfer(server
     *          sending DATA pakets) or an ERROR packet to be received in the Listening thread.
     * • Error handling:
     *      ○ Listening thread received an Error: Nothing.
     * • On complete transfers of file names: print to terminal:
     *      <file name 1>\n
     *      <file name 2>\n
     *       ⋯
     *      <file name n>\n
     */
    private byte[] DIRQ() {
        byte[] packet = {(byte)0,(byte)6};
        return packet;
    }
    /**
     * 2.1.6 DISC
     * • Disconnect (Server remove user from Logged-in list) from the server and close the program.
     * • Format: DISC
     * • Result: Check if User is logged in.
     *      ○ User is logged in sending DISC packet and waits for ACK with block number 0 or ERROR packet to
     *          be received in the Listening thread then closes the socket and exit the client program.
     *      ○ User is not logged in close socket and exit the client program.
     * • Error handling:
     *      ○ Listening thread received an Error: let the program exit just after the error is printed to the terminal.
     * • NOTE: don’t close the program via the listening thread.
     */
    private byte[] DISC(){
        byte[] packet = {(byte)0,(byte)0x0a};
        nextTimeShouldTerminate=true;
        return packet;
    }

    // server communication operated commands.
    // replaces the process methods in client.
    private byte[] SendACK(){
        byte[] packet = {(byte)0,(byte)4,(byte)0,(byte)0};
        return packet;
    }
    private byte[] SendACK(short blockNumber){
        byte first = (byte) (blockNumber & 0xFF); // Extracts the lower byte
        byte second = (byte) ((blockNumber >> 8) & 0xFF); // Shifts and extracts the higher byte
        byte[] packet = {(byte)0,(byte)4,first,second};
        return packet;
    }
    private byte[] ACKReceive(byte[] messageData) {
        if (messageData[1]==(byte)0){
            toPrint("ACK 0");
        }
        if (!packetsToSend.isEmpty()){
            return packetsToSend.removeFirst();
        }
        if (nextTimeShouldTerminate){
            shouldTerminate=true;
        }
        return null;
    }
    private byte[] receiveDATA(byte[] Command){
        dataHolder= new LinkedList<>();
        //removes the first two bytes into the short packetSize
        short packetSize = (short) (((short) Command[0]) << 8 | (short) (Command[1]) & 0x00ff);
        byte[] BlockNumberMessage = new byte[Command.length - 2];
        System.arraycopy(Command, 2, BlockNumberMessage, 0, Command.length - 2);
        //removes the third and forth bytes into the short blockNumber
        short blockNumber = (short) (((short) BlockNumberMessage[0]) << 8 | (short) (BlockNumberMessage[1]) & 0x00ff);
        byte[] data = new byte[BlockNumberMessage.length - 2];
        System.arraycopy(BlockNumberMessage, 2, data, 0, BlockNumberMessage.length - 2);
        // the array data contains only the data section
        dataHolder.addLast(data);
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

                Path filePath = Paths.get(PATH + FileName);
                Files.write(path, theFile);
            } catch (IOException e) {
            }
        }
        return SendACK(blockNumber);

    }
    private void prepareDATA(){
        packetsToSend = new LinkedList<>();
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
            packetsToSend.addLast(packet);
            packetNumber++;
            if (currentPacketSize < 512) {
                stop = true;
            } else {
                Byte[] newFile = new Byte[theFileToSend.length - 512];
                System.arraycopy(theFileToSend, 512, newFile, 0, newFile.length);
                theFileToSend = newFile;
            }
        }
    }
    private void toPrint(String toPrint) {
        System.out.println(toPrint);
    }
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
}
