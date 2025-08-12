package ua.akrgames.akrrwd;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CmdTabCompleter implements TabCompleter {

    private static final List<String> AKRRWD_SUBCOMMANDS = Arrays.asList("help", "reload", "reset", "settime", "toggle");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("randomreward")) {
            // No arguments for /randomreward, return empty list
            return completions;
        }

        if (command.getName().equalsIgnoreCase("akrrwd")) {
            if (args.length == 1) {
                // Suggest subcommands for /akrrwd
                StringUtil.copyPartialMatches(args[0], AKRRWD_SUBCOMMANDS, completions);
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("settime"))) {
                // Suggest player names and "all" for reset, player names for settime
                List<String> suggestions = new ArrayList<>();
                if (args[0].equalsIgnoreCase("reset")) {
                    suggestions.add("all");
                }
                for (Player player : sender.getServer().getOnlinePlayers()) {
                    suggestions.add(player.getName());
                }
                StringUtil.copyPartialMatches(args[1], suggestions, completions);
            } else if (args.length == 3 && args[0].equalsIgnoreCase("settime")) {
                // Suggest time format placeholder for /akrrwd settime <player> <days,hours,minutes,seconds>
                completions.add("days,hours,minutes,seconds");
            }
        }

        return completions;
    }
}