package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;

public class TftpClient {
    public static void main(String[] args) throws IOException {
        TftpEncoderDecoder encdec = new TftpEncoderDecoder();
        TftpProtocolClient protocol = new TftpProtocolClient();

        if (args.length == 0) {
            args = new String[]{"localhost", "hello"};
        }

        //BufferedReader and BufferedWriter automatically using UTF-8 encoding
        try (Socket sock = new Socket(args[0], 7777)) {
            BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream());

            boolean command=true;
            byte[] processedAnswer=null;

            while (!protocol.shouldTerminate()) {
                byte[] send = null;
                if(command) {
                    command=false;
                    Scanner scanner = new Scanner(System.in);//user Input
                    String commandS = scanner.nextLine();
                    send = (byte[]) protocol.process(commandS.getBytes());
                }
                else {
                    send=processedAnswer;
                }
                out.write((encdec.encode(send)), 0, send.length);
                out.flush();

                int read;
                while (!protocol.shouldTerminate() && (read = in.read()) >= 0) {
                    encdec.decodeNextByte((byte) read);
                }
                encdec.End();
                byte[] nextMessage = encdec.decodeNextByte((byte) 0);
                processedAnswer=protocol.processServer(nextMessage);
                if(processedAnswer == null)
                    command = true;

            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
