package bgu.spl.net.impl.tftp;
import bgu.spl.net.api.MessageEncoderDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    //TODO: Implement here the TFTP encoder and decoder
    private byte[] bytes = new byte[512];
    private int len = 0;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        // TODO: implement this
        if (nextByte == '\n') {
            return popString().getBytes(StandardCharsets.UTF_16);
        }

        pushByte(nextByte);
        return null; //not a line yet
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }

        bytes[len++] = nextByte;
    }

    private String popString() {
        //notice that we explicitly requesting that the string will be decoded from UTF-8
        //this is not actually required as it is the default encoding in java.
        String result = new String(bytes, 0, len, StandardCharsets.UTF_8);
        len = 0;
        return result;
    }

    @Override
    public byte[] encode(byte[] message){
        //TODO: implement this [v]
        String s = new String(message, StandardCharsets.UTF_16);
        byte[] outbytes = s.getBytes(StandardCharsets.UTF_8);

        return outbytes;
    }
}