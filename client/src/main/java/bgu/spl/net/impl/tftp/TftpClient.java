package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;

public class TftpClient {
    public static void main(String[] args) throws IOException {
        TftpEncoderDecoder encdecKeyBoard = new TftpEncoderDecoder();
        TftpProtocolClient protocolKeyBoard = new TftpProtocolClient();
        TftpEncoderDecoder encdecServer = new TftpEncoderDecoder();
        TftpProtocolClient protocolServer = new TftpProtocolClient();

        if (args.length == 0) {
            args = new String[]{"localhost", "hello"};
        }
        
        try (Socket sock = new Socket(args[0], 7777)) {
            BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream());
            KeyBoardListener KeyBoard = new KeyBoardListener(encdecKeyBoard,protocolKeyBoard,out);
            Thread KeyBoardThread = new Thread(KeyBoard);
            ServerListener Server = new ServerListener(encdecServer,protocolServer,in,out,KeyBoard);
            Thread ServerThread = new Thread(Server);
            KeyBoardThread.start();
            ServerThread.start();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
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

            try {
                synchronized (out) {
                    out.write((encdec.encode(send)), 0, send.length);
                    out.flush();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
                wait();
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
                while ((read = in.read()) >= 0) {
                    nextMessage = encdec.decodeNextByte((byte) read);
                }
            }catch (IOException e){
                throw new RuntimeException(e);
            }
            processedAnswer = protocol.processServer(nextMessage);
            if (processedAnswer == null) {
                if (protocol.shouldTerminate()) {
                    KeyBoard.Terminate();
                    this.Terminate();
                }
                KeyBoard.notifyAll();
            }
            else if (processedAnswer.length != 1) {
                try {
                    synchronized (out){
                        out.write((encdec.encode(processedAnswer)), 0, processedAnswer.length);
                        out.flush();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }
        
    public void Terminate(){terminate=true;}

}

