package com.wtm.modular.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Canonical lightweight module registry used by the event-driven UI lifecycle.
 * Runtime module implementations may register listeners without requiring the
 * workspace to poll or rebuild during ordinary navigation.
 */
public final class ModuleRegistry {
    public interface Listener {}

    private static final ModuleRegistry INSTANCE=new ModuleRegistry();
    private final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Object> modules=new CopyOnWriteArrayList<>();

    private ModuleRegistry(){}
    public static ModuleRegistry get(){return INSTANCE;}
    public void addListener(Listener listener){if(listener!=null&&!listeners.contains(listener))listeners.add(listener);}
    public void removeListener(Listener listener){listeners.remove(listener);}
    public List<Object> modules(){return List.copyOf(modules);}
    public void register(Object module){if(module!=null&&!modules.contains(module))modules.add(module);}
    public void unregister(Object module){modules.remove(module);}
}
