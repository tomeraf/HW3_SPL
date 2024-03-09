package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;
import jdk.internal.net.http.common.Pair;

import javax.tools.JavaFileManager;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    private boolean shouldTerminate = false;
    private int connectionId;
    private Connections<byte[]> connections;
    private Map<Integer,Boolean> users;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        users =new HashMap<>();
        this.connectionId=connectionId;
        this.connections=connections;
        users.put(connectionId,false);
    }

    @Override
    public void process(byte[] message) {//tomer and mor created
        short opcode = (short) (((short) message[0]) << 8 | (short) (message[1]) & 0x00ff);
        byte[] messageData = new byte[message.length - 2];
        System.arraycopy(message, 2, messageData, 0, message.length - 2);

                switch (opcode) {
                    case 1:
                        if(isLoggedIn()) {
                            RRQ(messageData);
                            break;
                        }
                    case 2:
                        if(isLoggedIn()) {
                            WRQ(messageData);
                            break;
                        }
                    case 3:
                        if(isLoggedIn()) {
                            DATA(messageData);
                            break;
                        }
                    //case 4: ACK
                    //case 5: ERROR
                    case 6:
                        if(isLoggedIn()) {
                            DIRQ(messageData);
                            break;
                        }
                    case 7:
                        if(isLoggedIn()) {
                        LOGRQ(messageData);
                        break;
                    }
                    case 8:
                        if(isLoggedIn()) {
                            DELRQ(messageData);
                            break;
                        }
                    //case 9: BCAST
                    case 10:
                        if(isLoggedIn()) {
                            shouldTerminate = false;
                            break;
                        }
                    default:
                        byte[] error = {0,4};
                        ERROR(error);
                }
    }

    private boolean isLoggedIn() {
        boolean isLoggedIn = users.get(connectionId);
        if (!isLoggedIn) {
            byte[] error = {0, 6};
            ERROR(error);
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
            6   User not logged in – Any opcode received before Login completes.
            7    User already logged in – Login username already connected.  */
    private void ERROR(byte[] Error) {
        short ErrorValue = (short) (((short) Error[0]) << 8 | (short) (Error[1]) & 0x00ff);
        String msg="";
        switch (ErrorValue) {
            case 0:
                msg="Error 0";
                break;
            case 1:
                msg="Error 1";
                break;
            case 2:
                msg="Error 2";
                break;
            case 3:
                msg="Error 3";
                break;
            case 4:
                msg="Error 4";
                break;
            case 5:
                msg="Error 5";
                break;
            case 6:
                msg="Error 6";
                break;
            case 7:
                msg="Error 7";
        }
        byte[] BMsg=msg.getBytes();
        connections.send(connectionId,BMsg);
    }

    private void RRQ(byte[] messageData){


    }

    private void WRQ(byte[] messageData){


    }
    private void DATA(byte[] messageData){


    }


    private void DIRQ(byte[] messageData){


    }

    private void LOGRQ(byte[] messageData){


    }

    private void DELRQ(byte[] messageData){


    }


    @Override
    public boolean shouldTerminate() {
       return shouldTerminate;
    } 


    
}
