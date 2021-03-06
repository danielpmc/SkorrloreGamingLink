package me.skorrloregaming;

import me.skorrloregaming.redis.RedisDatabase;
import org.apache.commons.lang.ArrayUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PlaytimeManager {
	public ConcurrentMap<UUID, Long> playtimeTracker = new ConcurrentHashMap<>();

	private Optional<JedisPool> jedisPool = Optional.empty();

	private PlaytimeManager instance;

	private Jedis jedis;

	private boolean connectToRedis() {
		instance = this;
		LinkServer.getPlugin().getLogger().info("Connecting to Redis..");
		String hostname = LinkServer.getPlugin().getConfig().getString("settings.redis.hostname", LinkServer.getPlugin().getConfig().getString("settings.redis.hostname", "localhost"));
		int port = LinkServer.getPlugin().getConfig().getInt("settings.redis.port", 6379);
		String password = LinkServer.getPlugin().getConfig().getString("settings.redis.password");
		JedisPoolConfig poolConfig = new JedisPoolConfig();
		poolConfig.setMaxWaitMillis(10 * 1000);
		if (password == null || password.equals("")) {
			jedisPool = Optional.ofNullable(new JedisPool(poolConfig, hostname, port, 0));
		} else {
			jedisPool = Optional.ofNullable(new JedisPool(poolConfig, hostname, port, 0, password));
		}
		return jedisPool.isPresent();
	}

	private PlaytimeManager getInstance() {
		return instance;
	}

	private boolean close() {
		if (jedisPool.isPresent()) {
			jedisPool.get().destroy();
			return true;
		}
		return false;
	}

	public Optional<JedisPool> getPool() {
		if (!jedisPool.isPresent() || jedisPool.get().isClosed()) {
			connectToRedis();
		}
		return jedisPool;
	}

	public void register() {
		connectToRedis();
		jedis = jedisPool.get().getResource();
		LinkServer.getPlugin().getLogger().info("Connected to Redis!");
	}

	public void unregister() {
		close();
	}

	public void set(String table, String key, String value) {
		if (value == null) {
			jedis.del(table + "." + key);
		} else {
			jedis.set(table + "." + key, value);
		}
	}

	public String getString(String table, String key) {
		return jedis.get(table + "." + key);
	}

	public boolean contains(String table, String key) {
		String response;
		return !((response = getString(table, key)) == null || response.length() == 0);
	}

	public PlaytimeManager() {
		Bukkit.getScheduler().runTaskTimer(LinkServer.getPlugin(), new Runnable() {
			@Override
			public void run() {
				Calendar calendar = Calendar.getInstance();
				calendar.setTimeInMillis(System.currentTimeMillis());
				Calendar newCalendar = Calendar.getInstance();
				UUID[] array = playtimeTracker.keySet().toArray(new UUID[0]);
				for (int i = 0; i < playtimeTracker.keySet().size(); i++) {
					UUID id = array[i];
					Player player = Bukkit.getPlayer(id);
					if (player == null) {
						handle_QuitEvent(CraftGo.Player.getOfflinePlayer(id));
					} else {
						long timeOfJoin = playtimeTracker.get(id).longValue();
						newCalendar.setTimeInMillis(timeOfJoin * 1000);
						if (!(newCalendar.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR))) {
							handle_QuitEvent(player);
							handle_JoinEvent(player);
						}
						if (!(newCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR))) {
							handle_QuitEvent(player);
							for (int day = 0; day <= 365; day++) {
								if (contains("playtime.dayOfYear." + day, player.getUniqueId().toString()))
									set("playtime.dayOfYear." + day, player.getUniqueId().toString(), null);
							}
							handle_JoinEvent(player);
						}
					}
				}
			}
		}, 20L, 20L);
	}

	public long getStoredPlayerPlaytime(OfflinePlayer player) {
		if (contains("playtime.total", player.getUniqueId().toString())) {
			return Long.parseLong(getString("playtime.total", player.getUniqueId().toString()));
		} else {
			return 0;
		}
	}

	public long getStoredPlayerPlaytime(OfflinePlayer player, int dayOfYear) {
		if (contains("playtime.dayOfYear." + dayOfYear, player.getUniqueId().toString())) {
			return Long.parseLong(getString("playtime.dayOfYear." + dayOfYear, player.getUniqueId().toString()));
		} else {
			return 0;
		}
	}

	public long[] getRangeOfStoredPlayerPlaytime(OfflinePlayer player, int startDayOfYear, int endDayOfYear) {
		ArrayList<Long> rangeOfData = new ArrayList<Long>();
		for (int i = startDayOfYear; i < endDayOfYear; i++) {
			rangeOfData.add(getStoredPlayerPlaytime(player, i));
		}
		return ArrayUtils.toPrimitive(rangeOfData.toArray(new Long[0]));
	}

	public int getLastKnownDayOfYear(OfflinePlayer player) {
		if (contains("playtime.lastKnownDayOfYear", player.getUniqueId().toString())) {
			return Integer.parseInt(getString("playtime.lastKnownDayOfYear", player.getUniqueId().toString()));
		} else {
			return 0;
		}
	}

	public void handle_JoinEvent(Player player) {
		if (playtimeTracker.containsKey(player.getUniqueId()))
			return;
		if (LinkServer.getAntiCheat().antiafk.lackingActivityMinutes.containsKey(player.getUniqueId()))
			if (LinkServer.getAntiCheat().antiafk.lackingActivityMinutes.get(player.getUniqueId()) > 5.0)
				return;
		long seconds = System.currentTimeMillis() / 1000l;
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(System.currentTimeMillis());
		int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
		if (dayOfYear < getLastKnownDayOfYear(player)) {
			for (int day = 0; day <= 365; day++) {
				if (contains("playtime.dayOfYear." + day, player.getUniqueId().toString()))
					set("playtime.dayOfYear." + day, player.getUniqueId().toString(), null);
			}
		}
		set("playtime.lastKnownDayOfYear", player.getUniqueId().toString(), dayOfYear + "");
		playtimeTracker.put(player.getUniqueId(), seconds);
	}

	public void handle_QuitEvent(OfflinePlayer player) {
		if (playtimeTracker.containsKey(player.getUniqueId())) {
			long lastSeconds = playtimeTracker.get(player.getUniqueId()).longValue();
			Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(lastSeconds * 1000);
			int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
			Calendar newCalendar = Calendar.getInstance();
			newCalendar.setTimeInMillis(System.currentTimeMillis());
			long secondsPassed = (System.currentTimeMillis() / 1000l) - playtimeTracker.get(player.getUniqueId()).longValue();
			playtimeTracker.remove(player.getUniqueId());
			long currentTimeInSeconds = getStoredPlayerPlaytime(player);
			set("playtime.total", player.getUniqueId().toString(), currentTimeInSeconds + secondsPassed + "");
			long currentTimeInSecondsDay = getStoredPlayerPlaytime(player, dayOfYear);
			set("playtime.dayOfYear." + dayOfYear, player.getUniqueId().toString(), currentTimeInSecondsDay + secondsPassed + "");
			set("playtime.lastKnownDayOfYear", player.getUniqueId().toString(), dayOfYear + "");
		}
	}

	public void updatePlaytime(Player player) {
		handle_QuitEvent(player);
		handle_JoinEvent(player);
	}

	public boolean onInventoryClick(InventoryClickEvent event) {
		Player player = (Player) event.getWhoClicked();
		if (!(event.getInventory().getTitle() == null) && event.getInventory().getTitle().endsWith("of " + player.getName())) {
			if (!(event.getCurrentItem() == null) && event.getCurrentItem().getType() == Material.EMERALD) {
				if (event.getCurrentItem().hasItemMeta()) {
					String itmDisplayName = event.getCurrentItem().getItemMeta().getDisplayName();
					int action = -2;
					if (itmDisplayName.equals("View previous month")) {
						action = -1;
					} else if (itmDisplayName.equals("View following month")) {
						action = 1;
					}
					if (action > -2) {
						String st = ChatColor.stripColor(event.getInventory().getTitle());
						String month = st.substring(st.indexOf(" ") + 1);
						String targetPlayerName = month.substring(month.indexOf(" of ") + 4);
						month = month.substring(0, month.indexOf(" "));
						int monthInt = (Link$.retrieveMonthId(month) + action) % 12;
						while (monthInt < 0)
							monthInt = 12 - Math.abs(monthInt);
						openComplexInventory(player, CraftGo.Player.getOfflinePlayer(targetPlayerName), monthInt);
					}
				}
			}
			return true;
		}
		return false;
	}

	public void openComplexInventory(Player player, OfflinePlayer targetPlayer, int monthId) {
		long delay = 0L;
		if (CraftGo.Player.isPocketPlayer(player)) {
			player.closeInventory();
			delay = LinkServer.getInventoryUpdateDelay();
		}
		Bukkit.getScheduler().runTaskLater(LinkServer.getPlugin(), new Runnable() {
			@Override
			public void run() {
				Calendar currentTime = Calendar.getInstance();
				currentTime.setTimeInMillis(System.currentTimeMillis());
				final int exactDayOfYear = currentTime.get(Calendar.DAY_OF_YEAR);
				Calendar calendar = Calendar.getInstance();
				calendar.set(Calendar.DAY_OF_MONTH, 0);
				calendar.set(Calendar.MONTH, monthId);
				calendar.set(Calendar.YEAR, currentTime.get(Calendar.YEAR));
				long[] range7Day = LinkServer.getPlaytimeManager().getRangeOfStoredPlayerPlaytime(targetPlayer, exactDayOfYear - 6, exactDayOfYear + 1);
				long totalTimePlayedInSeconds = 0L;
				for (int i = 0; i < range7Day.length; i++)
					totalTimePlayedInSeconds += range7Day[i];
				int invSize = 45;
				if (CraftGo.Player.isPocketPlayer(player))
					invSize = 54;
				Inventory inventory = Bukkit.createInventory(null, invSize, ChatColor.RESET + " " + Link$.formatMonthId(monthId) + " of " + targetPlayer.getName());
				String day7total = ChatColor.RESET + Link$.formatTime((int) totalTimePlayedInSeconds) + " during the past 7 days.";
				ItemStack viewPrevious = Link$.createMaterial(Material.EMERALD, "View previous month");
				viewPrevious = Link$.addLore(viewPrevious, new String[]{day7total});
				ItemStack viewFollowing = Link$.createMaterial(Material.EMERALD, "View following month");
				viewFollowing = Link$.addLore(viewFollowing, new String[]{day7total});
				inventory.setItem(18, viewPrevious);
				inventory.setItem(26, viewFollowing);
				for (int i = 0; i < inventory.getSize(); i++) {
					int horizontal = i % 7;
					int line = (int) (Math.floor(i / 7) + 1);
					int slot = (horizontal + 1) + (9 * (line - 1));
					if (slot < inventory.getSize()) {
						int dayOfMonth = i + 1;
						calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
						int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
						int monthOf = calendar.get(Calendar.MONTH);
						calendar.set(Calendar.DAY_OF_MONTH, 0);
						if (monthOf == monthId) {
							long timePlayedInSeconds = getStoredPlayerPlaytime(targetPlayer, dayOfYear);
							calendar.set(Calendar.DAY_OF_YEAR, dayOfYear);
							calendar.set(Calendar.SECOND, 0);
							calendar.add(Calendar.SECOND, (int) timePlayedInSeconds);
							String dn = ChatColor.RESET + Link$.formatTime((int) timePlayedInSeconds) + " on " + Link$.basicFormatCalendarTime(calendar);
							ItemStack itm = null;
							if (timePlayedInSeconds > 0) {
								itm = Link$.createMaterial(Material.COOKED_BEEF, dn);
							} else {
								itm = Link$.createMaterial(Material.COOKED_PORKCHOP, dn);
							}
							if (dayOfYear == exactDayOfYear) {
								if (CraftGo.Player.getProtocolVersion(player) > 314) {
									itm.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1);
								} else {
									itm.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 1);
								}
							}
							inventory.setItem(slot, itm);
						}
					}
				}
				player.openInventory(inventory);
			}
		}, delay);
	}
}
