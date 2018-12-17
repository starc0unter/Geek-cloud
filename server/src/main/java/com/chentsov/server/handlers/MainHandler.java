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
public class MainHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LogManager.getLogger(MainHandler.class.getSimpleName());

    private List<FileItem> cloudFileList = new ArrayList<>();
    private Map<Path, FileParts> fileParts = new ConcurrentHashMap<>();
    private String pathToStorage;

    MainHandler(String pathToStorage) {
        this.pathToStorage = pathToStorage;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            if (msg == null) return;

            logger.info("Message received");
            //todo: can use Visitor pattern here (+ double dispatch) to get rid of long instanceof check
            if (msg instanceof FileRequest) processFileRequest(ctx, (FileRequest) msg);
            else if (msg instanceof FileMessage) processFileMessage(ctx, (FileMessage) msg);
            else if (msg instanceof DeleteFileRequest) deleteFile(ctx, (DeleteFileRequest) msg);
            else if (msg instanceof RenameFileRequest) renameFile(ctx, (RenameFileRequest) msg);
            else if (msg instanceof FileListRequest) refreshFileList(ctx,
                    ((FileListRequest) msg).getCurrentCloudPath());
            else if (msg instanceof LogoutMessage) performLogOut(ctx, (LogoutMessage) msg);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    @SuppressWarnings("all")
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    @SuppressWarnings("all")
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

    private void refreshFileList(ChannelHandlerContext ctx, String stringPath) {
        String currentCloudPath = stringPath.isEmpty() ? pathToStorage : stringPath;
        boolean isRoot = Paths.get(currentCloudPath).equals(Paths.get(pathToStorage));
        FileItem.refreshFileList(cloudFileList, isRoot ? pathToStorage : currentCloudPath, isRoot);

        FileListResponse response = new FileListResponse(cloudFileList);
        logger.info("Sending refreshed client list");
        ctx.writeAndFlush(response);
    }

    /**
     * Processes a file request
     *
     * @param ctx ChannelHandlerContext that sends data to client
     * @param fr  FileRequest instance that holds info about requested file
     */
    private void processFileRequest(ChannelHandlerContext ctx, FileRequest fr) {
        List<Path> filePaths = new ArrayList<>();
        try {
            for (String stringPath : fr.getStringPaths()) {
                filePaths.addAll(Files.walk(Paths.get(stringPath)).collect(Collectors.toList()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        FileMessage.send(filePaths, pathToStorage, fr.getDestinationPath(), ctx::writeAndFlush, null);
    }

    /**
     * Receives files from cloud server.
     *
     * @param ctx a ChannelHandlerContext that maintains current pipeline
     * @param fm  received fileMessage
     * @throws IOException in case of i/o operations
     */
    private void processFileMessage(ChannelHandlerContext ctx, FileMessage fm) throws IOException {
        Path filePath = Paths.get(pathToStorage + File.separator + fm.getDestination());
        logger.info("Receiving file message: " + filePath);
        FileMessage.receive(fm, fileParts, () -> refreshFileList(ctx, fm.getDestination()));
    }

    private void deleteFile(ChannelHandlerContext ctx, DeleteFileRequest dfr) {
        List<FileItem> items = dfr.getItems();
        //getting info about parent of files to be deleted
        String parent = items.get(0).getFile().getParent();
        for (FileItem item : items) {
            item.remove();
        }
        refreshFileList(ctx, parent);
    }

    /**
     * Renames cloud file
     *
     * @param ctx a ChannelHandlerContext that maintains current pipeline
     * @param rfr received message
     */
    private void renameFile(ChannelHandlerContext ctx, RenameFileRequest rfr) {
        FileItem fileItem = rfr.getItem();
        fileItem.rename(rfr.getNewName());
        refreshFileList(ctx, fileItem.getFile().getParent());
    }

    private void performLogOut(ChannelHandlerContext ctx, LogoutMessage lm) {
        ctx.writeAndFlush(lm);
    }

}
