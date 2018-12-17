package com.chentsov.client;

import com.chentsov.common.messages.AbstractMessage;
import com.chentsov.common.messages.responses.FileMessage;
import io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.Socket;

/**
 * @author Evgenii Chentsov
 * <p>
 * The main class that maintains socket connection
 */
public class Connection {

    private static Connection connection;

    private Socket socket;
    private ObjectEncoderOutputStream out;
    private ObjectDecoderInputStream in;

    private static int port = 8189;
    private static String address = "127.0.0.1";
    private static final Logger logger = LogManager.getLogger(Connection.class.getSimpleName());

    public static void setPort(int port) {
        Connection.port = port;
    }

    public static void setAddress(String address) {
        Connection.address = address;
    }

    public static boolean isClosed() {
        return connection == null;
    }

    private Connection() throws IOException {
            logger.info("Initializing connection to server");
            this.socket = new Socket(address, port);
            this.out = new ObjectEncoderOutputStream(socket.getOutputStream(), FileMessage.getMaxFileSize() * 2);
            this.in = new ObjectDecoderInputStream(socket.getInputStream(), FileMessage.getMaxFileSize() * 2);
            logger.info("Connection has been established");
    }

    /**
     * Establishes client-server connection
     */
    public static Connection get() throws IOException {
        if (connection == null) connection = new Connection();
        return connection;
    }

    /**
     * Closes the connection between client and server
     */
    public static void close() {
        try {
            connection.out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            connection.in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            connection.socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        connection = null;
    }

    /**
     * Sends message from client to server
     *
     * @param msg a message to be sent
     * @return true in case of success; false otherwise
     */
    @SuppressWarnings("all")
    public boolean sendMsg(AbstractMessage msg) {
        try {
            out.writeObject(msg);
            out.flush();
            return true;
        } catch (IOException e) {
            logger.warn("Send message failed");
            return false;
        }
    }

    /**
     * Reads an object from the input stream
     *
     * @return A read object
     * @throws ClassNotFoundException in case if class of received object was not found
     * @throws IOException            in case of I/O errors
     */
    public AbstractMessage readObject() throws ClassNotFoundException, IOException {
        return (AbstractMessage) in.readObject();
    }

}
