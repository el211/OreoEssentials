package fr.elias.oreoEssentials.services;

import java.util.UUID;

public final class NoopHomeDirectory implements HomeDirectory {
    @Override public void setHomeServer(UUID uuid, String name, String server) {}
    @Override public String getHomeServer(UUID uuid, String name) { return null; }
    @Override public void deleteHome(UUID uuid, String name) {}
}
