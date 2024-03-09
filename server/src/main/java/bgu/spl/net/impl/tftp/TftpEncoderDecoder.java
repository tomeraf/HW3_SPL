package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

import java.util.LinkedList;
import java.util.List;


public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private List<Byte> b = new LinkedList<Byte>();

    public byte[] decodeNextByte(byte nextByte) {
        if(nextByte == '0'){
            byte[] ans = new byte[b.size()];
            for(int i = 0; i< b.size(); i++){
                ans[i] = b.get(i);
            }
            b=new LinkedList<Byte>();
            return ans;
        }
        b.add(nextByte);
        return null;
    }

    public byte[] encode(byte[] message) {
        byte[] ans = new byte[message.length+1];
        for(int i = 0; i<ans.length; i++){
            ans[i]=message[i];
        }
        ans[ans.length-1] = 0;
        return ans;
    }
}