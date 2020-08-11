package com.chentsov.server.handlers;

import com.chentsov.common.messages.requests.AuthRequest;
import com.chentsov.common.messages.responses.AuthResponse;
import com.chentsov.server.dbService.DBService;
import com.chentsov.server.dbService.dataset.UsersDataSet;
import com.chentsov.server.util.HashHelper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static com.chentsov.server.util.HashHelper.getPasswordHash;

/**
 * @author Evgenii Chentsov
 */
public class AuthHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LogManager.getLogger(AuthHandler.class.getSimpleName());

    private static final String ROOT_PATH = "server/cloud_storage/";
    private final DBService dbService = DBService.getInstance();

    public AuthHandler() {
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object msg) throws Exception {
        try {
            if (msg == null) return;

            if (msg instanceof AuthRequest) {
                processAuth(context, (AuthRequest) msg);
                return;
            }
            context.fireChannelRead(msg);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private void processAuth(ChannelHandlerContext context, AuthRequest msg) throws IOException {
        logger.info("Auth request received from " + msg.login);
        String pathToStorage = ROOT_PATH + msg.login;
        Files.createDirectories(Paths.get(pathToStorage));

        if (!msg.isNewUser) {
            logger.info("Authorizing user: " + msg.login);
            boolean isAuthorized = checkCredentials(msg.login, msg.password);
            context.writeAndFlush(new AuthResponse(isAuthorized, pathToStorage));
            //if auth is successful, add the main request processor
            if (isAuthorized) {
                context.pipeline().addLast(new MainHandler(pathToStorage));
                logger.info("Auth complete: " + msg.login);
            }
        } else {
            logger.info("Creating new user: " + msg.login);
            String salt = HashHelper.generateSalt();
            boolean userCreated = dbService.addUser(msg.login, getPasswordHash(msg.password, salt), salt);
            context.writeAndFlush(new AuthResponse(userCreated));
        }
    }

    /**
     * Checks if presented credentials exist and correct
     *
     * @param username received username
     * @param password received password
     * @return true if credentials are correct and false otherwise
     */
    private boolean checkCredentials(String username, String password) {
        List<UsersDataSet> resultSet = dbService.get(username);
        if (resultSet.size() != 1) return false;

        UsersDataSet data = resultSet.get(0);
        return data.getPassword().equals(getPasswordHash(password, data.getSalt()));
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext context) {
        context.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        context.close();
    }

}
