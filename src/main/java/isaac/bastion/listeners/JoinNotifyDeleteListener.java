package isaac.bastion.listeners;

import isaac.bastion.Bastion;
import isaac.bastion.Permissions;
import isaac.bastion.storage.BastionLogStorage.Log;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import vg.civcraft.mc.namelayer.GroupManager;
import vg.civcraft.mc.namelayer.NameAPI;
import vg.civcraft.mc.namelayer.NameLayerPlugin;
import vg.civcraft.mc.namelayer.group.Group;
import vg.civcraft.mc.namelayer.permission.PermissionType;

public class JoinNotifyDeleteListener implements Listener {
	private final Map<UUID, Long> lastNotify = new HashMap<>();

	@EventHandler
	public void on(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		Bukkit.getScheduler().runTaskAsynchronously(Bastion.getPlugin(), () -> {
			int[] groups = NameLayerPlugin.getGroupManagerDao()
					.getGroupNames(player.getUniqueId())
					.stream()
					.filter(groupName -> NameAPI.getGroupManager()
							.hasAccess(groupName, player.getUniqueId(),
									PermissionType.getPermission(Permissions.BASTION_LIST)))
					.map(GroupManager::getGroup)
					.mapToInt(Group::getGroupId)
					.toArray();
			Optional<Log> optLog = Bastion.getBastionLog().getLog(player.getWorld().getName(), groups).stream()
					.filter(log -> log.getTime().toInstant(ZoneOffset.UTC).toEpochMilli() > lastNotify.getOrDefault(player.getUniqueId(), 0L))
					.reduce((a, b) -> a.getTime().compareTo(b.getTime()) >= 0 ? a : b);
			if (!optLog.isPresent()) {
				return;
			}
			Log lastLog = optLog.get();
			lastNotify.put(player.getUniqueId(), lastLog.getTime().toInstant(ZoneOffset.UTC).toEpochMilli());

			player.sendMessage(ChatColor.DARK_RED + "Bastions have recently been removed! Use /bdl to list them");
		});
	}

}
