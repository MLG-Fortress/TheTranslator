package com.robomwm.thetranslator;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created on 5/26/2018.
 *
 * @author RoboMWM
 */
public class TheTranslator extends JavaPlugin implements Listener
{
    Map<String, String> languageCache;
    Map<Player, String> playerLanguageMap = new ConcurrentHashMap<>();
    Map<Player, String> originalMessage = new ConcurrentHashMap<>();

    public void onEnable()
    {
        getConfig().addDefault("key", "YOUR_AZURE_TRANSLATE_KEY");
        getConfig().options().copyDefaults(true);
        saveConfig();
        if (getConfig().getString("key").equals("YOUR_AZURE_TRANSLATE_KEY"))
        {
            getLogger().severe("Must specify a key in config.yml");
            getPluginLoader().disablePlugin(this);
            return;
        }
        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                try
                {
                    languageCache = Translate.getLanguages();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    getPluginLoader().disablePlugin(getMyself());
                    return;
                }
            }
        }.runTaskAsynchronously(this);
        Translate.setSubscriptionKey(getConfig().getString("key"));
        getServer().getPluginManager().registerEvents(this, this);
    }

    private JavaPlugin getMyself()
    {
        return this;
    }

    @EventHandler(ignoreCancelled = true)
    private void onQuit(PlayerQuitEvent event)
    {
        playerLanguageMap.remove(event.getPlayer());
        originalMessage.remove(event.getPlayer());
    }

    private boolean canSpeakEnglish(Player player)
    {
        if (!playerLanguageMap.containsKey(player))
        {
            if (player.getLocale().toLowerCase().startsWith("en"))
                setSpeakingEnglish(player);
            else
                playerLanguageMap.put(player, "");
        }
        return playerLanguageMap.containsKey(player) && playerLanguageMap.get(player).equalsIgnoreCase("en");
    }

    private void setSpeakingEnglish(Player player)
    {
        playerLanguageMap.put(player, "en");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    private void onChat(AsyncPlayerChatEvent event)
    {
        try
        {
            Player player = event.getPlayer();
            if (canSpeakEnglish(player))
                return;

            originalMessage.put(player, event.getMessage());

            if (playerLanguageMap.get(player).isEmpty())
            {
                //Discover if they're speaking English
                Translate.DetectResult detectResult = Translate.detect(event.getMessage());
                if (detectResult.getLanguage().equals("en"))
                {
                    //Stop analyzing/translating if we're sure they spoke English (may need to spot check later on)
                    if (detectResult.getScore() == 1.0f)
                        setSpeakingEnglish(player);
                    return;
                }
                else if (detectResult.getScore() > 0.5f && player.getLocale().startsWith(detectResult.getLanguage()))
                    playerLanguageMap.put(player, detectResult.getLanguage());
                else
                {
                    event.setMessage(Translate.translate(event.getMessage(), null, "en"));
                    return;
                }
            }

            event.setMessage(Translate.translate(event.getMessage(), playerLanguageMap.get(player), "en"));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        if (!(sender instanceof Player))
            return false;

        if (args.length == 0 || args[0].equalsIgnoreCase("list"))
        {
            return true;
        }

        playerLanguageMap.put((Player)sender, args[0]);
        return true;
    }
}
