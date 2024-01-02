package com.planetgallium.kitpvp.util;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

public class VaultHook {

    public static Economy ECONOMY;
    public static Permission PERMISSION;
    public static Chat CHAT;

    static {
        ServicesManager servicesManager = Bukkit.getServer().getServicesManager();

        RegisteredServiceProvider<Economy> economyServiceProvider = servicesManager.getRegistration(Economy.class);
        RegisteredServiceProvider<Permission> permissionServiceProvider = servicesManager.getRegistration(Permission.class);
        RegisteredServiceProvider<Chat> chatServiceProvider = servicesManager.getRegistration(Chat.class);

        if (economyServiceProvider != null)
            VaultHook.ECONOMY = economyServiceProvider.getProvider();
        if (permissionServiceProvider != null)
            VaultHook.PERMISSION = permissionServiceProvider.getProvider();
        if (chatServiceProvider != null)
            VaultHook.CHAT = chatServiceProvider.getProvider();
    }

}