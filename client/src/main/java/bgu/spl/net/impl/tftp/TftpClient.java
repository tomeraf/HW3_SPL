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
        TftpEncoderDecoder encdecKeyBoard = new TftpEncoderDecoder();
        TftpProtocolClient protocolKeyBoard = new TftpProtocolClient();
        TftpEncoderDecoder encdecServer = new TftpEncoderDecoder();
        TftpProtocolClient protocolServer = new TftpProtocolClient();


        try (Socket sock = new Socket("localhost", 7777)) {
            BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream());
            KeyBoardListener KeyBoard = new KeyBoardListener(encdecKeyBoard,protocolKeyBoard,out);
            Thread KeyBoardThread = new Thread(KeyBoard);
            ServerListener Server = new ServerListener(encdecServer,protocolServer,in,out,KeyBoard);
            Thread ServerThread = new Thread(Server);
            System.out.println("Starting Threads:");
            KeyBoardThread.start();
            ServerThread.start();
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

    public void run() {
        while (!terminate) {
            try {
            String commandS = scanner.nextLine();
            byte[] send = null;
            send = (byte[]) protocol.process(commandS.getBytes());
            if (send != null) {
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
            } catch (InterruptedException ignore) {}
        }
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
                while ((read = in.read()) >= 0 && nextMessage==null) {
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
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
        
    public void Terminate(){terminate=true;}

}

