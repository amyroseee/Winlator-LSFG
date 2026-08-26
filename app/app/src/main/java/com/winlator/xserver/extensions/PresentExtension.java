package com.winlator.xserver.extensions;

import static com.winlator.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import android.util.SparseArray;

import com.winlator.renderer.GPUImage;
import com.winlator.renderer.Texture;
import com.winlator.xconnector.XInputStream;
import com.winlator.xconnector.XOutputStream;
import com.winlator.xconnector.XStreamLock;
import com.winlator.core.Bitmask;
import com.winlator.xserver.Drawable;
import com.winlator.xserver.Pixmap;
import com.winlator.xserver.Window;
import com.winlator.xserver.WindowManager;
import com.winlator.xserver.XClient;
import com.winlator.xserver.XLock;
import com.winlator.xserver.XServer;
import com.winlator.xserver.errors.BadImplementation;
import com.winlator.xserver.errors.BadMatch;
import com.winlator.xserver.errors.BadWindow;
import com.winlator.xserver.errors.XRequestError;
import com.winlator.xserver.events.PresentCompleteNotify;
import com.winlator.xserver.events.PresentIdleNotify;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class PresentExtension extends Extension {
    public static final byte MAJOR_VERSION = 1;
    public static final byte MINOR_VERSION = 0;
    private static final int FAKE_INTERVAL = 1000000 / 60;

    public enum Kind {PIXMAP, MSC_NOTIFY}
    public enum Mode {COPY, FLIP, SKIP}
    private final SparseArray<Event> events = new SparseArray<>();
    private SyncExtension syncExtension;
    private static final long FIRE_EARLY_NS = 700_000L;
    private volatile int frameRateLimit;
    private final AtomicLong limiterGeneration = new AtomicLong();
    private final ConcurrentHashMap<Integer, WindowTiming> windowTimings = new ConcurrentHashMap<>();
    private final DelayQueue<PendingIdle> pendingIdles = new DelayQueue<>();
    private volatile Thread pacerThread;
    private volatile boolean closed;
    private final WindowManager.OnWindowModificationListener windowListener;

    private static class WindowTiming {
        long nextIdleNs;
    }

    private static class PendingIdle implements Delayed {
        final Window window;
        final Pixmap pixmap;
        final int serial;
        final int idleFence;
        final long targetNs;
        final long generation;
        final AtomicBoolean delivered = new AtomicBoolean();

        PendingIdle(Window window, Pixmap pixmap, int serial, int idleFence, long targetNs, long generation) {
            this.window = window;
            this.pixmap = pixmap;
            this.serial = serial;
            this.idleFence = idleFence;
            this.targetNs = targetNs;
            this.generation = generation;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(targetNs - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(targetNs, ((PendingIdle)other).targetNs);
        }
    }

    private static abstract class ClientOpcodes {
        private static final byte QUERY_VERSION = 0;
        private static final byte PRESENT_PIXMAP = 1;
        private static final byte SELECT_INPUT = 3;
    }

    private static class Event {
        private Window window;
        private XClient client;
        private int id;
        private Bitmask mask;
    }

    public PresentExtension(XServer xServer, byte majorOpcode) {
        super(xServer, majorOpcode);
        windowListener = new WindowManager.OnWindowModificationListener() {
            @Override
            public void onUnmapWindow(Window window) {
                windowTimings.remove(window.id);
            }
        };
        xServer.windowManager.addOnWindowModificationListener(windowListener);
    }

    @Override
    public String getName() {
        return "Present";
    }

    private void sendIdleNotify(Window window, Pixmap pixmap, int serial, int idleFence) {
        if (idleFence != 0) syncExtension.setTriggered(idleFence);

        synchronized (events) {
            for (int i = 0; i < events.size(); i++) {
                Event event = events.valueAt(i);
                if (event.window == window && event.mask.isSet(PresentIdleNotify.getEventMask())) {
                    event.client.sendEvent(new PresentIdleNotify(this, event.id, window, pixmap, serial, idleFence));
                }
            }
        }
    }

    /**
     * Changes Present backpressure immediately. Zero disables pacing; positive values are kept in
     * the user-facing 10-200 FPS range. Changing the target releases already queued buffers so the
     * next Present starts a fresh cadence instead of inheriting an interval from the old target.
     */
    public void setFrameRateLimit(int limit) {
        int normalized = limit <= 0 ? 0 : Math.max(10, Math.min(200, limit));
        if (frameRateLimit == normalized) return;
        frameRateLimit = normalized;
        limiterGeneration.incrementAndGet();
        windowTimings.clear();
        flushPendingIdles();
    }

    public int getFrameRateLimit() {
        return frameRateLimit;
    }

    private void emitIdleNotify(Window window, Pixmap pixmap, int serial, int idleFence) {
        int targetFps = frameRateLimit;
        if (targetFps == 0 || closed) {
            sendIdleNotify(window, pixmap, serial, idleFence);
            return;
        }

        long generation = limiterGeneration.get();
        long frameNs = 1_000_000_000L / targetFps;
        long now = System.nanoTime();
        WindowTiming timing = windowTimings.computeIfAbsent(window.id, ignored -> new WindowTiming());
        long targetNs;
        synchronized (timing) {
            if (timing.nextIdleNs <= now - frameNs) timing.nextIdleNs = now + frameNs;
            else timing.nextIdleNs += frameNs;
            targetNs = Math.max(now, timing.nextIdleNs - FIRE_EARLY_NS);
        }

        PendingIdle pending = new PendingIdle(window, pixmap, serial, idleFence, targetNs, generation);
        if (generation != limiterGeneration.get() || frameRateLimit == 0) {
            deliverIdle(pending);
            return;
        }
        ensurePacerThread();
        pendingIdles.offer(pending);
    }

    private synchronized void ensurePacerThread() {
        if (closed || pacerThread != null) return;
        pacerThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    PendingIdle pending = pendingIdles.take();
                    deliverIdle(pending);
                }
            }
            catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "PresentIdlePacer");
        pacerThread.setDaemon(true);
        pacerThread.start();
    }

    private void deliverIdle(PendingIdle pending) {
        if (pending.delivered.compareAndSet(false, true))
            sendIdleNotify(pending.window, pending.pixmap, pending.serial, pending.idleFence);
    }

    private void flushPendingIdles() {
        for (PendingIdle pending : pendingIdles) {
            if (pendingIdles.remove(pending)) deliverIdle(pending);
        }
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        limiterGeneration.incrementAndGet();
        Thread thread = pacerThread;
        pacerThread = null;
        if (thread != null) thread.interrupt();
        pendingIdles.clear();
        windowTimings.clear();
        xServer.windowManager.removeOnWindowModificationListener(windowListener);
    }

    private void sendCompleteNotify(Window window, int serial, Kind kind, Mode mode, long ust, long msc) {
        if (ust == 0 && msc == 0) {
            ust = System.nanoTime() / 1000;
            msc = ust / FAKE_INTERVAL;
        }

        synchronized (events) {
            for (int i = 0; i < events.size(); i++) {
                Event event = events.valueAt(i);
                if (event.window == window && event.mask.isSet(PresentCompleteNotify.getEventMask())) {
                    event.client.sendEvent(new PresentCompleteNotify(this, event.id, window, serial, kind, mode, ust, msc));
                }
            }
        }
    }

    private void queryVersion(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        inputStream.skip(8);

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeInt(MAJOR_VERSION);
            outputStream.writeInt(MINOR_VERSION);
            outputStream.writePad(16);
        }
    }

    private void presentPixmap(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int windowId = inputStream.readInt();
        int pixmapId = inputStream.readInt();
        int serial = inputStream.readInt();
        inputStream.skip(8);
        short xOff = inputStream.readShort();
        short yOff = inputStream.readShort();
        inputStream.skip(8);
        int idleFence = inputStream.readInt();
        inputStream.skip(client.getRemainingRequestLength());

        Window window = xServer.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);

        Pixmap pixmap = xServer.pixmapManager.getPixmap(pixmapId);

        Drawable content = window.getContent();
        if (pixmap != null && content.visual.depth != pixmap.drawable.visual.depth) throw new BadMatch();

        synchronized (content.renderLock) {
            if (pixmap != null) {
                content.copyArea((short)0, (short)0, xOff, yOff, pixmap.drawable.width, pixmap.drawable.height, pixmap.drawable);
                emitIdleNotify(window, pixmap, serial, idleFence);
            }
            else content.forceUpdate();
            sendCompleteNotify(window, serial, Kind.PIXMAP, Mode.COPY, 0, 0);
        }
    }

    private void selectInput(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int eventId = inputStream.readInt();
        int windowId = inputStream.readInt();
        Bitmask mask = new Bitmask(inputStream.readInt());

        Window window = xServer.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);

        Drawable content = window.getContent();
        final Texture texture = content.getTexture();

        if (!(texture instanceof GPUImage)) {
            xServer.getRenderer().xServerView.queueEvent(texture::destroy);
            content.setTexture(new GPUImage(content));
        }

        if (eventId > 0) {
            synchronized (events) {
                Event event = events.get(eventId);
                if (event != null) {
                    if (event.window != window || event.client != client) throw new BadMatch();

                    if (!mask.isEmpty()) {
                        event.mask = mask;
                    }
                    else events.remove(eventId);
                }
                else {
                    event = new Event();
                    event.id = eventId;
                    event.window = window;
                    event.client = client;
                    event.mask = mask;
                    events.put(eventId, event);
                }
            }
        }
    }

    @Override
    public void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int opcode = client.getRequestData();
        if (syncExtension == null) syncExtension = (SyncExtension)xServer.getExtensionByName("SYNC");

        switch (opcode) {
            case ClientOpcodes.QUERY_VERSION :
                queryVersion(client, inputStream, outputStream);
                break;
            case ClientOpcodes.PRESENT_PIXMAP:
                try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.PIXMAP_MANAGER)) {
                    presentPixmap(client, inputStream, outputStream);
                }
                break;
            case ClientOpcodes.SELECT_INPUT:
                try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
                    selectInput(client, inputStream, outputStream);
                }
                break;
            default:
                throw new BadImplementation();
        }
    }
}
