package bgu.spl.net.srv;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
//this class functions as a way to create, organize and manage all the handlers of the users connected.

public class ConnectionsImpl <T > implements Connections <T >{

    private Map<Integer,ConnectionHandler<T>> connections;


    public ConnectionsImpl(){
        this.connections =new HashMap<>();
    }
    // this function saves the new connectionID with its corresponding handler.
    @Override
    public boolean connect(int connectionId, ConnectionHandler<T> handler) {
        return connections.put(connectionId,handler) != null;
    }

    //this function sends the msg to the handler (uses the reference interface ConnectionHandler but in practice uses the instance of blockingConnectionHandler) of the connection ID specified.
    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> c = connections.get(connectionId);
        if (c == null)//map function "get" can return null if no id was found.
            return false;
        c.send(msg);
        return  true;
    }

    // this function removes the handler from the map and closes the handler.
    @Override
    public void disconnect(int connectionId) {
        ConnectionHandler c = connections.remove(connectionId);
        if (c != null) {
            try {
                c.close();// handler implements closable.
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
