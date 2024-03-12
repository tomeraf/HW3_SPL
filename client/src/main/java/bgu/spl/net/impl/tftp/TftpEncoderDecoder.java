package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

import java.util.LinkedList;
import java.util.List;


public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private List<Byte> b = new LinkedList<Byte>();

    public boolean isEnd=false;

    public byte[] decodeNextByte(byte nextByte) {
        if(isEnd){
            byte[] ans = new byte[b.size()];
            for(int i = 0; i< b.size(); i++){
                ans[i] = b.get(i);
            }
            b=new LinkedList<Byte>();
            isEnd=false;
            return ans;
        }
        b.add(nextByte);
        return null;
    }

    public void End() {
        isEnd=true;
    }

    public byte[] encode(byte[] message) {
        return message;
    }

}