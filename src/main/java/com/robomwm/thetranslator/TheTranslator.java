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
    Map<Player, Integer> englishScore = new ConcurrentHashMap<>();
    Map<Player, Integer> messageCount = new ConcurrentHashMap<>();

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

    //Not guaranteed removal since these are modified asynchronously
    @EventHandler(ignoreCancelled = true)
    private void onQuit(PlayerQuitEvent event)
    {
        englishScore.remove(event.getPlayer());
        messageCount.remove(event.getPlayer());
    }

//    private float isEnglish(String message)
//    {
//        try
//        {
//            Translate.DetectResult detectResult = Translate.detect(message);
//            if (detectResult.getLanguage().equals("en"))
//                return detectResult.getScore();
//            return 0f;
//        }
//        catch (Exception e)
//        {
//            e.printStackTrace();
//            return 0.9f;
//        }
//    }

    private boolean isEnglish(String message)
    {
        try
        {
            Translate.DetectResult detectResult = Translate.detect(message);
            return detectResult.getLanguage().equals("en");
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return true;
        }
    }

    private String translate(Player player, String message)
    {
        //No record this session, detect first
        if (!englishScore.containsKey(player))
        {
            if (isEnglish(message))
            {
                englishScore.put(player, 3);
                return null;
            }
            englishScore.put(player, -1);
        }

        int score = englishScore.get(player);

        //passed "test"
        if (score > 5)
        {
            messageCount.putIfAbsent(player, 0);
            messageCount.put(player, messageCount.get(player) + 1);

            //Double check every ~20 messages
            if (messageCount.get(player) % 20 == 0)
            {
                new BukkitRunnable()
                {
                    @Override
                    public void run()
                    {
                        if (!isEnglish(message))
                            englishScore.put(player, 0);
                    }
                }.runTaskAsynchronously(this);
            }
            return null;
        }

        //Seems to be using English, try only detecting
        if (score > 2 && isEnglish(message))
        {
            englishScore.put(player, score + 1);
            return null;
        }

        Translate.TranslateResult result;

        try
        {
            result = Translate.translate(message, "en");
        }
        catch (Exception e)
        {
            getLogger().severe("Translation failed for " + player.getName() + ": " + message);
            e.printStackTrace();
            return null;
        }

        //Translation source detected as english, increment score
        if (result.getLanguage().equals("en"))
        {
            englishScore.put(player, englishScore.get(player) + 1);
            return null;
        }

        //reset score otherwise, return translation
        //broadcast if first time
        if (score < 0)
        {
            getServer().dispatchCommand(getServer().getConsoleSender(), "broadcast Translating messages from "
                    + player.getName() + " from " + languageCache.get(result.getLanguage()) + " to English.\n");
            getServer().dispatchCommand(getServer().getConsoleSender(), "communicationconnector Translating messages from "
                    + player.getName() + " from " + languageCache.get(result.getLanguage()) + " to English.\n");
        }

        getLogger().info(player.getName() + ": " + message);
        englishScore.put(player, 0);
        return result.getResult();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    private void onChat(AsyncPlayerChatEvent event)
    {
        Player player = event.getPlayer();
        if (player.getLocale().startsWith("en"))
            return;
        String result = translate(player, event.getMessage());
        if (result == null)
            return;
        event.setMessage(result);
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        if (args.length == 0 || args[0].equalsIgnoreCase("list"))
        {
            for (String key : languageCache.keySet())
            {
                sender.sendMessage(key + " " + languageCache.get(key));
            }
        }

        return true;
    }
}
