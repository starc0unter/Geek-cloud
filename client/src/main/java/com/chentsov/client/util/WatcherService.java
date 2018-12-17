package com.chentsov.client.util;

import com.chentsov.client.controllers.CloudController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * A class that represent a watcher that looks for the changes in the directories
 */
public class WatcherService {

    private static final Logger logger = LogManager.getLogger(WatcherService.class.getSimpleName());

    private WatchService watcher;
    private final Map<WatchKey, Path> keys;
    private boolean trace = false;
    private CloudController controller;
    private ExecutorService executor;

    /**
     * Starts a thread to listen to watcher events
     */
    public void start() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        });
        executor.execute(this::process);
    }

    /**
     * Attempts to stop event processor thread
     */
    public void stop() {
        executor.shutdownNow();
    }

    public WatcherService(Path dir, CloudController controller) {
        this.keys = new HashMap<>();
        this.controller = controller;
        try {
            watcher = FileSystems.getDefault().newWatchService();
            registerDir(dir.toString());
            // enable trace after initial registration
            trace = true;
            this.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Registers chosen dir to be watched
     *
     * @param stringPath a path to the directory
     */
    void registerDir(String stringPath) {
        try {
            Path dir = Paths.get(stringPath);
            WatchEvent.Kind<?>[] actionsToWatch = {ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY};
            WatchKey key = dir.register(watcher, actionsToWatch/*, ExtendedWatchEventModifier.FILE_TREE*/);
            if (trace) {
                Path prev = keys.get(key);
                if (prev == null) {
                    logger.info("register: " + dir);
                } else {
                    if (!dir.equals(prev)) {
                        logger.info("update: " + prev + " -> " + dir);
                    }
                }
            }
            keys.put(key, dir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Processes event related to registered directories
     */
    private void process() {
        while (true) {
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException | ClosedWatchServiceException x) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent event : key.pollEvents()) {
                if (event.kind() == OVERFLOW) {
                    continue;
                }

                logger.info("Event type: " + event.kind().name() + ", child: " + keys.get(key));
                controller.refreshLocalFiles();
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                logger.info("remove: " + keys.get(key));
                key.cancel();
                keys.remove(key);
                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

}
