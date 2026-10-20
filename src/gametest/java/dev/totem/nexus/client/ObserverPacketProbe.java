package dev.totem.nexus.client;

/** Test-only counter for packets attempted by a read-only Observer screen. */
public final class ObserverPacketProbe {
    private static int sends;
    private static int teleportSends;
    private static int pageSends;
    private ObserverPacketProbe() { }
    public static void reset() { sends = 0; teleportSends = 0; pageSends = 0; }
    public static void record() { sends++; }
    public static void recordTeleport() { teleportSends++; }
    public static int teleportSends() { return teleportSends; }
    public static void recordPage() { pageSends++; }
    public static int pageSends() { return pageSends; }
    public static int sends() { return sends; }
}
