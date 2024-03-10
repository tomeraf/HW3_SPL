package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.srv.BaseServer;
import bgu.spl.net.srv.BlockingConnectionHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class TftpServer<T> extends BaseServer<T> {
    private final ExecutorService pool;//mor change
    private int numOfThreads;
    public TftpServer(int port, Supplier<MessagingProtocol<T>> protocolFactory, Supplier<MessageEncoderDecoder<T>> encdecFactory) {
        super(port, protocolFactory, encdecFactory);
        pool = Executors.newFixedThreadPool(numOfThreads);//mor change
    }

    @Override
    protected void execute(BlockingConnectionHandler<T> handler) {
        new Thread(handler).start();
    }
}
