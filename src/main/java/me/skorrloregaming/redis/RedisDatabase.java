package me.skorrloregaming.redis;

import com.google.gson.Gson;
import me.skorrloregaming.LinkServer;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Optional;

public class RedisDatabase {

	private Optional<JedisPool> jedisPool = Optional.empty();

	private RedisDatabase instance;

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

	private RedisDatabase getInstance() {
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
		LinkServer.getPlugin().getLogger().info("Connected to Redis!");
	}

	public void unregister() {
		close();
	}

	public String set(String table, String key, String value) {
		return jedisPool.get().getResource().set(table + "." + key, value);
	}

	public String getString(String table, String key) {
		return jedisPool.get().getResource().get(table + "." + key);
	}

	public boolean contains(String table, String key) {
		String response;
		return (response = getString(table, key)) == null || response.length() == 0;
	}
}