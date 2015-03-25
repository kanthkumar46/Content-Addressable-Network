import java.util.HashMap;
import java.util.Map;

public class Neighbours {
	private Map<String,String> neighboursList = new HashMap<>();

	public Map<String,String> getNeighboursList() {
		return neighboursList;
	}

	public void setNeighboursList(Map<String,String> neighboursList) {
		this.neighboursList = neighboursList;
	}	
}
