package isaac.bastion.commands;

import com.google.common.base.Strings;
import isaac.bastion.Bastion;
import isaac.bastion.Permissions;
import isaac.bastion.storage.BastionLogStorage.Log;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vg.civcraft.mc.namelayer.GroupManager;
import vg.civcraft.mc.namelayer.NameAPI;
import vg.civcraft.mc.namelayer.NameLayerPlugin;
import vg.civcraft.mc.namelayer.group.Group;
import vg.civcraft.mc.namelayer.permission.PermissionType;

public class BastionDeleteListCommand implements CommandExecutor {

	@Override
	public boolean onCommand(CommandSender sender, Command command, String arg, String[] args) {
		if (!(sender instanceof Player)) {
			return false;
		}

		Player player = (Player) sender;
		Bukkit.getScheduler().runTaskAsynchronously(Bastion.getPlugin(), () -> {
			sender.sendMessage( ChatColor.RED + " Bastion Delete List " + ChatColor.DARK_GRAY + Strings.repeat("-", 10) + "\n");
			int[] groups = NameLayerPlugin.getGroupManagerDao()
					.getGroupNames(player.getUniqueId())
					.stream()
					.filter(groupName -> NameAPI.getGroupManager()
							.hasAccess(groupName, player.getUniqueId(),
									PermissionType.getPermission(Permissions.BASTION_LIST)))
					.map(GroupManager::getGroup)
					.mapToInt(Group::getGroupId)
					.toArray();
			List<Log> logs = Bastion.getBastionLog().getLog(player.getWorld().getName(), groups);
			for (Log log : logs) {
				Group group = GroupManager.getGroup(log.getGroupId());
				player.sendMessage(group.getName() + ": (" + log.getX() + " " + log.getY() + " " + log.getZ() + ") " + ChronoUnit.HOURS.between(log.getTime(), LocalDateTime.now()) + " hours ago");
			}
		});

		return true;
	}
}
