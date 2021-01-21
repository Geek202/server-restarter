package me.geek.tom.serverrestarter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ServerRestarter implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            dispatcher.register(literal("restart")
                    .requires(s -> s.hasPermissionLevel(4))
                    .then(argument("reason", greedyString())
                            .executes(ctx -> {
                                shutdown(ctx, getString(ctx, "reason"));
                                return 0;
                            })
                    ).executes(ctx -> {
                        shutdown(ctx, "No reason specified");
                        return 0;
                    })
            );
        });
    }

    private void shutdown(CommandContext<ServerCommandSource> ctx, String reason) {
        ctx.getSource().sendFeedback(new LiteralText("Restarting server..."), true);
        MinecraftServer server = ctx.getSource().getMinecraftServer();
        server.stop(false);
        Path reasonPath = Paths.get(".restart_reason");
        try {
            Files.write(reasonPath,
                    reason.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LogManager.getLogger().error("Failed to save restart reason!", e);
        }

        Path configPath = Paths.get("restart_webhook.txt");
        if (Files.exists(configPath)) {
            try {
                List<String> lns = Files.readAllLines(configPath, Charset.defaultCharset());
                if (lns.size() != 0) {
                    String webhook = lns.get(0);
                    URL url = new URL(webhook);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("User-Agent", "DiscordBot (https://github.com/Geek202, v1)");
                    Gson gson = new Gson();
                    JsonObject msg = new JsonObject();
                    msg.addProperty("content", "Restart requested: " + reason);
                    msg.addProperty("username", "Server");
                    String payload = gson.toJson(msg);
                    OutputStream os = conn.getOutputStream();
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                    os.close();
                    int code = conn.getResponseCode();
                    if (code < 200 || code >= 300) {
                        throw new IOException(String.valueOf(code));
                    }
                }
            } catch (Exception e) {
                LogManager.getLogger().error("Failed to send webhook", e);
            }
        }
    }
}