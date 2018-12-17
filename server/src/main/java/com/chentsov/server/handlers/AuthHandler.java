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
    private DBService dbService = DBService.getInstance();

    public AuthHandler() {
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            if (msg == null) return;

            if (msg instanceof AuthRequest) {
                AuthRequest ar = (AuthRequest) msg;
                logger.info("Auth request received from " + ar.getLogin());
                String pathToStorage = ROOT_PATH + ar.getLogin();
                Files.createDirectories(Paths.get(pathToStorage));

                if (!ar.isNewUser()) {
                    logger.info("Authorizing user: " + ar.getLogin());
                    boolean isAuthorized = checkCredentials(ar.getLogin(), ar.getPassword());
                    ctx.writeAndFlush(new AuthResponse(isAuthorized, pathToStorage));
                    //if auth is successful, add the main request processor
                    if (isAuthorized) {
                        ctx.pipeline().addLast(new MainHandler(pathToStorage));
                        logger.info("Auth complete: " + ar.getLogin());
                    }
                } else {
                    logger.info("Creating new user: " + ar.getLogin());
                    String salt = HashHelper.generateSalt();
                    boolean userCreated = dbService.addUser(ar.getLogin(), getPasswordHash(ar.getPassword(), salt), salt);
                    ctx.writeAndFlush(new AuthResponse(userCreated));
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        } finally {
            ReferenceCountUtil.release(msg);
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
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

}
