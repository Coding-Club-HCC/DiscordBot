package club.coding.discord.framework.commands;

import club.coding.discord.framework.reflect.ReflectionUtil;
import club.coding.discord.framework.logging.FrameWorkLogger;
import club.coding.discord.framework.FrameWork;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.requests.restaction.CommandCreateAction;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;

/**
 * Manages all registered commands.
 * @since 1.0
 * @author Jonah
 * @see CommandModule
 * @see CommandOption
 * @see DiscordCommand
 */
public class CommandManager extends TimerTask {

    private static final Map<String, CommandModule> COMMAND_MODULE_MAP = new HashMap<>();


    public static CommandModule getCommand(String name) {
        return COMMAND_MODULE_MAP.get(name);
    }

    public static void reflectivelyRegisterCommands() {
        List<Class<?>> uninstantiated = ReflectionUtil.getAllClassesFor(CommandModule.class);

        int skipped = 0;
        for (Class<?> clazz : uninstantiated) {
            try {
                CommandModule module = (CommandModule) clazz.getDeclaredConstructor().newInstance();
                module.register();
                FrameWorkLogger.info("Registered command module! (" + clazz.getSimpleName() + ")");
            } catch (NoSuchMethodException ignored) {
                skipped++;
            } catch (Exception e) {
                FrameWorkLogger.error("An error occurred while registering command module: " + clazz.getSimpleName(), e);
            }
        }

        FrameWorkLogger.info("Finished registering command modules! Skipped " + skipped + " classes.");
    }


    public static void registerCommand(CommandModule commandModule) {
        JDA discordApp = FrameWork.getDiscordApp();

        if (commandModule.getCommandInfo() == null) {
            throw new IllegalArgumentException("CommandModule must have a DiscordCommand annotation!");
        }

        if (commandModule.getCommandInfo().guildOnly()) {
            for (Guild guild : discordApp.getGuilds()) {
                upsertCommand(commandModule.getCommandInfo().name(), commandModule.getCommandInfo().description(), commandModule.getOptions(), guild);
            }
        } else {
            upsertCommand(commandModule.getCommandInfo().name(), commandModule.getCommandInfo().description(), commandModule.getOptions(), null);
        }

        COMMAND_MODULE_MAP.put(commandModule.getCommandInfo().name(), commandModule);
    }

    private static void upsertCommand(String name, String desc, List<CommandOption> options, @Nullable Guild guild) {
        JDA discordApp = FrameWork.getDiscordApp();
        CommandCreateAction action = guild == null ? discordApp.upsertCommand(name, desc) : guild.upsertCommand(name, desc);
        for (CommandOption option : options) {
            action.addOption(option.getOptionType(), option.getName(), option.getDescription(), option.isRequired());
        }
        action.queue();
    }

    @SubscribeEvent
    public void onSlashCommandEvent(SlashCommandInteractionEvent event) {
        CommandModule command = COMMAND_MODULE_MAP.get(event.getName());
        if (command == null) {
            return;
        }
        if (command.getCommandInfo().permission() != Permission.UNKNOWN && !event.getMember().hasPermission(command.getCommandInfo().permission())) {
            event.reply("You do not have permission to use this command!").setEphemeral(true).queue();
            return;
        }
        try {
            command.execute(event);
        } catch (Throwable throwable) {
            FrameWorkLogger.error("An error occurred while executing command: " + event.getName(), throwable);
        }
    }

    @Override
    public void run() {
        // re-register commands in case of expiration
        COMMAND_MODULE_MAP.values().forEach(CommandManager::registerCommand);
    }
}
