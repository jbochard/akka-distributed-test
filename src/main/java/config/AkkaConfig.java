package config;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;

public class AkkaConfig {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Map<String, Object> loadClusterInfo() {
		Path clusterFile = Paths.get("/etc/cluster.info");
		if (Files.exists(clusterFile, LinkOption.NOFOLLOW_LINKS)) {
			try {
				Gson gson = new Gson();
				Map json = gson.fromJson(new FileReader("/etc/cluster.info"), Map.class);
				return json;
			} catch (JsonSyntaxException | JsonIOException | FileNotFoundException e) {	
				return null;
			}
		}
		return null;
	}
	
	@SuppressWarnings("rawtypes")
	public static ConfigValue getTcpSection(int port, Map<String, Object> clusterInfo) {
		Map<String, Object> res = new HashMap<String, Object>();
		String hostName = "127.0.0.1";
		
		if (clusterInfo != null) {
			hostName = ((Map) clusterInfo.get("instanceInfo")).get("trafficAddr").toString();
		}
		res.put("port", port);
		res.put("hostname", hostName);
		return ConfigValueFactory.fromMap(res);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static ConfigValue getSeedNodes(String clusterName, int port, Map<String, Object> clusterInfo) {
		List<String> res = new ArrayList<String>();
		
		if (clusterInfo != null) {
			String hostName = ((Map) clusterInfo.get("instanceInfo")).get("trafficAddr").toString();
			res.add("akka.tcp://" + clusterName + "@" + hostName + ":" + port);
			
			for (Map instance : ((List<Map>) clusterInfo.get("instances"))) {
				String host = instance.get("trafficAddr").toString();
				res.add("akka.tcp://" + clusterName + "@" + host + ":" + port);				
			}
		} else {
			res.add("akka.tcp://" + clusterName + "@127.0.0.1:" + port); 
		}
		Collections.sort(res);
		System.out.println(res);
		return ConfigValueFactory.fromAnyRef(res);		
	}
}
