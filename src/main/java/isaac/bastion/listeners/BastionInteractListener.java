package isaac.bastion.listeners;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;

import isaac.bastion.manager.BastionGroupManager;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.material.MaterialData;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import isaac.bastion.Bastion;
import isaac.bastion.BastionBlock;
import isaac.bastion.BastionType;
import isaac.bastion.Permissions;
import isaac.bastion.commands.PlayersStates;
import isaac.bastion.commands.PlayersStates.Mode;
import isaac.bastion.manager.BastionBlockManager;
import isaac.bastion.storage.BastionBlockStorage;
import vg.civcraft.mc.citadel.Citadel;
import vg.civcraft.mc.citadel.events.ReinforcementCreationEvent;
import vg.civcraft.mc.citadel.reinforcement.PlayerReinforcement;
import vg.civcraft.mc.citadel.reinforcement.Reinforcement;
import vg.civcraft.mc.namelayer.NameAPI;
import vg.civcraft.mc.namelayer.permission.PermissionType;

public class BastionInteractListener implements Listener {

	private BastionBlockManager blockManager;
	private BastionBlockStorage blockStorage;
	private BastionGroupManager groupManager;

	public BastionInteractListener() {
		blockManager = Bastion.getBastionManager();
		blockStorage = Bastion.getBastionStorage();
		groupManager = Bastion.getGroupManager();
	}

	@EventHandler(ignoreCancelled=true)
	public void onBlockClicked(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
			return;
		}

		final Player player = event.getPlayer();

		//Stop boat places in bastions
		Material isBoat = player.getInventory().getItemInMainHand().getType();
		if(isBoat == Material.BOAT || isBoat == Material.BOAT_ACACIA ||
			isBoat == Material.BOAT_BIRCH || isBoat == Material.BOAT_DARK_OAK ||
			isBoat == Material.BOAT_JUNGLE || isBoat == Material.BOAT_SPRUCE ) {
			Set<Block> blocks = new CopyOnWriteArraySet<Block>();
			blocks.add(event.getClickedBlock());
			Set<BastionBlock> blocking = blockManager.shouldStopBlockByBlockingBastion(null, blocks, player.getUniqueId());

			if (blocking.size() > 0) {
				event.setCancelled(true);
				player.sendMessage(ChatColor.RED+"Boat blocked by bastion");
				return;
			}
		}

		if (PlayersStates.playerInMode(player, Mode.NORMAL)) {
			return;
		}
		
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
			return;
		}		

		Block block = event.getClickedBlock();

		if (PlayersStates.playerInMode(player, Mode.INFO)) {
			boolean dev = player.hasPermission("Bastion.dev");
			TextComponent toSend = blockManager.infoMessageComponent(dev, block.getRelative(event.getBlockFace()), block, player);

			PlayersStates.touchPlayer(player);
			player.spigot().sendMessage(toSend);
		} else if (PlayersStates.playerInMode(player, Mode.DELETE)) {
			BastionBlock bastionBlock = blockStorage.getBastionBlock(block.getLocation());

			if (bastionBlock == null) {
				return;
			}

			if (bastionBlock.canRemove(player)) {
				TextComponent toSend = blockManager.bastionDeletedMessageComponent(bastionBlock);
				bastionBlock.destroy();
				player.spigot().sendMessage(toSend);
				PlayersStates.touchPlayer(player);
				event.setCancelled(true);
			}
		} else if (PlayersStates.playerInMode(player, Mode.MATURE)) {
			BastionBlock bastionBlock= blockStorage.getBastionBlock(block.getLocation());

			if (bastionBlock == null) {
				return;
			}
			bastionBlock.mature();
			player.sendMessage(ChatColor.GREEN + "Matured");
		} else if (PlayersStates.playerInMode(player, Mode.BASTION)) {
			final BastionType type = blockStorage.getAndRemovePendingBastion(block.getLocation());
			if(type == null) return; //if it wasnt stored it cant have been a bastion
			Reinforcement reinf = Citadel.getReinforcementManager().getReinforcement(block.getLocation());

			if (!(reinf instanceof PlayerReinforcement)) {
				return;
			}
			
			PlayerReinforcement reinforcement = (PlayerReinforcement) reinf;

			if (NameAPI.getGroupManager().hasAccess(reinforcement.getGroup(), player.getUniqueId(), PermissionType.getPermission(Permissions.BASTION_PLACE))) {
				final Location loc = block.getLocation().clone();
				new BukkitRunnable() {
					@Override
					public void run() {
						if(blockStorage.createBastion(loc,  type, player)) {
							TextComponent toSend = blockManager.bastionCreatedMessageComponent(loc);
							player.spigot().sendMessage(toSend);
						} else {
							blockStorage.addPendingBastion(loc, type);
							player.sendMessage(ChatColor.RED + "Failed to create bastion");
						}
					}
				}.runTask(Bastion.getPlugin());
				PlayersStates.touchPlayer(player);
			} else{
				player.sendMessage(ChatColor.RED + "You don't have the right permission");
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {
		ItemStack inHand = event.getItemInHand();
		if (inHand == null) return;

		BastionType type = blockToType(event.getBlock(), inHand);
		if(type != null) {
			Bastion.getPlugin().getLogger().log(Level.INFO, "Pending a bastion at {0}", event.getBlock().getLocation());
			blockStorage.addPendingBastion(event.getBlock().getLocation(), type);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onReinforcement(ReinforcementCreationEvent event) {
		if(Bastion.getCommonSettings().isCancelReinforcementModeInBastionField()) {
			Set<Block> blocks = new CopyOnWriteArraySet<Block>();
			blocks.add(event.getBlock());
			Set<BastionBlock> blocking = blockManager.shouldStopBlockByBlockingBastion(null, blocks,event.getPlayer().getUniqueId());

			if (blocking.size() != 0 && !groupManager.canPlaceBlock(event.getPlayer(), blocking)){
				event.setCancelled(true);
				event.getPlayer().sendMessage(ChatColor.RED + "A Bastion prevents you from reinforcing");
				return;
			}
		}

		if(event.getReinforcement() instanceof PlayerReinforcement) {
			final BastionType type = blockStorage.getAndRemovePendingBastion(event.getBlock().getLocation());
			if (type != null && !PlayersStates.playerInMode(event.getPlayer(), Mode.OFF)) {
				// Check Permissions.BASTION_PLACE; Citadel handles the canBypass() check...
				PlayerReinforcement reinforcement = (PlayerReinforcement) event.getReinforcement();
				final Player player = event.getPlayer();
				if (!NameAPI.getGroupManager().hasAccess(reinforcement.getGroup(), player.getUniqueId(), PermissionType.getPermission(Permissions.BASTION_PLACE))) {
					event.setCancelled(true);
					event.getPlayer().sendMessage(ChatColor.RED + "You lack permission to create a Bastion on this group");
					blockStorage.addPendingBastion(event.getBlock().getLocation(), type);
					return;
				}
				// end Check Permissions.BASTION_PLACE
				
				PlayersStates.touchPlayer(event.getPlayer());
				Bastion.getPlugin().getLogger().log(Level.INFO, "Registering to create a {0} bastion", type);
				final Location loc = event.getBlock().getLocation().clone();
				// Can't do it immediately, as the reinforcement doesn't exist _during_ the create event.
				new BukkitRunnable() {
					@Override
					public void run() {
						if (blockStorage.createBastion(loc, type, player)) {
							TextComponent toSend = blockManager.bastionCreatedMessageComponent(loc);
							player.spigot().sendMessage(toSend);
						} else {
							blockStorage.addPendingBastion(loc, type);
							player.sendMessage(ChatColor.RED + "Failed to create bastion");
						}
					}
				}.runTask(Bastion.getPlugin());
			} else {
				if (blockManager.changeBastionGroup(event.getPlayer(), (PlayerReinforcement) event.getReinforcement(), event.getBlock().getLocation()) == Boolean.FALSE) {
					event.setCancelled(true);
					event.getPlayer().sendMessage(ChatColor.RED + "You lack permission to alter a Bastion with this group");
					return;
				}
			}
		}
	}

	//@SuppressWarnings("deprecation")
	public BastionType blockToType(Block block, ItemStack inHand) {
		MaterialData mat = new MaterialData(block.getType()); //, block.getData()); -- again, we can't differentiate based on this?
		String displayName = null;
		List<String> lore = null;
		if (inHand != null) {
			ItemMeta im = inHand.getItemMeta();
			if (im != null && im.hasLore()) {
				lore = im.getLore();
			}
			if (im != null && im.hasDisplayName()) {
				displayName = im.getDisplayName();
			}
		}
		BastionType type = BastionType.getBastionType(mat, displayName, lore);
		return type;
	}
}
