package isaac.bastion.storage;

import isaac.bastion.Bastion;
import isaac.bastion.BastionBlock;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import vg.civcraft.mc.civmodcore.dao.ManagedDatasource;

public class BastionLogStorage {

	private static final String INSERT = "INSERT INTO bastion_log (group_id, world, x, y, z) VALUES (?, ?, ?, ?, ?)";
	private static final String SELECT = "SELECT * FROM bastion_log where AND world = ? group_id IN (%s) AND time >= NOW() - INTERVAL 24 HOUR ORDER BY id ASC";

	private final ManagedDatasource source;
	private final Logger logger;

	public BastionLogStorage(ManagedDatasource source, Logger logger) {
		this.source = source;
		this.logger = logger;
	}

	public void logDestroyAsync(BastionBlock block) {
		int group = block.getListGroupId();
		Bukkit.getScheduler().runTaskAsynchronously(Bastion.getPlugin(), () -> {
			Location location = block.getLocation();

			try (Connection connection = source.getConnection()) {
				PreparedStatement statement = connection.prepareStatement(INSERT);

				statement.setInt(1, group);
				statement.setString(2, location.getWorld().getName());
				statement.setInt(3, location.getBlockX());
				statement.setInt(4, location.getBlockY());
				statement.setInt(5, location.getBlockZ());

				statement.executeUpdate();
				logger.info("Bastion destroyed @ " + location);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		});
	}

	public List<Log> getLog(String world, int[] groups) {
		try (Connection connect = source.getConnection()) {
			StringBuilder format = new StringBuilder(groups.length * 2);
			for (int i = 0; i < groups.length; i++) {
				if (i > 0) {
					format.append(",");
				}
				format.append("?");
			}
			PreparedStatement statement = connect
					.prepareStatement(String.format(SELECT, format.toString()));

			statement.setString(1, world);
			for (int i = 0; i < groups.length; i++) {
				statement.setInt(i + 2, groups[i]);
			}

			ResultSet results = statement.executeQuery();
			List<Log> logs = new ArrayList<>();
			while (results.next()) {
				logs.add(new Log(results.getInt("group_id"), results.getInt("x"),
						results.getInt("y"), results.getInt("z"),
						results.getTimestamp("time").toLocalDateTime()));
			}
			return logs;
		} catch (SQLException ex) {
			ex.printStackTrace();
			return Collections.emptyList();
		}
	}

	public static class Log {

		private final int groupId;
		private final int x;
		private final int y;
		private final int z;
		private final LocalDateTime time;

		public Log(int groupId, int x, int y, int z, LocalDateTime time) {
			this.groupId = groupId;
			this.x = x;
			this.y = y;
			this.z = z;
			this.time = time;
		}

		public int getGroupId() {
			return groupId;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		public int getZ() {
			return z;
		}

		public LocalDateTime getTime() {
			return time;
		}
	}
}
