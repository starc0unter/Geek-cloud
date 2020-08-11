package com.chentsov.server.handlers;


import com.chentsov.common.FileItem;
import com.chentsov.common.FileParts;
import com.chentsov.common.messages.requests.*;
import com.chentsov.common.messages.responses.FileMessage;
import com.chentsov.common.messages.responses.FileListResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author Evgenii Chentsov
 */
public final class MainHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LogManager.getLogger(MainHandler.class.getSimpleName());

    private final List<FileItem> cloudFileList = new ArrayList<>();
    private final Map<Path, FileParts> fileParts = new ConcurrentHashMap<>();
    private final String pathToStorage;

    MainHandler(String pathToStorage) {
        this.pathToStorage = pathToStorage;
    }

    @Override
    public void channelRead(ChannelHandlerContext handlerContext, Object msg) throws Exception {
        try {
            if (msg == null) return;

            logger.info("Message received");
            //todo: can use Visitor pattern here (+ double dispatch) to get rid of long instanceof check
            if (msg instanceof FileRequest) processFileRequest(handlerContext, (FileRequest) msg);
            else if (msg instanceof FileMessage) processFileMessage(handlerContext, (FileMessage) msg);
            else if (msg instanceof DeleteFileRequest) deleteFile(handlerContext, (DeleteFileRequest) msg);
            else if (msg instanceof RenameFileRequest) renameFile(handlerContext, (RenameFileRequest) msg);
            else if (msg instanceof FileListRequest) refreshFileList(handlerContext,
                    ((FileListRequest) msg).currentCloudPath);
            else if (msg instanceof LogoutMessage) performLogOut(handlerContext, (LogoutMessage) msg);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    @SuppressWarnings("all")
    public void channelReadComplete(ChannelHandlerContext context) throws Exception {
        context.flush();
    }

    @Override
    @SuppressWarnings("all")
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
        context.close();
    }

    private void refreshFileList(ChannelHandlerContext context, String stringPath) {
        String currentCloudPath = stringPath.isEmpty() ? pathToStorage : stringPath;
        boolean isRoot = Paths.get(currentCloudPath).equals(Paths.get(pathToStorage));
        FileItem.refreshFileList(cloudFileList, isRoot ? pathToStorage : currentCloudPath, isRoot);

        FileListResponse response = new FileListResponse(cloudFileList);
        logger.info("Sending refreshed client list");
        context.writeAndFlush(response);
    }

    /**
     * Processes a file request
     *
     * @param context ChannelHandlerContext that sends data to client
     * @param request  FileRequest instance that holds info about requested file
     */
    private void processFileRequest(ChannelHandlerContext context, FileRequest request) {
        List<Path> filePaths = new ArrayList<>();
        try {
            for (String stringPath : request.getStringPaths()) {
                filePaths.addAll(Files.walk(Paths.get(stringPath)).collect(Collectors.toList()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        FileMessage.send(filePaths, pathToStorage, request.getDestinationPath(), context::writeAndFlush, null);
    }

    /**
     * Receives files from cloud server.
     *
     * @param context a ChannelHandlerContext that maintains current pipeline
     * @param msg  received fileMessage
     * @throws IOException in case of i/o operations
     */
    private void processFileMessage(ChannelHandlerContext context, FileMessage msg) throws IOException {
        Path filePath = Paths.get(pathToStorage + File.separator + msg.getDestination());
        logger.info("Receiving file message: " + filePath);
        FileMessage.receive(msg, fileParts, () -> refreshFileList(context, msg.getDestination()));
    }

    private void deleteFile(ChannelHandlerContext context, DeleteFileRequest request) {
        List<FileItem> items = request.items;
        //getting info about parent of files to be deleted
        String parent = items.get(0).getFile().getParent();
        for (FileItem item : items) {
            item.remove();
        }
        refreshFileList(context, parent);
    }

    /**
     * Renames cloud file
     *
     * @param context a ChannelHandlerContext that maintains current pipeline
     * @param request received message
     */
    private void renameFile(ChannelHandlerContext context, RenameFileRequest request) {
        FileItem fileItem = request.item;
        fileItem.rename(request.newName);
        refreshFileList(context, fileItem.getFile().getParent());
    }

    private void performLogOut(ChannelHandlerContext context, LogoutMessage message) {
        context.writeAndFlush(message);
    }

}
