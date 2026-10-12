package com.hecker.motionsense.wallpapers;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class OsloGestureClientTest {
    private static final String SERVICE = "com.google.oslo.service.serviceinterface.aidl.IOsloService";
    private static final String LISTENER = "com.google.oslo.service.serviceinterface.aidl.IOsloServiceGestureListener";
    private static final ComponentName COMPONENT = new ComponentName("com.google.oslo",
            "com.google.oslo.service.OsloService");

    private static class Events implements OsloGestureClient.Callback {
        final List<String> values = new ArrayList<>();
        public void onPresence() { values.add("presence"); }
        public void onReach() { values.add("reach"); }
        public void onFlick(int direction) { values.add("flick:" + direction); }
        public void onSwipe(int direction) { values.add("swipe:" + direction); }
    }

    private static class OsloBinder extends Binder {
        final Map<Integer, IBinder> listeners = new HashMap<>();
        final List<IBinder> removed = new ArrayList<>();
        int denyAt;
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            data.enforceInterface(SERVICE);
            assertNotNull(data.readStrongBinder());
            IBinder listener = data.readStrongBinder();
            assertNotNull(listener);
            if (code == 1) {
                int type = data.readInt();
                Bundle config = data.readTypedObject(Bundle.CREATOR);
                assertEquals(1, config.getInt("priority"));
                assertEquals(3, config.getInt("granularity"));
                assertEquals(1f, config.getFloat("radius"), 0f);
                assertTrue(config.getString("id").startsWith("com.google.pixel.livewallpaper.sidekick."));
                if (type == denyAt) {
                    reply.writeException(new SecurityException("Test registration denial"));
                    return true;
                }
                listeners.put(type, listener);
            } else if (code == 2) {
                removed.add(listener);
                listeners.values().removeIf(value -> value == listener);
            } else {
                fail("Unexpected transaction " + code);
            }
            reply.writeNoException();
            return true;
        }

        void send(int type, Bundle output) throws Exception {
            send(listeners.get(type), output);
        }
        void send(IBinder listener, Bundle output) throws Exception {
            Parcel data = Parcel.obtain();
            try {
                data.writeInterfaceToken(LISTENER);
                data.writeTypedObject(output, 0);
                assertTrue(listener.transact(1, data, null, IBinder.FLAG_ONEWAY));
            } finally {
                data.recycle();
            }
        }
    }

    private static class Owner extends ContextWrapper {
        OsloBinder service = new OsloBinder();
        ServiceConnection connection;
        int binds, unbinds;
        boolean granted = true;
        Owner() { super(RuntimeEnvironment.getApplication()); }
        @Override public String getPackageName() { return "com.google.pixel.livewallpaper"; }
        @Override public int checkSelfPermission(String name) {
            assertEquals("android.permission.STATUS_BAR_SERVICE", name);
            return granted ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
        }
        @Override public boolean bindService(Intent intent, ServiceConnection conn, int flags) {
            assertEquals(COMPONENT, intent.getComponent());
            binds++;
            connection = conn;
            conn.onServiceConnected(COMPONENT, service);
            return true;
        }
        @Override public void unbindService(ServiceConnection conn) {
            assertSame(connection, conn);
            unbinds++;
        }
    }

    private static Bundle detected() {
        Bundle output = new Bundle();
        output.putBoolean("detected", true);
        return output;
    }
    private static Bundle directional(int direction) {
        Bundle output = detected();
        output.putInt("direction", direction);
        return output;
    }
    private static void drain() { shadowOf(Looper.getMainLooper()).idle(); }

    @Test public void typesStayDistinctAndEchoesAreDeduplicated() throws Exception {
        Owner owner = new Owner();
        Events events = new Events();
        OsloGestureClient client = new OsloGestureClient(owner, events);
        client.start();
        assertEquals(7, owner.service.listeners.size());
        assertEquals(4, new HashSet<>(owner.service.listeners.values()).size());
        assertSame(owner.service.listeners.get(1), owner.service.listeners.get(2));
        assertSame(owner.service.listeners.get(4), owner.service.listeners.get(5));
        assertSame(owner.service.listeners.get(7), owner.service.listeners.get(8));
        owner.service.send(1, directional(4));
        owner.service.send(4, detected()); // Reach can omit its optional angle.
        owner.service.send(2, directional(4));
        owner.service.send(7, directional(4));
        owner.service.send(8, directional(4));
        owner.service.send(5, detected());
        owner.service.send(3, detected()); // Presence is identified by its subscription.
        drain();
        assertEquals(List.of("flick:4", "reach", "presence"), events.values);
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS);
        owner.service.send(7, directional(1));
        drain();
        assertEquals("swipe:1", events.values.get(3));
        Bundle ignored = detected();
        ignored.putBoolean("detected", false);
        owner.service.send(1, ignored);
        drain();
        assertEquals(4, events.values.size());
        client.stop();
        assertTrue(owner.service.listeners.isEmpty());
        assertEquals(4, owner.service.removed.size());
        assertEquals(1, owner.unbinds);
    }

    @Test public void stopRestartDropsPreviouslyQueuedEvents() throws Exception {
        Owner owner = new Owner();
        Events events = new Events();
        OsloGestureClient client = new OsloGestureClient(owner, events);
        client.start();
        owner.service.send(4, detected());
        client.stop();
        client.start();
        drain();
        assertTrue(events.values.isEmpty());
        owner.service.send(4, detected());
        drain();
        assertEquals(List.of("reach"), events.values);
        client.destroy();
    }

    @Test public void disconnectReconnectRegistersEveryTypeAgain() {
        Owner owner = new Owner();
        OsloGestureClient client = new OsloGestureClient(owner, new Events());
        client.start();
        owner.connection.onServiceDisconnected(COMPONENT);
        owner.service = new OsloBinder();
        shadowOf(Looper.getMainLooper()).idleFor(1250, TimeUnit.MILLISECONDS);
        assertEquals(2, owner.binds);
        assertEquals(7, owner.service.listeners.size());
        client.destroy();
    }

    @Test public void partialRegistrationFailureCleansOwnedListeners() {
        Owner owner = new Owner();
        owner.service.denyAt = 3;
        OsloGestureClient client = new OsloGestureClient(owner, new Events());
        client.start();
        assertTrue(owner.service.listeners.isEmpty());
        assertEquals(4, owner.service.removed.size());
        client.destroy();
    }

    @Test public void missingPermissionDoesNotRetryBindingForever() {
        Owner owner = new Owner();
        owner.granted = false;
        OsloGestureClient client = new OsloGestureClient(owner, new Events());
        client.start();
        shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.SECONDS);
        assertEquals(0, owner.binds);
        client.destroy();
    }
}
