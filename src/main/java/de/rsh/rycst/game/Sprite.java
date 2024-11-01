package de.rsh.rycst.game;

import java.awt.image.BufferedImage;

public class Sprite<Vector> {
    private Vector pos;
    private BufferedImage texture;
    public Sprite(Vector pos, BufferedImage tex) {
        this.pos = pos;
        this.texture = tex;
    }
    public Vector getPos() {
        return pos;
    }
    public BufferedImage getTexture() {
        return texture;
    }
}
