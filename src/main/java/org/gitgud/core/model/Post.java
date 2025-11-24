package org.gitgud.core.model;

import java.util.List;

public abstract class Post {
    int x, y;
    int width, height;
    List<String> lines;

    void setDimensions(int width, int height) {
        this.width = width + 4; // Pad content left and right by 1
        this.height = height + 2;
    }

    void setDimensionsFromLines() {
        if (lines == null) return;
        int maxWidth = lines.stream()
                .mapToInt(String::length)
                .max().orElse(0);
        int maxHeight = lines.size();
        setDimensions(maxWidth, maxHeight);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public List<String> getLines() {
        return lines;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    abstract public void update(double deltaTIme);
}
