package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.srv.BaseServer;
import bgu.spl.net.srv.BlockingConnectionHandler;

import java.util.function.Supplier;

public class TftpServer<T> extends BaseServer<T> {
    public TftpServer(int port, Supplier<MessagingProtocol<T>> protocolFactory, Supplier<MessageEncoderDecoder<T>> encdecFactory) {
        super(port, protocolFactory, encdecFactory);
    }

    @Override
    protected void execute(BlockingConnectionHandler<T> handler) {
        //TODO: Implement this
    }
}
