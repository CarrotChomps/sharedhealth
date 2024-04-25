package dev.neddslayer.sharedhealth;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.neddslayer.sharedhealth.components.SharedExhaustionComponent;
import dev.neddslayer.sharedhealth.components.SharedHealthComponent;
import dev.neddslayer.sharedhealth.components.SharedHungerComponent;
import dev.neddslayer.sharedhealth.components.SharedSaturationComponent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.integrated.IntegratedPlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static dev.neddslayer.sharedhealth.components.SharedComponentsInitializer.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class SharedHealth implements ModInitializer {

    public static final GameRules.Key<GameRules.BooleanRule> SYNC_HEALTH =
            GameRuleRegistry.register("shareHealth", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));
    public static final GameRules.Key<GameRules.BooleanRule> LIMIT_HEALTH =
            GameRuleRegistry.register("limitHealth", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));
    private static boolean lastHealthValue = true;

    private List<PlayerPair> pairs = new ArrayList<>();

    /**
     * Runs the mod initializer.
     */
    @Override
    public void onInitialize() {

        CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, environment) -> dispatcher.register(literal("test")
                .then(argument("value", StringArgumentType.string()))
                .executes(context -> {
                    String input = StringArgumentType.getString(context, "value");
                    if (input.equalsIgnoreCase("list")) {
                        for (PlayerPair pair : pairs) {
                            context.getSource()
                                    .sendFeedback(() -> Text.literal(pair.getPlayer1().getName() + " is paired with " + pair.getPlayer2().getName()), false);
                        }
                    }

                    context.getSource().sendFeedback(() -> Text.literal("No arguments"), false);
                    return -1;
                }))));

        CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, environment) -> dispatcher.register(literal("resetplayers")
                .then(argument("player1", StringArgumentType.string()))
                .then(argument("player2", StringArgumentType.string()))
                .executes(context -> {
                    ServerPlayerEntity user = context.getSource().getPlayerOrThrow();
                    MinecraftServer server = user.getServer();
                    // link the players together
                    final String target1 = StringArgumentType.getString(context, "player1");
                    final String target2 = StringArgumentType.getString(context, "player2");

                    if (server == null) {
                        context.getSource().sendFeedback(() -> Text.literal("Server doesn't exist?"), false);
                        return -1;
                    }

                    PlayerEntity player1 = server.getPlayerManager().getPlayer(target1);
                    PlayerEntity player2 = server.getPlayerManager().getPlayer(target2);

                    if (player1 == null || player2 == null) {
                        user.sendMessage(Text.literal("Player doesn't exist"));
                        return -1;
                    }

                    PlayerPair pair = new PlayerPair(player1, player2);
                    pairs.add(pair);
                    context.getSource().sendFeedback(() -> Text.literal("Linked " + target1 + " with " + target2), false);

                    return 1;
                }))));

        ServerTickEvents.END_WORLD_TICK.register((world -> {
            boolean currentHealthValue = world.getGameRules().getBoolean(SYNC_HEALTH);
            boolean limitHealthValue = world.getGameRules().getBoolean(LIMIT_HEALTH);

            if (currentHealthValue != lastHealthValue && currentHealthValue) {
                world.getPlayers().forEach(player -> player.sendMessageToClient(Text.translatable("gamerule.shareHealth.enabled").formatted(Formatting.GREEN, Formatting.BOLD), false));
                lastHealthValue = true;
            } else if (currentHealthValue != lastHealthValue) {
                world.getPlayers().forEach(player -> player.sendMessageToClient(Text.translatable("gamerule.shareHealth.disabled").formatted(Formatting.RED, Formatting.BOLD), false));
                lastHealthValue = false;
            }

            if (world.getGameRules().getBoolean(SYNC_HEALTH)) {
                SharedHealthComponent component = SHARED_HEALTH.get(world.getScoreboard());
                if (component.getHealth() > 20 && limitHealthValue) component.setHealth(20);
                float finalKnownHealth = component.getHealth();
                world.getPlayers().forEach(playerEntity -> {
                    try {
                        float currentHealth = playerEntity.getHealth();

                        if (currentHealth > finalKnownHealth) {
                            playerEntity.damage(world.getDamageSources().genericKill(), currentHealth - finalKnownHealth);
                        } else if (currentHealth < finalKnownHealth) {
                            playerEntity.heal(finalKnownHealth - currentHealth);
                        }
                    } catch (Exception e) {
                        System.err.println(e.getMessage());
                    }
                });
            }
        }));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> handler.player.setHealth(SHARED_HEALTH.get(handler.player.getWorld().getScoreboard()).getHealth()));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> newPlayer.setHealth(SHARED_HEALTH.get(newPlayer.getWorld().getScoreboard()).getHealth()));
    }
}
