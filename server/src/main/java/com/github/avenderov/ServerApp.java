package com.github.avenderov;

import ratpack.server.BaseDir;
import ratpack.server.RatpackServer;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

public class ServerApp {

    private static final Map<String, Path> FILES = new HashMap<>();

    public static void main(String[] args) throws Exception {
        IntStream.range(0, 5).forEach(i -> {
            try {
                var file = File.createTempFile("tmp", "test_" + i).toPath();
                Files.writeString(file, "test file " + i);

                FILES.put(Integer.toString(i), file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        RatpackServer.start(server ->
            server
                .serverConfig(c -> c
                    .baseDir(BaseDir.find()))
                .handlers(c -> c
                    .get("files/:id", ctx -> {
                        var id = ctx.getPathTokens().get("id");

                        var file = FILES.get(id);
                        if (file == null) {
                            ctx.notFound();
                        } else {
                            ctx.render(file);
                        }
                    })));
    }
}
