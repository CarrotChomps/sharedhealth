package dev.neddslayer.sharedhealth;

import net.minecraft.entity.player.PlayerEntity;

public class PlayerPair {
    private final PlayerEntity player1;
    private final PlayerEntity player2;

    public PlayerPair(PlayerEntity player1, PlayerEntity player2) {
        this.player1 = player1;
        this.player2 = player2;
    }

    public PlayerEntity getPlayer1() {
        return player1;
    }

    public PlayerEntity getPlayer2() {
        return player2;
    }
}
