package fr.elias.oreoEssentials.trade;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.function.Consumer;

public final class TradeSession {

    final Player a, b;
    private final OreoEssentials plugin;
    private final TradeConfig cfg;

    private final Consumer<TradeSession> onFinish;
    private final java.util.function.BiConsumer<TradeSession,String> onCancel;

    private boolean aConfirmed = false;
    private boolean bConfirmed = false;

    private ItemStack[] offerA = new ItemStack[0];
    private ItemStack[] offerB = new ItemStack[0];

    private SmartInventory invA;
    private SmartInventory invB;

    public TradeSession(OreoEssentials plugin, TradeConfig cfg, Player a, Player b,
                        Consumer<TradeSession> onFinish,
                        java.util.function.BiConsumer<TradeSession,String> onCancel) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.a = a;
        this.b = b;
        this.onFinish = onFinish;
        this.onCancel = onCancel;
    }

    public void open() {
        invA = TradeView.build(plugin, cfg, this, true);
        invB = TradeView.build(plugin, cfg, this, false);
        invA.open(a);
        invB.open(b);
        a.playSound(a.getLocation(), cfg.openSound, 1, 1);
        b.playSound(b.getLocation(), cfg.openSound, 1, 1);
    }

    /* called by view */
    public void clickConfirm(boolean forA) {
        if (forA) {
            if (!aConfirmed) {
                if (cfg.requireEmptyCursorOnConfirm && !isEmpty(a.getItemOnCursor())) {
                    a.sendMessage("§cEmpty your cursor before confirming.");
                    return;
                }
                aConfirmed = true;
                offerA = TradeView.snapshotOfferFrom(a, cfg.rows, true);
                a.playSound(a.getLocation(), cfg.confirmSound, 1, 1);
            } else {
                aConfirmed = false;
                a.playSound(a.getLocation(), cfg.clickSound, 1, 1);
            }
        } else {
            if (!bConfirmed) {
                if (cfg.requireEmptyCursorOnConfirm && !isEmpty(b.getItemOnCursor())) {
                    b.sendMessage("§cEmpty your cursor before confirming.");
                    return;
                }
                bConfirmed = true;
                offerB = TradeView.snapshotOfferFrom(b, cfg.rows, false);
                b.playSound(b.getLocation(), cfg.confirmSound, 1, 1);
            } else {
                bConfirmed = false;
                b.playSound(b.getLocation(), cfg.clickSound, 1, 1);
            }
        }

        // No manual refresh needed; SmartInvs will call update() every tick.

        if (aConfirmed && bConfirmed) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                a.closeInventory();
                b.closeInventory();
                onFinish.accept(this);
            });
        }
    }

    public void clickCancel() {
        a.closeInventory();
        b.closeInventory();
        onCancel.accept(this, "cancelled");
    }

    public boolean isConfirmed(boolean forA) { return forA ? aConfirmed : bConfirmed; }

    public ItemStack[] getOfferA() {
        return offerA == null ? new ItemStack[0]
                : Arrays.stream(offerA).filter(i -> i != null && !i.getType().isAir()).toArray(ItemStack[]::new);
    }
    public ItemStack[] getOfferB() {
        return offerB == null ? new ItemStack[0]
                : Arrays.stream(offerB).filter(i -> i != null && !i.getType().isAir()).toArray(ItemStack[]::new);
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }
}
