package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

import java.util.LinkedList;
import java.util.List;


public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private List<Byte> b = new LinkedList<Byte>();
    private byte opCode;
    private boolean gotTheFirst;
    private boolean gotTheSecond;
    private boolean sizeKnown;
    private short sizeLeftToDecode;
    public boolean gotTheThird;
    private byte theThird;
    public boolean gotTheForth;
    private byte theForth;
    public boolean waitForZero;

    public TftpEncoderDecoder(){
        this.opCode=0;
        this.sizeKnown=false;
        this.sizeLeftToDecode = 0;
        this.gotTheFirst = false;
        this.gotTheSecond = false;
        this.gotTheThird = false;
        this.gotTheForth = false;
        this.waitForZero=false;
    }

    public byte[] decodeNextByte(byte nextByte) {
        // we have been through the opcode and size is not known.
        // in this case we wait for a zero in the end.
        if(waitForZero){
            b.add(nextByte);
            if(nextByte==(byte)0){
                byte[] ans = new byte[b.size()];
                for (int i = 0; i < b.size(); i++) {
                    ans[i]=b.remove(0);
                }
                reset();
                return ans;
            } else {
                return null;
            }
        }
        // size already known now iterate until size left to decode is 0.
        if (sizeKnown){
            b.add(nextByte);
            sizeLeftToDecode--;
            if (sizeLeftToDecode==0){
                byte[] ans = new byte[b.size()];
                for (int i = 0; i < b.size(); i++) {
                    ans[i]=b.remove(0);
                }
                reset();
                return ans;
            }
            return null;
        }
        // the first iteration will come in here.
        if (!gotTheFirst){
            b.add(nextByte);
            gotTheFirst = true;
            return null;
        }
        // second iteration go here, opcode will be checked.
        // we will find what is the opcode and act accordingly.
        else if (!gotTheSecond) {
            b.add(nextByte);
            gotTheSecond = true;
            opCode = nextByte;
            if (opCode == (byte) 6) {                   //DIRC
                reset();
                return (new byte[]{(byte) 0, (byte) 6});
            } else if (opCode == 0x0a) {                //DISC
                reset();
                return (new byte[]{(byte) 0, 0x0a});
            }
        }
        // third iteration go here , in case of BCAST wait for zero -> true
        else if (!gotTheThird) {
            b.add(nextByte);
            theThird = nextByte;
            gotTheThird=true;
            if (opCode == (byte)9)      //BCAST
                waitForZero = true;
            return null;
        }
        // forth iteration will go here.
        else if (!gotTheForth) {
            gotTheForth=true;
            b.add(nextByte);
            theForth = nextByte;
            if (opCode==(byte)4){           //ACK
                reset();
                return (new byte[] {(byte)0,opCode,theThird,theForth});
            } else if (opCode==(byte)3){        //DATA
                sizeKnown = true;
                sizeLeftToDecode = (short) (((short) theThird) << 8 | (short) (theForth) & 0x00ff);
                sizeLeftToDecode+=2;
                return null;
            } else {                    //LOGRQ,WRQ,RRQ,ERROR
                waitForZero=true;
            }
        }
        return null;
    }

    private void reset() {
        this.opCode=0;
        this.sizeKnown=false;
        this.sizeLeftToDecode = 0;
        this.gotTheFirst = false;
        this.gotTheSecond = false;
        this.gotTheThird = false;
        this.gotTheForth = false;
        this.waitForZero=false;
    }

    public byte[] encode(byte[] message) {
        return message;
    }

}