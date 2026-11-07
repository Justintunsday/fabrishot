/*
 * MIT License
 *
 * Copyright (c) 2021 Ramid Khan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.ramidzkh.fabrishot.capture;

import me.ramidzkh.fabrishot.config.Config;
import me.ramidzkh.fabrishot.config.FileFormat;
import me.ramidzkh.fabrishot.event.FramebufferCaptureCallback;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

public class CaptureTask {

    private static final long UHD_WAIT_NANOS = Duration.ofMinutes(1).toNanos();

    private final Minecraft client = Minecraft.getInstance();
    private final ClientLevel level = client.level;
    private final Path file;
    private final int width = Config.CAPTURE_WIDTH;
    private final int height = Config.CAPTURE_HEIGHT;
    private final int frameDelay = Config.CAPTURE_DELAY;
    private final boolean hideHud = Config.HIDE_HUD;
    private final boolean saveFile = Config.SAVE_FILE;
    private final FileFormat fileFormat = Config.CAPTURE_FILE_FORMAT;
    private final boolean uhd = width == 3840 && height == 2160;

    private boolean hidHud;
    private int framesRendered;
    private long firstFrameNanos;

    public CaptureTask(Path file) {
        this.file = file;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean onRenderTick() {
        var framebuffer = client.getMainRenderTarget();
        if (client.level != level || framebuffer.width != width || framebuffer.height != height) {
            return true;
        }

        if (framesRendered++ == 0) {
            if (uhd) {
                firstFrameNanos = System.nanoTime();
                client.gui.getChat().addMessage(Component.translatable("fabrishot.capture.uhd_wait"));
            } else {
                hideHud();
            }
            return false;
        }
        if (framesRendered <= frameDelay || (uhd && System.nanoTime() - firstFrameNanos < UHD_WAIT_NANOS)) {
            return false;
        }

        // Keep the HUD available during the wait, then render a frame without it if requested.
        if (uhd && hideHud()) {
            return false;
        }

        Screenshot.takeScreenshot(framebuffer, image -> {
            Util.ioPool().execute(() -> {
                try (image) {
                    FramebufferCaptureCallback.EVENT.invoker().onCapture(image);

                    if (saveFile) {
                        FramebufferWriter.write(image, file, fileFormat);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });
        return true;
    }

    private boolean hideHud() {
        if (!hideHud || client.options.hideGui) {
            return false;
        }
        client.options.hideGui = true;
        hidHud = true;
        return true;
    }

    public void restoreHud() {
        if (hidHud) {
            client.options.hideGui = false;
        }
    }
}
