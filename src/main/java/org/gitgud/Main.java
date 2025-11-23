package org.gitgud;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.util.*;

public class Main {

    // --- 1. DATA MODEL ---

    static class Post {
        int x, y;           // World coordinates (Top-Left)
        int width, height;
        List<String> lines; // The wrapped text content

        public Post(String rawText) {
            this.lines = wrapTextIdeally(rawText);
            this.width = lines.stream().mapToInt(String::length).max().orElse(0) + 2;
            this.height = lines.size() + 2;
        }

        private List<String> wrapTextIdeally(String text) {
            double visualBias = 1.8;
            int targetWidth = (int) Math.sqrt(text.length() * visualBias);
            if (targetWidth < 8) targetWidth = 8;

            List<String> wrapped = new ArrayList<>();
            String[] words = text.split("\\s+");
            StringBuilder currentLine = new StringBuilder();

            for (String word : words) {
                if (currentLine.length() + 1 + word.length() > targetWidth) {
                    if (currentLine.length() > 0) {
                        wrapped.add(currentLine.toString());
                        currentLine = new StringBuilder();
                    }
                }
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            }
            if (currentLine.length() > 0) wrapped.add(currentLine.toString());

            int maxWidth = 0;
            for(String s : wrapped) maxWidth = Math.max(maxWidth, s.length());
            for(int i=0; i<wrapped.size(); i++) {
                String s = wrapped.get(i);
                while(s.length() < maxWidth) s += " ";
                wrapped.set(i, s);
            }
            return wrapped;
        }
    }

    // --- 2. ALGORITHMS ---

    static class SpiralIterator implements Iterator<int[]> {
        int x = 0, y = 0;
        int dx = 0, dy = -1;
        int stepCount = 0;
        int stepLimit = 1;
        int turnCount = 0;

        @Override
        public boolean hasNext() { return true; }

        @Override
        public int[] next() {
            int[] current = {x, y};
            x += dx;
            y += dy;
            stepCount++;

            if (stepCount >= stepLimit) {
                stepCount = 0;
                turnCount++;
                int temp = dx; dx = -dy; dy = temp;
                if (turnCount % 2 == 0) stepLimit++;
            }
            return current;
        }
    }

    static void packPosts(List<Post> posts) {
        posts.sort((p1, p2) -> (p2.width * p2.height) - (p1.width * p1.height));
        List<Post> placed = new ArrayList<>();
        int padding = 1;

        for (Post p : posts) {
            SpiralIterator spiral = new SpiralIterator();
            while (true) {
                int[] coord = spiral.next();
                int potentialX = coord[0] - (p.width / 2);
                int potentialY = coord[1] - (p.height / 2);

                boolean collides = false;
                for (Post existing : placed) {
                    if (potentialX < existing.x + existing.width + padding &&
                            potentialX + p.width + padding > existing.x &&
                            potentialY < existing.y + existing.height + padding &&
                            potentialY + p.height + padding > existing.y) {
                        collides = true;
                        break;
                    }
                }

                if (!collides) {
                    p.x = potentialX;
                    p.y = potentialY;
                    placed.add(p);
                    break;
                }
                if (Math.abs(coord[0]) > 5000) break;
            }
        }
    }

    // --- 3. RENDERER & ENGINE ---

    static volatile int camX = 0;
    static volatile int camY = 0;
    static volatile boolean running = true;

    // Double Buffering State
    static char[][] currentScreen; // What is currently visible on terminal
    static char[][] nextBuffer;    // What we are calculating for the next frame
    static int termWidth = 0;
    static int termHeight = 0;

    public static void main(String[] args) throws IOException, InterruptedException {
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        terminal.enterRawMode();
        terminal.puts(InfoCmp.Capability.cursor_invisible);
        terminal.puts(InfoCmp.Capability.clear_screen);

        NonBlockingReader reader = terminal.reader();

        // --- Data Setup ---
        List<String> rawMessages = Arrays.asList(
                "Hello World", "Double Buffering", "Diff Rendering",
                "WASD to move", "No flickering", "Java + JLine",
                "The quick brown fox jumps over the lazy dog.",
                "Box 1", "Box 2", "Box 3", "Box 4", "Box 5",
                "Center", "Optimization is key",
                "This text box is intentionally long to test the wrapping logic."
        );

        List<Post> wall = new ArrayList<>();
        for (String msg : rawMessages) wall.add(new Post(msg));
        for(int i=0; i<2; i++) {
            for(String msg : rawMessages) wall.add(new Post(msg + " [" + i + "]"));
        }

        System.out.print("Packing...");
        packPosts(wall);

        // --- INPUT THREAD ---
        Thread inputThread = new Thread(() -> {
            try {
                while (running) {
                    int input = reader.read();

                    if (input == 'q') {
                        running = false;
                        break;
                    }

                    int speed = 1;
                    if (Character.isUpperCase(input)) speed = 3;
                    input = Character.toLowerCase(input);

                    if (input == 'w') camY -= speed;
                    if (input == 's') camY += speed;
                    if (input == 'a') camX -= 2 * speed;
                    if (input == 'd') camX += 2 * speed;
                    if (input == 'r') { camX = 0; camY = 0; }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        inputThread.start();

        // --- MAIN RENDER LOOP ---
        while (running) {
            long startTime = System.currentTimeMillis();

            render(terminal, wall);

            long usedTime = System.currentTimeMillis() - startTime;
            long sleepTime = 33 - usedTime;
            if (sleepTime > 0) Thread.sleep(sleepTime);
        }

        terminal.puts(InfoCmp.Capability.cursor_visible);
        terminal.close();
        System.exit(0);
    }

    private static void render(Terminal terminal, List<Post> posts) {
        int width = terminal.getWidth();
        int height = terminal.getHeight();

        // Handle Resize or Init
        if (width != termWidth || height != termHeight) {
            termWidth = width;
            termHeight = height;
            currentScreen = new char[height][width];
            nextBuffer = new char[height][width];

            // Initialize with spaces to avoid null char issues
            for (char[] row : currentScreen) Arrays.fill(row, ' ');

            terminal.puts(InfoCmp.Capability.clear_screen);
        }

        // 1. Draw everything to the calculation buffer (nextBuffer)
        // Clear buffer with background
        for (char[] row : nextBuffer) Arrays.fill(row, ' ');

        // Grid dots
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                if ((r + camY) % 10 == 0 && (c + camX) % 20 == 0) {
                    nextBuffer[r][c] = '·';
                }
            }
        }

        // Draw Posts to buffer
        for (Post p : posts) {
            if (p.x + p.width < camX || p.x > camX + width ||
                    p.y + p.height < camY || p.y > camY + height) {
                continue;
            }
            drawBoxToBuffer(nextBuffer, p, width, height);
        }

        // UI
        String coords = " [WASD] Move | [Shift] Fast | [Q] Quit | Pos: " + camX + "," + camY + " ";
        for(int i=0; i<coords.length() && i < width; i++) nextBuffer[0][i] = coords.charAt(i);

        // 2. Diff Rendering Loop (The SnakeZ approach)
        boolean screenChanged = false;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // CRITICAL: Avoid writing to the very last character of the screen.
                // Writing to (height-1, width-1) often forces a scroll on many terminals.
                if (y == height - 1 && x == width - 1) continue;

                if (nextBuffer[y][x] != currentScreen[y][x]) {
                    // Move cursor to exact position
                    terminal.puts(InfoCmp.Capability.cursor_address, y, x);

                    // Print the character
                    // We use terminal.writer() to ensure it shares the stream/buffer correctly
                    terminal.writer().print(nextBuffer[y][x]);

                    // Update our known state
                    currentScreen[y][x] = nextBuffer[y][x];
                    screenChanged = true;
                }
            }
        }

        if (screenChanged) {
            terminal.flush(); // Flush the batch of updates
        }
    }

    private static void drawBoxToBuffer(char[][] buffer, Post p, int screenW, int screenH) {
        int screenX = p.x - camX;
        int screenY = p.y - camY;

        char H_LINE = '─';
        char V_LINE = '│';
        char TL = '┌', TR = '┐', BL = '└', BR = '┘';

        for (int y = 0; y < p.height; y++) {
            int drawY = screenY + y;
            if (drawY < 0 || drawY >= screenH) continue;

            for (int x = 0; x < p.width; x++) {
                int drawX = screenX + x;
                if (drawX < 0 || drawX >= screenW) continue;

                char c = ' ';
                if (y == 0) c = (x == 0) ? TL : (x == p.width - 1 ? TR : H_LINE);
                else if (y == p.height - 1) c = (x == 0) ? BL : (x == p.width - 1 ? BR : H_LINE);
                else {
                    if (x == 0 || x == p.width - 1) c = V_LINE;
                    else {
                        int textRow = y - 1;
                        if (textRow < p.lines.size()) {
                            String line = p.lines.get(textRow);
                            int textCol = x - 1;
                            if (textCol < line.length()) c = line.charAt(textCol);
                        }
                    }
                }
                buffer[drawY][drawX] = c;
            }
        }
    }
}