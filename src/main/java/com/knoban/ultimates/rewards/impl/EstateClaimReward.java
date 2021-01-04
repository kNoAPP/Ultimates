package com.knoban.ultimates.rewards.impl;

import com.knoban.ultimates.cardholder.Holder;
import com.knoban.ultimates.primal.Tier;
import com.knoban.ultimates.rewards.Reward;
import com.knoban.ultimates.rewards.RewardInfo;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Map;

@RewardInfo(name = "estate-claim")
public class EstateClaimReward extends Reward {

    public EstateClaimReward(Map<String, Object> data) {
        super(data);
        this.icon = createIcon(amount);
    }

    public EstateClaimReward(ItemStack icon, Tier tier, long amount) {
        super(icon, tier, amount);
    }

    public EstateClaimReward(Tier tier, long amount) {
        super(createIcon(amount), tier, amount);
    }

    private static ItemStack createIcon(long amount) {
        ItemStack icon = new ItemStack(Material.CAMPFIRE);
        ItemMeta wisdomIM = icon.getItemMeta();
        wisdomIM.setDisplayName("§3" + amount + " Extra Estate" + (amount == 1 ? "" : "s"));
        wisdomIM.setLore(Arrays.asList("§7Used to §9claim §7chunks of land."));
        icon.setItemMeta(wisdomIM);
        return icon;
    }

    @Override
    public void reward(Holder holder) {
        Player p = Bukkit.getPlayer(holder.getUniqueId());
        if(p != null) {
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST_FAR, 3F, 0.8F);
            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RETURN, 3F, 0.5F);
            p.sendMessage("§2You got an extra §3" + amount + " Estate Claim" + (amount == 1 ? "" : "s") + "§2!");
            p.sendMessage("§7Claim more land with §e/estate claim§7.");
        }

        holder.incrementMaxEstateClaims((int) amount);
    }
}
