package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;

public class TftpClient {
    public static void main(String[] args) throws IOException {
        args = new String[2];
        TftpEncoderDecoder encdec = new TftpEncoderDecoder();
        TftpProtocolClient protocol = new TftpProtocolClient();
//        TftpEncoderDecoder encdec = new TftpEncoderDecoder();
//        TftpProtocolClient protocol = new TftpProtocolClient();


        try (Socket sock = new Socket("localhost", 7777)) {
            BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream());
            KeyBoardListener KeyBoard = new KeyBoardListener(encdec,protocol,out);
            Thread KeyBoardThread = new Thread(KeyBoard);
            ServerListener Server = new ServerListener(encdec,protocol,in,out,KeyBoard);
            Thread ServerThread = new Thread(Server);
            System.out.println("Starting Threads");
            KeyBoardThread.start();
            ServerThread.start();
            System.out.println("Server connections online");
            KeyBoardThread.join();
            ServerThread.join();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        catch (InterruptedException x){}
    }
}
class KeyBoardListener implements Runnable {
    private TftpEncoderDecoder encdec;
    private TftpProtocolClient protocol;
    private BufferedOutputStream out;
    private boolean terminate = false;
    private Scanner scanner = new Scanner(System.in);//user Input

    KeyBoardListener(TftpEncoderDecoder encdec, TftpProtocolClient protocol, BufferedOutputStream out) {
        this.encdec = encdec;
        this.protocol = protocol;
        this.out = out;
    }

    private  boolean checkCommand(String commandS){
        boolean good=true;
        if(commandS.length()<3)
            return false;
        else {
            char s0 = commandS.charAt(0);
            char s1 = commandS.charAt(1);
            char s2 = commandS.charAt(2);
            String test= ""+s0+s1+s2;
            if(!test.equals("RRQ") && !test.equals("WRQ")) {
                if(commandS.length()==3)
                    return false;
                else {
                    test += commandS.charAt(3);
                    if(!test.equals("DIRQ") && !test.equals("DISC")) {
                        if(commandS.length()==4)
                            return false;
                        else {
                            test += commandS.charAt(4);
                            if(!test.equals("LOGRQ") && !test.equals("DELRQ")) {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    public void run() {
        try {
            while (!terminate) {
                String commandS = scanner.nextLine();
                if(!this.checkCommand(commandS))
                    System.out.println("Error 4");
                else {
                    byte[] send = protocol.process(commandS.getBytes());
                    if(send!=null) {
                        try {
                            synchronized (out) {
                                out.write((encdec.encode(send)), 0, send.length);
                                out.flush();
                            }
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                        synchronized (this) {
                            wait();
                        }
                    }
                }
            }
        } catch (InterruptedException ignore) {}
    }
    public void Terminate(){terminate=true;}
    
}

class ServerListener implements Runnable {
    private TftpEncoderDecoder encdec;
    private TftpProtocolClient protocol;
    private BufferedInputStream in;
    private boolean terminate = false;
    private Scanner scanner = new Scanner(System.in);//user Input
    private BufferedOutputStream out;
    private KeyBoardListener KeyBoard;

    ServerListener(TftpEncoderDecoder encdec, TftpProtocolClient protocol, BufferedInputStream in,BufferedOutputStream out,KeyBoardListener KeyBoard) {
        this.encdec = encdec;
        this.protocol = protocol;
        this.in = in;
        this.KeyBoard=KeyBoard;
        this.out=out;
    }

    public void run() {
        while (!terminate) {
            int read;
            byte[] nextMessage = null;
            byte[] processedAnswer = null;

            try{
                while (nextMessage==null && (read = in.read()) >= 0) {
                    nextMessage = encdec.decodeNextByte((byte) read);
                    if(nextMessage!=null)
                        processedAnswer = protocol.processServer(nextMessage);
                }
            } catch (IOException e){
                e.printStackTrace();
                throw new RuntimeException();
            }

            if (processedAnswer == null) {
                if (protocol.shouldTerminate()) {
                    KeyBoard.Terminate();
                    this.Terminate();
                }
                synchronized (KeyBoard) {
                    KeyBoard.notifyAll();
                }
            }
            else if (processedAnswer.length != 1) {
                try {
                    synchronized (out){
                        out.write((encdec.encode(processedAnswer)), 0, processedAnswer.length);
                        out.flush();
                        if(protocol.isFileTransferDone())
                            synchronized (KeyBoard) {
                                KeyBoard.notifyAll();
                            }

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
        
    public void Terminate(){terminate=true;}

}

